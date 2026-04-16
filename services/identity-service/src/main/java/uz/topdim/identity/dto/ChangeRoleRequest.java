package uz.topdim.identity.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import uz.topdim.identity.entity.Role;

@Data
public class ChangeRoleRequest {
    @NotNull(message = "Роль обязательна")
    private Role newRole;
}
