package dev.professional.blog.fastinsert.app;

import com.microsoft.sqlserver.jdbc.SQLServerBulkCopy;
import com.microsoft.sqlserver.jdbc.SQLServerBulkCopyOptions;
import com.microsoft.sqlserver.jdbc.SQLServerConnection;
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
import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetMetaDataImpl;
import javax.sql.rowset.RowSetProvider;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.util.Date;
import java.util.List;

/**
 * Final experiments:
 *  1. OPTION(RECOMPILE) — query plan recompilation per batch
 *  2. Simulated client-side network latency per batch
 *  3. INSERT ... SELECT with ORDER BY — sort during insert
 *  4. SQLServerBulkCopy option tuning (batch size, keep-nulls, check-constraints)
 */
@SpringBootTest(properties = "spring.config.location=classpath:application.properties")
@SpringJUnitConfig
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Slf4j
class PlanAndBulkOptionsTests {

    @Autowired private PersonRepository personRepository;
    @Autowired private MultipleRunner multipleRunner;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DataSource dataSource;

    @BeforeAll
    void setUp() {
        // Staging table for INSERT ... SELECT experiments
        jdbcTemplate.execute("""
                IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'person_staging_sort')
                CREATE TABLE person_staging_sort (
                    person_id INT, user_name NVARCHAR(30), first_name NVARCHAR(10),
                    last_name NVARCHAR(15), years INT
                )""");
        // Pre-load the staging table with 100k rows once
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM person_staging_sort", Long.class);
        if (count == null || count < 100_000) {
            jdbcTemplate.execute("TRUNCATE TABLE person_staging_sort");
            List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
            execBatch(people, 1000,
                    "INSERT INTO person_staging_sort (person_id,user_name,first_name,last_name,years) VALUES (?,?,?,?,?)");
        }
    }

    // ========== 1. OPTION(RECOMPILE) ==========

    @Test
    void plan_baseline() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("plan_cached", param, v -> insertPlain(people, 1000));
        assertCount(people.size(), "person");
    }

    @Test
    void plan_recompile() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("plan_recompile", param, v -> insertWithRecompile(people, 1000));
        assertCount(people.size(), "person");
    }

    // ========== 2. Simulated latency ==========

    @Test
    void latency_zero() {
        // Baseline — same as insertPlain
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("latency_0ms", param, v -> insertPlain(people, 1000));
        assertCount(people.size(), "person");
    }

    @Test
    void latency_1ms_perBatch() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("latency_1ms", param,
                v -> insertWithLatency(people, 1000, 1));
        assertCount(people.size(), "person");
    }

    @Test
    void latency_5ms_perBatch() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("latency_5ms", param,
                v -> insertWithLatency(people, 1000, 5));
        assertCount(people.size(), "person");
    }

    // ========== 3. INSERT ... SELECT with ORDER BY ==========

    @Test
    void insertSelect_noOrderBy() {
        TestParam param = new TestParam(RandomPersonGenerator.generateRandomPersons(1), 100_000, new Date(), 1);
        multipleRunner.execJob("insertSelect_plain", param, v -> {
            truncate("person");
            jdbcTemplate.execute(
                    "INSERT INTO person (person_id,user_name,first_name,last_name,years) " +
                    "SELECT person_id,user_name,first_name,last_name,years FROM person_staging_sort");
        });
        assertCount(100_000, "person");
    }

    @Test
    void insertSelect_withOrderBy() {
        TestParam param = new TestParam(RandomPersonGenerator.generateRandomPersons(1), 100_000, new Date(), 1);
        multipleRunner.execJob("insertSelect_orderBy", param, v -> {
            truncate("person");
            jdbcTemplate.execute(
                    "INSERT INTO person (person_id,user_name,first_name,last_name,years) " +
                    "SELECT person_id,user_name,first_name,last_name,years FROM person_staging_sort " +
                    "ORDER BY last_name, first_name");
        });
        assertCount(100_000, "person");
    }

    // ========== 4. SQLServerBulkCopy option tuning ==========

    @Test
    void bulkCopy_default() {
        runOpts("bulkCopy_default", 1000, false, false);
    }

    @Test
    void bulkCopy_bigBatch() {
        runOpts("bulkCopy_bigBatch", 5000, false, false);
    }

    @Test
    void bulkCopy_tablock() {
        runOpts("bulkCopy_tablock", 1000, true, false);
    }

    @Test
    void bulkCopy_withChecks() {
        runOpts("bulkCopy_checkConstraints", 1000, false, true);
    }

    private void runOpts(String label, int batchSize, boolean tablock, boolean checkConstraints) {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, batchSize, new Date(), 1);
        multipleRunner.execJob(label, param, v -> {
            try {
                SQLServerBulkCopyOptions opts = new SQLServerBulkCopyOptions();
                opts.setBatchSize(batchSize);
                if (tablock) opts.setTableLock(true);
                if (checkConstraints) opts.setCheckConstraints(true);
                runBulkCopy(people, opts);
            } catch (Exception e) { throw new RuntimeException(e); }
        });
        assertCount(people.size(), "person");
    }

    // ========== Helpers ==========

    private void insertPlain(List<Person> people, int batchSize) {
        truncate("person");
        String sql = "INSERT INTO person (person_id,user_name,first_name,last_name,years) VALUES (?,?,?,?,?)";
        execBatch(people, batchSize, sql);
    }

    private void insertWithRecompile(List<Person> people, int batchSize) {
        truncate("person");
        String sql = "INSERT INTO person (person_id,user_name,first_name,last_name,years) VALUES (?,?,?,?,?) OPTION (RECOMPILE)";
        execBatch(people, batchSize, sql);
    }

    private void insertWithLatency(List<Person> people, int batchSize, int latencyMs) {
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
                    if (++count % batchSize == 0) {
                        ps.executeBatch();
                        Thread.sleep(latencyMs); // simulate network round-trip delay
                    }
                }
                if (count % batchSize != 0) ps.executeBatch();
                conn.commit();
            }
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private void runBulkCopy(List<Person> people, SQLServerBulkCopyOptions options) {
        truncate("person");
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (SQLServerBulkCopy bulk = new SQLServerBulkCopy(conn.unwrap(SQLServerConnection.class))) {
                bulk.setDestinationTableName("person");
                bulk.setBulkCopyOptions(options);
                // Explicitly map ordinal source positions to destination column names;
                // this skips the driver's type inference from the source metadata.
                bulk.addColumnMapping(1, "person_id");
                bulk.addColumnMapping(2, "user_name");
                bulk.addColumnMapping(3, "first_name");
                bulk.addColumnMapping(4, "last_name");
                bulk.addColumnMapping(5, "years");

                CachedRowSet rowSet = RowSetProvider.newFactory().createCachedRowSet();
                RowSetMetaDataImpl md = new RowSetMetaDataImpl();
                md.setColumnCount(5);
                md.setColumnName(1, "person_id");    md.setColumnType(1, Types.INTEGER);
                md.setColumnName(2, "user_name");    md.setColumnType(2, Types.NVARCHAR);  md.setPrecision(2, 30);
                md.setColumnName(3, "first_name");   md.setColumnType(3, Types.NVARCHAR);  md.setPrecision(3, 10);
                md.setColumnName(4, "last_name");    md.setColumnType(4, Types.NVARCHAR);  md.setPrecision(4, 15);
                md.setColumnName(5, "years");        md.setColumnType(5, Types.INTEGER);
                rowSet.setMetaData(md);
                for (Person p : people) {
                    rowSet.moveToInsertRow();
                    rowSet.updateInt(1, p.getPersonId());
                    rowSet.updateString(2, p.getUserName());
                    rowSet.updateString(3, p.getFirstName());
                    rowSet.updateString(4, p.getLastName());
                    rowSet.updateInt(5, p.getYears());
                    rowSet.insertRow();
                }
                rowSet.moveToCurrentRow();
                bulk.writeToServer(rowSet);
                conn.commit();
            }
        } catch (Exception e) { throw new RuntimeException(e); }
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

    private void assertCount(int expected, String table) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        Assertions.assertThat(count).isEqualTo(expected);
    }
}
