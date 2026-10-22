package dev.professional.blog.fastinsert.hibernate5;

import dev.professional.blog.fastinsert.app.AbstractBenchmarkTest;
import dev.professional.blog.fastinsert.app.TestParam;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Part 5: Hibernate tuning — StatelessSession and Persistable
 * (avoids the SELECT-before-INSERT that plain saveAll issues).
 * Runs WITH JDBC batching (batch_size=10000) — opted in since it's off by default.
 */
@SpringBootTest(properties = {
        "spring.config.location=classpath:application.properties",
        "spring.jpa.properties.hibernate.jdbc.batch_size=10000",
        "spring.jpa.properties.hibernate.order_inserts=true",
        "spring.jpa.properties.hibernate.order_updates=true"
})
class Part5HibernateTuningTests extends AbstractBenchmarkTest {

    @Autowired
    private StatelessSessionInsertRepository statelessSessionInsertRepository;
    @Autowired
    private PersistablePersonRepository persistablePersonRepository;

    /** Part 5: Hibernate StatelessSession (no 1st-level cache, no dirty checking) */
    @ParameterizedTest
    @MethodSource(TEST_PARAMS)
    void statelessSessionInsert(TestParam param) {
        if (param.getObjectsPerInsert() > 1) return;
        multipleRunner.execJob("statelessSession", param,
                v -> statelessSessionInsertRepository.insertStateless(
                        param.getPeople(), param.getBatchSize()));
        assertInsertedCount(param);
    }

    /** Part 5: saveAll with Persistable (skips the SELECT per entity) */
    @ParameterizedTest
    @MethodSource(TEST_PARAMS)
    void saveAllPersistableInsert(TestParam param) {
        if (param.getObjectsPerInsert() > 1) return;
        multipleRunner.execJob("saveAllPersistable", param,
                v -> {
                    List<PersistablePerson> persistable = param.getPeople().stream()
                            .map(p -> new PersistablePerson(
                                    p.getPersonId(), p.getUserName(), p.getFirstName(),
                                    p.getLastName(), p.getYears(), true))
                            .collect(Collectors.toList());
                    persistablePersonRepository.saveAllAndFlush(persistable);
                });
        assertInsertedCount(param);
    }
}
