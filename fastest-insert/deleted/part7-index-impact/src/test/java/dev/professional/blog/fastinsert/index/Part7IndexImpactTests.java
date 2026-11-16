package dev.professional.blog.fastinsert.index;

import dev.professional.blog.fastinsert.app.AbstractBenchmarkTest;
import dev.professional.blog.fastinsert.app.TestParam;
import dev.professional.blog.fastinsert.common.Person;
import dev.professional.blog.fastinsert.common.PersonDescriptors;
import dev.professional.blog.fastinsert.common.RandomPersonGenerator;
import dev.professional.blog.fastinsert.hibernate.ChunkEntityPersistRepository;
import dev.professional.blog.fastinsert.jdbc.BulkCopyRepository;
import dev.professional.blog.fastinsert.jdbc.SpringTemplateBulkInsertRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

/**
 * Part 7: Index impact — how 0/1/3/5 non-clustered indexes affect insert speed.
 */
class Part7IndexImpactTests extends AbstractBenchmarkTest {

    private static final String INDEX_PARAMS = "indexParams";

    @Autowired
    private IndexBenchmarkHelper indexBenchmarkHelper;
    @Autowired
    private BulkCopyRepository bulkCopyRepository;
    @Autowired
    private SpringTemplateBulkInsertRepository springTemplateBulkInsertRepository;
    @Autowired
    private ChunkEntityPersistRepository chunkEntityPersistRepository;

    @ParameterizedTest
    @MethodSource(INDEX_PARAMS)
    void bulkApiInsert_withIndexes(IndexParam param) {
        indexBenchmarkHelper.setIndexCount(param.indexCount);
        try {
            multipleRunner.execJob("bulkApi_idx" + param.indexCount, param.toTestParam(),
                    v -> bulkCopyRepository.performBulkInsert(
                            param.people, PersonDescriptors.PERSON_TABLE, param.batchSize));
            assertCount(param.people.size());
        } finally {
            indexBenchmarkHelper.setIndexCount(0);
        }
    }

    @ParameterizedTest
    @MethodSource(INDEX_PARAMS)
    void jdbcTemplateInsert_withIndexes(IndexParam param) {
        indexBenchmarkHelper.setIndexCount(param.indexCount);
        try {
            multipleRunner.execJob("jdbcTemplate_idx" + param.indexCount, param.toTestParam(),
                    v -> springTemplateBulkInsertRepository.insertPeople(
                            PersonDescriptors.PERSON_TABLE, param.people, param.batchSize, 1));
            assertCount(param.people.size());
        } finally {
            indexBenchmarkHelper.setIndexCount(0);
        }
    }

    @ParameterizedTest
    @MethodSource(INDEX_PARAMS)
    void hibernatePersist_withIndexes(IndexParam param) {
        indexBenchmarkHelper.setIndexCount(param.indexCount);
        try {
            multipleRunner.execJob("hibernate_idx" + param.indexCount, param.toTestParam(),
                    v -> chunkEntityPersistRepository.persistAndFlushInChunks(
                            param.people, param.batchSize));
            assertCount(param.people.size());
        } finally {
            indexBenchmarkHelper.setIndexCount(0);
        }
    }

    /** Index impact: 0, 1, 3, 5 indexes × batch 1000 */
    public static Stream<IndexParam> indexParams() {
        List<IndexParam> params = new ArrayList<>();
        Date start = new Date();
        List<Person> people = RandomPersonGenerator.generateRandomPersons(100_000);
        int[] indexCounts = {0, 1, 3, 5};
        for (int ic : indexCounts) {
            params.add(new IndexParam(people, 1000, start, ic));
        }
        return params.stream();
    }

    static class IndexParam {
        final List<Person> people;
        final int batchSize;
        final Date startDate;
        final int indexCount;

        IndexParam(List<Person> people, int batchSize, Date startDate, int indexCount) {
            this.people = people;
            this.batchSize = batchSize;
            this.startDate = startDate;
            this.indexCount = indexCount;
        }

        TestParam toTestParam() {
            return new TestParam(people, batchSize, startDate, 1);
        }

        @Override
        public String toString() {
            return indexCount + " indexes, batch=" + batchSize;
        }
    }
}
