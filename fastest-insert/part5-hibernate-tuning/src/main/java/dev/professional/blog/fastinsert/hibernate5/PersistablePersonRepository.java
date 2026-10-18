package dev.professional.blog.fastinsert.hibernate5;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * JPA repository for {@link PersistablePerson}.
 *
 * <p>When {@code saveAll()} is called on entities implementing {@code Persistable},
 * Spring Data uses {@code persist()} (INSERT) instead of {@code merge()} (SELECT + INSERT),
 * avoiding the per-entity SELECT that made {@code saveAll()} ~7x slower in Part 1.</p>
 */
public interface PersistablePersonRepository extends JpaRepository<PersistablePerson, Integer> {
}
