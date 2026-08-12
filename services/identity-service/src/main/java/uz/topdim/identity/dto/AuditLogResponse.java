package uz.topdim.identity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogResponse {
    private Long id;
    private Long userId;
    private String userEmail;
    private String userName;
    private String userRole;
    private String action;
    private String entityName;
    private Long entityId;
    private String details;
    private LocalDateTime createdAt;
}
