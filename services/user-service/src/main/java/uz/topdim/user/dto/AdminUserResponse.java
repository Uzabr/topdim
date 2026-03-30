package uz.topdim.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO ответа для admin — краткая информация о пользователе.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserResponse {
    private Long id;
    private String email;
    private String phone;
    private String firstName;
    private String lastName;
    private String role;
    private boolean enabled;
    private boolean emailVerified;
    private boolean phoneVerified;
    private LocalDateTime createdAt;
}
