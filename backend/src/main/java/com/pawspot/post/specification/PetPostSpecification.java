package com.pawspot.post.specification;

import org.springframework.data.jpa.domain.Specification;

import com.pawspot.post.PetPost;
import com.pawspot.post.enums.PetSpecies;
import com.pawspot.post.enums.PostStatus;
import com.pawspot.post.enums.PostType;
import java.util.Locale;

public class PetPostSpecification {

    public static Specification<PetPost> withFilters(
            PostType type,
            PostStatus status,
            PetSpecies species,
            String city,
            String search
            
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
                var storedCity = criteriaBuilder.function(
                    "unaccent",
                    String.class,
                    criteriaBuilder.lower(criteriaBuilder.trim(root.get("city")))
                );

                var requestedCity = criteriaBuilder.function(
                    "unaccent",
                    String.class,
                    criteriaBuilder.literal(city.trim().toLowerCase(Locale.ROOT))
                );

                predicate = criteriaBuilder.and(
                    predicate,
                    criteriaBuilder.like(
                        storedCity,
                        criteriaBuilder.concat(
                            criteriaBuilder.concat("%", requestedCity),
                            "%"
                        )
                    )
                );
            }

            if (search != null && !search.isBlank()) {
                var requestedSearch = criteriaBuilder.function(
                    "unaccent",
                    String.class,
                    criteriaBuilder.literal(search.trim().toLowerCase(Locale.ROOT))
                );

                var pattern = criteriaBuilder.concat(
                    criteriaBuilder.concat("%", requestedSearch),
                    "%"
                );

                var title = criteriaBuilder.function(
                    "unaccent",
                    String.class,
                    criteriaBuilder.lower(root.get("title"))
                );

                var description = criteriaBuilder.function(
                    "unaccent",
                    String.class,
                    criteriaBuilder.lower(root.get("description"))
                );

                predicate = criteriaBuilder.and(
                    predicate,
                    criteriaBuilder.or(
                        criteriaBuilder.like(title, pattern),
                        criteriaBuilder.like(description, pattern)
                    )
                );
            }

            return predicate;
        };
    }
}
