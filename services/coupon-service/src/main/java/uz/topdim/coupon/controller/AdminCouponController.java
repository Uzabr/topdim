package uz.topdim.coupon.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.coupon.dto.*;
import uz.topdim.coupon.entity.CouponStatus;
import uz.topdim.coupon.service.CouponOfferService;
import uz.topdim.coupon.service.MerchantService;
import java.util.List;

/**
 * REST контроллер администрирования купонов.
 * Endpoints: создание, обновление, удаление купонов (Admin only).
 * Требует роль ADMIN.
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminCouponController {

    private final CouponOfferService couponOfferService;
    private final MerchantService merchantService;

    // ==================== Coupons ====================

    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN', 'SUPER_ADMIN')")
    @GetMapping("/coupons")
    public ResponseEntity<ApiResponse<Page<CouponOfferResponse>>> getAllCoupons(
            @RequestParam(required = false) CouponStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(couponOfferService.getAllForAdmin(status, page, size)));
    }

    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN', 'SUPER_ADMIN')")
    @PostMapping("/coupons")
    public ResponseEntity<ApiResponse<CouponOfferResponse>> createCoupon(
            @Valid @RequestBody CreateCouponOfferRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Купон создан", couponOfferService.create(request)));
    }

    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN', 'SUPER_ADMIN')")
    @GetMapping("/coupons/{id}")
    public ResponseEntity<ApiResponse<CouponOfferResponse>> getCouponById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(couponOfferService.getByIdAdmin(id)));
    }

    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN', 'SUPER_ADMIN')")
    @PutMapping("/coupons/{id}")
    public ResponseEntity<ApiResponse<CouponOfferResponse>> updateCoupon(
            @PathVariable Long id,
            @Valid @RequestBody CreateCouponOfferRequest request,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String userRole
    ) {
        return ResponseEntity.ok(ApiResponse.success("Купон обновлён", couponOfferService.update(id, request, userId, userRole)));
    }

    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN', 'SUPER_ADMIN')")
    @PatchMapping("/coupons/{id}/status")
    public ResponseEntity<ApiResponse<CouponOfferResponse>> updateCouponStatus(
            @PathVariable Long id,
            @RequestParam CouponStatus status
    ) {
        return ResponseEntity.ok(ApiResponse.success("Статус обновлён", couponOfferService.updateStatus(id, status)));
    }

    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN', 'SUPER_ADMIN')")
    @DeleteMapping("/coupons/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCoupon(@PathVariable Long id) {
        couponOfferService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Купон удалён", null));
    }

    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN', 'SUPER_ADMIN')")
    @PostMapping("/coupons/{id}/archive")
    public ResponseEntity<ApiResponse<CouponOfferResponse>> archiveCoupon(
            @PathVariable Long id,
            @Valid @RequestBody ArchiveCouponRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Купон снят с публикации", couponOfferService.archive(id, request.getReason())));
    }

    /** Отправить купон на согласование мерчанту (DRAFT/REVISION_REQUESTED → WAITING_FOR_MERCHANT). */
    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN', 'SUPER_ADMIN')")
    @PostMapping("/coupons/{id}/send-to-approval")
    public ResponseEntity<ApiResponse<CouponOfferResponse>> sendToApproval(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                "Купон отправлен на согласование", couponOfferService.sendToApproval(id)));
    }

    /** Отклонить заявку партнёра (LEAD/DRAFT → ARCHIVED с причиной). */
    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN', 'SUPER_ADMIN')")
    @PostMapping("/coupons/{id}/reject-request")
    public ResponseEntity<ApiResponse<CouponOfferResponse>> rejectRequest(
            @PathVariable Long id,
            @Valid @RequestBody RejectCouponRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Заявка отклонена", couponOfferService.rejectPartnerRequest(id, request.getReason())));
    }

    /** Модератор берёт лид в работу (LEAD → DRAFT). */
    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN', 'SUPER_ADMIN')")
    @PatchMapping("/coupons/{id}/take-to-work")
    public ResponseEntity<ApiResponse<CouponOfferResponse>> takeToWork(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long moderatorId,
            @RequestHeader("X-User-Email") String moderatorEmail
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Купон взят в работу", couponOfferService.takeToWork(id, moderatorId, moderatorEmail)));
    }

    // ==================== Merchants ====================

    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN', 'SUPER_ADMIN')")
    @GetMapping("/merchants")
    public ResponseEntity<ApiResponse<List<MerchantResponse>>> getAllMerchants() {
        return ResponseEntity.ok(ApiResponse.success(merchantService.getAllMerchants()));
    }

    /** Paginated admin merchant list with search, active filter, readiness & coupon counts. */
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @GetMapping("/merchants/page")
    public ResponseEntity<ApiResponse<Page<AdminMerchantSummaryResponse>>> getMerchantPage(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                merchantService.getAdminMerchantPage(search, active, org.springframework.data.domain.PageRequest.of(page, size))));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @GetMapping("/merchants/{id}")
    public ResponseEntity<ApiResponse<MerchantResponse>> getMerchant(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(merchantService.getMerchantById(id)));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @PostMapping("/merchants")
    public ResponseEntity<ApiResponse<MerchantResponse>> createMerchant(
            @Valid @RequestBody CreateMerchantRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Партнёр создан", merchantService.createMerchant(request)));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @PutMapping("/merchants/{id}")
    public ResponseEntity<ApiResponse<MerchantResponse>> updateMerchant(
            @PathVariable Long id,
            @Valid @RequestBody CreateMerchantRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Партнёр обновлён", merchantService.updateMerchant(id, request)));
    }

    /** Activate/deactivate merchant safely. */
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @PatchMapping("/merchants/{id}/active")
    public ResponseEntity<ApiResponse<MerchantResponse>> setMerchantActiveStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateMerchantActiveRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                request.getActive() ? "Мерчант активирован" : "Мерчант деактивирован",
                merchantService.setMerchantActiveStatus(id, request.getActive())));
    }

    /** Merchant coupons for admin detail page. */
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @GetMapping("/merchants/{id}/coupons")
    public ResponseEntity<ApiResponse<Page<CouponOfferResponse>>> getMerchantCoupons(
            @PathVariable Long id,
            @RequestParam(required = false) CouponStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                couponOfferService.getMerchantCouponsForAdmin(id, status, page, size)));
    }

}
