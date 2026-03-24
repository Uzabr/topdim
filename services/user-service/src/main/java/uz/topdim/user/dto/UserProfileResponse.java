package uz.topdim.user.dto;

import lombok.*;

import java.time.LocalDateTime;

/**
 * DTO ответа профиля.
 * Поля: id, email, phone, name, createdAt.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {
    private Long id;
    private String email;
    private String phone;
    private String firstName;
    private String lastName;
    private String role;
    private String avatarUrl;
    private boolean emailVerified;
    private boolean phoneVerified;
    private LocalDateTime createdAt;
}
