package uz.topdim.coupon.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BotLeadRequest {

    @NotBlank(message = "Company name is required")
    private String companyName;

    @NotBlank(message = "Phone is required")
    private String phone;

    private String firstName;
    private String lastName;
    private String promoDescription;
    private String sourceLink;
    private String telegramChatId;
    private String telegramUsername;
    private String voiceFileId;
}
