package com.pawspot.report;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pawspot.report.request.CreatePostReportRequest;
import com.pawspot.user.User;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/posts")
public class PostReportController {

    PostReportService postReportService;

    public PostReportController(PostReportService postReportService) {
        this.postReportService = postReportService;
    }

    @PostMapping("/{postId}/reports")
    public ResponseEntity<CreatePostReportResponse> createReport(
        @PathVariable("postId") Long postId,
        @Valid @RequestBody CreatePostReportRequest request,
        Authentication authentication
    ) {
        User reporter = (User) authentication.getPrincipal();

        Long reportId = postReportService.submitReport(postId, reporter, request);
        
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(new CreatePostReportResponse(reportId));
    }
}


record CreatePostReportResponse(Long id) {}