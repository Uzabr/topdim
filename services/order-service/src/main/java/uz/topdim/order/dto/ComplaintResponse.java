package uz.topdim.order.dto;

import lombok.Builder;
import lombok.Data;
import uz.topdim.order.entity.ComplaintStatus;

import java.time.LocalDateTime;

@Data
@Builder
public class ComplaintResponse {
    private Long id;
    private Long userId;
    private Long orderId;
    private String subject;
    private String description;
    private ComplaintStatus status;
    private String resolution;
    private LocalDateTime createdAt;
}
