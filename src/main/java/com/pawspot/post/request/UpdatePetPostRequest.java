package com.pawspot.post.request;

import com.pawspot.post.enums.PetSpecies;
import com.pawspot.post.enums.PostStatus;
import com.pawspot.post.enums.PostType;

import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdatePetPostRequest {
    private PostType type;

    @Pattern(
        regexp = "(?s).*\\S.*",
        message = "Title cannot be blank"
    )
    private String title;

    @Pattern(
        regexp = "(?s).*\\S.*",
        message = "Description cannot be blank"
    )
    private String description;

    private PetSpecies species;

    @Pattern(
        regexp = "(?s).*\\S.*",
        message = "City cannot be blank"
    )
    private String city;

    private String area;

    private PostStatus status;

    public boolean hasUpdates() {
        return type != null
            || title != null
            || description != null
            || species != null
            || city != null
            || area != null
            || status != null;
    }
}
