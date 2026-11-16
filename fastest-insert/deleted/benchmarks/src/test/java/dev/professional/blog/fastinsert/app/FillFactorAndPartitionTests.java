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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Date;
import java.util.List;

/**
 * Fill factor tuning + partitioning + SWITCH experiments.
 *
 * Fill factor controls how full each index page is filled during index creation
 * or rebuild. Low fill factor = more empty space = fewer page splits during
 * updates/inserts, at the cost of more pages (more I/O for reads).
 *
 * Partitioning + SWITCH: load into a staging table (same schema, same filegroup),
 * then atomically SWITCH the data into a partition of the main table.
 */
@SpringBootTest(properties = "spring.config.location=classpath:application.properties")
@SpringJUnitConfig
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Slf4j
class FillFactorAndPartitionTests {

    @Autowired private PersonRepository personRepository;
    @Autowired private FastInsertService fastInsertService;
    @Autowired private MultipleRunner multipleRunner;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DataSource dataSource;

    private boolean partitioningSupported = false;

    @BeforeAll
    void setUp() {
        // Partitioned table setup (if supported)
        try {
            jdbcTemplate.execute(
                    "IF NOT EXISTS (SELECT * FROM sys.partition_functions WHERE name = 'pf_person') " +
                    "CREATE PARTITION FUNCTION pf_person (INT) AS RANGE LEFT FOR VALUES " +
                    "(25000, 50000, 75000)");
            jdbcTemplate.execute(
                    "IF NOT EXISTS (SELECT * FROM sys.partition_schemes WHERE name = 'ps_person') " +
                    "CREATE PARTITION SCHEME ps_person AS PARTITION pf_person ALL TO ([PRIMARY])");
            jdbcTemplate.execute(
                    "IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'person_partitioned') " +
                    "CREATE TABLE person_partitioned (" +
                    "  person_id INT NOT NULL, user_name NVARCHAR(30), first_name NVARCHAR(10), " +
                    "  last_name NVARCHAR(15), years INT, " +
                    "  CONSTRAINT PK_person_partitioned PRIMARY KEY CLUSTERED (person_id) ON ps_person(person_id) " +
                    ") ON ps_person(person_id)");
            // Staging table for SWITCH — must have same schema + check constraint matching partition
            jdbcTemplate.execute(
                    "IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'person_staging') " +
                    "CREATE TABLE person_staging (" +
                    "  person_id INT NOT NULL, user_name NVARCHAR(30), first_name NVARCHAR(10), " +
                    "  last_name NVARCHAR(15), years INT, " +
                    "  CONSTRAINT PK_person_staging PRIMARY KEY CLUSTERED (person_id), " +
                    "  CONSTRAINT CK_staging_partition CHECK (person_id > 75000)" +
                    ")");
            partitioningSupported = true;
            log.info("Partitioning: SUPPORTED");
        } catch (Exception e) {
            log.warn("Partitioning not supported: {}", e.getMessage());
        }
    }

    @AfterAll
    void cleanup() {
        try { rebuildPrimaryKey(100); } catch (Exception ignored) {}
    }

    private String getPersonPkName() {
        return jdbcTemplate.queryForObject(
                "SELECT name FROM sys.indexes WHERE object_id = OBJECT_ID('person') AND is_primary_key = 1",
                String.class);
    }

    // ========== Fill Factor Tuning ==========

    @ParameterizedTest
    @ValueSource(ints = {50, 70, 90, 100})
    void fillFactor_bulkApi(int fillFactor) {
        rebuildPrimaryKey(fillFactor);
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("fillFactor_" + fillFactor + "_bulkApi", param,
                v -> fastInsertService.insertBulkApi(people, 1000));
        assertCount(people.size());
    }

    @ParameterizedTest
    @ValueSource(ints = {50, 70, 90, 100})
    void fillFactor_jdbcTemplate(int fillFactor) {
        rebuildPrimaryKey(fillFactor);
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("fillFactor_" + fillFactor + "_jdbc", param,
                v -> fastInsertService.insertJdbcTemplateBatch(1000, 1, people));
        assertCount(people.size());
    }

    private void rebuildPrimaryKey(int fillFactor) {
        String pkName = getPersonPkName();
        jdbcTemplate.execute("ALTER INDEX [" + pkName + "] ON person REBUILD WITH (FILLFACTOR = " + fillFactor + ")");
    }

    // ========== Partitioned Table Direct Insert ==========

    @Test
    void partitionedTable_directInsert() {
        if (!partitioningSupported) {
            log.warn("Skipping — partitioning not supported");
            return;
        }
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("partitioned_direct", param,
                v -> insertIntoTable(people, 1000, "person_partitioned"));
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM person_partitioned", Long.class);
        Assertions.assertThat(count).isEqualTo(people.size());
    }

    // ========== SWITCH PARTITION Pattern ==========

    @Test
    void switchPartition_bulkLoad() {
        if (!partitioningSupported) {
            log.warn("Skipping — partitioning not supported");
            return;
        }
        // Generate 100k people with IDs > 75000 (fits in partition 4)
        List<Person> source = RandomPersonGenerator.generateRandomPersons(100_000);
        List<Person> people = new java.util.ArrayList<>(source.size());
        for (int i = 0; i < source.size(); i++) {
            Person s = source.get(i);
            people.add(new Person(75001 + i, s.getUserName(), s.getFirstName(), s.getLastName(), s.getYears()));
        }
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("switch_bulkLoad", param, v -> switchLoad(people, 1000));
    }

    private void switchLoad(List<Person> people, int batchSize) {
        try (Connection conn = dataSource.getConnection()) {
            // 1. Clean target partition (SWITCH out any existing data)
            conn.setAutoCommit(true);
            conn.createStatement().execute("TRUNCATE TABLE person_staging");
            try {
                conn.createStatement().execute(
                        "ALTER TABLE person_partitioned SWITCH PARTITION 4 TO person_staging");
            } catch (Exception ignored) { /* already empty */ }
            conn.createStatement().execute("TRUNCATE TABLE person_staging");

            // 2. Bulk load into staging (heap-fast)
            String sql = "INSERT INTO person_staging (person_id,user_name,first_name,last_name,years) " +
                    "VALUES (?,?,?,?,?)";
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                int count = 0;
                for (Person p : people) {
                    ps.setInt(1, p.getPersonId());
                    ps.setString(2, p.getUserName());
                    ps.setString(3, p.getFirstName());
                    ps.setString(4, p.getLastName());
                    ps.setInt(5, p.getYears());
                    ps.addBatch();
                    if (++count % batchSize == 0) ps.executeBatch();
                }
                if (count % batchSize != 0) ps.executeBatch();
                conn.commit();
            }

            // 3. SWITCH staging into partition (metadata-only operation)
            conn.setAutoCommit(true);
            conn.createStatement().execute(
                    "ALTER TABLE person_staging SWITCH TO person_partitioned PARTITION 4");
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private void insertIntoTable(List<Person> people, int batchSize, String tableName) {
        String sql = "INSERT INTO " + tableName + " (person_id,user_name,first_name,last_name,years) " +
                "VALUES (?,?,?,?,?)";
        try {
            jdbcTemplate.execute("TRUNCATE TABLE " + tableName);
        } catch (Exception e) {
            jdbcTemplate.execute("DELETE FROM " + tableName);
        }
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
