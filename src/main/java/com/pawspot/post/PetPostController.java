package com.pawspot.post;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.pawspot.user.User;

import jakarta.validation.Valid;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@RestController
@RequestMapping("/api/posts")
public class PetPostController {
    private final PetPostService petPostService;

    public PetPostController(PetPostService petPostService) {
        this.petPostService = petPostService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PetPostResponse> createPost(@Valid @ModelAttribute CreatePetPostRequest request,
        @RequestPart("image") MultipartFile image,
        Authentication authentication)
    {
        User user = (User) authentication.getPrincipal();

        PetPost post = petPostService.createPost(request, image, user);

        PetPostResponse response = new PetPostResponse(
            post.getId(),
            post.getType(),
            post.getTitle(),
            post.getDescription(),
            post.getSpecies(),
            post.getCity(),
            post.getArea(),
            post.getStatus(),
            post.getImageUrl(),
            post.getCreatedAt(),
            post.getUpdatedAt(),
            post.getUser().getId(),
            post.getUser().getName()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<PetPostResponse>> getPosts() {
        List<PetPost> posts = petPostService.getPosts();
        List<PetPostResponse> response = posts.stream()
            .map(post -> new PetPostResponse(
                    post.getId(),
                    post.getType(),
                    post.getTitle(),
                    post.getDescription(),
                    post.getSpecies(),
                    post.getCity(),
                    post.getArea(),
                    post.getStatus(),
                    post.getImageUrl(),
                    post.getCreatedAt(),
                    post.getUpdatedAt(),
                    post.getUser().getId(),
                    post.getUser().getName()
            )).toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PetPostResponse> getPost(@PathVariable Long id) {
        PetPost post = petPostService.getPost(id);
        PetPostResponse response = new PetPostResponse (
            post.getId(),
            post.getType(),
            post.getTitle(),
            post.getDescription(),
            post.getSpecies(),
            post.getCity(),
            post.getArea(),
            post.getStatus(),
            post.getImageUrl(),
            post.getCreatedAt(),
            post.getUpdatedAt(),
            post.getUser().getId(),
            post.getUser().getName()
        );

        return ResponseEntity.ok(response);
    }
    
}
