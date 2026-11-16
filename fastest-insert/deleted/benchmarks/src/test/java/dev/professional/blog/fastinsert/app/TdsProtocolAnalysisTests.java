package dev.professional.blog.fastinsert.app;

import dev.professional.blog.fastinsert.common.Person;
import dev.professional.blog.fastinsert.common.RandomPersonGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

/**
 * TDS Protocol Analysis — runs one insert per strategy with a small batch (1,000 rows)
 * while Extended Events + pktmon capture the wire-level protocol differences.
 *
 * <p>Not a performance benchmark — the goal is to capture and compare the TDS commands
 * each strategy sends. Run with Extended Events session 'TDS_Benchmark' active.</p>
 */
@SpringBootTest
class TdsProtocolAnalysisTests {

    @Autowired
    private FastInsertService fastInsertService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final int ROW_COUNT = 1_000;
    private static final int BATCH_SIZE = 100;
    private List<Person> people;

    @BeforeEach
    void setup() {
        people = RandomPersonGenerator.generateRandomPersons(ROW_COUNT);
        jdbcTemplate.execute("TRUNCATE TABLE person");
    }

    private void captureConnectionStats(String label) {
        List<Map<String, Object>> stats = jdbcTemplate.queryForList(
                "SELECT " +
                "  c.net_packet_size, " +
                "  c.num_reads, " +
                "  c.num_writes, " +
                "  s.total_elapsed_time, " +
                "  s.reads AS logical_reads, " +
                "  s.writes AS logical_writes, " +
                "  s.row_count " +
                "FROM sys.dm_exec_connections c " +
                "JOIN sys.dm_exec_sessions s ON s.session_id = c.session_id " +
                "WHERE s.session_id = @@SPID"
        );
        if (!stats.isEmpty()) {
            Map<String, Object> row = stats.get(0);
            System.out.printf("[TDS] %s: packet_size=%s num_reads=%s num_writes=%s " +
                            "logical_reads=%s logical_writes=%s row_count=%s%n",
                    label,
                    row.get("net_packet_size"),
                    row.get("num_reads"),
                    row.get("num_writes"),
                    row.get("logical_reads"),
                    row.get("logical_writes"),
                    row.get("row_count")
            );
        }
    }

    @Test
    void strategy1_hibernatePersist() {
        captureConnectionStats("hibernate_BEFORE");
        fastInsertService.insertHibernateBatch(people, BATCH_SIZE);
        captureConnectionStats("hibernate_AFTER");
    }

    @Test
    void strategy2_jdbcTemplate() {
        captureConnectionStats("jdbcTemplate_BEFORE");
        fastInsertService.insertJdbcTemplateBatch(BATCH_SIZE, 1, people);
        captureConnectionStats("jdbcTemplate_AFTER");
    }

    @Test
    void strategy3_bulkApi() {
        captureConnectionStats("bulkApi_BEFORE");
        fastInsertService.insertBulkApi(people, BATCH_SIZE);
        captureConnectionStats("bulkApi_AFTER");
    }

    @Test
    void strategy4_tvp() {
        captureConnectionStats("tvp_BEFORE");
        fastInsertService.insertWithTvp(people, BATCH_SIZE);
        captureConnectionStats("tvp_AFTER");
    }

    @Test
    void strategy5_plainJdbc() {
        captureConnectionStats("plainJdbc_BEFORE");
        fastInsertService.insertPlainJdbc(people, BATCH_SIZE);
        captureConnectionStats("plainJdbc_AFTER");
    }

    @Test
    void strategy6_multiValue200() {
        captureConnectionStats("multiValue200_BEFORE");
        fastInsertService.insertJdbcTemplateBatch(BATCH_SIZE, 200, people);
        captureConnectionStats("multiValue200_AFTER");
    }
}
