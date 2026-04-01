package uz.topdim.user.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class StaffResponse {
    private Long id;
    private String name;
    private String phone;
    private String role;
    private boolean active;
    private LocalDateTime createdAt;
}
