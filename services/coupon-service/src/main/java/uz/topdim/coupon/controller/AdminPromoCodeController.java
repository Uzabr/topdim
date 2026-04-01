package uz.topdim.coupon.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.coupon.dto.CreatePromoCodeRequest;
import uz.topdim.coupon.dto.PromoCodeResponse;
import uz.topdim.coupon.service.PromoCodeService;

@RestController
@RequestMapping("/api/v1/admin/promocodes")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@RequiredArgsConstructor
public class AdminPromoCodeController {

    private final PromoCodeService promoCodeService;

    @PostMapping
    public ResponseEntity<ApiResponse<Long>> createPromoCode(
            @Valid @RequestBody CreatePromoCodeRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Промокод успешно создан",
                promoCodeService.createPromoCode(request)
        ));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<PromoCodeResponse>>> getAllPromoCodes(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                promoCodeService.getAllPromoCodes(PageRequest.of(page, size))
        ));
    }
}
