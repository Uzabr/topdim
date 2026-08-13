package uz.topdim.coupon.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.coupon.config.FeignInternalAuthConfig;

import java.util.Set;

@FeignClient(
        name = "identity-service",
        path = "/api/v1",
        configuration = FeignInternalAuthConfig.class
)
public interface IdentityPartnerAccessClient {

    @GetMapping("/internal/partner-access/{userId}")
    ApiResponse<PartnerAccessContext> getPartnerAccessContext(@PathVariable("userId") Long userId);

    @GetMapping("/internal/partner-access/merchants/{merchantId}/active-staff-location-ids")
    ApiResponse<Set<Long>> getActiveStaffLocationIds(@PathVariable("merchantId") Long merchantId);

    @GetMapping("/internal/moderation-assignees/{userId}")
    ApiResponse<ModerationAssigneeContext> getModerationAssignee(@PathVariable("userId") Long userId);
}
