package com.pawspot.post.specification;

import org.springframework.data.jpa.domain.Specification;

import com.pawspot.post.PetPost;
import com.pawspot.post.enums.PetSpecies;
import com.pawspot.post.enums.PostStatus;
import com.pawspot.post.enums.PostType;

public class PetPostSpecification {

    public static Specification<PetPost> withFilters(
            PostType type,
            PostStatus status,
            PetSpecies species,
            String city
    ) {
        return (root, query, criteriaBuilder) -> {
            var predicate = criteriaBuilder.conjunction();

            if (type != null) {
                predicate = criteriaBuilder.and(
                    predicate,
                    criteriaBuilder.equal(root.get("type"), type)
                );
            }

            if (status != null) {
                predicate = criteriaBuilder.and(
                    predicate,
                    criteriaBuilder.equal(root.get("status"), status)
                );
            }

            if (species != null) {
                predicate = criteriaBuilder.and(
                    predicate,
                    criteriaBuilder.equal(root.get("species"), species)
                );
            }

            if (city != null && !city.isBlank()) {
                predicate = criteriaBuilder.and(
                    predicate,
                    criteriaBuilder.equal(root.get("city"), city)
                );
            }

            return predicate;
        };
    }
}
