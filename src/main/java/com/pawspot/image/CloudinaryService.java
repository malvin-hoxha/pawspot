package com.pawspot.image;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.pawspot.exception.ImageUploadException;

import java.io.IOException;

@Service
public class CloudinaryService {
    
    private final Cloudinary cloudinary;

    public CloudinaryService(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    public ImageUploadResult upload(MultipartFile image) {

        if (image == null || image.isEmpty()) {
            throw new ImageUploadException(
                "Image is required",
                null
            );
        }

        if (image.getContentType() == null || !image.getContentType().startsWith("image/")) {
            throw new ImageUploadException(
                "File must be an image",
                null
            );
        }

        long maxSize = 5 * 1024 * 1024;

        if (image.getSize() > maxSize) {
            throw new ImageUploadException(
                "Image must be smaller than 5 MB",
                null
            );
        }
        
        try {

            Map uploadResult = cloudinary.uploader().upload(
                image.getBytes(),
                ObjectUtils.asMap(
                    "folder", "pawspot/posts",
                    "resource_type", "image"
                )
            );

            String imageUrl = uploadResult.get("secure_url").toString();
            String publicId = uploadResult.get("public_id").toString();

            return new ImageUploadResult(
                imageUrl,
                publicId
            );
        } catch (IOException e) {
            throw new ImageUploadException(
                "Failed to upload image",
                e
            );
        }
    }
}
