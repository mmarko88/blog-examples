package dev.professional.blog.fastinsert.jdbc;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import dev.professional.blog.fastinsert.common.ColumnDescriptor;
import dev.professional.blog.fastinsert.common.TableDescriptor;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/**
 * JDBC PreparedStatement batch insert with multi-value support.
 *
 * <p>Builds {@code INSERT INTO t (c1,c2) VALUES (?,?),(?,?)...} statements
 * where {@code objectsPerInsert} controls how many value tuples go into a single
 * statement (Part 3 explores the effect of this parameter).</p>
 *
 * <p>Blog posts: <em>Part 2</em> (single-value batching) and <em>Part 3</em> (multi-value).</p>
 */
@Repository
@Slf4j
@Transactional
public class BulkPersistCustomRepositoryImpl implements BulkPersistCustomRepository {
    @PersistenceContext
    private final EntityManager entityManager;

    public BulkPersistCustomRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public <T> void insert(List<T> objects, TableDescriptor<T> descriptor, int batchSize, int objectsPerInsert) {
        Session session = entityManager.unwrap(Session.class);
        session.doWork(connection ->
                insertInternal(objects, descriptor, connection, batchSize, objectsPerInsert));
    }

    private <T> void insertInternal(List<T> objects,
                                    TableDescriptor<T> descriptor,
                                    Connection connection,
                                    int batchSize,
                                    int objectsPerInsert) throws SQLException {
        List<List<T>> allChunks = Lists.partition(objects, objectsPerInsert);

        try (PreparedStatement ps = prepareInsertStatement(descriptor, connection, objectsPerInsert)) {
            boolean pending = false;
            for (int i = 0; i < allChunks.size(); i++) {
                List<T> currentChunk = allChunks.get(i);
                if (currentChunk.size() == objectsPerInsert) {
                    insertBatchData(descriptor, ps, currentChunk);
                    ps.addBatch();
                    pending = true;
                    if ((i + 1) % batchSize == 0) {
                        pending = executeBatch(ps, true);
                    }
                } else {
                    pending = executeBatch(ps, pending);
                    insertBatchDataWithCustomSize(connection, descriptor, currentChunk);
                }
            }
            executeBatch(ps, pending);
        }
    }

    private <T> void insertBatchDataWithCustomSize(Connection connection,
                                                   TableDescriptor<T> descriptor,
                                                   List<T> chunk) throws SQLException {
        try (PreparedStatement ps = prepareInsertStatement(descriptor, connection, chunk.size())) {
            insertBatchData(descriptor, ps, chunk);
            ps.addBatch();
            executeBatch(ps, true);
        }
    }

    private static boolean executeBatch(PreparedStatement ps, boolean pending) throws SQLException {
        if (pending) {
            ps.executeBatch();
            ps.clearBatch();
        }
        return false;
    }

    public static <T> void insertBatchData(TableDescriptor<T> tableDescriptor,
                                           PreparedStatement ps,
                                           List<T> objectsToInsert) throws SQLException {
        int paramIndex = 0;
        List<ColumnDescriptor<T>> columns = tableDescriptor.getColumns();
        for (T object : objectsToInsert) {
            for (ColumnDescriptor<T> col : columns) {
                ps.setObject(++paramIndex, col.getObjectFieldValue(object));
            }
        }
    }

    private PreparedStatement prepareInsertStatement(TableDescriptor<?> descriptor,
                                                     Connection connection,
                                                     int valueBindingRepetitions) throws SQLException {
        return connection.prepareStatement(getInsertQuery(descriptor, valueBindingRepetitions));
    }

    public static String getInsertQuery(@NonNull TableDescriptor<?> descriptor, int numOfInsertsInStatement) {
        Preconditions.checkArgument(numOfInsertsInStatement > 0, "numOfInsertStatements must be > 0");
        return "INSERT INTO " +
                descriptor.getTableName() +
                " (" + descriptor.getColumnsCommaSeparated() + ") " +
                "VALUES " +
                repeatCommaSeparated(numOfInsertsInStatement,
                        "(" + repeatCommaSeparated(descriptor.getColumns().size(), "?") + ")");
    }

    private static String repeatCommaSeparated(int count, String token) {
        String values = Strings.repeat(token + ",", count);
        return values.substring(0, values.length() - 1);
    }
}
