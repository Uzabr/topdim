package uz.topdim.coupon.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.coupon.dto.merchantprofile.MerchantProfileChangePayload;
import uz.topdim.coupon.dto.merchantprofile.MerchantProfileChangeResponse;
import uz.topdim.coupon.dto.merchantprofile.MerchantProfileChangeSummary;
import uz.topdim.coupon.entity.MerchantProfileChangeStatus;
import uz.topdim.coupon.service.MerchantProfileDraftService;
import uz.topdim.coupon.service.PartnerAccessResolver;
import uz.topdim.coupon.service.ResolvedPartnerAccess;

@RestController
@RequestMapping("/api/v1/partner/merchant/change-requests")
@PreAuthorize("hasAnyRole('PARTNER', 'ADMIN', 'SUPER_ADMIN')")
@RequiredArgsConstructor
@Validated
public class PartnerMerchantProfileChangeController {

    private final MerchantProfileDraftService draftService;
    private final PartnerAccessResolver partnerAccessResolver;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<MerchantProfileChangeSummary>>> list(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(required = false) MerchantProfileChangeStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        ResolvedPartnerAccess access = partnerAccessResolver.resolveOwnerOrManager(userId);
        return ResponseEntity.ok(ApiResponse.success(
                draftService.list(status, PageRequest.of(page, size), access)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<MerchantProfileChangeResponse>> createDraft(
            @RequestHeader("X-User-Id") Long userId
    ) {
        ResolvedPartnerAccess access = partnerAccessResolver.resolveOwnerOrManager(userId);
        MerchantProfileChangeResponse response = draftService.createDraft(userId, access);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Черновик изменения профиля создан", response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<MerchantProfileChangeResponse>> get(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long id
    ) {
        ResolvedPartnerAccess access = partnerAccessResolver.resolveOwnerOrManager(userId);
        return ResponseEntity.ok(ApiResponse.success(draftService.get(id, access)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<MerchantProfileChangeResponse>> update(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long id,
            @Valid @RequestBody MerchantProfileChangePayload payload
    ) {
        ResolvedPartnerAccess access = partnerAccessResolver.resolveOwnerOrManager(userId);
        return ResponseEntity.ok(ApiResponse.success(
                "Черновик изменения профиля обновлён",
                draftService.update(id, payload, access)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDraft(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long id
    ) {
        ResolvedPartnerAccess access = partnerAccessResolver.resolveOwnerOrManager(userId);
        draftService.deleteDraft(id, access);
        return ResponseEntity.noContent().build();
    }
}
