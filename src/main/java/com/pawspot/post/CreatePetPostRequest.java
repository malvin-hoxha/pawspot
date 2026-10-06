package com.pawspot.post;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

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

    public PostType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public PetSpecies getSpecies() {
        return species;
    }

    public String getCity() {
        return city;
    }

    public String getArea() {
        return area;
    }
}