package uz.topdim.identity.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "partner_applications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PartnerApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", nullable = false, length = 50)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 50)
    private String lastName;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(name = "company_name", nullable = false, length = 100)
    private String companyName;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(length = 255)
    private String email;

    @Column(length = 100)
    private String city;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(name = "working_hours")
    private String workingHours;

    @Column(name = "business_category", length = 100)
    private String businessCategory;

    @Column(length = 255)
    private String website;

    @Column(name = "telegram_username", length = 100)
    private String telegramUsername;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String source = "WEB";

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "linked_user_id")
    private Long linkedUserId;

    @Column(name = "linked_merchant_id")
    private Long linkedMerchantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ApplicationStatus status = ApplicationStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
