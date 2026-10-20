package dev.professional.blog.fastinsert.jdbc;

import com.microsoft.sqlserver.jdbc.SQLServerBulkCopy;
import com.microsoft.sqlserver.jdbc.SQLServerBulkCopyOptions;
import com.microsoft.sqlserver.jdbc.SQLServerConnection;
import dev.professional.blog.fastinsert.common.ColumnDescriptor;
import dev.professional.blog.fastinsert.common.Person;
import dev.professional.blog.fastinsert.common.TableDescriptor;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.hibernate.Session;
import org.springframework.stereotype.Service;

/**
 * Inserts persons via the SQL Server {@link SQLServerBulkCopy} API — the TDS bulk-insert
 * protocol that bypasses standard INSERT parsing entirely.
 *
 * <p>Blog post: Part 2 — JDBC Strategies. This is the single-thread baseline that Parts 4–6
 * build on (useBulkCopyForBatchInsert flag, parallel threads).</p>
 *
 * <p>The implementation unwraps the Hibernate-managed JDBC connection via
 * {@code Session.doWork()}, populates a {@link javax.sql.rowset.CachedRowSet} with column
 * metadata from {@link TableDescriptor}, and writes it through
 * {@link SQLServerBulkCopy#writeToServer}.</p>
 *
 * @see dev.professional.blog.fastinsert.parallel.ParallelInsertExecutor for multi-threaded version
 */

import javax.sql.RowSetMetaData;
import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetMetaDataImpl;
import javax.sql.rowset.RowSetProvider;
import java.sql.SQLException;
import java.util.List;

/**
 * MS SQL Server Bulk Copy API insert — bypasses the SQL INSERT path entirely
 * and streams data in TDS bulk-insert format.
 *
 * <p>Blog post: <em>Part 2 — Hibernate vs PreparedStatement vs JdbcTemplate vs Bulk API</em></p>
 */
@Service
@Transactional
public class BulkCopyRepository {
    @PersistenceContext
    EntityManager entityManager;

    public void performBulkInsert(List<Person> persons,
                                  TableDescriptor<Person> descriptor,
                                  int batchSize) {
        Session unwrap = entityManager.unwrap(Session.class);
        unwrap.doWork(connection -> {
            try (SQLServerBulkCopy bulkCopy =
                         new SQLServerBulkCopy(connection.unwrap(SQLServerConnection.class))) {
                SQLServerBulkCopyOptions options = new SQLServerBulkCopyOptions();
                options.setBatchSize(batchSize);
                bulkCopy.setBulkCopyOptions(options);
                bulkCopy.setDestinationTableName(descriptor.getTableName());

                CachedRowSet dataToInsert = createCachedRowSet(persons, descriptor);
                bulkCopy.writeToServer(dataToInsert);
            }
        });
    }

    private CachedRowSet createCachedRowSet(List<Person> persons, TableDescriptor<Person> descriptor) throws SQLException {
        CachedRowSet rowSet = RowSetProvider.newFactory().createCachedRowSet();
        rowSet.setMetaData(createMetadata(descriptor));

        for (Person person : persons) {
            rowSet.moveToInsertRow();
            for (int i = 0; i < descriptor.getColumns().size(); i++) {
                ColumnDescriptor<Person> column = descriptor.getColumns().get(i);
                rowSet.updateObject(i + 1, column.getObjectFieldValue(person), column.getSqlType());
            }
            rowSet.insertRow();
        }
        rowSet.moveToCurrentRow();
        return rowSet;
    }

    private static RowSetMetaData createMetadata(TableDescriptor<Person> descriptor) throws SQLException {
        RowSetMetaData metadata = new RowSetMetaDataImpl();
        metadata.setColumnCount(descriptor.getColumns().size());
        for (int i = 0; i < descriptor.getColumns().size(); i++) {
            metadata.setColumnName(i + 1, descriptor.getColumns().get(i).getColumnName());
            metadata.setColumnType(i + 1, descriptor.getColumns().get(i).getSqlType());
        }
        return metadata;
    }
}
