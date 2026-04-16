package uz.topdim.identity.dto;

import lombok.*; import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
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
