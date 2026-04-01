package uz.topdim.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO ответа для отображения сотрудника в списке персонала.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaffResponse {
    private Long id;
    private String email;
    private String phone;
    private String firstName;
    private String lastName;
    private String role;
    private boolean enabled;
    private LocalDateTime createdAt;
}
