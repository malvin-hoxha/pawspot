package com.pawspot.post;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.pawspot.common.PageResponse;
import com.pawspot.post.enums.PetSpecies;
import com.pawspot.post.enums.PostStatus;
import com.pawspot.post.enums.PostType;
import com.pawspot.post.mapper.PetPostMapper;
import com.pawspot.post.request.CreatePetPostRequest;
import com.pawspot.post.request.PostFilterRequest;
import com.pawspot.post.request.UpdatePetPostRequest;
import com.pawspot.post.response.PetPostResponse;
import com.pawspot.user.User;

import jakarta.validation.Valid;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


@RestController
@RequestMapping("/api/posts")
public class PetPostController {
    private final PetPostService petPostService;
    private final PetPostMapper petPostMapper;

    public PetPostController(PetPostService petPostService, PetPostMapper petPostMapper) {
        this.petPostService = petPostService;
        this.petPostMapper = petPostMapper;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PetPostResponse> createPost(@Valid @ModelAttribute CreatePetPostRequest request,
        @RequestPart("image") MultipartFile image,
        Authentication authentication)
    {
        User user = (User) authentication.getPrincipal();

        PetPost post = petPostService.createPost(request, image, user);

        PetPostResponse response = petPostMapper.toResponse(post);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<PageResponse<PetPostResponse>> getPosts(
        @Valid @ModelAttribute PostFilterRequest request
    ) {
        Page<PetPost> posts = petPostService.getPosts(
            request.getType(),
            request.getStatus(),
            request.getSpecies(),
            request.getCity(),
            request.getSearch(),
            request.getPage(),
            request.getSize(),
            request.getSortBy(),
            request.getDirection()
        );

        return ResponseEntity.ok(petPostMapper.toPageResponse(posts));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PetPostResponse> getPost(@PathVariable Long id) {
        PetPost post = petPostService.getPost(id);
        PetPostResponse response = petPostMapper.toResponse(post);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/my")
    public ResponseEntity<List<PetPostResponse>> getMyPosts(Authentication authentication) {
        User user = (User) authentication.getPrincipal();


        List<PetPost> posts = petPostService.getMyPosts(user.getId());

        List<PetPostResponse> response = petPostMapper.toResponseList(posts);

        return  ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<PetPostResponse> updatePost(@PathVariable Long id,
        @Valid @RequestBody UpdatePetPostRequest request,
        Authentication authentication
    ) {
        User user = (User) authentication.getPrincipal();

        PetPost post = petPostService.updatePost(id, request, user);
        
        return ResponseEntity.ok(
            petPostMapper.toResponse(post)
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePost(
        @PathVariable Long id,
        Authentication authentication
    ) {
        User user = (User) authentication.getPrincipal();

        petPostService.deletePost(id, user);

        return ResponseEntity.noContent().build();
    }
    
    
}
