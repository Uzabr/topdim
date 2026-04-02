package uz.topdim.coupon.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.coupon.dto.CreateCategoryRequest;
import uz.topdim.coupon.dto.ExcelImportResponse;
import uz.topdim.coupon.service.CategoryService;

@RestController
@RequestMapping("/api/v1/admin/categories")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@RequiredArgsConstructor
public class AdminCategoryController {

    private final CategoryService categoryService;

    @PostMapping
    public ResponseEntity<ApiResponse<Long>> createCategory(
            @RequestBody CreateCategoryRequest request) {
        Long id = categoryService.createCategory(request);
        return ResponseEntity.ok(ApiResponse.success("Категория успешно создана", id));
    }

    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<ExcelImportResponse>> uploadCategories(
            @RequestParam("file") MultipartFile file) {
        ExcelImportResponse response = categoryService.importCategoriesFromExcel(file);
        return ResponseEntity.ok(ApiResponse.success("Файл успешно обработан", response));
    }
}
