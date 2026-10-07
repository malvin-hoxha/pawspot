package com.pawspot.post.request;

import com.pawspot.post.PetSpecies;
import com.pawspot.post.PostStatus;
import com.pawspot.post.PostType;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdatePetPostRequest {
    private PostType type;
    private String title;
    private String description;
    private PetSpecies species;
    private String city;
    private String area;
    private PostStatus status;
}
