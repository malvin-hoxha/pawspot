package com.pawspot.report;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.pawspot.exception.PostNotFoundException;
import com.pawspot.post.PetPost;
import com.pawspot.post.PetPostRepository;
import com.pawspot.report.enums.ReportStatus;
import com.pawspot.report.request.CreatePostReportRequest;
import com.pawspot.report.response.PostReportResponse;
import com.pawspot.user.User;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PostReportService {

    PostReportRepository postReportRepository;
    PetPostRepository petPostRepository;

    public PostReportService(PostReportRepository postReportRepository, PetPostRepository petPostRepository) {
        this.postReportRepository = postReportRepository;
        this.petPostRepository = petPostRepository;
    }

    public Long submitReport(Long postId, User reporter, CreatePostReportRequest request) {
        PetPost post = petPostRepository.findById(postId).orElseThrow(() -> new PostNotFoundException("Post not found"));

        String description = request.description();
        if (description == null || description.isBlank()) {
            description = null;
        } else {
            description = description.strip();
        }

        PostReport postReport = new PostReport(post, reporter, request.reason(), description);
        return postReportRepository.save(postReport).getId();
    }

    @Transactional(readOnly = true)
    public Page<PostReportResponse> getPendingReports(int page, int size) {
        PageRequest pageable = PageRequest.of(
            page,
            size,
            Sort.by(Sort.Direction.ASC, "createdAt", "id")
        );


        return postReportRepository.findByStatus(ReportStatus.PENDING, pageable).map(this::toResponse);
    }

    private PostReportResponse toResponse(PostReport report) {
        var post = report.getPost();

        return new PostReportResponse(
            report.getId(),
            report.getReportedPostId(),
            post == null ? null : post.getTitle(),
            report.getReason(),
            report.getDescription(),
            report.getStatus(),
            report.getCreatedAt()
        );
    }

    @Transactional 
    public void dismissReport(Long reportId) {
        PostReport postReport = postReportRepository.findById(reportId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,"Report not found"));
        postReport.setStatus(ReportStatus.DISMISSED);
    }
}