package uz.topdim.coupon.dto.merchantprofile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record MerchantProfileChangePayload(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 5000) String description,
        @Size(max = 500) String logoUrl,
        @Size(max = 500) String coverUrl,
        @Email @Size(max = 255) String email,
        @Size(max = 500) @Pattern(regexp = "^$|https?://.+") String website,
        @Size(max = 255) String contactPerson,
        @Valid @NotNull List<MerchantProfileLocationPayload> locations
) {
}
