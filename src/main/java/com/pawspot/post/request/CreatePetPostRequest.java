package com.pawspot.post.request;

import com.pawspot.post.enums.PetSpecies;
import com.pawspot.post.enums.PostType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreatePetPostRequest {

    @NotNull
    private PostType type;

    @NotBlank
    @Size(max = 255)
    private String title;

    @NotBlank
    @Size(max = 2000)
    private String description;

    @NotNull
    private PetSpecies species;

    @NotBlank
    @Size(max = 255)
    private String city;

    @Size(max = 255)
    private String area;
}