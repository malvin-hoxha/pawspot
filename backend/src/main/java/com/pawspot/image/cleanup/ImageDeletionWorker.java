package com.pawspot.image.cleanup;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pawspot.image.CloudinaryService;

@Service
public class ImageDeletionWorker {
    private static final Logger log =
        LoggerFactory.getLogger(ImageDeletionWorker.class);

    private static final Duration RETRY_DELAY = Duration.ofMinutes(5);

    private final ImageDeletionTaskRepository tasks;
    private final CloudinaryService cloudinaryService;

    public ImageDeletionWorker(
        ImageDeletionTaskRepository tasks,
        CloudinaryService cloudinaryService
    ) {
        this.tasks = tasks;
        this.cloudinaryService = cloudinaryService;
    }

    @Transactional
    public void process(Long taskId) {
        ImageDeletionTask task = tasks.findByIdForUpdate(taskId)
            .orElse(null);

        if (task == null) {
            return;
        }

        // Another worker may have deferred the task before we acquired its lock.
        if (task.getNextAttemptAt().isAfter(Instant.now())) {
            return;
        }

        try {
            cloudinaryService.delete(task.getPublicId());
        } catch (RuntimeException ex) {
            task.setAttempts(task.getAttempts() + 1);
            task.setNextAttemptAt(Instant.now().plus(RETRY_DELAY));
            log.warn(
                "Image deletion attempt {} failed for task {}",
                task.getAttempts(),
                task.getId()
            );

            return;
        }
        tasks.delete(task);
    }
}
