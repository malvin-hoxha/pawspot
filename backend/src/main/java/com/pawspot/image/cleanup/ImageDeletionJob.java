package com.pawspot.image.cleanup;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    prefix = "app.images.cleanup",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class ImageDeletionJob {

    private static final Logger log =
        LoggerFactory.getLogger(ImageDeletionJob.class);

    private final ImageDeletionTaskRepository tasks;
    private final ImageDeletionWorker worker;
    private final int batchSize;

    public ImageDeletionJob(
        ImageDeletionTaskRepository tasks,
        ImageDeletionWorker worker,
        @Value("${app.images.cleanup.batch-size:20}") int batchSize
    ) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException(
                "Image deletion batch size must be positive"
            );
        }

        this.tasks = tasks;
        this.worker = worker;
        this.batchSize = batchSize;
    }

    @Scheduled(
        fixedDelayString = "${app.images.cleanup.delay-ms:60000}",
        initialDelayString = "${app.images.cleanup.initial-delay-ms:60000}"
    )
    public void processReadyTasks() {
        var readyIds = tasks.findReadyIds(
            Instant.now(),
            PageRequest.of(0, batchSize)
        );

        for (Long taskId : readyIds) {
            try {
                worker.process(taskId);
            } catch (RuntimeException ex) {
                // One failed transaction must not stop the remaining tasks.
                log.error(
                    "Failed to process image deletion task {}",
                    taskId,
                    ex
                );
            }
        }
    }

}
