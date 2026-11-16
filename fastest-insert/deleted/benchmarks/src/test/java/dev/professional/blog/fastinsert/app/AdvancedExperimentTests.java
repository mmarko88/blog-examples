package dev.professional.blog.fastinsert.app;

import dev.professional.blog.fastinsert.common.Person;
import dev.professional.blog.fastinsert.common.PersonRepository;
import dev.professional.blog.fastinsert.common.RandomPersonGenerator;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
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
 * Advanced experiments:
 * 1. Compression (ROW/PAGE) impact on inserts
 * 2. Minimal logging (SIMPLE recovery + TABLOCK on heap)
 * 3. Temp table staging pattern
 * 4. Connection string tuning (sendStringParametersAsUnicode=false)
 * 5. Columnstore index inserts
 */
@SpringBootTest(properties = "spring.config.location=classpath:application.properties")
@SpringJUnitConfig
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Slf4j
class AdvancedExperimentTests {

    @Autowired private PersonRepository personRepository;
    @Autowired private FastInsertService fastInsertService;
    @Autowired private MultipleRunner multipleRunner;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DataSource dataSource;

    @BeforeAll
    void setUp() {
        // Create compressed tables
        jdbcTemplate.execute("""
                IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'person_row_compressed')
                CREATE TABLE person_row_compressed (
                    person_id INT PRIMARY KEY,
                    user_name NVARCHAR(30),
                    first_name NVARCHAR(10),
                    last_name NVARCHAR(15),
                    years INT
                ) WITH (DATA_COMPRESSION = ROW)
                """);

        jdbcTemplate.execute("""
                IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'person_page_compressed')
                CREATE TABLE person_page_compressed (
                    person_id INT PRIMARY KEY,
                    user_name NVARCHAR(30),
                    first_name NVARCHAR(10),
                    last_name NVARCHAR(15),
                    years INT
                ) WITH (DATA_COMPRESSION = PAGE)
                """);

        // Create heap table (no clustered index) for minimal logging
        jdbcTemplate.execute("""
                IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'person_heap')
                BEGIN
                    CREATE TABLE person_heap (
                        person_id INT,
                        user_name NVARCHAR(30),
                        first_name NVARCHAR(10),
                        last_name NVARCHAR(15),
                        years INT
                    )
                END
                """);

        // Create columnstore table
        jdbcTemplate.execute("""
                IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'person_columnstore')
                BEGIN
                    CREATE TABLE person_columnstore (
                        person_id INT,
                        user_name NVARCHAR(30),
                        first_name NVARCHAR(10),
                        last_name NVARCHAR(15),
                        years INT
                    )
                    CREATE CLUSTERED COLUMNSTORE INDEX CCI_person_columnstore
                        ON person_columnstore
                END
                """);
    }

    // ========== 1. Compression Impact ==========

    @Test
    void noCompression_jdbc() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("compress_none", param,
                v -> insertIntoTable(people, 1000, "person"));
        assertTableCount("person", people.size());
    }

    @Test
    void rowCompression_jdbc() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("compress_row", param,
                v -> insertIntoTable(people, 1000, "person_row_compressed"));
        assertTableCount("person_row_compressed", people.size());
    }

    @Test
    void pageCompression_jdbc() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("compress_page", param,
                v -> insertIntoTable(people, 1000, "person_page_compressed"));
        assertTableCount("person_page_compressed", people.size());
    }

    // ========== 2. Minimal Logging (Heap + TABLOCK) ==========

    @Test
    void heapInsert_noTablock() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("heap_noTablock", param,
                v -> insertIntoTable(people, 1000, "person_heap"));
        assertTableCount("person_heap", people.size());
    }

    @Test
    void heapInsert_withTablock() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("heap_tablock", param,
                v -> insertIntoTableWithHint(people, 1000, "person_heap", "TABLOCK"));
        assertTableCount("person_heap", people.size());
    }

    @Test
    void clusteredInsert_withTablock() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("clustered_tablock", param,
                v -> insertIntoTableWithHint(people, 1000, "person", "TABLOCK"));
        assertTableCount("person", people.size());
    }

    // ========== 3. Temp Table Staging ==========

    @Test
    void tempTableStaging_jdbc() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("tempStaging", param,
                v -> insertViaTempTable(people, 1000));
        assertTableCount("person", people.size());
    }

    @Test
    void directInsert_jdbc_baseline() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("directInsert", param,
                v -> insertIntoTable(people, 1000, "person"));
        assertTableCount("person", people.size());
    }

    // ========== 5. Columnstore Index Inserts ==========

    @Test
    void columnstoreInsert_jdbc() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("columnstore", param,
                v -> insertIntoTable(people, 1000, "person_columnstore"));
        assertTableCount("person_columnstore", people.size());
    }

    @Test
    void columnstoreInsert_largeBatch() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 5000, new Date(), 1);
        multipleRunner.execJob("columnstore_5k", param,
                v -> insertIntoTable(people, 5000, "person_columnstore"));
        assertTableCount("person_columnstore", people.size());
    }

    // ========== Helper Methods ==========

    private void insertIntoTable(List<Person> people, int batchSize, String tableName) {
        String sql = "INSERT INTO " + tableName + " (person_id,user_name,first_name,last_name,years) " +
                "VALUES (?,?,?,?,?)";
        truncateTable(tableName);
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

    private void insertIntoTableWithHint(List<Person> people, int batchSize, String tableName, String hint) {
        String sql = "INSERT INTO " + tableName + " WITH (" + hint + ") " +
                "(person_id,user_name,first_name,last_name,years) VALUES (?,?,?,?,?)";
        truncateTable(tableName);
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

    private void insertViaTempTable(List<Person> people, int batchSize) {
        truncateTable("person");
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            // Create temp table (heap, no indexes)
            conn.createStatement().execute(
                    "CREATE TABLE #staging (person_id INT, user_name NVARCHAR(30), " +
                    "first_name NVARCHAR(10), last_name NVARCHAR(15), years INT)");

            // Bulk load into temp table
            String sql = "INSERT INTO #staging (person_id,user_name,first_name,last_name,years) " +
                    "VALUES (?,?,?,?,?)";
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

            // Move from temp to real table in one operation
            conn.createStatement().execute(
                    "INSERT INTO person (person_id,user_name,first_name,last_name,years) " +
                    "SELECT person_id,user_name,first_name,last_name,years FROM #staging");

            // Clean up temp table
            conn.createStatement().execute("DROP TABLE #staging");
            conn.commit();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private void truncateTable(String tableName) {
        try {
            jdbcTemplate.execute("TRUNCATE TABLE " + tableName);
        } catch (Exception e) {
            jdbcTemplate.execute("DELETE FROM " + tableName);
        }
    }

    private void assertTableCount(String tableName, int expected) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Long.class);
        Assertions.assertThat(count).isEqualTo(expected);
    }
}
