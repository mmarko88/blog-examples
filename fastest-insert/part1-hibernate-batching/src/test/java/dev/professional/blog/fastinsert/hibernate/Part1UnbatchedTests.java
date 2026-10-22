package dev.professional.blog.fastinsert.hibernate;

import dev.professional.blog.fastinsert.app.AbstractBenchmarkTest;
import dev.professional.blog.fastinsert.app.TestParam;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Part 1 unbatched baselines — the same persist and saveAll strategies,
 * but with JDBC batching fully disabled (batch_size=0), matching the blog's
 * "Persist" and "SaveAll" series.
 */
@SpringBootTest(properties = "spring.config.location=classpath:application.properties")
class Part1UnbatchedTests extends AbstractBenchmarkTest {

    @Autowired
    private ChunkEntityPersistRepository chunkEntityPersistRepository;
    @Autowired
    private PersonPersistService personPersistService;

    /** Unbatched persist — same chunk loop, no JDBC batching behind it */
    @ParameterizedTest
    @MethodSource(TEST_PARAMS)
    void persistUnbatched(TestParam param) {
        if (param.getObjectsPerInsert() > 1) return;
        multipleRunner.execJob("persistUnbatched", param,
                v -> chunkEntityPersistRepository.persistAndFlushInChunks(
                        param.getPeople(), param.getBatchSize()));
        assertInsertedCount(param);
    }

    /** Unbatched saveAll — SELECT per entity + INSERT, no JDBC batching */
    @ParameterizedTest
    @MethodSource(TEST_PARAMS)
    void saveAllUnbatched(TestParam param) {
        if (param.getObjectsPerInsert() > 1) return;
        multipleRunner.execJob("saveAllUnbatched", param,
                v -> personPersistService.saveAll(param.getPeople()));
        assertInsertedCount(param);
    }
}
