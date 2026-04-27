package uz.topdim.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import uz.topdim.common.dto.ApiResponse;

/**
 * Feign клиент для identity-service.
 * Получение контекста доступа партнёра (OWNER/MANAGER/CASHIER).
 */
@FeignClient(name = "identity-service", path = "/api/v1")
public interface IdentityPartnerAccessClient {

    @GetMapping("/internal/partner-access/{userId}")
    ApiResponse<PartnerAccessContext> getPartnerAccessContext(@PathVariable("userId") Long userId);
}
