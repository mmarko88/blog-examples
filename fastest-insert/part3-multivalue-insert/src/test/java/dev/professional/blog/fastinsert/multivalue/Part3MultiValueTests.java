package dev.professional.blog.fastinsert.multivalue;

import dev.professional.blog.fastinsert.app.AbstractBenchmarkTest;
import dev.professional.blog.fastinsert.app.TestParam;
import dev.professional.blog.fastinsert.common.PersonDescriptors;
import dev.professional.blog.fastinsert.jdbc.BulkPersistCustomRepository;
import dev.professional.blog.fastinsert.jdbc.SpringTemplateBulkInsertRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Part 3: Multi-value INSERT — N objects per statement (objectsPerInsert > 1).
 * Reuses the Part 2 JDBC strategies; single-object scenarios are skipped.
 */
class Part3MultiValueTests extends AbstractBenchmarkTest {

    @Autowired
    private BulkPersistCustomRepository bulkPersistCustomRepository;
    @Autowired
    private SpringTemplateBulkInsertRepository springTemplateBulkInsertRepository;

    @ParameterizedTest
    @MethodSource(TEST_PARAMS)
    void regularBatchInsert(TestParam param) {
        if (param.getObjectsPerInsert() <= 1) return;
        multipleRunner.execJob("regularBatchInsert", param,
                v -> bulkPersistCustomRepository.insert(
                        param.getPeople(), PersonDescriptors.PERSON_TABLE,
                        param.getBatchSize(), param.getObjectsPerInsert()));
        assertInsertedCount(param);
    }

    @ParameterizedTest
    @MethodSource(TEST_PARAMS)
    void jdbcTemplateInsert(TestParam param) {
        if (param.getObjectsPerInsert() <= 1) return;
        multipleRunner.execJob("jdbcTemplateInsert", param,
                v -> springTemplateBulkInsertRepository.insertPeople(
                        PersonDescriptors.PERSON_TABLE, param.getPeople(),
                        param.getBatchSize(), param.getObjectsPerInsert()));
        assertInsertedCount(param);
    }
}
