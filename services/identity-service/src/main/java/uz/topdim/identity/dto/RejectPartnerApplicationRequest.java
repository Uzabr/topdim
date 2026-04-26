package uz.topdim.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RejectPartnerApplicationRequest {
    @NotBlank
    @Size(max = 500)
    private String reason;
}
