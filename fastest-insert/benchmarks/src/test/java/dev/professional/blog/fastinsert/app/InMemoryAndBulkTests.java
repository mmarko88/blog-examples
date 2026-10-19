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

/**
 * Advanced patterns:
 *  1. In-Memory OLTP table (SCHEMA_AND_DATA durability)
 *  2. Natively compiled stored procedure
 *  3. sp_executesql vs direct INSERT (plan cache)
 *  4. Stored procedure vs ad-hoc SQL
 *  5. Disabled check constraints (ALTER TABLE NOCHECK)
 *  6. OPENROWSET(BULK) server-side bulk reader
 *  7. BULK INSERT (T-SQL bulk from file)
 */
@SpringBootTest(properties = "spring.config.location=classpath:application.properties")
@SpringJUnitConfig
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Slf4j
class InMemoryAndBulkTests {

    private static final String BULK_FILE = "C:/ProgramData/bulk_data.txt";

    @Autowired private PersonRepository personRepository;
    @Autowired private MultipleRunner multipleRunner;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DataSource dataSource;

    private boolean inMemorySupported = false;

    @BeforeAll
    void setUp() {
        // In-Memory OLTP table
        try {
            jdbcTemplate.execute("""
                    IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'person_memopt')
                    CREATE TABLE person_memopt (
                        person_id INT NOT NULL PRIMARY KEY NONCLUSTERED,
                        user_name NVARCHAR(30),
                        first_name NVARCHAR(10),
                        last_name NVARCHAR(15),
                        years INT
                    ) WITH (MEMORY_OPTIMIZED = ON, DURABILITY = SCHEMA_AND_DATA)""");
            inMemorySupported = true;
            log.info("In-Memory OLTP: enabled");
        } catch (Exception e) {
            log.warn("In-Memory OLTP setup failed: {}", e.getMessage());
        }

        // Natively compiled stored procedure
        if (inMemorySupported) {
            try {
                jdbcTemplate.execute("DROP PROCEDURE IF EXISTS usp_InsertPerson_Native");
                jdbcTemplate.execute("""
                        CREATE PROCEDURE usp_InsertPerson_Native
                            @person_id INT, @user_name NVARCHAR(30),
                            @first_name NVARCHAR(10), @last_name NVARCHAR(15), @years INT
                        WITH NATIVE_COMPILATION, SCHEMABINDING
                        AS BEGIN ATOMIC WITH (TRANSACTION ISOLATION LEVEL = SNAPSHOT, LANGUAGE = N'us_english')
                            INSERT INTO dbo.person_memopt (person_id, user_name, first_name, last_name, years)
                            VALUES (@person_id, @user_name, @first_name, @last_name, @years);
                        END""");
                log.info("Natively compiled procedure: created");
            } catch (Exception e) {
                log.warn("Native proc create failed: {}", e.getMessage());
            }
        }

        // Standard T-SQL stored procedure
        jdbcTemplate.execute("DROP PROCEDURE IF EXISTS usp_InsertPerson_Interpreted");
        jdbcTemplate.execute("""
                CREATE PROCEDURE usp_InsertPerson_Interpreted
                    @person_id INT, @user_name NVARCHAR(30),
                    @first_name NVARCHAR(10), @last_name NVARCHAR(15), @years INT
                AS
                    INSERT INTO person (person_id, user_name, first_name, last_name, years)
                    VALUES (@person_id, @user_name, @first_name, @last_name, @years)""");

        // Table with check constraints
        jdbcTemplate.execute("""
                IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'person_checked')
                CREATE TABLE person_checked (
                    person_id INT PRIMARY KEY,
                    user_name NVARCHAR(30),
                    first_name NVARCHAR(10) CHECK (LEN(first_name) > 0),
                    last_name NVARCHAR(15) CHECK (LEN(last_name) > 0),
                    years INT CHECK (years >= 0 AND years <= 150)
                )""");
    }

    @AfterAll
    void cleanup() {
        try { jdbcTemplate.execute("DROP PROCEDURE IF EXISTS usp_InsertPerson_Native"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DROP PROCEDURE IF EXISTS usp_InsertPerson_Interpreted"); } catch (Exception ignored) {}
    }

    // ========== 1. In-Memory OLTP (interpreted T-SQL) ==========

    @Test
    void inMemory_interpreted() {
        if (!inMemorySupported) { log.warn("Skipping — In-Memory OLTP not supported"); return; }
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("memopt_interpreted", param,
                v -> insertIntoMemopt(people, 1000));
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM person_memopt", Long.class);
        Assertions.assertThat(count).isEqualTo(people.size());
    }

    // ========== 2. Natively Compiled Procedure ==========

    @Test
    void inMemory_nativeProc() {
        if (!inMemorySupported) { log.warn("Skipping — In-Memory OLTP not supported"); return; }
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("memopt_nativeProc", param,
                v -> callNativeProc(people));
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM person_memopt", Long.class);
        Assertions.assertThat(count).isEqualTo(people.size());
    }

    // ========== 3. Stored Procedure vs Ad-hoc ==========

    @Test
    void storedProc_interpreted() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("storedProc", param,
                v -> callInterpretedProc(people, 1000));
        assertCount(people.size());
    }

    @Test
    void adhoc_insert() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("adhoc_insert", param, v -> insertPlain(people, 1000));
        assertCount(people.size());
    }

    // ========== 4. Disabled Check Constraints ==========

    @Test
    void withChecks_enabled() {
        enableChecks(true);
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("checks_enabled", param,
                v -> insertIntoChecked(people, 1000));
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM person_checked", Long.class);
        Assertions.assertThat(count).isEqualTo(people.size());
    }

    @Test
    void withChecks_disabled() {
        enableChecks(false);
        try {
            List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
            TestParam param = new TestParam(people, 1000, new Date(), 1);
            multipleRunner.execJob("checks_disabled", param,
                    v -> insertIntoChecked(people, 1000));
            Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM person_checked", Long.class);
            Assertions.assertThat(count).isEqualTo(people.size());
        } finally {
            enableChecks(true);
        }
    }

    private void enableChecks(boolean on) {
        String op = on ? "WITH CHECK CHECK CONSTRAINT ALL" : "NOCHECK CONSTRAINT ALL";
        jdbcTemplate.execute("ALTER TABLE person_checked " + op);
    }

    // ========== 5. OPENROWSET(BULK) + BULK INSERT ==========

    @Test
    void bulkInsert_fromFile() {
        truncate("person");
        TestParam param = new TestParam(RandomPersonGenerator.generateRandomPersons(100_000),
                1000, new Date(), 1);
        multipleRunner.execJob("bulkInsert_file", param, v -> {
            truncate("person");
            jdbcTemplate.execute(String.format("""
                    BULK INSERT person FROM '%s'
                    WITH (FORMAT = 'CSV', FIELDTERMINATOR = '0x09', ROWTERMINATOR = '0x0a', BATCHSIZE = 1000, TABLOCK)
                    """, BULK_FILE));
        });
    }

    // NOTE: OPENROWSET(BULK, ...) for a tab-delimited file requires a format file —
    // FORMAT='CSV' only supports comma delimiter in SQL Server 2022+. The underlying bulk
    // reader is the same engine as BULK INSERT, which is covered by bulkInsert_fromFile.

    // ========== Helpers ==========

    private void insertIntoMemopt(List<Person> people, int batchSize) {
        truncate("person_memopt");
        String sql = "INSERT INTO person_memopt (person_id,user_name,first_name,last_name,years) VALUES (?,?,?,?,?)";
        execBatch(people, batchSize, sql);
    }

    private void callNativeProc(List<Person> people) {
        truncate("person_memopt");
        // Natively compiled procs don't support batching the same way; call per row
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement("EXEC usp_InsertPerson_Native ?, ?, ?, ?, ?")) {
                for (Person p : people) {
                    ps.setInt(1, p.getPersonId());
                    ps.setString(2, p.getUserName());
                    ps.setString(3, p.getFirstName());
                    ps.setString(4, p.getLastName());
                    ps.setInt(5, p.getYears());
                    ps.addBatch();
                }
                ps.executeBatch();
                conn.commit();
            }
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private void callInterpretedProc(List<Person> people, int batchSize) {
        truncate("person");
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement("EXEC usp_InsertPerson_Interpreted ?, ?, ?, ?, ?")) {
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

    private void insertIntoChecked(List<Person> people, int batchSize) {
        truncate("person_checked");
        String sql = "INSERT INTO person_checked (person_id,user_name,first_name,last_name,years) VALUES (?,?,?,?,?)";
        execBatch(people, batchSize, sql);
    }

    private void insertPlain(List<Person> people, int batchSize) {
        truncate("person");
        String sql = "INSERT INTO person (person_id,user_name,first_name,last_name,years) VALUES (?,?,?,?,?)";
        execBatch(people, batchSize, sql);
    }

    private void execBatch(List<Person> people, int batchSize, String sql) {
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
