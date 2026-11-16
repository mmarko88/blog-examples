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
 * Column-type and statistics experiments:
 *  1. XML column vs NVARCHAR(MAX) vs structured columns
 *  2. JSON (NVARCHAR(MAX) + CHECK ISJSON)
 *  3. Sparse columns (NULL-heavy tables)
 *  4. AUTO_UPDATE_STATISTICS on/off
 */
@SpringBootTest(properties = "spring.config.location=classpath:application.properties")
@SpringJUnitConfig
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Slf4j
class ColumnTypeAndStatsTests {

    @Autowired private PersonRepository personRepository;
    @Autowired private MultipleRunner multipleRunner;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DataSource dataSource;

    @BeforeAll
    void setUp() {
        // XML column table
        jdbcTemplate.execute("""
                IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'person_xml')
                CREATE TABLE person_xml (
                    person_id INT PRIMARY KEY,
                    user_name NVARCHAR(30),
                    data XML
                )""");

        // JSON column (NVARCHAR(MAX) + CHECK)
        jdbcTemplate.execute("""
                IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'person_json')
                CREATE TABLE person_json (
                    person_id INT PRIMARY KEY,
                    user_name NVARCHAR(30),
                    data NVARCHAR(MAX) CHECK (ISJSON(data) = 1)
                )""");

        // Sparse columns — table with 20 sparse NULL-mostly columns
        jdbcTemplate.execute("""
                IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'person_sparse')
                CREATE TABLE person_sparse (
                    person_id INT PRIMARY KEY,
                    user_name NVARCHAR(30),
                    first_name NVARCHAR(10),
                    last_name NVARCHAR(15),
                    years INT,
                    attr1 NVARCHAR(50) SPARSE NULL, attr2 NVARCHAR(50) SPARSE NULL,
                    attr3 NVARCHAR(50) SPARSE NULL, attr4 NVARCHAR(50) SPARSE NULL,
                    attr5 NVARCHAR(50) SPARSE NULL, attr6 NVARCHAR(50) SPARSE NULL,
                    attr7 NVARCHAR(50) SPARSE NULL, attr8 NVARCHAR(50) SPARSE NULL,
                    attr9 NVARCHAR(50) SPARSE NULL, attr10 NVARCHAR(50) SPARSE NULL,
                    attr11 NVARCHAR(50) SPARSE NULL, attr12 NVARCHAR(50) SPARSE NULL,
                    attr13 NVARCHAR(50) SPARSE NULL, attr14 NVARCHAR(50) SPARSE NULL,
                    attr15 NVARCHAR(50) SPARSE NULL, attr16 NVARCHAR(50) SPARSE NULL,
                    attr17 NVARCHAR(50) SPARSE NULL, attr18 NVARCHAR(50) SPARSE NULL,
                    attr19 NVARCHAR(50) SPARSE NULL, attr20 NVARCHAR(50) SPARSE NULL
                )""");

        // Non-sparse counterpart
        jdbcTemplate.execute("""
                IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'person_nonsparse')
                CREATE TABLE person_nonsparse (
                    person_id INT PRIMARY KEY,
                    user_name NVARCHAR(30),
                    first_name NVARCHAR(10),
                    last_name NVARCHAR(15),
                    years INT,
                    attr1 NVARCHAR(50) NULL, attr2 NVARCHAR(50) NULL,
                    attr3 NVARCHAR(50) NULL, attr4 NVARCHAR(50) NULL,
                    attr5 NVARCHAR(50) NULL, attr6 NVARCHAR(50) NULL,
                    attr7 NVARCHAR(50) NULL, attr8 NVARCHAR(50) NULL,
                    attr9 NVARCHAR(50) NULL, attr10 NVARCHAR(50) NULL,
                    attr11 NVARCHAR(50) NULL, attr12 NVARCHAR(50) NULL,
                    attr13 NVARCHAR(50) NULL, attr14 NVARCHAR(50) NULL,
                    attr15 NVARCHAR(50) NULL, attr16 NVARCHAR(50) NULL,
                    attr17 NVARCHAR(50) NULL, attr18 NVARCHAR(50) NULL,
                    attr19 NVARCHAR(50) NULL, attr20 NVARCHAR(50) NULL
                )""");
    }

    @AfterAll
    void cleanup() {
        try {
            jdbcTemplate.execute("ALTER DATABASE testdb SET AUTO_UPDATE_STATISTICS ON");
        } catch (Exception ignored) {}
    }

    // ========== 1. XML column ==========

    @Test
    void xmlColumn_insert() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("xml_insert", param, v -> insertXml(people, 1000));
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM person_xml", Long.class);
        Assertions.assertThat(count).isEqualTo(people.size());
    }

    // ========== 2. JSON column ==========

    @Test
    void jsonColumn_insert() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("json_insert", param, v -> insertJson(people, 1000));
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM person_json", Long.class);
        Assertions.assertThat(count).isEqualTo(people.size());
    }

    // ========== 3. Sparse vs non-sparse ==========

    @Test
    void sparse_mostlyNull() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("sparse_mostlyNull", param,
                v -> insertSparse(people, 1000, "person_sparse", false));
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM person_sparse", Long.class);
        Assertions.assertThat(count).isEqualTo(people.size());
    }

    @Test
    void nonsparse_mostlyNull() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("nonsparse_mostlyNull", param,
                v -> insertSparse(people, 1000, "person_nonsparse", false));
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM person_nonsparse", Long.class);
        Assertions.assertThat(count).isEqualTo(people.size());
    }

    @Test
    void sparse_mostlyFilled() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("sparse_mostlyFilled", param,
                v -> insertSparse(people, 1000, "person_sparse", true));
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM person_sparse", Long.class);
        Assertions.assertThat(count).isEqualTo(people.size());
    }

    @Test
    void nonsparse_mostlyFilled() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("nonsparse_mostlyFilled", param,
                v -> insertSparse(people, 1000, "person_nonsparse", true));
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM person_nonsparse", Long.class);
        Assertions.assertThat(count).isEqualTo(people.size());
    }

    // ========== 4. AUTO_UPDATE_STATISTICS ==========

    @Test
    void stats_autoUpdateOn() {
        setAutoStats(true);
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("stats_on", param, v -> insertPlain(people, 1000));
        assertCount(people.size());
    }

    @Test
    void stats_autoUpdateOff() {
        setAutoStats(false);
        try {
            List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
            TestParam param = new TestParam(people, 1000, new Date(), 1);
            multipleRunner.execJob("stats_off", param, v -> insertPlain(people, 1000));
            assertCount(people.size());
        } finally {
            setAutoStats(true);
        }
    }

    private void setAutoStats(boolean on) {
        try {
            jdbcTemplate.execute("ALTER DATABASE testdb SET AUTO_UPDATE_STATISTICS " + (on ? "ON" : "OFF"));
            log.info("AUTO_UPDATE_STATISTICS = {}", on ? "ON" : "OFF");
        } catch (Exception e) {
            log.warn("stats toggle failed: {}", e.getMessage());
        }
    }

    // ========== 5. SQL Server built-in encryption (ENCRYPTBYPASSPHRASE) ==========
    // Lightweight proxy for Always Encrypted — same AES operations server-side.

    @Test
    void encrypted_insert() {
        jdbcTemplate.execute("""
                IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'person_encrypted')
                CREATE TABLE person_encrypted (
                    person_id INT PRIMARY KEY,
                    user_name_enc VARBINARY(256),
                    first_name_enc VARBINARY(256),
                    last_name_enc VARBINARY(256),
                    years INT
                )""");
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("encrypted_passphrase", param,
                v -> insertEncrypted(people, 1000));
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM person_encrypted", Long.class);
        Assertions.assertThat(count).isEqualTo(people.size());
    }

    @Test
    void encrypted_baseline() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("encrypted_baseline", param, v -> insertPlain(people, 1000));
        assertCount(people.size());
    }

    private void insertEncrypted(List<Person> people, int batchSize) {
        truncate("person_encrypted");
        // SQL Server encrypts 3 columns per row via ENCRYPTBYPASSPHRASE (AES-256)
        String sql = "INSERT INTO person_encrypted (person_id, user_name_enc, first_name_enc, last_name_enc, years) " +
                "VALUES (?, ENCRYPTBYPASSPHRASE('secret-key', ?), " +
                "        ENCRYPTBYPASSPHRASE('secret-key', ?), " +
                "        ENCRYPTBYPASSPHRASE('secret-key', ?), ?)";
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

    // ========== Helpers ==========

    private void insertXml(List<Person> people, int batchSize) {
        truncate("person_xml");
        String sql = "INSERT INTO person_xml (person_id, user_name, data) VALUES (?, ?, ?)";
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                int count = 0;
                for (Person p : people) {
                    ps.setInt(1, p.getPersonId());
                    ps.setString(2, p.getUserName());
                    ps.setString(3, String.format(
                            "<person><first>%s</first><last>%s</last><years>%d</years></person>",
                            p.getFirstName(), p.getLastName(), p.getYears()));
                    ps.addBatch();
                    if (++count % batchSize == 0) ps.executeBatch();
                }
                if (count % batchSize != 0) ps.executeBatch();
                conn.commit();
            }
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private void insertJson(List<Person> people, int batchSize) {
        truncate("person_json");
        String sql = "INSERT INTO person_json (person_id, user_name, data) VALUES (?, ?, ?)";
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                int count = 0;
                for (Person p : people) {
                    ps.setInt(1, p.getPersonId());
                    ps.setString(2, p.getUserName());
                    ps.setString(3, String.format(
                            "{\"first\":\"%s\",\"last\":\"%s\",\"years\":%d}",
                            p.getFirstName(), p.getLastName(), p.getYears()));
                    ps.addBatch();
                    if (++count % batchSize == 0) ps.executeBatch();
                }
                if (count % batchSize != 0) ps.executeBatch();
                conn.commit();
            }
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private void insertSparse(List<Person> people, int batchSize, String tableName, boolean fillAttrs) {
        truncate(tableName);
        // Insert into base columns only (NULLs for the 20 attr columns)
        StringBuilder sql = new StringBuilder("INSERT INTO " + tableName +
                " (person_id, user_name, first_name, last_name, years");
        if (fillAttrs) {
            for (int i = 1; i <= 20; i++) sql.append(", attr").append(i);
        }
        sql.append(") VALUES (?,?,?,?,?");
        if (fillAttrs) sql.append(",?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?");
        sql.append(")");
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                int count = 0;
                for (Person p : people) {
                    ps.setInt(1, p.getPersonId());
                    ps.setString(2, p.getUserName());
                    ps.setString(3, p.getFirstName());
                    ps.setString(4, p.getLastName());
                    ps.setInt(5, p.getYears());
                    if (fillAttrs) {
                        for (int i = 0; i < 20; i++) {
                            ps.setString(6 + i, "attr_value_" + p.getPersonId() + "_" + i);
                        }
                    }
                    ps.addBatch();
                    if (++count % batchSize == 0) ps.executeBatch();
                }
                if (count % batchSize != 0) ps.executeBatch();
                conn.commit();
            }
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private void insertPlain(List<Person> people, int batchSize) {
        truncate("person");
        String sql = "INSERT INTO person (person_id,user_name,first_name,last_name,years) VALUES (?,?,?,?,?)";
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
