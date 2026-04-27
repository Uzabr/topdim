package uz.topdim.identity.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.identity.dto.PartnerAccessContextResponse;
import uz.topdim.identity.service.PartnerStaffService;

/**
 * Internal endpoint для определения контекста доступа партнёра.
 * Используется другими сервисами (order-service) для выяснения
 * роли пользователя (OWNER/MANAGER/CASHIER) и его привязки к филиалу.
 */
@RestController
@RequestMapping("/api/v1/internal/partner-access")
@RequiredArgsConstructor
public class InternalPartnerAccessController {

    private final PartnerStaffService partnerStaffService;

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<PartnerAccessContextResponse>> getAccessContext(
            @PathVariable Long userId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                partnerStaffService.resolveAccessContext(userId)));
    }
}
