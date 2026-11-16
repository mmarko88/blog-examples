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
 * Final experiments:
 *  1. XML column indexes (primary + secondary) impact on inserts
 *  2. JSON indexes (computed column + non-clustered) impact on inserts
 *  3. tempdb spill — restricted memory grant forcing a sort to spill
 */
@SpringBootTest(properties = "spring.config.location=classpath:application.properties")
@SpringJUnitConfig
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Slf4j
class XmlJsonAndSpillTests {

    @Autowired private PersonRepository personRepository;
    @Autowired private MultipleRunner multipleRunner;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DataSource dataSource;

    @BeforeAll
    void setUp() {
        // XML table with indexable XML column
        jdbcTemplate.execute("""
                IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'person_xml_idx')
                CREATE TABLE person_xml_idx (
                    person_id INT PRIMARY KEY,
                    user_name NVARCHAR(30),
                    data XML
                )""");
        // JSON table with computed column + index
        jdbcTemplate.execute("""
                IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'person_json_idx')
                CREATE TABLE person_json_idx (
                    person_id INT PRIMARY KEY,
                    user_name NVARCHAR(30),
                    data NVARCHAR(MAX),
                    years_from_json AS JSON_VALUE(data, '$.years')
                )""");

        // Pre-create a staging table for spill test (sort source)
        jdbcTemplate.execute("""
                IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'person_spill_src')
                CREATE TABLE person_spill_src (
                    person_id INT, user_name NVARCHAR(30),
                    first_name NVARCHAR(10), last_name NVARCHAR(15), years INT
                )""");
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM person_spill_src", Long.class);
        if (count == null || count < 100_000) {
            jdbcTemplate.execute("TRUNCATE TABLE person_spill_src");
            List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
            execBatch(people, 1000,
                    "INSERT INTO person_spill_src (person_id,user_name,first_name,last_name,years) VALUES (?,?,?,?,?)");
        }
    }

    @AfterAll
    void cleanup() {
        try { jdbcTemplate.execute("DROP INDEX IF EXISTS PXML_person_xml_idx_data ON person_xml_idx"); } catch (Exception ignored) {}
    }

    // ========== 1. XML indexes ==========

    @Test
    void xml_noIndex() {
        dropXmlIndex();
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("xml_noIndex", param, v -> insertXml(people, 1000));
        assertTable("person_xml_idx", people.size());
    }

    @Test
    void xml_primaryIndex() {
        dropXmlIndex();
        // Primary XML index — large shredded node-table index
        jdbcTemplate.execute("CREATE PRIMARY XML INDEX PXML_person_xml_idx_data ON person_xml_idx(data)");
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("xml_primary", param, v -> insertXml(people, 1000));
        assertTable("person_xml_idx", people.size());
        dropXmlIndex();
    }

    private void dropXmlIndex() {
        try { jdbcTemplate.execute("DROP INDEX IF EXISTS PXML_person_xml_idx_data ON person_xml_idx"); } catch (Exception ignored) {}
    }

    private void insertXml(List<Person> people, int batchSize) {
        truncate("person_xml_idx");
        String sql = "INSERT INTO person_xml_idx (person_id, user_name, data) VALUES (?, ?, ?)";
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                int c = 0;
                for (Person p : people) {
                    ps.setInt(1, p.getPersonId());
                    ps.setString(2, p.getUserName());
                    ps.setString(3, String.format("<person><first>%s</first><last>%s</last><years>%d</years></person>",
                            p.getFirstName(), p.getLastName(), p.getYears()));
                    ps.addBatch();
                    if (++c % batchSize == 0) ps.executeBatch();
                }
                if (c % batchSize != 0) ps.executeBatch();
                conn.commit();
            }
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    // ========== 2. JSON indexes ==========

    @Test
    void json_noIndex() {
        dropJsonIndex();
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("json_noIndex", param, v -> insertJson(people, 1000));
        assertTable("person_json_idx", people.size());
    }

    @Test
    void json_computedIndex() {
        dropJsonIndex();
        jdbcTemplate.execute("CREATE INDEX IX_person_json_idx_years ON person_json_idx(years_from_json)");
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("json_computedIndex", param, v -> insertJson(people, 1000));
        assertTable("person_json_idx", people.size());
        dropJsonIndex();
    }

    private void dropJsonIndex() {
        try { jdbcTemplate.execute("DROP INDEX IF EXISTS IX_person_json_idx_years ON person_json_idx"); } catch (Exception ignored) {}
    }

    private void insertJson(List<Person> people, int batchSize) {
        truncate("person_json_idx");
        String sql = "INSERT INTO person_json_idx (person_id, user_name, data) VALUES (?, ?, ?)";
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                int c = 0;
                for (Person p : people) {
                    ps.setInt(1, p.getPersonId());
                    ps.setString(2, p.getUserName());
                    ps.setString(3, String.format("{\"first\":\"%s\",\"last\":\"%s\",\"years\":%d}",
                            p.getFirstName(), p.getLastName(), p.getYears()));
                    ps.addBatch();
                    if (++c % batchSize == 0) ps.executeBatch();
                }
                if (c % batchSize != 0) ps.executeBatch();
                conn.commit();
            }
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    // ========== 3. tempdb spill ==========
    // Use MIN_GRANT_PERCENT or MAX_GRANT_PERCENT query hints to force a tiny memory grant;
    // combined with ORDER BY on a non-indexed column, the sort will spill to tempdb.

    @Test
    void spill_normalGrant() {
        TestParam param = new TestParam(RandomPersonGenerator.generateRandomPersons(1), 100_000, new Date(), 1);
        multipleRunner.execJob("spill_normalGrant", param, v -> {
            truncate("person");
            jdbcTemplate.execute(
                    "INSERT INTO person (person_id,user_name,first_name,last_name,years) " +
                    "SELECT person_id,user_name,first_name,last_name,years FROM person_spill_src " +
                    "ORDER BY last_name, first_name");
        });
        assertPersonCount(100_000);
    }

    @Test
    void spill_tinyGrant() {
        TestParam param = new TestParam(RandomPersonGenerator.generateRandomPersons(1), 100_000, new Date(), 1);
        multipleRunner.execJob("spill_tinyGrant", param, v -> {
            truncate("person");
            jdbcTemplate.execute(
                    "INSERT INTO person (person_id,user_name,first_name,last_name,years) " +
                    "SELECT person_id,user_name,first_name,last_name,years FROM person_spill_src " +
                    "ORDER BY last_name, first_name " +
                    "OPTION (MIN_GRANT_PERCENT = 0, MAX_GRANT_PERCENT = 0.001)");
        });
        assertPersonCount(100_000);
    }

    // ========== Helpers ==========

    private void execBatch(List<Person> people, int batchSize, String sql) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                int c = 0;
                for (Person p : people) {
                    ps.setInt(1, p.getPersonId());
                    ps.setString(2, p.getUserName());
                    ps.setString(3, p.getFirstName());
                    ps.setString(4, p.getLastName());
                    ps.setInt(5, p.getYears());
                    ps.addBatch();
                    if (++c % batchSize == 0) ps.executeBatch();
                }
                if (c % batchSize != 0) ps.executeBatch();
                conn.commit();
            }
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private void truncate(String table) {
        try { jdbcTemplate.execute("TRUNCATE TABLE " + table); }
        catch (Exception e) { jdbcTemplate.execute("DELETE FROM " + table); }
    }

    private void assertTable(String table, int expected) {
        Long c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        Assertions.assertThat(c).isEqualTo(expected);
    }

    private void assertPersonCount(int expected) {
        long c = personRepository.count();
        Assertions.assertThat(c).isEqualTo(expected);
    }
}
