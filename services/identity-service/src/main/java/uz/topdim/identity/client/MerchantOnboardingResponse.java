package uz.topdim.identity.client;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MerchantOnboardingResponse {
    private Long id;
    private String name;
    private Long userId;
    private boolean active;
}
