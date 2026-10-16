package com.pawspot.post.request;

import com.pawspot.post.enums.PetSpecies;
import com.pawspot.post.enums.PostStatus;
import com.pawspot.post.enums.PostType;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
    @Size(max = 255)
    private String title;

    @Pattern(
        regexp = "(?s).*\\S.*",
        message = "Description cannot be blank"
    )
    @Size(max = 2000)
    private String description;

    private PetSpecies species;

    @Pattern(
        regexp = "(?s).*\\S.*",
        message = "City cannot be blank"
    )
    @Size(max = 255)
    private String city;

    @Size(max = 255)
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
