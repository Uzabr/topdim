package uz.topdim.notification.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.notification.config.FeignInternalAuthConfig;

@FeignClient(
        name = "identity-service",
        path = "/api/v1",
        configuration = FeignInternalAuthConfig.class
)
public interface IdentityNotificationTargetClient {

    @GetMapping("/internal/notification-targets/{userId}")
    ApiResponse<InternalNotificationTargetResponse> getTarget(
            @PathVariable("userId") Long userId
    );
}
