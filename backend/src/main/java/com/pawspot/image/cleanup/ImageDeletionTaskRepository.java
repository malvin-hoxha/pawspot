package com.pawspot.image.cleanup;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface ImageDeletionTaskRepository extends JpaRepository<ImageDeletionTask, Long>{

    @Query("""
        select task.id
        from ImageDeletionTask task
        where task.nextAttemptAt <= :now
        order by task.nextAttemptAt asc, task.id asc
        """)
    List<Long> findReadyIds(
        @Param("now") Instant now,
        Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select task
        from ImageDeletionTask task
        where task.id = :id
        """)
    Optional<ImageDeletionTask> findByIdForUpdate(@Param("id") Long id);
}
