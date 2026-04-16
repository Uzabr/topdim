package uz.topdim.coupon.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.topdim.coupon.dto.*;
import uz.topdim.coupon.service.DirectoryService;

import java.util.Map;

/**
 * Административный API для управления справочником базаров и магазинов.
 * Структура CreateShopRequest совместима с будущим Telegram-ботом.
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminDirectoryController {

    private final DirectoryService directoryService;

    @PostMapping("/bazaars")
    public ResponseEntity<Map<String, Object>> createBazaar(@Valid @RequestBody CreateBazaarRequest request) {
        return ResponseEntity.ok(Map.of("data", directoryService.createBazaar(request)));
    }

    @PutMapping("/bazaars/{id}")
    public ResponseEntity<Map<String, Object>> updateBazaar(
            @PathVariable Long id,
            @Valid @RequestBody CreateBazaarRequest request) {
        return ResponseEntity.ok(Map.of("data", directoryService.updateBazaar(id, request)));
    }

    @PostMapping("/shops")
    public ResponseEntity<Map<String, Object>> createShop(@Valid @RequestBody CreateShopRequest request) {
        return ResponseEntity.ok(Map.of("data", directoryService.createShop(request)));
    }

    @PutMapping("/shops/{id}")
    public ResponseEntity<Map<String, Object>> updateShop(
            @PathVariable Long id,
            @Valid @RequestBody CreateShopRequest request) {
        return ResponseEntity.ok(Map.of("data", directoryService.updateShop(id, request)));
    }
}
