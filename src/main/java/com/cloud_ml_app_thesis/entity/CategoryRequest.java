package com.cloud_ml_app_thesis.entity;
import com.cloud_ml_app_thesis.entity.status.CategoryRequestStatus;
import com.cloud_ml_app_thesis.enumeration.status.CategoryRequestStatusEnum;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "category_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(length = 5000)
    private String description;

    @ManyToOne
    @JoinColumn(name = "status_id",nullable = false)
    private CategoryRequestStatus status; // Default status

    @ManyToOne
    @JoinColumn(name = "requested_by", nullable = false)
    private User requestedBy; // The user who requested this category

    @ManyToOne
    @JoinColumn(name = "processed_by")
    private User processedBy; // The admin who approved the request (nullable)

    @ManyToOne
    @JoinColumn(name = "approved_category_id")
    private Category approvedCategory; // The actual category created from this request

    @Column(length = 5000)
    private String rejectionReason;

    @Column(nullable = false)
    private LocalDateTime requestedAt;

    @Column
    private LocalDateTime processedAt; // When the request was approved/rejected

    @ManyToMany
    @JoinTable(
            name = "category_request_parents",
            joinColumns = @JoinColumn(name = "category_request_id"),
            inverseJoinColumns = @JoinColumn(name = "parent_category_id")
    )
    private Set<Category> parentCategories; // Requested parent categories


    public void setRequestedAt(LocalDateTime requestedAt) {
        this.requestedAt = Objects.requireNonNullElseGet(requestedAt, LocalDateTime::now);
    }

    public void setStatus(CategoryRequestStatus status){
        this.status = status;
        if(status.getName() != CategoryRequestStatusEnum.REJECTED){
            this.rejectionReason = null;
        }
    }
    public void setStatus(CategoryRequestStatus status, String rejectionReason){
        this.status = status;
        //To prevent wrong usage in code
        if(status.getName() != CategoryRequestStatusEnum.REJECTED){
            this.rejectionReason = null;
        } else {
            this.rejectionReason = rejectionReason;
        }
    }
}
