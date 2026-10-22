package dev.professional.blog.fastinsert.app;

import dev.professional.blog.fastinsert.FastestInsertApplication;
import dev.professional.blog.fastinsert.common.Person;
import dev.professional.blog.fastinsert.common.PersonRepository;
import dev.professional.blog.fastinsert.common.RandomPersonGenerator;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

/**
 * Base class for per-module benchmark tests. Boots the shared
 * {@link FastestInsertApplication} context, wires the common infrastructure
 * (person repository for assertions, {@link MultipleRunner} for timed
 * repetitions), and provides the default 16-scenario parameter sweep
 * (100k people × objects-per-insert {1,10,100,400} × batch {10,100,1000,5000}).
 *
 * <p>Requires a running MS SQL Server with a {@code testdb} database.</p>
 */
@SpringBootTest(properties = "spring.config.location=classpath:application.properties")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Slf4j
public abstract class AbstractBenchmarkTest {
    protected static final String TEST_PARAMS = "testParams";

    @Autowired
    protected PersonRepository personRepository;
    @Autowired
    protected MultipleRunner multipleRunner;
    @Autowired
    protected JdbcTemplate jdbcTemplate;

    protected void assertInsertedCount(TestParam param) {
        long count = personRepository.count();
        Assertions.assertThat(count).isEqualTo(param.getPeople().size());
    }

    protected void assertCount(int expected) {
        Assertions.assertThat(personRepository.count()).isEqualTo(expected);
    }

    public static Stream<TestParam> testParams() {
        List<TestParam> params = new ArrayList<>();
        Date startDate = new Date();
        int[] objectsPerInsert = {1, 10, 100, 400};
        int[] batchSizes = {10, 100, 1_000, 5_000};

        for (int opi : objectsPerInsert) {
            List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
            for (int bs : batchSizes) {
                params.add(new TestParam(people, bs, startDate, opi));
            }
        }
        return params.stream();
    }
}
