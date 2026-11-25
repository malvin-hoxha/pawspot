package com.pawspot.image.cleanup;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class ImageDeletionJobTest {

    @Mock
    private ImageDeletionTaskRepository tasks;

    @Mock
    private ImageDeletionWorker worker;

    @Test
    void shouldRequestFirstBatchAndProcessEveryTaskInOrder() {
        int batchSize = 3;
        when(tasks.findReadyIds(
            any(Instant.class),
            eq(PageRequest.of(0, batchSize))
        )).thenReturn(List.of(11L, 12L, 13L));
        ImageDeletionJob job = new ImageDeletionJob(tasks, worker, batchSize);

        job.processReadyTasks();

        verify(tasks).findReadyIds(
            any(Instant.class),
            eq(PageRequest.of(0, batchSize))
        );
        InOrder processing = inOrder(worker);
        processing.verify(worker).process(11L);
        processing.verify(worker).process(12L);
        processing.verify(worker).process(13L);
    }

    @Test
    void shouldNotInvokeWorkerWhenNoTasksAreReady() {
        when(tasks.findReadyIds(any(Instant.class), any()))
            .thenReturn(List.of());
        ImageDeletionJob job = new ImageDeletionJob(tasks, worker, 5);

        job.processReadyTasks();

        verifyNoInteractions(worker);
    }

    @Test
    void shouldContinueAfterOneTaskFails() {
        when(tasks.findReadyIds(any(Instant.class), any()))
            .thenReturn(List.of(21L, 22L, 23L));
        doAnswer(invocation -> {
            Long taskId = invocation.getArgument(0);
            if (taskId.equals(22L)) {
                throw new RuntimeException("Processing failed");
            }
            return null;
        }).when(worker).process(anyLong());
        ImageDeletionJob job = new ImageDeletionJob(tasks, worker, 5);

        job.processReadyTasks();

        InOrder processing = inOrder(worker);
        processing.verify(worker).process(21L);
        processing.verify(worker).process(22L);
        processing.verify(worker).process(23L);
    }

    @Test
    void shouldRejectNonPositiveBatchSizes() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new ImageDeletionJob(tasks, worker, 0)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new ImageDeletionJob(tasks, worker, -1)
        );

        verify(tasks, never()).findReadyIds(any(), any());
        verifyNoInteractions(worker);
    }
}
