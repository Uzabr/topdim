package uz.topdim.identity.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * DTO для Super Admin staff list (admin/moderator users).
 * Отличается от PartnerStaffResponse — используется в SuperAdminController.
 */
@Data
@Builder
public class AdminStaffResponse {
    private Long id;
    private String email;
    private String phone;
    private String firstName;
    private String lastName;
    private String role;
    private boolean enabled;
    private LocalDateTime createdAt;
}
