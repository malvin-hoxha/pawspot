package com.pawspot.report.response;

import java.time.Instant;

import com.pawspot.report.enums.ReportReason;
import com.pawspot.report.enums.ReportStatus;

public record PostReportResponse(
    Long id,
    Long reportedPostId,
    String postTitle,
    ReportReason reason,
    String description,
    ReportStatus status,
    Instant createdAt

) {}
