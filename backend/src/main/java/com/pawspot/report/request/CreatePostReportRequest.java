package com.pawspot.report.request;
import com.pawspot.report.enums.ReportReason;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;


public record CreatePostReportRequest(
    @NotNull ReportReason reason,
    @Size(max = 1000) String description
) {

}