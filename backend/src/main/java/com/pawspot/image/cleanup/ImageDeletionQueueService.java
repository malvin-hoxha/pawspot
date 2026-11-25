package com.pawspot.image.cleanup;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service 
public class ImageDeletionQueueService {
    
    private final ImageDeletionTaskRepository tasks;

    public ImageDeletionQueueService(ImageDeletionTaskRepository tasks) {
        this.tasks = tasks;
    }

    //It must be included in an active transaction otherwise throw error
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            return;
        }

        // Store the task in the caller's existing transaction.
        tasks.save(new ImageDeletionTask(publicId));
    }
}
