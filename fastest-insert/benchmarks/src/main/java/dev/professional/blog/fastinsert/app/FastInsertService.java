package dev.professional.blog.fastinsert.app;

import dev.professional.blog.fastinsert.common.Person;
import dev.professional.blog.fastinsert.common.PersonDescriptors;
import dev.professional.blog.fastinsert.hibernate.ChunkEntityPersistRepository;
import dev.professional.blog.fastinsert.hibernate.PersonPersistService;
import dev.professional.blog.fastinsert.jdbc.BulkCopyRepository;
import dev.professional.blog.fastinsert.jdbc.BulkPersistCustomRepository;
import dev.professional.blog.fastinsert.jdbc.SpringTemplateBulkInsertRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Orchestrates all insert strategies for benchmarking.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class FastInsertService {
    @PersistenceContext
    private EntityManager entityManager;

    private final ChunkEntityPersistRepository chunkEntityPersistRepository;
    private final PersonPersistService personPersistService;
    private final BulkPersistCustomRepository bulkPersistCustomRepository;
    private final SpringTemplateBulkInsertRepository springTemplateBulkInsertRepository;
    private final BulkCopyRepository bulkCopyRepository;

    public void cleanUpPeopleTable() {
        entityManager.createNativeQuery("truncate table person").executeUpdate();
        entityManager.clear();
    }

    /** Part 1: Hibernate persist-and-flush in chunks */
    public void insertHibernateBatch(List<Person> people, int batchSize) {
        chunkEntityPersistRepository.persistAndFlushInChunks(people, batchSize);
    }

    /** Part 1: JPA saveAll (triggers SELECT per entity, then INSERT) */
    public void saveAll(List<Person> people) {
        personPersistService.saveAll(people);
    }

    /** Part 2: JDBC PreparedStatement with multi-value support */
    public void insertJdbcBatch(int batchSize, int objectsPerInsert, List<Person> people) {
        bulkPersistCustomRepository.insert(people, PersonDescriptors.PERSON_TABLE, batchSize, objectsPerInsert);
    }

    /** Part 2: Spring JdbcTemplate batch */
    public void insertJdbcTemplateBatch(int batchSize, int objectsPerInsert, List<Person> people) {
        springTemplateBulkInsertRepository.insertPeople(PersonDescriptors.PERSON_TABLE, people, batchSize, objectsPerInsert);
    }

    /** Part 2: MS SQL Bulk Copy API */
    public void insertBulkApi(List<Person> people, int batchSize) {
        bulkCopyRepository.performBulkInsert(people, PersonDescriptors.PERSON_TABLE, batchSize);
    }
}
