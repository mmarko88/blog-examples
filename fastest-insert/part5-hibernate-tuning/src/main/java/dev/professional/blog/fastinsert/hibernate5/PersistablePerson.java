package dev.professional.blog.fastinsert.hibernate5;

import dev.professional.blog.fastinsert.common.PersonConstants;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

/**
 * A Person entity that implements {@link Persistable} to avoid the
 * SELECT-before-INSERT problem in JPA's {@code saveAll()}.
 *
 * <p>By default, Spring Data JPA calls {@code EntityManager.merge()} for entities
 * whose ID is not null (it assumes they already exist). This triggers a SELECT
 * per entity to check if it's new or existing — the exact reason {@code saveAll()}
 * was ~7x slower in Part 1.</p>
 *
 * <p>Implementing {@code Persistable} with {@code isNew() = true} tells Spring Data
 * to always use {@code persist()} (INSERT) instead of {@code merge()} (SELECT + INSERT).
 * Combined with Hibernate batching, this should close the gap between saveAll and
 * manual persist-in-chunks.</p>
 */
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = PersonConstants.PERSON_TABLE_NAME)
@Getter
public class PersistablePerson implements Persistable<Integer> {
    @Id
    @Column(name = PersonConstants.PERSON_ID, columnDefinition = PersonConstants.PERSON_ID_COL_DEF)
    private int personId;
    @Column(name = PersonConstants.USER_NAME, nullable = false, length = PersonConstants.USERNAME_LENGTH, columnDefinition = PersonConstants.USERNAME_COL_DEF)
    private String userName;
    @Column(name = PersonConstants.FIRST_NAME, nullable = false, length = PersonConstants.FIRST_NAME_LENGTH, columnDefinition = PersonConstants.FIRST_NAME_COL_DEF)
    private String firstName;
    @Column(name = PersonConstants.LAST_NAME, nullable = false, length = PersonConstants.LAST_NAME_LENGTH, columnDefinition = PersonConstants.LAST_NAME_COL_DEF)
    private String lastName;
    @Column(name = PersonConstants.YEARS, columnDefinition = PersonConstants.YEARS_COL_DEF)
    private int years;

    @Transient
    private boolean isNew = true;

    @Override
    public Integer getId() {
        return personId;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }
}
