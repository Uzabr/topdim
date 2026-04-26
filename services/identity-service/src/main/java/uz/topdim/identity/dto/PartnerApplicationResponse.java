package uz.topdim.identity.dto;

import lombok.Builder;
import lombok.Data;
import uz.topdim.identity.entity.ApplicationStatus;
import java.time.LocalDateTime;

@Data @Builder
public class PartnerApplicationResponse {
    private Long id;
    private String firstName;
    private String lastName;
    private String phone;
    private String email;
    private String companyName;
    private String city;
    private String address;
    private String workingHours;
    private String businessCategory;
    private String website;
    private String telegramUsername;
    private String comment;
    private String source;
    private ApplicationStatus status;
    private String rejectionReason;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private Long linkedUserId;
    private Long linkedMerchantId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
