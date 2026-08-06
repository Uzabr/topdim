package uz.topdim.identity.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BlockUserRequest {
    @NotNull(message = "Статус блокировки обязателен")
    private Boolean blocked;
}
