package com.pawspot.post;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.pawspot.exception.ForbiddenOperationException;
import com.pawspot.exception.PostNotFoundException;
import com.pawspot.exception.validation.PostValidationException;
import com.pawspot.image.CloudinaryService;
import com.pawspot.image.ImageUploadResult;
import com.pawspot.post.enums.PetSpecies;
import com.pawspot.post.enums.PostStatus;
import com.pawspot.post.enums.PostType;
import com.pawspot.post.mapper.PetPostMapper;
import com.pawspot.post.request.CreatePetPostRequest;
import com.pawspot.post.request.UpdatePetPostRequest;
import com.pawspot.post.specification.PetPostSpecification;
import com.pawspot.user.User;

@Service 
public class PetPostService {
    private final PetPostRepository petPostRepository;
    private final CloudinaryService cloudinaryService;
    private final PetPostMapper petPostMapper;


    public PetPostService(PetPostRepository petPostRepository, CloudinaryService cloudinaryService,
        PetPostMapper petPostMapper
    ) {
        this.petPostRepository = petPostRepository;
        this.cloudinaryService = cloudinaryService;
        this.petPostMapper = petPostMapper;
    }

    public PetPost createPost(CreatePetPostRequest request, MultipartFile image, User user) {

        ImageUploadResult uploadResult = cloudinaryService.upload(image);

        PetPost post = petPostMapper.toEntity(request);

        post.setImageUrl(uploadResult.imageUrl());
        post.setImagePublicId(uploadResult.publicId());

        post.setUser(user);

        return petPostRepository.save(post);
    }

    public Page<PetPost> getPosts(PostType type, PostStatus status,
        PetSpecies species, String city, int page, int size) {

        Pageable pageable = PageRequest.of(page, size);

        Specification<PetPost> specification = PetPostSpecification.withFilters(
            type,
            status,
            species,
            city
        );

        return petPostRepository.findAll(specification, pageable);
    }

    public PetPost getPost(Long id) {
        return petPostRepository.findById(id)
            .orElseThrow(() -> new PostNotFoundException("Post not found"));
    }

    public List<PetPost> getMyPosts(Long userId) {
        return petPostRepository.findByUserId(userId);
    }

    public PetPost updatePost(Long id, UpdatePetPostRequest request, User user) {
        PetPost post = petPostRepository.findById(id).orElseThrow(() ->
            new PostNotFoundException("Post not found")
        );

        if (!post.getUser().getId().equals(user.getId())) {
            throw new ForbiddenOperationException("You cannot update this post");
        }

        if (!request.hasUpdates()) {
            throw new PostValidationException(
                "At least one field must be provided"
            );
        }

        petPostMapper.updatePostFromRequest(request, post);

        return petPostRepository.save(post);
    }

    public void deletePost(Long id, User user) {
        PetPost post = petPostRepository.findById(id)
            .orElseThrow(() -> 
                new PostNotFoundException("Post not found")
            );

        if (!post.getUser().getId().equals(user.getId())) {
            throw new ForbiddenOperationException(
                "You cannot delete this post"
            );
        }

        cloudinaryService.delete(post.getImagePublicId());

        petPostRepository.delete(post);
    }
    

}
