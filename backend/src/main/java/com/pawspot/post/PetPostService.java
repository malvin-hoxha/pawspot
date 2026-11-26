package com.pawspot.post;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import com.pawspot.user.enums.UserRole;
import com.pawspot.image.cleanup.ImageDeletionQueueService;

@Service 
public class PetPostService {
    private final PetPostRepository petPostRepository;
    private final CloudinaryService cloudinaryService;
    private final PetPostMapper petPostMapper;
    private final ImageDeletionQueueService imageDeletionQueueService;



    public PetPostService(
        PetPostRepository petPostRepository, 
        CloudinaryService cloudinaryService,
        PetPostMapper petPostMapper, 
        ImageDeletionQueueService imageDeletionQueueService
    ) {
        this.petPostRepository = petPostRepository;
        this.cloudinaryService = cloudinaryService;
        this.petPostMapper = petPostMapper;
        this.imageDeletionQueueService = imageDeletionQueueService;
    }

    public PetPost createPost(CreatePetPostRequest request, MultipartFile image, User user) {
        
        ImageUploadResult uploadResult = cloudinaryService.upload(image);

        try {
            PetPost post = petPostMapper.toEntity(request);

            post.setImageUrl(uploadResult.imageUrl());
            post.setImagePublicId(uploadResult.publicId());
            post.setUser(user);

            return petPostRepository.save(post);
        } catch (RuntimeException ex) {
            try {
                imageDeletionQueueService.enqueueAfterFailedCreation(uploadResult.publicId());
            } catch (RuntimeException cleanupEx) {
                // Preserve the original failure and attach the queueing failure.
                ex.addSuppressed(cleanupEx);
            }

            throw ex;
        }
    }

    

    public Page<PetPost> getPosts(PostType type, PostStatus status,
        PetSpecies species, String city, String search,int page, int size, String sortBy, String direction) {

        Sort.Direction sortDirection = direction.equalsIgnoreCase("asc")
            ? Sort.Direction.ASC
            : Sort.Direction.DESC;

        Sort sort = Sort.by(sortDirection, sortBy);

        if (!sortBy.equals("id")) {
            sort = sort.and(Sort.by(Sort.Direction.DESC, "id"));
        }

        Pageable pageable = PageRequest.of(page, size, sort);

        Specification<PetPost> specification = PetPostSpecification.withFilters(
            type,
            status,
            species,
            city,
            search
        );
        
        return petPostRepository.findAll(specification, pageable);
    }



    public PetPost getPost(Long id) {
        return petPostRepository.findById(id)
            .orElseThrow(() -> new PostNotFoundException("Post not found"));
    }

    public Page<PetPost> getMyPosts(Long userId, int page, int size) {

        Pageable pageable = PageRequest.of(
            page,
            size,
            Sort.by(Sort.Direction.DESC, "createdAt")
                .and(Sort.by(Sort.Direction.DESC, "id"))
        );

        return petPostRepository.findByUserId(userId, pageable);
    }

    @Transactional
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

    @Transactional 
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

        deletePostAndQueueImageCleanup(post);
    }

    @Transactional
    public void deletePostAsAdmin(Long id, User admin) {
        if(admin.getRole() != UserRole.ADMIN) {
            throw new ForbiddenOperationException(
                "Only admins can remove reported posts"
            );
        }

        PetPost post = getPost(id);
        deletePostAndQueueImageCleanup(post);
    }

    private void deletePostAndQueueImageCleanup(PetPost post) {
        // Persist the cleanup task and delete the post in the same transaction.
        imageDeletionQueueService.enqueue(post.getImagePublicId());
        petPostRepository.delete(post);
    }
}
