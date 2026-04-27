package uz.topdim.coupon.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.coupon.dto.CreateMerchantOnboardingRequest;
import uz.topdim.coupon.dto.MerchantContextResponse;
import uz.topdim.coupon.dto.MerchantLocationResponse;
import uz.topdim.coupon.dto.MerchantResponse;
import uz.topdim.coupon.service.MerchantService;

@RestController
@RequestMapping("/api/v1/internal/merchants")
@RequiredArgsConstructor
public class InternalMerchantController {
    private final MerchantService merchantService;

    @GetMapping("/by-user/{userId}")
    public ResponseEntity<ApiResponse<MerchantContextResponse>> getByUserId(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.success(
                merchantService.getMerchantContextByUserId(userId)
        ));
    }

    @PostMapping("/onboarding")
    public ResponseEntity<ApiResponse<MerchantResponse>> createFromOnboarding(
            @Valid @RequestBody CreateMerchantOnboardingRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Merchant created from onboarding",
                merchantService.createFromOnboarding(request)
        ));
    }

    /** Активные локации мерчанта по userId владельца. */
    @GetMapping("/by-user/{userId}/locations")
    public ResponseEntity<ApiResponse<java.util.List<MerchantLocationResponse>>> getLocationsByUserId(
            @PathVariable Long userId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                merchantService.getLocationsByOwnerUserId(userId)));
    }
}
