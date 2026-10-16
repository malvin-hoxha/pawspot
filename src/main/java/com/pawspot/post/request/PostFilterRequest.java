package com.pawspot.post.request;

import com.pawspot.post.enums.PetSpecies;
import com.pawspot.post.enums.PostStatus;
import com.pawspot.post.enums.PostType;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter 
@Setter 
public class PostFilterRequest {
    private PostType type;
    private PostStatus status;
    private PetSpecies species;
    private String city;
    private String search;

    @Min(value = 0, message = "Page must be zero or greater")
    private int page = 0;

    @Min(value = 1, message = "Size must be at least 1")
    @Max(value = 100, message = "Size must not exceed 100")
    private int size = 10;

    @Pattern(
        regexp = "createdAt|updatedAt|title|city",
        message = "Invalid sort field"
    )
    private String sortBy = "createdAt";

    @Pattern(
        regexp = "(?i)asc|desc",
        message = "Direction must be 'asc' or 'desc'"
    )
    private String direction = "desc";
}
