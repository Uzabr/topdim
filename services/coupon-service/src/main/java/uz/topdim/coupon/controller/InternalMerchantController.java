package uz.topdim.coupon.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.coupon.dto.MerchantContextResponse;
import uz.topdim.coupon.service.MerchantService;

@RestController
@RequestMapping("/api/v1/internal/merchants")
@RequiredArgsConstructor
public class InternalMerchantController {
    private final MerchantService merchantService;

    @GetMapping("/by-user/{userId}")
    public ResponseEntity<ApiResponse<MerchantContextResponse>> getByUserId(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.success(
                merchantService.getMerchantContextByUserId(userId)
        ));
    }
}
