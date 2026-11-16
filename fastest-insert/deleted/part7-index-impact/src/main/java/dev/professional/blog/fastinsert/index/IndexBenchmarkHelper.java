package dev.professional.blog.fastinsert.index;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * Manages non-clustered indexes on the person table for benchmarking.
 *
 * <p>Each non-clustered index that SQL Server must maintain during an INSERT
 * adds overhead: the engine must update the B-tree for every inserted row.
 * More indexes = slower inserts, but faster reads.</p>
 *
 * <p>This helper creates/drops indexes in controlled steps so benchmarks can
 * measure the exact cost of 0, 1, 3, or 5 non-clustered indexes on insert
 * performance across different strategies.</p>
 *
 * <p>Index definitions chosen to represent realistic column patterns:</p>
 * <ul>
 *   <li>IX_person_username — single column, nvarchar(30), most selective</li>
 *   <li>IX_person_lastname — single column, nvarchar(15)</li>
 *   <li>IX_person_firstname — single column, nvarchar(10)</li>
 *   <li>IX_person_years — single column, INT, low selectivity</li>
 *   <li>IX_person_name_composite — composite (last_name, first_name)</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IndexBenchmarkHelper {

    private final JdbcTemplate jdbcTemplate;

    /** All index definitions, ordered by creation priority */
    private static final List<IndexDef> ALL_INDEXES = Arrays.asList(
            new IndexDef("IX_person_username", "person", "user_name"),
            new IndexDef("IX_person_lastname", "person", "last_name"),
            new IndexDef("IX_person_firstname", "person", "first_name"),
            new IndexDef("IX_person_years", "person", "years"),
            new IndexDef("IX_person_name_composite", "person", "last_name, first_name")
    );

    /**
     * Ensures exactly {@code count} non-clustered indexes exist on the person table.
     *
     * @param count number of indexes (0–5). Creates the first N from ALL_INDEXES.
     */
    public void setIndexCount(int count) {
        if (count < 0 || count > ALL_INDEXES.size()) {
            throw new IllegalArgumentException("Index count must be 0-" + ALL_INDEXES.size());
        }

        // Drop all indexes first
        dropAllIndexes();

        // Create the first N
        for (int i = 0; i < count; i++) {
            createIndex(ALL_INDEXES.get(i));
        }

        log.info("Set index count to {} on person table", count);
    }

    /** Drop all benchmark indexes (safe — ignores if not exists) */
    public void dropAllIndexes() {
        for (IndexDef idx : ALL_INDEXES) {
            dropIndexIfExists(idx);
        }
    }

    /** Get the current number of non-clustered indexes on the person table */
    public int getCurrentIndexCount() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys.indexes WHERE object_id = OBJECT_ID('person') " +
                        "AND type_desc = 'NONCLUSTERED' AND name LIKE 'IX_person_%'",
                Integer.class);
        return count != null ? count : 0;
    }

    private void createIndex(IndexDef idx) {
        String sql = String.format("CREATE NONCLUSTERED INDEX %s ON %s (%s)",
                idx.name, idx.table, idx.columns);
        jdbcTemplate.execute(sql);
        log.debug("Created index: {}", idx.name);
    }

    private void dropIndexIfExists(IndexDef idx) {
        String sql = String.format(
                "IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = '%s' AND object_id = OBJECT_ID('%s')) " +
                        "DROP INDEX %s ON %s",
                idx.name, idx.table, idx.name, idx.table);
        jdbcTemplate.execute(sql);
    }

    private record IndexDef(String name, String table, String columns) {}
}
