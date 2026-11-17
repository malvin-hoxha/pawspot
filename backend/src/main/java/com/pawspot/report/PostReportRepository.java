package com.pawspot.report;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.pawspot.report.enums.ReportStatus;

public interface PostReportRepository extends JpaRepository<PostReport, Long>{
    Page<PostReport> findByStatus(ReportStatus status, Pageable pageable);
}
