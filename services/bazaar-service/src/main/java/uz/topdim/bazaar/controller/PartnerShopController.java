package uz.topdim.bazaar.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.topdim.bazaar.dto.ShopResponse;
import uz.topdim.bazaar.dto.UpdateShopRequest;
import uz.topdim.bazaar.service.PartnerShopService;
import uz.topdim.common.dto.ApiResponse;

import java.util.List;

/**
 * Контроллер партнёра — управление СВОИМИ магазинами.
 */
@RestController
@RequestMapping("/api/v1/partner/shops")
@PreAuthorize("hasAnyRole('PARTNER', 'ADMIN', 'SUPER_ADMIN')")
@RequiredArgsConstructor
public class PartnerShopController {

    private final PartnerShopService partnerShopService;

    /** Мои магазины. */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ShopResponse>>> getMyShops(
            @RequestHeader("X-User-Id") Long userId
    ) {
        return ResponseEntity.ok(ApiResponse.success(partnerShopService.getMyShops(userId)));
    }

    /** Детали моего магазина. */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ShopResponse>> getMyShop(
            @RequestHeader("X-User-Id") Long userId, @PathVariable Long id
    ) {
        return ResponseEntity.ok(ApiResponse.success(partnerShopService.getMyShop(userId, id)));
    }

    /** Обновить мой магазин. */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ShopResponse>> updateMyShop(
            @RequestHeader("X-User-Id") Long userId, @PathVariable Long id,
            @RequestBody UpdateShopRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Магазин обновлён",
                partnerShopService.updateMyShop(userId, id, request)));
    }
}
