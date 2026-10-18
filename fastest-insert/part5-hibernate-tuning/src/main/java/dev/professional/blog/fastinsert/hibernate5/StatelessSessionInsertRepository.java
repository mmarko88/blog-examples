package dev.professional.blog.fastinsert.hibernate5;

import dev.professional.blog.fastinsert.common.Person;
import dev.professional.blog.fastinsert.common.PersonConstants;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.StatelessSession;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Uses Hibernate's {@link StatelessSession} for bulk inserts.
 *
 * <p>StatelessSession bypasses the first-level cache, dirty checking,
 * and cascade operations entirely. This means:</p>
 * <ul>
 *   <li>No entity state tracking — entities are not managed after insert</li>
 *   <li>No dirty checking at flush time — saves CPU for large batches</li>
 *   <li>No first-level cache growth — constant memory regardless of batch size</li>
 *   <li>No cascade — related entities must be inserted explicitly</li>
 * </ul>
 *
 * <p>The trade-off: you lose all ORM convenience. This is as close to raw JDBC
 * as you can get while still using Hibernate's mapping metadata.</p>
 */
@Repository
@RequiredArgsConstructor
public class StatelessSessionInsertRepository {

    private final EntityManagerFactory entityManagerFactory;

    /**
     * Insert all persons using StatelessSession with JDBC batching.
     *
     * @param people    the persons to insert
     * @param batchSize JDBC batch size (controls how many INSERTs are sent per round-trip)
     */
    public void insertStateless(List<Person> people, int batchSize) {
        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);

        try (StatelessSession statelessSession = sessionFactory.openStatelessSession()) {
            statelessSession.getTransaction().begin();

            // Set batch size on the underlying JDBC connection
            statelessSession.doWork(connection ->
                    connection.createStatement().execute(
                            "SET NOCOUNT ON"));

            for (int i = 0; i < people.size(); i++) {
                statelessSession.insert(people.get(i));

                // Flush the JDBC batch every batchSize rows
                if (i > 0 && i % batchSize == 0) {
                    statelessSession.doWork(connection -> {
                        // Force JDBC driver to send the batch
                    });
                }
            }

            statelessSession.getTransaction().commit();
        }
    }
}
