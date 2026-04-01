package uz.topdim.user.dto;

import lombok.Builder;
import lombok.Data;
import uz.topdim.user.entity.ApplicationStatus;

import java.time.LocalDateTime;

@Data
@Builder
public class PartnerApplicationResponse {
    private Long id;
    private String firstName;
    private String lastName;
    private String phone;
    private String companyName;
    private String comment;
    private ApplicationStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
