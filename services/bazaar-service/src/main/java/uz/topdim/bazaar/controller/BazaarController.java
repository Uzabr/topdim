package uz.topdim.bazaar.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.bazaar.dto.*;
import uz.topdim.bazaar.entity.BazaarMap;
import uz.topdim.bazaar.entity.ShopCategory;
import uz.topdim.bazaar.service.BazaarService;
import uz.topdim.bazaar.service.ShopService;

import java.util.List;

/**
 * REST контроллер базаров и магазинов.
 * Публичные endpoints: список, детали, магазины базара.
 * Admin endpoints: создание базаров и магазинов.
 */
@RestController
@RequiredArgsConstructor
public class BazaarController {

    private final BazaarService bazaarService;
    private final ShopService shopService;

    // ==================== Bazaars ====================

    @GetMapping("/api/v1/bazaars")
    public ResponseEntity<ApiResponse<List<BazaarResponse>>> getAllBazaars(
            @RequestParam(required = false) String city
    ) {
        return ResponseEntity.ok(ApiResponse.success(bazaarService.getAllBazaars(city)));
    }

    @GetMapping("/api/v1/bazaars/{id}")
    public ResponseEntity<ApiResponse<BazaarResponse>> getBazaar(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(bazaarService.getBazaarById(id)));
    }

    @GetMapping("/api/v1/bazaars/{id}/map")
    public ResponseEntity<ApiResponse<List<BazaarMap>>> getBazaarMaps(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(bazaarService.getBazaarMaps(id)));
    }

    @GetMapping("/api/v1/bazaars/{id}/shops")
    public ResponseEntity<ApiResponse<List<ShopResponse>>> getBazaarShops(
            @PathVariable Long id,
            @RequestParam(required = false) Boolean hasCoupon
    ) {
        return ResponseEntity.ok(ApiResponse.success(shopService.getShopsByBazaar(id, hasCoupon)));
    }

    // ==================== Shops ====================

    @GetMapping("/api/v1/shops/{id}")
    public ResponseEntity<ApiResponse<ShopResponse>> getShop(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(shopService.getShopById(id)));
    }

    @GetMapping("/api/v1/shops/search")
    public ResponseEntity<ApiResponse<List<ShopResponse>>> searchShops(@RequestParam String q) {
        return ResponseEntity.ok(ApiResponse.success(shopService.searchShops(q)));
    }

    @GetMapping("/api/v1/shops/categories")
    public ResponseEntity<ApiResponse<List<ShopCategory>>> getShopCategories() {
        return ResponseEntity.ok(ApiResponse.success(shopService.getAllCategories()));
    }

    // ==================== Admin ====================

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @PostMapping("/api/v1/admin/bazaars")
    public ResponseEntity<ApiResponse<BazaarResponse>> createBazaar(@Valid @RequestBody CreateBazaarRequest request) {
        BazaarResponse bazaar = bazaarService.createBazaar(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Базар создан", bazaar));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @PutMapping("/api/v1/admin/bazaars/{id}")
    public ResponseEntity<ApiResponse<BazaarResponse>> updateBazaar(
            @PathVariable Long id, @Valid @RequestBody CreateBazaarRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Базар обновлён", bazaarService.updateBazaar(id, request)));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @PostMapping("/api/v1/admin/shops")
    public ResponseEntity<ApiResponse<ShopResponse>> createShop(@Valid @RequestBody CreateShopRequest request) {
        ShopResponse shop = shopService.createShop(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Магазин создан", shop));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @PutMapping("/api/v1/admin/shops/{id}")
    public ResponseEntity<ApiResponse<ShopResponse>> updateShop(
            @PathVariable Long id, @Valid @RequestBody CreateShopRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Магазин обновлён", shopService.updateShop(id, request)));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @PostMapping("/api/v1/admin/bazaars/{id}/maps")
    public ResponseEntity<ApiResponse<BazaarMap>> uploadMap(
            @PathVariable Long id, @RequestBody BazaarMap map) {
        return ResponseEntity.ok(ApiResponse.success("Карта загружена", bazaarService.uploadMap(id, map)));
    }
}
