package com.pawspot.post.request;

import com.pawspot.post.enums.PetSpecies;
import com.pawspot.post.enums.PostType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreatePetPostRequest {

    @NotNull
    private PostType type;

    @NotBlank
    private String title;

    @NotBlank
    private String description;

    @NotNull
    private PetSpecies species;

    @NotBlank
    private String city;

    private String area;
}