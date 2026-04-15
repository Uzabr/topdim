package uz.topdim.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import uz.topdim.identity.entity.Role;
import uz.topdim.identity.validation.StrongPassword;

@Data
public class CreateAdminRequest {
    @NotBlank @Email
    private String email;
    private String phone;
    @NotBlank @StrongPassword
    private String password;
    @NotBlank
    private String firstName;
    private String lastName;
    private Role role; // null → default ADMIN
}
