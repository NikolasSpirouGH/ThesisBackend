package com.cloud_ml_app_thesis.specification;

import com.cloud_ml_app_thesis.dto.request.dataset.DatasetSearchRequest;
import org.springframework.data.jpa.domain.Specification;
import com.cloud_ml_app_thesis.entity.dataset.Dataset;
import com.cloud_ml_app_thesis.entity.Category;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class DatasetSpecification {
    public static Specification<Dataset> getDatasetsByCriteria(DatasetSearchRequest request, Set<Integer> childCategoryIds) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1️⃣ Name filter (LIKE, case-insensitive)
            if (request.getName() != null) {
                predicates.add(cb.like(cb.lower(root.get("originalFileName")), "%" + request.getName().toLowerCase() + "%"));
            }

            // 2️⃣ Owner filter (Exact match)
            if (request.getOwnerUsername() != null) {
                predicates.add(cb.equal(root.get("user").get("username"), request.getOwnerUsername()));
            }

            // 3️⃣ Public/Private filter (If provided)
            if (request.getIsPublic() != null) {
                predicates.add(cb.equal(root.get("isPublic"), request.getIsPublic()));
            }

            // 4️⃣ Category filter (Find datasets that belong to a category or its children)
            if (request.getCategoryId() != null) {
                Join<Dataset, Category> categoryJoin = root.join("categories");
                if (request.getIncludeChildCategories() != null && request.getIncludeChildCategories()) {
                    predicates.add(categoryJoin.get("id").in(childCategoryIds));
                } else {
                    predicates.add(cb.equal(categoryJoin.get("id"), request.getCategoryId()));
                }
            }

            // 5️⃣ Content Type filter (LIKE, case-insensitive)
            if (request.getContentType() != null) {
                predicates.add(cb.like(cb.lower(root.get("contentType")), "%" + request.getContentType().toLowerCase() + "%"));
            }

            // 6️⃣ Date Range filter (Upload Date From)
            if (request.getUploadDateFrom() != null) {
                ZonedDateTime dateFrom = ZonedDateTime.parse(request.getUploadDateFrom(), DateTimeFormatter.ISO_DATE_TIME);
                predicates.add(cb.greaterThanOrEqualTo(root.get("uploadDate"), dateFrom));
            }

            // 7️⃣ Date Range filter (Upload Date To)
            if (request.getUploadDateTo() != null) {
                ZonedDateTime dateTo = ZonedDateTime.parse(request.getUploadDateTo(), DateTimeFormatter.ISO_DATE_TIME);
                predicates.add(cb.lessThanOrEqualTo(root.get("uploadDate"), dateTo));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
