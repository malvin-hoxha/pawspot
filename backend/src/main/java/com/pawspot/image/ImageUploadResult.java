package com.pawspot.image;

public record ImageUploadResult(
        String imageUrl,
        String publicId
) {
}