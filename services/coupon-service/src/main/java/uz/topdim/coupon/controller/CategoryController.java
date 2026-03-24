package uz.topdim.coupon.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.coupon.dto.CategoryResponse;
import uz.topdim.coupon.service.MerchantService;

import java.util.List;

/**
 * REST контроллер категорий купонов.
 * Публичные endpoints: список категорий.
 * Admin endpoints: создание, обновление, удаление категорий.
 */
@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final MerchantService merchantService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getAllCategories() {
        return ResponseEntity.ok(ApiResponse.success(merchantService.getAllCategories()));
    }
}
