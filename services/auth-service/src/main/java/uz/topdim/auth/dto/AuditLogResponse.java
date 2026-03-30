package uz.topdim.auth.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class AuditLogResponse {
    private Long id;
    private Long userId;
    private String action;
    private String entityName;
    private Long entityId;
    private String details;
    private LocalDateTime createdAt;
}
