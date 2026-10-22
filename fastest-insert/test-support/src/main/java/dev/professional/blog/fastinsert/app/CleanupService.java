package dev.professional.blog.fastinsert.app;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Test-table maintenance shared by all benchmark modules.
 */
@Service
@Transactional
public class CleanupService {
    @PersistenceContext
    private EntityManager entityManager;

    public void cleanUpPeopleTable() {
        entityManager.createNativeQuery("truncate table person").executeUpdate();
        entityManager.clear();
    }
}
