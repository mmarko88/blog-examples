package dev.professional.blog.fastinsert.tvp;

import dev.professional.blog.fastinsert.app.AbstractBenchmarkTest;
import dev.professional.blog.fastinsert.app.TestParam;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Part 8: Table-Valued Parameters and bare-metal PreparedStatement JDBC.
 */
class Part8TvpJdbcTests extends AbstractBenchmarkTest {

    @Autowired
    private TvpInsertRepository tvpInsertRepository;
    @Autowired
    private PlainJdbcInsertRepository plainJdbcInsertRepository;

    @BeforeAll
    void setUp() {
        // Ensure TVP type exists before any TVP tests run
        tvpInsertRepository.ensureTvpTypeExists();
    }

    /** Part 8: Table-Valued Parameters */
    @ParameterizedTest
    @MethodSource(TEST_PARAMS)
    void tvpInsert(TestParam param) {
        if (param.getObjectsPerInsert() > 1) return;
        multipleRunner.execJob("tvpInsert", param,
                v -> tvpInsertRepository.insertWithTvp(param.getPeople(), param.getBatchSize()));
        assertInsertedCount(param);
    }

    /** Part 8: Plain JDBC (no Hibernate/JPA overhead) */
    @ParameterizedTest
    @MethodSource(TEST_PARAMS)
    void plainJdbcInsert(TestParam param) {
        if (param.getObjectsPerInsert() > 1) return;
        multipleRunner.execJob("plainJdbc", param,
                v -> plainJdbcInsertRepository.insertBatch(param.getPeople(), param.getBatchSize()));
        assertInsertedCount(param);
    }
}
