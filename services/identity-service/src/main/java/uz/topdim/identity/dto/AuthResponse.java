package uz.topdim.identity.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO ответа аутентификации.
 * Содержит access token и данные пользователя.
 * Refresh token передаётся через httpOnly cookie (M4), не в JSON body.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String accessToken;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String refreshToken;
    private String tokenType;
    private long expiresIn;
    private UserDto user;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserDto {
        private Long id;
        private String email;
        private boolean emailPlaceholder;
        private String phone;
        private boolean phoneVerified;
        private String firstName;
        private String lastName;
        private String role;
        private String avatarUrl;
        private String trustLevel;
    }
}
