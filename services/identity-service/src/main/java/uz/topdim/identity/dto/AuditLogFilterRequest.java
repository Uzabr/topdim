package uz.topdim.identity.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class AuditLogFilterRequest {
    Long userId;
    String action;
    String entityName;
    String search;
    LocalDateTime from;
    LocalDateTime to;
}
