package dev.professional.blog.fastinsert.jdbc;

import dev.professional.blog.fastinsert.app.AbstractBenchmarkTest;
import dev.professional.blog.fastinsert.app.TestParam;
import dev.professional.blog.fastinsert.common.PersonDescriptors;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Part 2: JDBC PreparedStatement, JdbcTemplate, and Bulk Copy API strategies.
 */
class Part2JdbcStrategyTests extends AbstractBenchmarkTest {

    @Autowired
    private BulkPersistCustomRepository bulkPersistCustomRepository;
    @Autowired
    private SpringTemplateBulkInsertRepository springTemplateBulkInsertRepository;
    @Autowired
    private BulkCopyRepository bulkCopyRepository;

    /** Part 2: JDBC PreparedStatement batch insert */
    @ParameterizedTest
    @MethodSource(TEST_PARAMS)
    void regularBatchInsert(TestParam param) {
        multipleRunner.execJob("regularBatchInsert", param,
                v -> bulkPersistCustomRepository.insert(
                        param.getPeople(), PersonDescriptors.PERSON_TABLE,
                        param.getBatchSize(), param.getObjectsPerInsert()));
        assertInsertedCount(param);
    }

    /** Part 2: Spring JdbcTemplate batch insert */
    @ParameterizedTest
    @MethodSource(TEST_PARAMS)
    void jdbcTemplateInsert(TestParam param) {
        multipleRunner.execJob("jdbcTemplateInsert", param,
                v -> springTemplateBulkInsertRepository.insertPeople(
                        PersonDescriptors.PERSON_TABLE, param.getPeople(),
                        param.getBatchSize(), param.getObjectsPerInsert()));
        assertInsertedCount(param);
    }

    /** Part 2: MS SQL Bulk Copy API */
    @ParameterizedTest
    @MethodSource(TEST_PARAMS)
    void bulkApiInsert(TestParam param) {
        if (param.getObjectsPerInsert() > 1) return;
        multipleRunner.execJob("bulkApiInsert", param,
                v -> bulkCopyRepository.performBulkInsert(
                        param.getPeople(), PersonDescriptors.PERSON_TABLE, param.getBatchSize()));
        assertInsertedCount(param);
    }
}
