package com.pawspot.report;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.pawspot.report.enums.ReportStatus;
import java.time.Instant;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostReportRepository extends JpaRepository<PostReport, Long>{
    Page<PostReport> findByStatus(ReportStatus status, Pageable pageable);

    List<PostReport> findByReportedPostIdAndStatus(
        Long reportedPostId,
        ReportStatus status
    );

    @Modifying // Modifies data
    @Query(
        value = """
            DELETE FROM public.post_reports
            WHERE status IN ('DISMISSED', 'POST_REMOVED')
            AND resolved_at <= :cutoff
            """,
        // Executes the query as raw SQL instead of JPQL — needed here because
        nativeQuery = true
    )
    int deleteResolvedBefore(@Param("cutoff") Instant cutoff);
}
