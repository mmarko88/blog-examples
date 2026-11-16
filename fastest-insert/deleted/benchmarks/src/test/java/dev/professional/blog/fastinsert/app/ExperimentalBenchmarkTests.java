package dev.professional.blog.fastinsert.app;

import dev.professional.blog.fastinsert.common.Person;
import dev.professional.blog.fastinsert.common.PersonRepository;
import dev.professional.blog.fastinsert.common.RandomPersonGenerator;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Date;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Experimental benchmarks:
 * A) Multi-value sweep: objectsPerInsert from 10 to 1000
 * B) Row size impact: narrow (5 cols, ~60 bytes) vs wide (20 cols, ~2KB) vs LOB (NVARCHAR(MAX))
 * C) Concurrent reads: SELECT queries running during INSERT
 * D) NOLOCK + TABLOCK hints for bulk insert
 */
@SpringBootTest(properties = "spring.config.location=classpath:application.properties")
@SpringJUnitConfig
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Slf4j
class ExperimentalBenchmarkTests {

    @Autowired private PersonRepository personRepository;
    @Autowired private FastInsertService fastInsertService;
    @Autowired private MultipleRunner multipleRunner;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DataSource dataSource;

    @BeforeAll
    void setUp() {
        // Create wide_person table for row-size experiment
        jdbcTemplate.execute("""
                IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'wide_person')
                CREATE TABLE wide_person (
                    person_id INT PRIMARY KEY,
                    user_name NVARCHAR(30),
                    first_name NVARCHAR(10),
                    last_name NVARCHAR(15),
                    years INT,
                    email NVARCHAR(100),
                    phone NVARCHAR(20),
                    address_line1 NVARCHAR(100),
                    address_line2 NVARCHAR(100),
                    city NVARCHAR(50),
                    state NVARCHAR(30),
                    zip_code NVARCHAR(10),
                    country NVARCHAR(50),
                    department NVARCHAR(50),
                    title NVARCHAR(100),
                    company NVARCHAR(100),
                    notes NVARCHAR(500),
                    created_date DATETIME2 DEFAULT GETDATE(),
                    updated_date DATETIME2 DEFAULT GETDATE(),
                    is_active BIT DEFAULT 1
                )
                """);

        // Create lob_person table for LOB experiment
        jdbcTemplate.execute("""
                IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'lob_person')
                CREATE TABLE lob_person (
                    person_id INT PRIMARY KEY,
                    user_name NVARCHAR(30),
                    first_name NVARCHAR(10),
                    last_name NVARCHAR(15),
                    years INT,
                    bio NVARCHAR(MAX),
                    resume NVARCHAR(MAX)
                )
                """);
    }

    // ========== A) Multi-value sweep ==========

    // Max objectsPerInsert = 420 (SQL Server limit: 2100 params / 5 columns = 420)
    @ParameterizedTest
    @ValueSource(ints = {1, 10, 25, 50, 100, 200, 300, 400})
    void multiValueSweep_jdbcTemplate(int objectsPerInsert) {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), objectsPerInsert);
        multipleRunner.execJob("mvSweep_" + objectsPerInsert, param,
                v -> fastInsertService.insertJdbcTemplateBatch(1000, objectsPerInsert, people));
        assertCount(people.size());
    }

    // ========== B) Row size impact ==========

    @Test
    void narrowRow_bulkApi() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("narrow_bulkApi", param,
                v -> fastInsertService.insertBulkApi(people, 1000));
        assertCount(people.size());
    }

    @Test
    void narrowRow_jdbcTemplate() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("narrow_jdbc", param,
                v -> fastInsertService.insertJdbcTemplateBatch(1000, 1, people));
        assertCount(people.size());
    }

    @Test
    void wideRow_jdbcTemplate() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("wide_jdbc", param, v -> insertWideRows(people, 1000));
        long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM wide_person", Long.class);
        Assertions.assertThat(count).isEqualTo(people.size());
    }

    @Test
    void lobRow_jdbcTemplate() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(50_000); // smaller due to LOB size
        TestParam param = new TestParam(people, 500, new Date(), 1);
        multipleRunner.execJob("lob_jdbc", param, v -> insertLobRows(people, 500));
        long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM lob_person", Long.class);
        Assertions.assertThat(count).isEqualTo(people.size());
    }

    private void insertWideRows(List<Person> people, int batchSize) {
        String sql = "INSERT INTO wide_person (person_id,user_name,first_name,last_name,years," +
                "email,phone,address_line1,address_line2,city,state,zip_code,country," +
                "department,title,company,notes) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        jdbcTemplate.execute("TRUNCATE TABLE wide_person");
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                int count = 0;
                for (Person p : people) {
                    ps.setInt(1, p.getPersonId());
                    ps.setString(2, p.getUserName());
                    ps.setString(3, p.getFirstName());
                    ps.setString(4, p.getLastName());
                    ps.setInt(5, p.getYears());
                    ps.setString(6, p.getUserName() + "@example.com");
                    ps.setString(7, "+1-555-" + (1000 + p.getPersonId() % 9000));
                    ps.setString(8, p.getPersonId() + " Main Street");
                    ps.setString(9, "Suite " + (p.getPersonId() % 100));
                    ps.setString(10, "City" + (p.getPersonId() % 50));
                    ps.setString(11, "State" + (p.getPersonId() % 50));
                    ps.setString(12, String.valueOf(10000 + p.getPersonId() % 90000));
                    ps.setString(13, "Country" + (p.getPersonId() % 10));
                    ps.setString(14, "Dept" + (p.getPersonId() % 20));
                    ps.setString(15, "Title " + p.getFirstName());
                    ps.setString(16, "Company" + (p.getPersonId() % 100));
                    ps.setString(17, "Notes for person " + p.getUserName() + " — " + p.getFirstName() + " " + p.getLastName());
                    ps.addBatch();
                    if (++count % batchSize == 0) ps.executeBatch();
                }
                if (count % batchSize != 0) ps.executeBatch();
                conn.commit();
            }
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private void insertLobRows(List<Person> people, int batchSize) {
        String sql = "INSERT INTO lob_person (person_id,user_name,first_name,last_name,years,bio,resume) " +
                "VALUES (?,?,?,?,?,?,?)";
        String loremIpsum = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. ".repeat(20); // ~1.1KB per field
        jdbcTemplate.execute("TRUNCATE TABLE lob_person");
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                int count = 0;
                for (Person p : people) {
                    ps.setInt(1, p.getPersonId());
                    ps.setString(2, p.getUserName());
                    ps.setString(3, p.getFirstName());
                    ps.setString(4, p.getLastName());
                    ps.setInt(5, p.getYears());
                    ps.setString(6, "Bio: " + p.getFirstName() + " " + p.getLastName() + " — " + loremIpsum);
                    ps.setString(7, "Resume: " + loremIpsum + loremIpsum); // ~2.2KB
                    ps.addBatch();
                    if (++count % batchSize == 0) ps.executeBatch();
                }
                if (count % batchSize != 0) ps.executeBatch();
                conn.commit();
            }
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    // ========== C) Concurrent reads during INSERT ==========

    @Test
    void insertWithConcurrentReads_bulkApi() throws Exception {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);

        AtomicBoolean inserting = new AtomicBoolean(true);
        AtomicLong readCount = new AtomicLong(0);
        AtomicLong readTime = new AtomicLong(0);

        // Start 4 reader threads
        ExecutorService readers = Executors.newFixedThreadPool(4);
        for (int i = 0; i < 4; i++) {
            readers.submit(() -> {
                while (inserting.get()) {
                    try (Connection conn = dataSource.getConnection()) {
                        long start = System.currentTimeMillis();
                        ResultSet rs = conn.createStatement().executeQuery(
                                "SELECT COUNT(*), AVG(years) FROM person WITH (NOLOCK)");
                        rs.next();
                        readTime.addAndGet(System.currentTimeMillis() - start);
                        readCount.incrementAndGet();
                        Thread.sleep(10); // don't hammer
                    } catch (Exception ignored) {}
                }
            });
        }

        multipleRunner.execJob("concurrent_bulkApi", param,
                v -> fastInsertService.insertBulkApi(people, 1000));

        inserting.set(false);
        readers.shutdown();
        readers.awaitTermination(10, TimeUnit.SECONDS);

        log.info("Concurrent reads during insert: {} reads, avg {}ms per read",
                readCount.get(), readCount.get() > 0 ? readTime.get() / readCount.get() : 0);
        assertCount(people.size());
    }

    @Test
    void insertWithoutConcurrentReads_bulkApi() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("noConcurrent_bulkApi", param,
                v -> fastInsertService.insertBulkApi(people, 1000));
        assertCount(people.size());
    }

    // ========== D) TABLOCK hint ==========

    @Test
    void insertWithTablock_jdbc() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("tablock_jdbc", param, v -> insertWithTablock(people, 1000));
        assertCount(people.size());
    }

    @Test
    void insertWithoutTablock_jdbc() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("noTablock_jdbc", param, v -> insertWithoutTablock(people, 1000));
        assertCount(people.size());
    }

    private void insertWithTablock(List<Person> people, int batchSize) {
        String sql = "INSERT INTO person WITH (TABLOCK) (person_id,first_name,last_name,user_name,years) " +
                "VALUES (?,?,?,?,?)";
        insertRawJdbc(people, batchSize, sql);
    }

    private void insertWithoutTablock(List<Person> people, int batchSize) {
        String sql = "INSERT INTO person (person_id,first_name,last_name,user_name,years) " +
                "VALUES (?,?,?,?,?)";
        insertRawJdbc(people, batchSize, sql);
    }

    private void insertRawJdbc(List<Person> people, int batchSize, String sql) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                int count = 0;
                for (Person p : people) {
                    ps.setInt(1, p.getPersonId());
                    ps.setString(2, p.getFirstName());
                    ps.setString(3, p.getLastName());
                    ps.setString(4, p.getUserName());
                    ps.setInt(5, p.getYears());
                    ps.addBatch();
                    if (++count % batchSize == 0) ps.executeBatch();
                }
                if (count % batchSize != 0) ps.executeBatch();
                conn.commit();
            }
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private void assertCount(int expected) {
        long count = personRepository.count();
        Assertions.assertThat(count).isEqualTo(expected);
    }
}
