package uz.topdim.identity.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

/** DTO для partner staff (кассиры). */
@Data @Builder
public class PartnerStaffResponse {
    private Long id;
    private String name;
    private String phone;
    private String role;
    private boolean active;
    private LocalDateTime createdAt;
}
