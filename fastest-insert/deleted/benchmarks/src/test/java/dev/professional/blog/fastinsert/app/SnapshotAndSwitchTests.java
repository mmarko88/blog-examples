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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Final experiments:
 *  1. SNAPSHOT isolation under concurrent writers (version store growth)
 *  2. Partition SWITCH with varying index counts
 */
@SpringBootTest(properties = "spring.config.location=classpath:application.properties")
@SpringJUnitConfig
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Slf4j
class SnapshotAndSwitchTests {

    @Autowired private PersonRepository personRepository;
    @Autowired private MultipleRunner multipleRunner;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DataSource dataSource;

    private boolean snapshotEnabled = false;

    @BeforeAll
    void setUp() {
        // Enable snapshot isolation
        try {
            jdbcTemplate.execute("ALTER DATABASE testdb SET ALLOW_SNAPSHOT_ISOLATION ON");
            snapshotEnabled = true;
            log.info("SNAPSHOT isolation enabled");
        } catch (Exception e) {
            log.warn("Snapshot isolation setup failed: {}", e.getMessage());
        }

        // Partitioned tables for SWITCH tests (reuse those from FillFactorAndPartitionTests)
        // Create a dedicated set for the index-count sweep
        for (int ix : new int[]{0, 1, 3}) {
            String name = "person_part_" + ix;
            jdbcTemplate.execute(
                    "IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = '" + name + "') " +
                    "CREATE TABLE " + name + " (" +
                    "  person_id INT NOT NULL, user_name NVARCHAR(30), first_name NVARCHAR(10), " +
                    "  last_name NVARCHAR(15), years INT, " +
                    "  CONSTRAINT PK_" + name + " PRIMARY KEY CLUSTERED (person_id)" +
                    ")");
            // Staging table (same schema, no FKs, no indexes required for SWITCH except PK)
            String staging = name + "_stg";
            jdbcTemplate.execute(
                    "IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = '" + staging + "') " +
                    "CREATE TABLE " + staging + " (" +
                    "  person_id INT NOT NULL, user_name NVARCHAR(30), first_name NVARCHAR(10), " +
                    "  last_name NVARCHAR(15), years INT, " +
                    "  CONSTRAINT PK_" + staging + " PRIMARY KEY CLUSTERED (person_id)" +
                    ")");
            // Add N non-clustered indexes to BOTH tables so SWITCH works
            if (ix >= 1) {
                addIndexIfMissing(name, "IX_" + name + "_last", "last_name");
                addIndexIfMissing(staging, "IX_" + staging + "_last", "last_name");
            }
            if (ix >= 3) {
                addIndexIfMissing(name, "IX_" + name + "_first", "first_name");
                addIndexIfMissing(staging, "IX_" + staging + "_first", "first_name");
                addIndexIfMissing(name, "IX_" + name + "_years", "years");
                addIndexIfMissing(staging, "IX_" + staging + "_years", "years");
            }
        }
    }

    private void addIndexIfMissing(String table, String idx, String col) {
        try {
            jdbcTemplate.execute(
                    "IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = '" + idx + "') " +
                    "CREATE INDEX " + idx + " ON " + table + "(" + col + ")");
        } catch (Exception ignored) {}
    }

    @AfterAll
    void cleanup() {
        try { jdbcTemplate.execute("ALTER DATABASE testdb SET ALLOW_SNAPSHOT_ISOLATION OFF"); } catch (Exception ignored) {}
    }

    // ========== 1. SNAPSHOT under write contention ==========

    @Test
    void snapshot_singleWriter() {
        if (!snapshotEnabled) { log.warn("Skipping — snapshot not available"); return; }
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("snap_single", param,
                v -> insertWithIsolation(people, 1000, "SNAPSHOT"));
        assertPersonCount(people.size());
    }

    @Test
    void snapshot_concurrentWriters2() {
        if (!snapshotEnabled) { log.warn("Skipping"); return; }
        runConcurrent("snap_concurrent_2", 2);
    }

    @Test
    void snapshot_concurrentWriters4() {
        if (!snapshotEnabled) { log.warn("Skipping"); return; }
        runConcurrent("snap_concurrent_4", 4);
    }

    @Test
    void readcommitted_concurrentWriters4() {
        // Baseline — same scenario but with READ_COMMITTED isolation
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("rc_concurrent_4", param, v -> {
            truncatePerson();
            runWithConcurrentReaders(people, 1000, 4, "READ COMMITTED");
        });
        assertPersonCount(people.size());
    }

    private void runConcurrent(String label, int readerThreads) {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob(label, param, v -> {
            truncatePerson();
            runWithConcurrentReaders(people, 1000, readerThreads, "SNAPSHOT");
        });
        assertPersonCount(people.size());
    }

    private void runWithConcurrentReaders(List<Person> people, int batchSize, int readers,
                                          String isolationName) {
        AtomicBoolean inserting = new AtomicBoolean(true);
        AtomicInteger readCount = new AtomicInteger();
        ExecutorService exec = Executors.newFixedThreadPool(readers);
        for (int i = 0; i < readers; i++) {
            exec.submit(() -> {
                while (inserting.get()) {
                    try (Connection c = dataSource.getConnection()) {
                        c.createStatement().execute("SET TRANSACTION ISOLATION LEVEL " + isolationName);
                        try (var rs = c.createStatement().executeQuery("SELECT COUNT(*), AVG(years) FROM person")) {
                            rs.next();
                            readCount.incrementAndGet();
                        }
                        Thread.sleep(5);
                    } catch (Exception ignored) {}
                }
            });
        }
        try {
            insertWithIsolation(people, batchSize, isolationName);
        } finally {
            inserting.set(false);
            exec.shutdown();
            try { exec.awaitTermination(10, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
            log.info("{}: {} readers made {} reads", isolationName, readers, readCount.get());
        }
    }

    private void insertWithIsolation(List<Person> people, int batchSize, String isolationName) {
        String sql = "INSERT INTO person (person_id,user_name,first_name,last_name,years) VALUES (?,?,?,?,?)";
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            conn.createStatement().execute("SET TRANSACTION ISOLATION LEVEL " + isolationName);
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

    // ========== 2. Partition SWITCH with indexes ==========

    @Test
    void switchWithIndexes_0idx() {
        runSwitchTest(0);
    }

    @Test
    void switchWithIndexes_1idx() {
        runSwitchTest(1);
    }

    @Test
    void switchWithIndexes_3idx() {
        runSwitchTest(3);
    }

    private void runSwitchTest(int idxCount) {
        String target = "person_part_" + idxCount;
        String staging = target + "_stg";
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("switchIdx_" + idxCount, param, v -> doSwitch(people, target, staging, 1000));
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + target, Long.class);
        Assertions.assertThat(count).isEqualTo(people.size());
    }

    private void doSwitch(List<Person> people, String target, String staging, int batchSize) {
        try {
            jdbcTemplate.execute("TRUNCATE TABLE " + target);
            jdbcTemplate.execute("TRUNCATE TABLE " + staging);
        } catch (Exception ignored) {}

        // Load into staging
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            String sql = "INSERT INTO " + staging + " (person_id,user_name,first_name,last_name,years) VALUES (?,?,?,?,?)";
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

        // SWITCH (non-partitioned → non-partitioned: this is actually a straight ALTER TABLE SWITCH)
        jdbcTemplate.execute("ALTER TABLE " + staging + " SWITCH TO " + target);
    }

    private void truncatePerson() {
        try (Connection c = dataSource.getConnection()) {
            c.createStatement().execute("TRUNCATE TABLE person");
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private void assertPersonCount(int expected) {
        long count = personRepository.count();
        Assertions.assertThat(count).isEqualTo(expected);
    }
}
