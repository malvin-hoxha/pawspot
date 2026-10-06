package com.pawspot.post;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.pawspot.image.CloudinaryService;
import com.pawspot.image.ImageUploadResult;
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

}
