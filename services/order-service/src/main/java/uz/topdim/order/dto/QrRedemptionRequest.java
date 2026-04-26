package uz.topdim.order.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Запрос на погашение купона по QR-токену.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QrRedemptionRequest {
    @NotBlank(message = "QR-токен обязателен")
    private String qrToken;

    private String staffName;
}
