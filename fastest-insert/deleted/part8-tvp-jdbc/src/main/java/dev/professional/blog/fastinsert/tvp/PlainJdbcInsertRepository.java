package dev.professional.blog.fastinsert.tvp;

import dev.professional.blog.fastinsert.common.Person;
import dev.professional.blog.fastinsert.common.PersonConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/**
 * Plain JDBC insert without any Hibernate/JPA overhead.
 *
 * <p>This is the "bare metal" baseline — uses raw JDBC PreparedStatement batching
 * directly against the DataSource. No entity manager, no first-level cache,
 * no dirty checking, no Spring Data abstractions.</p>
 *
 * <p>Purpose: quantify the exact overhead that Hibernate/JPA adds to bulk inserts.
 * By comparing this against Part 1's Hibernate persist and Part 5's StatelessSession,
 * we can measure the cost of each abstraction layer.</p>
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class PlainJdbcInsertRepository {

    private final DataSource dataSource;

    private static final String INSERT_SQL =
            "INSERT INTO " + PersonConstants.PERSON_TABLE_NAME +
            " (person_id, user_name, first_name, last_name, years) VALUES (?, ?, ?, ?, ?)";

    /**
     * Insert persons using raw JDBC PreparedStatement batching.
     *
     * @param people    the persons to insert
     * @param batchSize JDBC batch size (executeBatch every N rows)
     */
    public void insertBatch(List<Person> people, int batchSize) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {
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
                    }
                }
                // Flush remaining
                if (count % batchSize != 0) {
                    ps.executeBatch();
                }

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Plain JDBC batch insert failed", e);
        }
    }
}
