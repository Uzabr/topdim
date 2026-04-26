package uz.topdim.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ApprovePartnerApplicationRequest {
    @NotBlank
    @Email
    private String loginEmail;

    @NotBlank
    @Size(min = 8)
    private String temporaryPassword;

    @NotBlank
    private String merchantName;

    private String contactPerson;
    private String city;

    @NotBlank
    private String address;

    @NotBlank
    private String phone;

    private String workingHours;
    private String website;
}
