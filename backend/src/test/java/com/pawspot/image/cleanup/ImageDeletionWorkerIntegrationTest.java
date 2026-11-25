package com.pawspot.image.cleanup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import com.pawspot.image.CloudinaryService;
import com.pawspot.support.PostgresIntegrationTest;

@SpringBootTest(properties = "app.scheduling.enabled=false")
class ImageDeletionWorkerIntegrationTest extends PostgresIntegrationTest {

    private static final Duration RETRY_DELAY = Duration.ofMinutes(5);

    @Autowired
    private ImageDeletionWorker worker;

    @Autowired
    private ImageDeletionTaskRepository tasks;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockitoBean
    private CloudinaryService cloudinaryService;

    @Test
    void shouldDeleteSuccessfulTaskAndPreserveUnrelatedTask() {
        ImageDeletionTask due = saveTask(
            "pawspot/posts/due-image",
            Instant.now().minusSeconds(1)
        );
        ImageDeletionTask unrelated = saveTask(
            "pawspot/posts/unrelated-image",
            Instant.now().plusSeconds(60)
        );

        worker.process(due.getId());

        verify(cloudinaryService, times(1)).delete(due.getPublicId());
        assertFalse(tasks.existsById(due.getId()));
        assertTrue(tasks.existsById(unrelated.getId()));
    }

    @Test
    void shouldDeferFailedTaskForFiveMinutes() {
        ImageDeletionTask task = saveTask(
            "pawspot/posts/retry-image",
            Instant.now().minusSeconds(1)
        );
        doThrow(new RuntimeException("Cloudinary unavailable"))
            .when(cloudinaryService)
            .delete(task.getPublicId());

        Instant beforeFailure = Instant.now();
        worker.process(task.getId());
        Instant afterFailure = Instant.now();

        ImageDeletionTask deferred = tasks.findById(task.getId())
            .orElseThrow();
        Instant retryAt = deferred.getNextAttemptAt();

        assertEquals(1, deferred.getAttempts());
        assertFalse(retryAt.isBefore(beforeFailure.plus(RETRY_DELAY)));
        assertFalse(retryAt.isAfter(afterFailure.plus(RETRY_DELAY)));

        worker.process(task.getId());

        ImageDeletionTask unchanged = tasks.findById(task.getId())
            .orElseThrow();
        assertEquals(1, unchanged.getAttempts());
        assertEquals(retryAt, unchanged.getNextAttemptAt());
        verify(cloudinaryService, times(1)).delete(task.getPublicId());
    }

    @Test
    void shouldIgnoreFutureAndMissingTasks() {
        Instant scheduledAt = Instant.now()
            .plusSeconds(60)
            .truncatedTo(ChronoUnit.MICROS);
        ImageDeletionTask future = saveTask(
            "pawspot/posts/future-image",
            scheduledAt
        );

        worker.process(future.getId());
        worker.process(Long.MAX_VALUE);

        ImageDeletionTask unchanged = tasks.findById(future.getId())
            .orElseThrow();
        assertEquals(0, unchanged.getAttempts());
        assertEquals(scheduledAt, unchanged.getNextAttemptAt());
        verifyNoInteractions(cloudinaryService);
    }

    @Test
    void shouldReprocessAfterDatabaseRollback() {
        ImageDeletionTask task = saveTask(
            "pawspot/posts/rollback-image",
            Instant.now().minusSeconds(1)
        );

        transactionTemplate.executeWithoutResult(status -> {
            worker.process(task.getId());
            tasks.flush();

            assertFalse(tasks.existsById(task.getId()));
            status.setRollbackOnly();
        });

        assertTrue(tasks.existsById(task.getId()));

        worker.process(task.getId());

        assertFalse(tasks.existsById(task.getId()));
        verify(cloudinaryService, times(2)).delete(task.getPublicId());
    }

    @Test
    void shouldProcessConcurrentTaskOnlyOnce() throws Exception {
        ImageDeletionTask task = saveTask(
            "pawspot/posts/concurrent-image",
            Instant.now().minusSeconds(1)
        );
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<?> first = executor.submit(() -> {
            ready.countDown();
            start.await();
            worker.process(task.getId());
            return null;
        });
        Future<?> second = executor.submit(() -> {
            ready.countDown();
            start.await();
            worker.process(task.getId());
            return null;
        });

        try {
            assertTrue(
                ready.await(5, TimeUnit.SECONDS),
                "Workers did not become ready"
            );

            transactionTemplate.executeWithoutResult(status -> {
                tasks.findByIdForUpdate(task.getId()).orElseThrow();
                start.countDown();
                awaitBlockedDatabaseTransactions(2);
            });

            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);

            verify(cloudinaryService, times(1)).delete(task.getPublicId());
            assertFalse(tasks.existsById(task.getId()));
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(
                executor.awaitTermination(5, TimeUnit.SECONDS),
                "Executor did not terminate"
            );
        }
    }

    @Test
    void shouldSelectReadyTasksInOrderAndRespectBatchLimit() {
        Instant cutoff = Instant.parse("2026-11-01T12:00:00Z");
        ImageDeletionTask exactFirst = saveTask(
            "pawspot/posts/exact-first",
            cutoff
        );
        ImageDeletionTask oldest = saveTask(
            "pawspot/posts/oldest",
            cutoff.minusSeconds(60)
        );
        ImageDeletionTask exactSecond = saveTask(
            "pawspot/posts/exact-second",
            cutoff
        );
        ImageDeletionTask exactThird = saveTask(
            "pawspot/posts/exact-third",
            cutoff
        );
        saveTask("pawspot/posts/future", cutoff.plusSeconds(1));

        assertEquals(
            List.of(
                oldest.getId(),
                exactFirst.getId(),
                exactSecond.getId(),
                exactThird.getId()
            ),
            tasks.findReadyIds(cutoff, PageRequest.of(0, 10))
        );
        assertEquals(
            List.of(oldest.getId(), exactFirst.getId()),
            tasks.findReadyIds(cutoff, PageRequest.of(0, 2))
        );
    }

    private ImageDeletionTask saveTask(String publicId, Instant nextAttemptAt) {
        ImageDeletionTask task = new ImageDeletionTask(publicId);
        task.setNextAttemptAt(nextAttemptAt);
        return tasks.saveAndFlush(task);
    }
}
