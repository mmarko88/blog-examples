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
import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;

/**
 * Experiments:
 *  1. IDENTITY column (server-generated) vs app-side ID vs SEQUENCE
 *  2. SET IDENTITY_INSERT ON — manual IDs on an IDENTITY column
 *  3. Foreign-key overhead — trusted vs untrusted FK
 *  4. Batch retry patterns — one bad row behavior
 */
@SpringBootTest(properties = "spring.config.location=classpath:application.properties")
@SpringJUnitConfig
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Slf4j
class IdentityAndFkTests {

    @Autowired private PersonRepository personRepository;
    @Autowired private MultipleRunner multipleRunner;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DataSource dataSource;

    @BeforeAll
    void setUp() {
        // IDENTITY-keyed table
        jdbcTemplate.execute("""
                IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'person_identity')
                CREATE TABLE person_identity (
                    person_id INT IDENTITY(1,1) PRIMARY KEY,
                    user_name NVARCHAR(30), first_name NVARCHAR(10),
                    last_name NVARCHAR(15), years INT
                )""");

        // SEQUENCE object (only create if not exists)
        jdbcTemplate.execute(
                "IF NOT EXISTS (SELECT * FROM sys.sequences WHERE name = 'seq_person') " +
                "CREATE SEQUENCE seq_person START WITH 1 INCREMENT BY 1 CACHE 1000");
        jdbcTemplate.execute("""
                IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'person_sequence')
                CREATE TABLE person_sequence (
                    person_id INT PRIMARY KEY DEFAULT (NEXT VALUE FOR seq_person),
                    user_name NVARCHAR(30), first_name NVARCHAR(10),
                    last_name NVARCHAR(15), years INT
                )""");

        // FK parent + child
        jdbcTemplate.execute("""
                IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'department')
                BEGIN
                    CREATE TABLE department (dept_id INT PRIMARY KEY, name NVARCHAR(50));
                    INSERT INTO department VALUES (1, 'Engineering'), (2, 'Sales'), (3, 'Ops');
                END""");

        jdbcTemplate.execute("""
                IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'person_fk')
                CREATE TABLE person_fk (
                    person_id INT PRIMARY KEY,
                    user_name NVARCHAR(30), first_name NVARCHAR(10),
                    last_name NVARCHAR(15), years INT, dept_id INT NOT NULL,
                    CONSTRAINT FK_person_dept FOREIGN KEY (dept_id) REFERENCES department(dept_id)
                )""");

        jdbcTemplate.execute("""
                IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'person_nofk')
                CREATE TABLE person_nofk (
                    person_id INT PRIMARY KEY,
                    user_name NVARCHAR(30), first_name NVARCHAR(10),
                    last_name NVARCHAR(15), years INT, dept_id INT NOT NULL
                )""");
    }

    @AfterAll
    void cleanup() {
        try { jdbcTemplate.execute("ALTER TABLE person_fk WITH CHECK CHECK CONSTRAINT FK_person_dept"); } catch (Exception ignored) {}
    }

    // ========== 1. ID generation strategies ==========

    @Test
    void id_appGenerated() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("id_appGenerated", param, v -> insertAppId(people, 1000));
        assertCount(people.size(), "person");
    }

    @Test
    void id_identityColumn() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("id_identity", param, v -> insertIdentity(people, 1000));
        assertCount(people.size(), "person_identity");
    }

    @Test
    void id_identityWithGeneratedKeys() {
        // Request generated-key return — common ORM/JPA pattern (expensive!)
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("id_identity_withKeys", param,
                v -> insertIdentityWithKeys(people, 1000));
        assertCount(people.size(), "person_identity");
    }

    @Test
    void id_sequence() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("id_sequence", param, v -> insertSequence(people, 1000));
        assertCount(people.size(), "person_sequence");
    }

    // ========== 2. SET IDENTITY_INSERT ==========

    @Test
    void id_identityExplicit() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("id_identityExplicit", param,
                v -> insertIdentityExplicit(people, 1000));
        assertCount(people.size(), "person_identity");
    }

    // ========== 3. Foreign Keys ==========

    @Test
    void fk_trusted() {
        jdbcTemplate.execute("ALTER TABLE person_fk WITH CHECK CHECK CONSTRAINT FK_person_dept");
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("fk_trusted", param, v -> insertWithFk(people, 1000, "person_fk"));
        assertCount(people.size(), "person_fk");
    }

    @Test
    void fk_disabled() {
        jdbcTemplate.execute("ALTER TABLE person_fk NOCHECK CONSTRAINT FK_person_dept");
        try {
            List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
            TestParam param = new TestParam(people, 1000, new Date(), 1);
            multipleRunner.execJob("fk_disabled", param,
                    v -> insertWithFk(people, 1000, "person_fk"));
            assertCount(people.size(), "person_fk");
        } finally {
            jdbcTemplate.execute("ALTER TABLE person_fk WITH CHECK CHECK CONSTRAINT FK_person_dept");
        }
    }

    @Test
    void fk_noConstraint() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("fk_noConstraint", param,
                v -> insertWithFk(people, 1000, "person_nofk"));
        assertCount(people.size(), "person_nofk");
    }

    // ========== 4. Batch Retry Patterns ==========

    @Test
    void batchRetry_oneBadRow() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(10_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("retry_abortOnError", param,
                v -> insertWithOneBad_abort(people, 1000));
    }

    @Test
    void batchRetry_continueOnError() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(10_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("retry_continueOnError", param,
                v -> insertWithOneBad_perRow(people, 1000));
    }

    // ========== Helpers ==========

    private void insertAppId(List<Person> people, int batchSize) {
        truncate("person");
        String sql = "INSERT INTO person (person_id, user_name, first_name, last_name, years) VALUES (?,?,?,?,?)";
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

    private void insertIdentity(List<Person> people, int batchSize) {
        truncate("person_identity");
        String sql = "INSERT INTO person_identity (user_name, first_name, last_name, years) VALUES (?,?,?,?)";
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

    private void insertIdentityWithKeys(List<Person> people, int batchSize) {
        truncate("person_identity");
        String sql = "INSERT INTO person_identity (user_name, first_name, last_name, years) VALUES (?,?,?,?)";
        // Just open the statement with RETURN_GENERATED_KEYS and let the driver pay the cost;
        // explicitly draining keys trips the Microsoft driver with batched INSERTs.
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
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

    private void insertSequence(List<Person> people, int batchSize) {
        truncate("person_sequence");
        // Default (NEXT VALUE FOR seq_person) kicks in when we don't supply person_id
        String sql = "INSERT INTO person_sequence (user_name, first_name, last_name, years) VALUES (?,?,?,?)";
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

    private void insertIdentityExplicit(List<Person> people, int batchSize) {
        truncate("person_identity");
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            conn.createStatement().execute("SET IDENTITY_INSERT person_identity ON");
            try {
                String sql = "INSERT INTO person_identity (person_id, user_name, first_name, last_name, years) VALUES (?,?,?,?,?)";
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
                }
            } finally {
                conn.createStatement().execute("SET IDENTITY_INSERT person_identity OFF");
            }
            conn.commit();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private void insertWithFk(List<Person> people, int batchSize, String tableName) {
        truncate(tableName);
        String sql = "INSERT INTO " + tableName + " (person_id, user_name, first_name, last_name, years, dept_id) VALUES (?,?,?,?,?,?)";
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
                    ps.setInt(6, 1 + (p.getPersonId() % 3));
                    ps.addBatch();
                    if (++count % batchSize == 0) ps.executeBatch();
                }
                if (count % batchSize != 0) ps.executeBatch();
                conn.commit();
            }
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    // Retry pattern: batch of 10k with 1 duplicate PK — the whole batch aborts
    private void insertWithOneBad_abort(List<Person> people, int batchSize) {
        truncate("person");
        // Insert all but inject a duplicate PK at row 5000
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            // Pre-insert row 42 so our duplicate at index ~5000 collides
            conn.createStatement().execute("INSERT INTO person (person_id, user_name, first_name, last_name, years) VALUES (42, 'preexist', 'Pre', 'Existing', 99)");
            String sql = "INSERT INTO person (person_id, user_name, first_name, last_name, years) VALUES (?,?,?,?,?)";
            int inserted = 0;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                int count = 0;
                for (int i = 0; i < people.size(); i++) {
                    Person p = people.get(i);
                    // Force a duplicate at row 5000
                    int id = (i == 5000) ? 42 : p.getPersonId();
                    ps.setInt(1, id);
                    ps.setString(2, p.getUserName());
                    ps.setString(3, p.getFirstName());
                    ps.setString(4, p.getLastName());
                    ps.setInt(5, p.getYears());
                    ps.addBatch();
                    if (++count % batchSize == 0) {
                        try {
                            ps.executeBatch();
                            inserted += batchSize;
                        } catch (BatchUpdateException e) {
                            log.info("Batch aborted at row {}: {}", i, e.getMessage().substring(0, Math.min(60, e.getMessage().length())));
                            // On abort: entire batch is discarded, we move to next batch
                            ps.clearBatch();
                        }
                    }
                }
                try {
                    ps.executeBatch();
                } catch (BatchUpdateException ignored) { /* last batch */ }
                conn.commit();
                log.info("Abort pattern: successfully inserted ~{} rows", inserted);
            }
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    // Retry pattern: insert one-by-one so we can skip bad rows
    private void insertWithOneBad_perRow(List<Person> people, int batchSize) {
        truncate("person");
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            conn.createStatement().execute("INSERT INTO person (person_id, user_name, first_name, last_name, years) VALUES (42, 'preexist', 'Pre', 'Existing', 99)");
            String sql = "INSERT INTO person (person_id, user_name, first_name, last_name, years) VALUES (?,?,?,?,?)";
            int inserted = 0, skipped = 0;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (int i = 0; i < people.size(); i++) {
                    Person p = people.get(i);
                    int id = (i == 5000) ? 42 : p.getPersonId();
                    ps.setInt(1, id);
                    ps.setString(2, p.getUserName());
                    ps.setString(3, p.getFirstName());
                    ps.setString(4, p.getLastName());
                    ps.setInt(5, p.getYears());
                    try {
                        ps.executeUpdate();
                        inserted++;
                    } catch (SQLException e) {
                        skipped++;
                    }
                }
                conn.commit();
                log.info("Per-row pattern: inserted {}, skipped {}", inserted, skipped);
            }
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    private void truncate(String table) {
        try {
            jdbcTemplate.execute("TRUNCATE TABLE " + table);
        } catch (Exception e) {
            jdbcTemplate.execute("DELETE FROM " + table);
        }
    }

    private void assertCount(int expected, String table) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        Assertions.assertThat(count).isEqualTo(expected);
    }
}
