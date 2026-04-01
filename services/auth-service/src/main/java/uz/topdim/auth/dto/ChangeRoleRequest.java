package uz.topdim.auth.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import uz.topdim.auth.entity.Role;

@Data
public class ChangeRoleRequest {
    @NotNull(message = "Роль обязательна")
    private Role newRole;
}
