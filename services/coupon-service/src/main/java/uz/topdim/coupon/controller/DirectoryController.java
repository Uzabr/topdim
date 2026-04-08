package uz.topdim.coupon.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.topdim.coupon.dto.*;
import uz.topdim.coupon.service.DirectoryService;

import java.util.List;
import java.util.Map;

/**
 * Публичный API справочника базаров и магазинов.
 */
@RestController
@RequestMapping("/api/v1/directory")
@RequiredArgsConstructor
public class DirectoryController {

    private final DirectoryService directoryService;

    // ═══ Bazaars ═══

    @GetMapping("/bazaars")
    public ResponseEntity<Map<String, Object>> getBazaars(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<BazaarResponse> result = directoryService.getBazaars(search, type, page, size);
        return ResponseEntity.ok(Map.of(
                "data", result.getContent(),
                "totalPages", result.getTotalPages(),
                "totalElements", result.getTotalElements()
        ));
    }

    @GetMapping("/bazaars/{id}")
    public ResponseEntity<Map<String, Object>> getBazaarById(@PathVariable Long id) {
        return ResponseEntity.ok(Map.of("data", directoryService.getBazaarById(id)));
    }

    @GetMapping("/bazaars/{id}/shops")
    public ResponseEntity<Map<String, Object>> getShopsByBazaar(
            @PathVariable Long id,
            @RequestParam(required = false) String search) {
        List<ShopResponse> shops = directoryService.getShopsByBazaarId(id, search);
        return ResponseEntity.ok(Map.of("data", shops));
    }

    // ═══ Shops ═══

    @GetMapping("/shops")
    public ResponseEntity<Map<String, Object>> getShops(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<ShopResponse> result = directoryService.getShops(search, category, page, size);
        return ResponseEntity.ok(Map.of(
                "data", result.getContent(),
                "totalPages", result.getTotalPages(),
                "totalElements", result.getTotalElements()
        ));
    }

    @GetMapping("/shops/{id}")
    public ResponseEntity<Map<String, Object>> getShopById(@PathVariable Long id) {
        return ResponseEntity.ok(Map.of("data", directoryService.getShopById(id)));
    }

    // ═══ Area search ═══

    @GetMapping("/area")
    public ResponseEntity<Map<String, Object>> searchInArea(
            @RequestParam double minLat,
            @RequestParam double maxLat,
            @RequestParam double minLon,
            @RequestParam double maxLon) {
        AreaSearchResponse result = directoryService.searchInArea(minLat, maxLat, minLon, maxLon);
        return ResponseEntity.ok(Map.of("data", result));
    }
}
