package dev.professional.blog.fastinsert.app;

import dev.professional.blog.fastinsert.common.Person;
import dev.professional.blog.fastinsert.common.PersonRepository;
import dev.professional.blog.fastinsert.common.RandomPersonGenerator;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Date;
import java.util.List;

/**
 * Transaction pattern experiments:
 *  - Isolation level: READ_UNCOMMITTED vs READ_COMMITTED (default) vs SERIALIZABLE
 *  - Transaction size: one giant txn vs chunked (commit per batch)
 *  - Savepoints: mark savepoint per batch (allows partial rollback)
 */
@SpringBootTest(properties = "spring.config.location=classpath:application.properties")
@SpringJUnitConfig
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Slf4j
class TransactionPatternTests {

    @Autowired private PersonRepository personRepository;
    @Autowired private MultipleRunner multipleRunner;
    @Autowired private DataSource dataSource;

    // ========== Isolation Levels ==========

    @Test
    void readUncommitted() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("iso_readUncommitted", param,
                v -> insertWithIsolation(people, 1000, Connection.TRANSACTION_READ_UNCOMMITTED));
        assertCount(people.size());
    }

    @Test
    void readCommitted() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("iso_readCommitted", param,
                v -> insertWithIsolation(people, 1000, Connection.TRANSACTION_READ_COMMITTED));
        assertCount(people.size());
    }

    @Test
    void serializable() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("iso_serializable", param,
                v -> insertWithIsolation(people, 1000, Connection.TRANSACTION_SERIALIZABLE));
        assertCount(people.size());
    }

    // ========== Transaction Size ==========

    @Test
    void oneLargeTransaction() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("txn_oneBig", param,
                v -> insertOneBigTxn(people, 1000));
        assertCount(people.size());
    }

    @Test
    void commitPerBatch() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("txn_commitPerBatch", param,
                v -> insertCommitPerBatch(people, 1000));
        assertCount(people.size());
    }

    @Test
    void autoCommitEveryRow() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(10_000); // 10x less, this is painful
        TestParam param = new TestParam(people, 1, new Date(), 1);
        multipleRunner.execJob("txn_autoCommitPerRow", param,
                v -> insertAutoCommit(people));
        assertCount(people.size());
    }

    // ========== Savepoints ==========

    @Test
    void savepointsPerBatch() {
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        TestParam param = new TestParam(people, 1000, new Date(), 1);
        multipleRunner.execJob("txn_savepoints", param,
                v -> insertWithSavepoints(people, 1000));
        assertCount(people.size());
    }

    // ========== Helpers ==========

    private void insertWithIsolation(List<Person> people, int batchSize, int isolation) {
        String sql = "INSERT INTO person (person_id,user_name,first_name,last_name,years) VALUES (?,?,?,?,?)";
        truncate();
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            conn.setTransactionIsolation(isolation);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                batchAll(ps, people, batchSize);
                conn.commit();
            }
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private void insertOneBigTxn(List<Person> people, int batchSize) {
        String sql = "INSERT INTO person (person_id,user_name,first_name,last_name,years) VALUES (?,?,?,?,?)";
        truncate();
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                batchAll(ps, people, batchSize);
                conn.commit();
            }
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private void insertCommitPerBatch(List<Person> people, int batchSize) {
        String sql = "INSERT INTO person (person_id,user_name,first_name,last_name,years) VALUES (?,?,?,?,?)";
        truncate();
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                int count = 0;
                for (Person p : people) {
                    bindParams(ps, p);
                    ps.addBatch();
                    if (++count % batchSize == 0) {
                        ps.executeBatch();
                        conn.commit(); // commit after each batch
                    }
                }
                if (count % batchSize != 0) {
                    ps.executeBatch();
                    conn.commit();
                }
            }
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private void insertAutoCommit(List<Person> people) {
        String sql = "INSERT INTO person (person_id,user_name,first_name,last_name,years) VALUES (?,?,?,?,?)";
        truncate();
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(true); // commit per statement
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (Person p : people) {
                    bindParams(ps, p);
                    ps.executeUpdate();
                }
            }
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private void insertWithSavepoints(List<Person> people, int batchSize) {
        String sql = "INSERT INTO person (person_id,user_name,first_name,last_name,years) VALUES (?,?,?,?,?)";
        truncate();
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                int count = 0;
                for (Person p : people) {
                    bindParams(ps, p);
                    ps.addBatch();
                    if (++count % batchSize == 0) {
                        ps.executeBatch();
                        conn.setSavepoint("sp_" + count);
                    }
                }
                if (count % batchSize != 0) ps.executeBatch();
                conn.commit();
            }
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private void batchAll(PreparedStatement ps, List<Person> people, int batchSize) throws Exception {
        int count = 0;
        for (Person p : people) {
            bindParams(ps, p);
            ps.addBatch();
            if (++count % batchSize == 0) ps.executeBatch();
        }
        if (count % batchSize != 0) ps.executeBatch();
    }

    private void bindParams(PreparedStatement ps, Person p) throws Exception {
        ps.setInt(1, p.getPersonId());
        ps.setString(2, p.getUserName());
        ps.setString(3, p.getFirstName());
        ps.setString(4, p.getLastName());
        ps.setInt(5, p.getYears());
    }

    private void truncate() {
        try (Connection conn = dataSource.getConnection()) {
            conn.createStatement().execute("TRUNCATE TABLE person");
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private void assertCount(int expected) {
        long count = personRepository.count();
        Assertions.assertThat(count).isEqualTo(expected);
    }
}
