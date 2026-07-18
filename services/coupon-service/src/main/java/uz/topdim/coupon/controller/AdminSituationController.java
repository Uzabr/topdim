package uz.topdim.coupon.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.coupon.dto.AdminSituationResponse;
import uz.topdim.coupon.dto.CreateSituationRequest;
import uz.topdim.coupon.dto.SituationCouponRequest;
import uz.topdim.coupon.dto.UpdateSituationRequest;
import uz.topdim.coupon.service.SituationService;

import java.util.List;

/**
 * Admin-контроллер ситуаций: CRUD + назначение купонов.
 * Доступ: ADMIN, SUPER_ADMIN (по образцу AdminCategoryController).
 */
@RestController
@RequestMapping("/api/v1/admin/situations")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@RequiredArgsConstructor
public class AdminSituationController {

    private final SituationService situationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminSituationResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(situationService.getAllSituationsForAdmin()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminSituationResponse>> getOne(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(situationService.getSituationForAdmin(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Long>> create(
            @Valid @RequestBody CreateSituationRequest request) {
        Long id = situationService.createSituation(request);
        return ResponseEntity.ok(ApiResponse.success("Ситуация успешно создана", id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateSituationRequest request) {
        situationService.updateSituation(id, request);
        return ResponseEntity.ok(ApiResponse.success("Ситуация обновлена", null));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        situationService.deleteSituation(id);
        return ResponseEntity.ok(ApiResponse.success("Ситуация удалена", null));
    }

    @PutMapping("/{id}/coupons")
    public ResponseEntity<ApiResponse<Void>> assignCoupons(
            @PathVariable Long id,
            @Valid @RequestBody SituationCouponRequest request) {
        situationService.assignCoupons(id, request);
        return ResponseEntity.ok(ApiResponse.success("Купоны назначены", null));
    }

    @DeleteMapping("/{id}/coupons")
    public ResponseEntity<ApiResponse<Void>> clearCoupons(@PathVariable Long id) {
        situationService.clearCoupons(id);
        return ResponseEntity.ok(ApiResponse.success("Купоны удалены из ситуации", null));
    }
}
