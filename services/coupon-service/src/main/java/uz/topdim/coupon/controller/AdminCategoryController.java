package uz.topdim.coupon.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.coupon.dto.AdminCategoryResponse;
import uz.topdim.coupon.dto.CreateCategoryRequest;
import uz.topdim.coupon.dto.ExcelImportResponse;
import uz.topdim.coupon.service.CategoryService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/categories")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@RequiredArgsConstructor
public class AdminCategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminCategoryResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(categoryService.getAllCategoriesForAdmin()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminCategoryResponse>> getOne(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(categoryService.getCategoryForAdmin(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Long>> createCategory(
            @Valid @RequestBody CreateCategoryRequest request) {
        Long id = categoryService.createCategory(request);
        return ResponseEntity.ok(ApiResponse.success("Категория успешно создана", id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminCategoryResponse>> updateCategory(
            @PathVariable Long id,
            @Valid @RequestBody CreateCategoryRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Категория успешно обновлена",
                categoryService.updateCategory(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.ok(ApiResponse.success("Категория успешно удалена", null));
    }

    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<ExcelImportResponse>> uploadCategories(
            @RequestParam("file") MultipartFile file) {
        ExcelImportResponse response = categoryService.importCategoriesFromExcel(file);
        return ResponseEntity.ok(ApiResponse.success("Файл успешно обработан", response));
    }
}
