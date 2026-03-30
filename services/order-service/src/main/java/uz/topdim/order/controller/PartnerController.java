package uz.topdim.order.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.order.dto.PartnerStatsResponse;
import uz.topdim.order.dto.RedemptionResponse;
import uz.topdim.order.service.PartnerService;

import java.util.List;

/**
 * Контроллер партнёра — статистика и погашения.
 * merchantId и couponOfferIds передаются через headers из Gateway.
 */
@RestController
@RequestMapping("/api/v1/partner")
@RequiredArgsConstructor
public class PartnerController {

    private final PartnerService partnerService;

    /** Статистика продаж партнёра. */
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<PartnerStatsResponse>> getStats(
            @RequestHeader("X-Merchant-Id") Long merchantId,
            @RequestParam List<Long> couponOfferIds
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                partnerService.getStats(merchantId, couponOfferIds)));
    }

    /** История погашений. */
    @GetMapping("/redemptions")
    public ResponseEntity<ApiResponse<Page<RedemptionResponse>>> getRedemptions(
            @RequestHeader("X-Merchant-Id") Long merchantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                partnerService.getRedemptions(merchantId, page, size)));
    }
}
