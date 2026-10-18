package dev.professional.blog.fastinsert.hibernate;

import dev.professional.blog.fastinsert.common.Person;
import dev.professional.blog.fastinsert.common.PersonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Simple JPA repository-based insert — delegates to {@code saveAllAndFlush}.
 *
 * <p>Blog post Part 1 compares this approach (which triggers a SELECT per entity
 * to check if it already exists) against the chunk-based {@code persist} approach
 * in {@link ChunkEntityPersistRepositoryImpl}.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PersonPersistService {
    private final PersonRepository personRepository;

    public void saveAll(List<Person> people) {
        personRepository.saveAllAndFlush(people);
    }
}
