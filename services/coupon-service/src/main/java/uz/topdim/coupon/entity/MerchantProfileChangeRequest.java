package uz.topdim.coupon.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "merchant_profile_change_requests")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantProfileChangeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    @Column(name = "author_user_id", nullable = false)
    private Long authorUserId;

    @Column(name = "author_staff_id")
    private Long authorStaffId;

    @Column(name = "author_role", nullable = false, length = 16)
    private String authorRole;

    @Column(name = "base_profile_version", nullable = false)
    private long baseProfileVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MerchantProfileChangeStatus status;

    @Column(name = "assignee_user_id")
    private Long assigneeUserId;

    @Column(name = "moderation_comment", length = 2000)
    private String moderationComment;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "cover_url", length = 500)
    private String coverUrl;

    private String email;

    @Column(length = 500)
    private String website;

    @Column(name = "contact_person")
    private String contactPerson;

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<MerchantProfileChangeLocation> locations = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "withdrawn_at")
    private LocalDateTime withdrawnAt;

    @Version
    @Column(name = "lock_version", nullable = false)
    private long lockVersion;
}
