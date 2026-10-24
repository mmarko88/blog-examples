package dev.professional.blog.fastinsert.bulkcopy;

import dev.professional.blog.fastinsert.app.AbstractBenchmarkTest;
import dev.professional.blog.fastinsert.app.TestParam;
import dev.professional.blog.fastinsert.common.PersonDescriptors;
import dev.professional.blog.fastinsert.hibernate.ChunkEntityPersistRepository;
import dev.professional.blog.fastinsert.jdbc.BulkPersistCustomRepository;
import dev.professional.blog.fastinsert.jdbc.SpringTemplateBulkInsertRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Part 4: useBulkCopyForBatchInsert=true — the same Part 1/2 batched inserts,
 * but the JDBC driver rewrites them into a single bulk-copy operation.
 */
@SpringBootTest(properties = {
        "spring.config.location=classpath:application.properties",
        "spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=testdb;encrypt=true;trustServerCertificate=true;useBulkCopyForBatchInsert=true",
        "spring.jpa.properties.hibernate.jdbc.batch_size=10000",
        "spring.jpa.properties.hibernate.order_inserts=true",
        "spring.jpa.properties.hibernate.order_updates=true"
})
class Part4BulkCopyParameterTests extends AbstractBenchmarkTest {

    @Autowired
    private ChunkEntityPersistRepository chunkEntityPersistRepository;
    @Autowired
    private BulkPersistCustomRepository bulkPersistCustomRepository;
    @Autowired
    private SpringTemplateBulkInsertRepository springTemplateBulkInsertRepository;

    /** Hibernate batch insert rewritten to bulk copy by the driver */
    @ParameterizedTest
    @MethodSource(TEST_PARAMS)
    void hibernatePersist(TestParam param) {
        if (param.getObjectsPerInsert() > 1) return;
        multipleRunner.execJob("hibernatePersist_bulkCopy", param,
                v -> chunkEntityPersistRepository.persistAndFlushInChunks(
                        param.getPeople(), param.getBatchSize()));
        assertInsertedCount(param);
    }

    @ParameterizedTest
    @MethodSource(TEST_PARAMS)
    void regularBatchInsert(TestParam param) {
        if (param.getObjectsPerInsert() > 1) return;
        multipleRunner.execJob("regularBatchInsert_bulkCopy", param,
                v -> bulkPersistCustomRepository.insert(
                        param.getPeople(), PersonDescriptors.PERSON_TABLE,
                        param.getBatchSize(), param.getObjectsPerInsert()));
        assertInsertedCount(param);
    }

    @ParameterizedTest
    @MethodSource(TEST_PARAMS)
    void jdbcTemplateInsert(TestParam param) {
        if (param.getObjectsPerInsert() > 1) return;
        multipleRunner.execJob("jdbcTemplateInsert_bulkCopy", param,
                v -> springTemplateBulkInsertRepository.insertPeople(
                        PersonDescriptors.PERSON_TABLE, param.getPeople(),
                        param.getBatchSize(), param.getObjectsPerInsert()));
        assertInsertedCount(param);
    }
}
