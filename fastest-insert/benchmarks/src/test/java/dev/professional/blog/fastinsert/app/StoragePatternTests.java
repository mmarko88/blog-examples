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
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Storage pattern experiments:
 *  1. Change Tracking (lightweight CDC) overhead
 *  2. Temporal (system-versioned) tables
 *  3. TDE (Transparent Data Encryption) impact (if supported)
 *  4. GUID clustered key vs INT clustered key (page split cost)
 */
@SpringBootTest(properties = "spring.config.location=classpath:application.properties")
@SpringJUnitConfig
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Slf4j
class StoragePatternTests {

    @Autowired private PersonRepository personRepository;
    @Autowired private MultipleRunner multipleRunner;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DataSource dataSource;

    private boolean ctEnabled = false;
    private boolean temporalSupported = false;

    @BeforeAll
    void setUp() {
        // Change Tracking enabled at DB level + table level
        try {
            jdbcTemplate.execute(
                    "IF NOT EXISTS (SELECT * FROM sys.change_tracking_databases WHERE database_id = DB_ID()) " +
                    "ALTER DATABASE testdb SET CHANGE_TRACKING = ON (CHANGE_RETENTION = 2 DAYS, AUTO_CLEANUP = ON)");

            jdbcTemplate.execute("""
                    IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'person_ct')
                    CREATE TABLE person_ct (
                        person_id INT PRIMARY KEY,
                        user_name NVARCHAR(30),
                        first_name NVARCHAR(10),
                        last_name NVARCHAR(15),
                        years INT
                    )""");

            // Enable change tracking on person_ct
            jdbcTemplate.execute(
                    "IF NOT EXISTS (SELECT * FROM sys.change_tracking_tables WHERE object_id = OBJECT_ID('person_ct')) " +
                    "ALTER TABLE person_ct ENABLE CHANGE_TRACKING WITH (TRACK_COLUMNS_UPDATED = OFF)");
            ctEnabled = true;
            log.info("Change Tracking: enabled");
        } catch (Exception e) {
            log.warn("Change Tracking setup failed: {}", e.getMessage());
        }

        // Temporal table (system-versioned)
        try {
            jdbcTemplate.execute("""
                    IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'person_temporal')
                    CREATE TABLE person_temporal (
                        person_id INT PRIMARY KEY,
                        user_name NVARCHAR(30),
                        first_name NVARCHAR(10),
                        last_name NVARCHAR(15),
                        years INT,
                        ValidFrom DATETIME2 GENERATED ALWAYS AS ROW START NOT NULL,
                        ValidTo DATETIME2 GENERATED ALWAYS AS ROW END NOT NULL,
                        PERIOD FOR SYSTEM_TIME (ValidFrom, ValidTo)
                    ) WITH (SYSTEM_VERSIONING = ON)""");
            temporalSupported = true;
            log.info("Temporal tables: enabled");
        } catch (Exception e) {
            log.warn("Temporal table setup failed: {}", e.getMessage());
        }

        // GUID-keyed table (clustered)
        jdbcTemplate.execute("""
                IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'person_guid')
                CREATE TABLE person_guid (
                    person_id UNIQUEIDENTIFIER PRIMARY KEY CLUSTERED,
                    user_name NVARCHAR(30),
                    first_name NVARCHAR(10),
                    last_name NVARCHAR(15),
                    years INT
                )""");

        // NEWSEQUENTIALID() guid table (sequential — no page splits)
        jdbcTemplate.execute("""
                IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'person_seqguid')
                CREATE TABLE person_seqguid (
                    person_id UNIQUEIDENTIFIER DEFAULT NEWSEQUENTIALID() PRIMARY KEY CLUSTERED,
                    user_name NVARCHAR(30),
                    first_name NVARCHAR(10),
                    last_name NVARCHAR(15),
                    years INT
                )""");
    }

    @AfterAll
    void cleanup() {
        try {
            jdbcTemplate.execute("ALTER TABLE person_temporal SET (SYSTEM_VERSIONING = OFF)");
        } catch (Exception ignored) {}
    }

    // ========== 1. Change Tracking ==========

    @Test
    void changeTracking_enabled() {
        if (!ctEnabled) {
            log.warn("Skipping — Change Tracking not supported");
            return;
        }
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("ct_enabled", param,
                v -> insertIntoTable(people, 1000, "person_ct"));
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM person_ct", Long.class);
        Assertions.assertThat(count).isEqualTo(people.size());
    }

    @Test
    void changeTracking_baseline() {
        // Baseline — same schema but without change tracking
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("ct_baseline", param,
                v -> insertIntoTable(people, 1000, "person"));
        assertPersonCount(people.size());
    }

    // ========== 2. Temporal Tables ==========

    @Test
    void temporal_insert() {
        if (!temporalSupported) {
            log.warn("Skipping — temporal tables not supported");
            return;
        }
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("temporal_insert", param,
                v -> insertIntoTable(people, 1000, "person_temporal"));
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM person_temporal", Long.class);
        Assertions.assertThat(count).isEqualTo(people.size());
    }

    @Test
    void temporal_baseline() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("temporal_baseline", param,
                v -> insertIntoTable(people, 1000, "person"));
        assertPersonCount(people.size());
    }

    // ========== 3. GUID vs INT clustered key ==========

    @Test
    void clusteredKey_int_sequential() {
        // baseline: INT monotonic (default person table)
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("key_int_sequential", param,
                v -> insertIntoTable(people, 1000, "person"));
        assertPersonCount(people.size());
    }

    @Test
    void clusteredKey_guid_random() {
        // GUID random — causes page splits galore
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("key_guid_random", param,
                v -> insertIntoGuidTable(people, 1000));
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM person_guid", Long.class);
        Assertions.assertThat(count).isEqualTo(people.size());
    }

    @Test
    void clusteredKey_guid_sequential() {
        // Sequential GUID via NEWSEQUENTIALID() — no page splits
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("key_guid_sequential", param,
                v -> insertIntoSeqGuidTable(people, 1000));
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM person_seqguid", Long.class);
        Assertions.assertThat(count).isEqualTo(people.size());
    }

    // ========== Helpers ==========

    private void insertIntoTable(List<Person> people, int batchSize, String tableName) {
        truncate(tableName);
        String sql = "INSERT INTO " + tableName + " (person_id,user_name,first_name,last_name,years) VALUES (?,?,?,?,?)";
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

    private void insertIntoGuidTable(List<Person> people, int batchSize) {
        truncate("person_guid");
        String sql = "INSERT INTO person_guid (person_id,user_name,first_name,last_name,years) VALUES (?,?,?,?,?)";
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                int count = 0;
                for (Person p : people) {
                    // Each row gets a NEW random GUID — pure random key order
                    ps.setString(1, UUID.randomUUID().toString());
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

    private void insertIntoSeqGuidTable(List<Person> people, int batchSize) {
        truncate("person_seqguid");
        // Column has DEFAULT NEWSEQUENTIALID() — don't pass it
        String sql = "INSERT INTO person_seqguid (user_name,first_name,last_name,years) VALUES (?,?,?,?)";
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                int count = 0;
                for (Person p : people) {
                    ps.setString(1, p.getUserName());
                    ps.setString(2, p.getFirstName());
                    ps.setString(3, p.getLastName());
                    ps.setInt(4, p.getYears());
                    ps.addBatch();
                    if (++count % batchSize == 0) ps.executeBatch();
                }
                if (count % batchSize != 0) ps.executeBatch();
                conn.commit();
            }
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private void truncate(String table) {
        try {
            jdbcTemplate.execute("TRUNCATE TABLE " + table);
        } catch (Exception e) {
            jdbcTemplate.execute("DELETE FROM " + table);
        }
    }

    private void assertPersonCount(int expected) {
        long count = personRepository.count();
        Assertions.assertThat(count).isEqualTo(expected);
    }
}
