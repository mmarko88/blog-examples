package dev.professional.blog.fastinsert.app;

import dev.professional.blog.fastinsert.common.Person;
import dev.professional.blog.fastinsert.common.PersonRepository;
import dev.professional.blog.fastinsert.common.RandomPersonGenerator;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.*;

/**
 * Advanced patterns:
 *  1. Snapshot isolation (READ_COMMITTED_SNAPSHOT)
 *  2. MERGE vs INSERT (upsert cost)
 *  3. Trigger overhead (AFTER INSERT)
 *  4. Async producer-consumer pipeline
 */
@SpringBootTest(properties = "spring.config.location=classpath:application.properties")
@SpringJUnitConfig
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Slf4j
class AdvancedPatternTests {

    @Autowired private PersonRepository personRepository;
    @Autowired private FastInsertService fastInsertService;
    @Autowired private MultipleRunner multipleRunner;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DataSource dataSource;

    private boolean rcsiEnabled = false;

    @BeforeAll
    void setUp() {
        // Create trigger table
        jdbcTemplate.execute("""
                IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'person_trigger')
                CREATE TABLE person_trigger (
                    person_id INT PRIMARY KEY,
                    user_name NVARCHAR(30),
                    first_name NVARCHAR(10),
                    last_name NVARCHAR(15),
                    years INT
                )""");
        jdbcTemplate.execute("""
                IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'person_audit')
                CREATE TABLE person_audit (
                    audit_id INT IDENTITY(1,1) PRIMARY KEY,
                    person_id INT,
                    action NVARCHAR(10),
                    audit_time DATETIME2 DEFAULT GETDATE()
                )""");
        jdbcTemplate.execute("""
                IF EXISTS (SELECT * FROM sys.triggers WHERE name = 'tr_person_trigger_insert')
                DROP TRIGGER tr_person_trigger_insert""");
        jdbcTemplate.execute("""
                CREATE TRIGGER tr_person_trigger_insert ON person_trigger
                AFTER INSERT AS
                BEGIN
                    SET NOCOUNT ON;
                    INSERT INTO person_audit (person_id, action)
                    SELECT person_id, 'INSERT' FROM inserted;
                END""");
    }

    @AfterAll
    void cleanup() {
        try {
            jdbcTemplate.execute("ALTER DATABASE testdb SET READ_COMMITTED_SNAPSHOT OFF WITH ROLLBACK IMMEDIATE");
        } catch (Exception ignored) {}
    }

    // ========== 1. Snapshot Isolation ==========

    @Test
    void rcsi_off_bulkApi() {
        setRcsi(false);
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("rcsi_off_bulkApi", param,
                v -> fastInsertService.insertBulkApi(people, 1000));
        assertCount(people.size());
    }

    @Test
    void rcsi_off_jdbc() {
        setRcsi(false);
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("rcsi_off_jdbc", param,
                v -> fastInsertService.insertJdbcTemplateBatch(1000, 1, people));
        assertCount(people.size());
    }

    @Test
    void rcsi_on_bulkApi() {
        setRcsi(true);
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("rcsi_on_bulkApi", param,
                v -> fastInsertService.insertBulkApi(people, 1000));
        assertCount(people.size());
    }

    @Test
    void rcsi_on_jdbc() {
        setRcsi(true);
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("rcsi_on_jdbc", param,
                v -> fastInsertService.insertJdbcTemplateBatch(1000, 1, people));
        assertCount(people.size());
    }

    private void setRcsi(boolean on) {
        try {
            jdbcTemplate.execute("ALTER DATABASE testdb SET READ_COMMITTED_SNAPSHOT " +
                    (on ? "ON" : "OFF") + " WITH ROLLBACK IMMEDIATE");
            rcsiEnabled = on;
            log.info("READ_COMMITTED_SNAPSHOT = {}", on ? "ON" : "OFF");
        } catch (Exception e) {
            log.warn("Could not toggle RCSI: {}", e.getMessage());
        }
    }

    // ========== 2. MERGE vs INSERT ==========

    @Test
    void plainInsert_baseline() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("merge_plain_insert", param,
                v -> insertPlainBatch(people, 1000));
        assertCount(people.size());
    }

    @Test
    void mergeInsert_sameRowCount() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("merge_upsert", param,
                v -> mergeBatch(people, 1000));
        assertCount(people.size());
    }

    private void insertPlainBatch(List<Person> people, int batchSize) {
        truncate("person");
        String sql = "INSERT INTO person (person_id,user_name,first_name,last_name,years) VALUES (?,?,?,?,?)";
        execBatch(people, batchSize, sql);
    }

    private void mergeBatch(List<Person> people, int batchSize) {
        truncate("person");
        // MERGE for upsert — INSERT if not exists, UPDATE if exists
        String sql = """
                MERGE INTO person AS tgt
                USING (SELECT ? AS person_id, ? AS user_name, ? AS first_name, ? AS last_name, ? AS years) AS src
                ON tgt.person_id = src.person_id
                WHEN MATCHED THEN UPDATE SET user_name = src.user_name, first_name = src.first_name,
                                              last_name = src.last_name, years = src.years
                WHEN NOT MATCHED THEN INSERT (person_id, user_name, first_name, last_name, years)
                                      VALUES (src.person_id, src.user_name, src.first_name, src.last_name, src.years);
                """;
        execBatch(people, batchSize, sql);
    }

    // ========== 3. Trigger Overhead ==========

    @Test
    void noTrigger_baseline() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("trigger_none", param,
                v -> insertPlainBatch(people, 1000));
        assertCount(people.size());
    }

    @Test
    void withTrigger_afterInsert() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("trigger_afterInsert", param,
                v -> insertIntoTrigger(people, 1000));
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM person_trigger", Long.class);
        Assertions.assertThat(count).isEqualTo(people.size());
    }

    private void insertIntoTrigger(List<Person> people, int batchSize) {
        truncate("person_audit");
        truncate("person_trigger");
        String sql = "INSERT INTO person_trigger (person_id,user_name,first_name,last_name,years) VALUES (?,?,?,?,?)";
        execBatch(people, batchSize, sql);
    }

    // ========== 4. Async / Producer-Consumer Pipeline ==========

    @Test
    void sync_baseline() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("async_sync_baseline", param, v -> {
            // Simulate "work" of generation + insert in sequence
            List<Person> generated = new ArrayList<>(people.size());
            for (Person p : people) {
                // Simulate some per-record work
                generated.add(p);
            }
            insertPlainBatch(generated, 1000);
        });
        assertCount(people.size());
    }

    @Test
    void async_producerConsumer() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("async_pipeline", param,
                v -> runProducerConsumer(people, 1000));
        assertCount(people.size());
    }

    /**
     * Producer-consumer: 1 producer thread fills a bounded queue in batches of 1000;
     * 1 consumer thread reads from the queue and inserts.
     * This overlaps generation/CPU work with DB I/O.
     */
    private void runProducerConsumer(List<Person> people, int batchSize) {
        truncate("person");
        BlockingQueue<List<Person>> queue = new ArrayBlockingQueue<>(4);
        List<Person> POISON = List.of();
        ExecutorService exec = Executors.newFixedThreadPool(2);

        Future<?> producer = exec.submit(() -> {
            try {
                List<Person> batch = new ArrayList<>(batchSize);
                for (Person p : people) {
                    batch.add(p);
                    if (batch.size() == batchSize) {
                        queue.put(batch);
                        batch = new ArrayList<>(batchSize);
                    }
                }
                if (!batch.isEmpty()) queue.put(batch);
                queue.put(POISON);
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });

        Future<?> consumer = exec.submit(() -> {
            String sql = "INSERT INTO person (person_id,user_name,first_name,last_name,years) VALUES (?,?,?,?,?)";
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    while (true) {
                        List<Person> batch = queue.take();
                        if (batch == POISON) break;
                        for (Person p : batch) bindAndAdd(ps, p);
                        ps.executeBatch();
                    }
                    conn.commit();
                }
            } catch (Exception e) { throw new RuntimeException(e); }
        });

        try {
            producer.get();
            consumer.get();
        } catch (Exception e) { throw new RuntimeException(e); }
        finally { exec.shutdown(); }
    }

    // ========== Helpers ==========

    private void execBatch(List<Person> people, int batchSize, String sql) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                int count = 0;
                for (Person p : people) {
                    bindAndAdd(ps, p);
                    if (++count % batchSize == 0) ps.executeBatch();
                }
                if (count % batchSize != 0) ps.executeBatch();
                conn.commit();
            }
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private void bindAndAdd(PreparedStatement ps, Person p) throws Exception {
        ps.setInt(1, p.getPersonId());
        ps.setString(2, p.getUserName());
        ps.setString(3, p.getFirstName());
        ps.setString(4, p.getLastName());
        ps.setInt(5, p.getYears());
        ps.addBatch();
    }

    private void truncate(String table) {
        try {
            jdbcTemplate.execute("TRUNCATE TABLE " + table);
        } catch (Exception e) {
            jdbcTemplate.execute("DELETE FROM " + table);
        }
    }

    private void assertCount(int expected) {
        long count = personRepository.count();
        Assertions.assertThat(count).isEqualTo(expected);
    }
}
