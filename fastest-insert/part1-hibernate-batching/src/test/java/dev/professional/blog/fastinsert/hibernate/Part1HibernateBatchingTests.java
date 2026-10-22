package dev.professional.blog.fastinsert.hibernate;

import dev.professional.blog.fastinsert.app.AbstractBenchmarkTest;
import dev.professional.blog.fastinsert.app.TestParam;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Part 1: Hibernate persist-and-flush vs JPA saveAll — WITH JDBC batching
 * (batch_size=10000). See {@link Part1UnbatchedTests} for the unbatched baseline.
 */
@SpringBootTest(properties = {
        "spring.config.location=classpath:application.properties",
        "spring.jpa.properties.hibernate.jdbc.batch_size=10000",
        "spring.jpa.properties.hibernate.order_inserts=true",
        "spring.jpa.properties.hibernate.order_updates=true"
})
class Part1HibernateBatchingTests extends AbstractBenchmarkTest {

    @Autowired
    private ChunkEntityPersistRepository chunkEntityPersistRepository;
    @Autowired
    private PersonPersistService personPersistService;

    /** Part 1: Hibernate persist-and-flush */
    @ParameterizedTest
    @MethodSource(TEST_PARAMS)
    void hibernatePersist(TestParam param) {
        if (param.getObjectsPerInsert() > 1) return;
        multipleRunner.execJob("hibernatePersist", param,
                v -> chunkEntityPersistRepository.persistAndFlushInChunks(
                        param.getPeople(), param.getBatchSize()));
        assertInsertedCount(param);
    }

    /** Part 1: JPA saveAll (SELECT per entity + INSERT) */
    @ParameterizedTest
    @MethodSource(TEST_PARAMS)
    void saveAllInsert(TestParam param) {
        if (param.getObjectsPerInsert() > 1) return;
        multipleRunner.execJob("saveAll", param,
                v -> personPersistService.saveAll(param.getPeople()));
        assertInsertedCount(param);
    }
}
