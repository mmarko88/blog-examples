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
 * Index tuning + minimal-logging experiments:
 *  1. Full non-clustered index vs filtered index vs INCLUDE columns
 *  2. Computed columns: persisted vs non-persisted
 *  3. Trace flag 610 (minimally-logged inserts into clustered tables)
 *  4. Delayed durability (async log flush)
 */
@SpringBootTest(properties = "spring.config.location=classpath:application.properties")
@SpringJUnitConfig
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Slf4j
class IndexAndLoggingTests {

    @Autowired private PersonRepository personRepository;
    @Autowired private MultipleRunner multipleRunner;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DataSource dataSource;

    @BeforeAll
    void setUp() {
        // Table with computed columns (persisted + non-persisted)
        jdbcTemplate.execute("""
                IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'person_computed')
                CREATE TABLE person_computed (
                    person_id INT PRIMARY KEY,
                    user_name NVARCHAR(30),
                    first_name NVARCHAR(10),
                    last_name NVARCHAR(15),
                    years INT,
                    full_name AS (first_name + N' ' + last_name) PERSISTED,
                    years_bucket AS (CASE WHEN years < 30 THEN N'young' ELSE N'old' END)
                )""");
    }

    @AfterAll
    void cleanup() {
        try { dropAllIndexes(); } catch (Exception ignored) {}
        try {
            jdbcTemplate.execute("ALTER DATABASE testdb SET DELAYED_DURABILITY = DISABLED");
        } catch (Exception ignored) {}
    }

    // ========== 1. Index Varieties ==========

    @Test
    void baseline_noIndexes() {
        dropAllIndexes();
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("idx_none", param, v -> insertPerson(people, 1000));
        assertCount(people.size());
    }

    @Test
    void fullIndex_single() {
        dropAllIndexes();
        jdbcTemplate.execute("CREATE INDEX IX_person_lastname ON person(last_name)");
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("idx_full_lastname", param, v -> insertPerson(people, 1000));
        assertCount(people.size());
    }

    @Test
    void filteredIndex_single() {
        dropAllIndexes();
        // Only ~17% of rows match (years >= 50)
        jdbcTemplate.execute("CREATE INDEX IX_person_seniors ON person(last_name) WHERE years >= 50");
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("idx_filtered", param, v -> insertPerson(people, 1000));
        assertCount(people.size());
    }

    @Test
    void includeIndex_single() {
        dropAllIndexes();
        // Covering index with INCLUDE instead of multi-column key
        jdbcTemplate.execute(
                "CREATE INDEX IX_person_lastname_inc ON person(last_name) INCLUDE (first_name, years)");
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("idx_include", param, v -> insertPerson(people, 1000));
        assertCount(people.size());
    }

    @Test
    void compositeKeyIndex_single() {
        dropAllIndexes();
        // Multi-column key (comparison baseline for INCLUDE)
        jdbcTemplate.execute(
                "CREATE INDEX IX_person_composite ON person(last_name, first_name, years)");
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("idx_composite", param, v -> insertPerson(people, 1000));
        assertCount(people.size());
    }

    // ========== 2. Computed Columns ==========

    @Test
    void computedColumn_insert() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("computed_cols", param,
                v -> insertComputed(people, 1000));
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM person_computed", Long.class);
        Assertions.assertThat(count).isEqualTo(people.size());
    }

    @Test
    void computedColumn_baseline() {
        dropAllIndexes();
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("computed_baseline", param, v -> insertPerson(people, 1000));
        assertCount(people.size());
    }

    // ========== 3. Trace Flag 610 ==========
    // DBCC TRACEON (610, -1) enables minimally-logged bulk inserts into tables with a clustered index.
    // Default behavior (fully-logged) vs TF 610 (minimally-logged) when combined with TABLOCK.

    @Test
    void tf610_disabled_tablock() {
        dropAllIndexes();
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("tf610_off_tablock", param,
                v -> insertWithTablock(people, 1000));
        assertCount(people.size());
    }

    @Test
    void tf610_enabled_tablock() {
        dropAllIndexes();
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        try {
            jdbcTemplate.execute("DBCC TRACEON (610, -1) WITH NO_INFOMSGS");
            multipleRunner.execJob("tf610_on_tablock", param,
                    v -> insertWithTablock(people, 1000));
            assertCount(people.size());
        } finally {
            jdbcTemplate.execute("DBCC TRACEOFF (610, -1) WITH NO_INFOMSGS");
        }
    }

    // ========== 4. Delayed Durability ==========

    @Test
    void delayedDurability_disabled() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        setDelayedDurability("DISABLED");
        multipleRunner.execJob("dd_disabled", param, v -> insertPerson(people, 1000));
        assertCount(people.size());
    }

    @Test
    void delayedDurability_forced() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        setDelayedDurability("FORCED");
        try {
            multipleRunner.execJob("dd_forced", param, v -> insertPerson(people, 1000));
            assertCount(people.size());
        } finally {
            setDelayedDurability("DISABLED");
        }
    }

    private void setDelayedDurability(String mode) {
        try {
            jdbcTemplate.execute("ALTER DATABASE testdb SET DELAYED_DURABILITY = " + mode);
            log.info("DELAYED_DURABILITY = {}", mode);
        } catch (Exception e) {
            log.warn("Delayed durability setting failed: {}", e.getMessage());
        }
    }

    // ========== Helpers ==========

    private void dropAllIndexes() {
        for (String idx : List.of("IX_person_lastname", "IX_person_seniors",
                "IX_person_lastname_inc", "IX_person_composite")) {
            try { jdbcTemplate.execute("DROP INDEX IF EXISTS " + idx + " ON person"); } catch (Exception ignored) {}
        }
    }

    private void insertPerson(List<Person> people, int batchSize) {
        truncate("person");
        String sql = "INSERT INTO person (person_id,user_name,first_name,last_name,years) VALUES (?,?,?,?,?)";
        execBatch(people, batchSize, sql);
    }

    private void insertComputed(List<Person> people, int batchSize) {
        truncate("person_computed");
        String sql = "INSERT INTO person_computed (person_id,user_name,first_name,last_name,years) VALUES (?,?,?,?,?)";
        execBatch(people, batchSize, sql);
    }

    private void insertWithTablock(List<Person> people, int batchSize) {
        truncate("person");
        String sql = "INSERT INTO person WITH (TABLOCK) (person_id,user_name,first_name,last_name,years) VALUES (?,?,?,?,?)";
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
