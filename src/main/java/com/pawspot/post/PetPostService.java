package com.pawspot.post;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.pawspot.exception.ForbiddenOperationException;
import com.pawspot.exception.PostNotFoundException;
import com.pawspot.image.CloudinaryService;
import com.pawspot.image.ImageUploadResult;
import com.pawspot.post.request.CreatePetPostRequest;
import com.pawspot.post.request.UpdatePetPostRequest;
import com.pawspot.user.User;

@Service 
public class PetPostService {
    private final PetPostRepository petPostRepository;
    private final CloudinaryService cloudinaryService;


    public PetPostService(PetPostRepository petPostRepository, CloudinaryService cloudinaryService) {
        this.petPostRepository = petPostRepository;
        this.cloudinaryService = cloudinaryService;
    }

    public PetPost createPost(CreatePetPostRequest request, MultipartFile image, User user) {

        ImageUploadResult uploadResult = cloudinaryService.upload(image);

        PetPost post = new PetPost();

        post.setType(request.getType());
        post.setTitle(request.getTitle());
        post.setDescription(request.getDescription());
        post.setSpecies(request.getSpecies());
        post.setCity(request.getCity());
        post.setArea(request.getArea());

        post.setImageUrl(uploadResult.imageUrl());
        post.setImagePublicId(uploadResult.publicId());

        post.setUser(user);

        return petPostRepository.save(post);
    }

    public List<PetPost> getPosts() {
        return petPostRepository.findAll();
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

        if (request.getType() != null) {
            post.setType(request.getType());
        }

        if (request.getTitle() != null) {
            post.setTitle(request.getTitle());
        }

        if (request.getDescription() != null) {
            post.setDescription(request.getDescription());
        }

        if (request.getSpecies() != null) {
            post.setSpecies(request.getSpecies());
        }

        if (request.getCity() != null) {
            post.setCity(request.getCity());
        }

        if (request.getArea() != null) {
            post.setArea(request.getArea());
        }

        if (request.getStatus() != null) {
            post.setStatus(request.getStatus());
        }

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

        cloudinaryService.delete((post.getImagePublicId()));

        petPostRepository.delete(post);
    }

}
