package uz.topdim.coupon.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.coupon.dto.MerchantLocationResponse;
import uz.topdim.coupon.dto.MerchantResponse;
import uz.topdim.coupon.service.MerchantService;

import java.util.List;

/**
 * Partner-facing merchant profile controller.
 * Owner/Manager can view their merchant profile and locations.
 */
@RestController
@RequestMapping("/api/v1/partner/merchant")
@PreAuthorize("hasAnyRole('PARTNER', 'ADMIN', 'SUPER_ADMIN')")
@RequiredArgsConstructor
public class PartnerMerchantController {

    private final MerchantService merchantService;

    /** Получить профиль мерчанта текущего пользователя. */
    @GetMapping
    public ResponseEntity<ApiResponse<MerchantResponse>> getMyMerchant(
            @RequestHeader("X-User-Id") Long userId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                merchantService.getMyMerchant(userId)));
    }

    /** Получить локации мерчанта текущего пользователя. */
    @GetMapping("/locations")
    public ResponseEntity<ApiResponse<List<MerchantLocationResponse>>> getMyLocations(
            @RequestHeader("X-User-Id") Long userId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                merchantService.getLocationsByOwnerUserId(userId)));
    }
}
