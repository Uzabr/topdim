package uz.topdim.identity.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Payload входа через Google (ID-token, полученный клиентом от Google Identity Services).
 */
@Data
public class GoogleAuthRequest {

    @NotBlank(message = "idToken обязателен")
    private String idToken;
}
