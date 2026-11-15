package com.pawspot.report;

import org.springframework.stereotype.Service;

import com.pawspot.exception.PostNotFoundException;
import com.pawspot.post.PetPost;
import com.pawspot.post.PetPostRepository;
import com.pawspot.report.request.CreatePostReportRequest;
import com.pawspot.user.User;

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
}
