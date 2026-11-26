package com.pawspot.image.cleanup;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import com.pawspot.support.PostgresIntegrationTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties = "app.scheduling.enabled=false")
class ImageDeletionQueueIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private ImageDeletionQueueService queue;

    @Autowired
    private ImageDeletionTaskRepository tasks;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void shouldPersistTaskWhenTransactionCommits() {
        transactionTemplate.executeWithoutResult(status ->
            queue.enqueue("pawspot/posts/queued-image")
        );

        var saved = tasks.findAll();

        assertEquals(1, saved.size());

        ImageDeletionTask task = saved.get(0);

        assertEquals("pawspot/posts/queued-image", task.getPublicId());
        assertEquals(0, task.getAttempts());
        assertNotNull(task.getId());
        assertNotNull(task.getCreatedAt());
        assertEquals(task.getCreatedAt(), task.getNextAttemptAt());
    }

    @Test
    void shouldDiscardTaskWhenTransactionRollsBack() {
        transactionTemplate.executeWithoutResult(status -> {
            queue.enqueue("pawspot/posts/rolled-back-image");

            // Simulate a later failure in the caller's transaction.
            status.setRollbackOnly();
        });

        assertEquals(0L, tasks.count());
    }

    @Test
    void shouldCommitFailedCreationTaskIndependentlyOfOuterRollback() {
        transactionTemplate.executeWithoutResult(status -> {
            queue.enqueueAfterFailedCreation(
                "pawspot/posts/failed-creation"
            );

            status.setRollbackOnly();
        });

        var saved = tasks.findAll();

        assertEquals(1, saved.size());
        assertEquals(
            "pawspot/posts/failed-creation",
            saved.get(0).getPublicId()
        );
    }

    @Test
    void shouldRejectEnqueueWithoutAnExistingTransaction() {
        assertThrows(
            IllegalTransactionStateException.class,
            () -> queue.enqueue("pawspot/posts/no-transaction")
        );

        assertEquals(0L, tasks.count());
    }
}
