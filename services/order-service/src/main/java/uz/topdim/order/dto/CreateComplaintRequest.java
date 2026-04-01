package uz.topdim.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateComplaintRequest {
    @NotNull(message = "ID заказа обязателен")
    private Long orderId;

    @NotBlank(message = "Тема обращения обязательна")
    private String subject;

    @NotBlank(message = "Описание проблемы обязательно")
    private String description;
}
