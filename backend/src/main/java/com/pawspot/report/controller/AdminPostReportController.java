package com.pawspot.report.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pawspot.common.PageResponse;
import com.pawspot.report.PostReportService;
import com.pawspot.report.response.PostReportResponse;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;



@RestController
@RequestMapping("/api/admin/post-reports")
public class AdminPostReportController {
    private final PostReportService postReportService;

    public AdminPostReportController(PostReportService postReportService) {
        this.postReportService = postReportService;
    }

    @GetMapping
    public PageResponse<PostReportResponse> getPendingReports(
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size
    ) {
        Page<PostReportResponse> result = postReportService.getPendingReports(page, size);

        return new PageResponse<>(
            result.getContent(),
            result.getNumber(),
            result.getSize(),
            result.getTotalElements(),
            result.getTotalPages(),
            result.isFirst(),
            result.isLast()
        );
    }

    @PostMapping("/{reportId}/dismiss")
    public ResponseEntity<Void> dismissReport(@PathVariable("reportId") Long reportId) {
        postReportService.dismissReport(reportId);
        return ResponseEntity.noContent().build();
    }
    
    
}
