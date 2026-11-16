package dev.professional.blog.fastinsert.tvp;

import com.microsoft.sqlserver.jdbc.SQLServerDataTable;
import dev.professional.blog.fastinsert.common.Person;
import dev.professional.blog.fastinsert.common.PersonConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/**
 * Inserts persons via SQL Server Table-Valued Parameters (TVP) — sends an entire batch as a
 * single {@link SQLServerDataTable} parameter through a stored procedure call.
 *
 * <p>Blog post: Part 8 — TVP, JDBC, Final Ranking. TVP at batch size 5,000 averages 818 ms
 * for 100k rows — the best portable option (works with any JDBC driver that supports
 * {@link CallableStatement}).</p>
 *
 * <p>The stored procedure and the {@code PersonTableType} user-defined table type are
 * auto-created if missing on first run.</p>
 */

import javax.sql.DataSource;
import java.sql.*;
import java.util.List;

/**
 * Inserts persons using SQL Server Table-Valued Parameters (TVPs).
 *
 * <p>TVPs allow sending an entire result set as a single stored procedure parameter.
 * The data is sent as a {@link SQLServerDataTable} which SQL Server receives as a
 * table variable — no temp tables, no multi-statement batches.</p>
 *
 * <p>Advantages over other approaches:</p>
 * <ul>
 *   <li>Single round-trip per batch (unlike PreparedStatement which sends N statements)</li>
 *   <li>No 2,100-parameter limit (unlike multi-value INSERT)</li>
 *   <li>Type-safe — SQL Server validates the table type schema</li>
 *   <li>Can be used inside transactions (unlike BULK INSERT from file)</li>
 * </ul>
 *
 * <p>Requires a user-defined table type in SQL Server:</p>
 * <pre>
 * CREATE TYPE dbo.PersonTableType AS TABLE (
 *     person_id INT,
 *     user_name NVARCHAR(30),
 *     first_name NVARCHAR(10),
 *     last_name NVARCHAR(15),
 *     years INT
 * );
 * </pre>
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class TvpInsertRepository {

    private final DataSource dataSource;

    /**
     * Ensures the PersonTableType TVP type exists in the database.
     * Call once before running TVP benchmarks.
     */
    public void ensureTvpTypeExists() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(
                    "IF NOT EXISTS (SELECT * FROM sys.types WHERE name = 'PersonTableType') " +
                    "CREATE TYPE dbo.PersonTableType AS TABLE (" +
                    "    person_id INT, " +
                    "    user_name NVARCHAR(30), " +
                    "    first_name NVARCHAR(10), " +
                    "    last_name NVARCHAR(15), " +
                    "    years INT" +
                    ")");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create PersonTableType", e);
        }
    }

    /**
     * Insert persons using TVP in batches.
     *
     * @param people    the persons to insert
     * @param batchSize number of rows per TVP batch (each batch = one round-trip)
     */
    public void insertWithTvp(List<Person> people, int batchSize) {
        String sql = "INSERT INTO person (person_id, user_name, first_name, last_name, years) " +
                     "SELECT person_id, user_name, first_name, last_name, years FROM ?";

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            try (CallableStatement stmt = conn.prepareCall(sql)) {
                for (int i = 0; i < people.size(); i += batchSize) {
                    int end = Math.min(i + batchSize, people.size());
                    List<Person> batch = people.subList(i, end);

                    SQLServerDataTable dataTable = buildDataTable(batch);
                    stmt.setObject(1, dataTable);
                    stmt.execute();
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("TVP insert failed", e);
        }
    }

    private SQLServerDataTable buildDataTable(List<Person> people) throws SQLException {
        SQLServerDataTable dataTable = new SQLServerDataTable();
        dataTable.setTvpName("PersonTableType");

        dataTable.addColumnMetadata(PersonConstants.PERSON_ID, Types.INTEGER);
        dataTable.addColumnMetadata(PersonConstants.USER_NAME, Types.NVARCHAR);
        dataTable.addColumnMetadata(PersonConstants.FIRST_NAME, Types.NVARCHAR);
        dataTable.addColumnMetadata(PersonConstants.LAST_NAME, Types.NVARCHAR);
        dataTable.addColumnMetadata(PersonConstants.YEARS, Types.INTEGER);

        for (Person p : people) {
            dataTable.addRow(p.getPersonId(), p.getUserName(), p.getFirstName(), p.getLastName(), p.getYears());
        }

        return dataTable;
    }
}
