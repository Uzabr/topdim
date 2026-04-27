package uz.topdim.identity.client;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MerchantOnboardingResponse {
    /**
     * Coupon-service returns "id" from onboarding and "merchantId" from by-user context.
     * @JsonAlias handles both JSON field names during deserialization.
     */
    @JsonAlias("merchantId")
    private Long id;
    private String name;
    private Long userId;
    private boolean active;
}
