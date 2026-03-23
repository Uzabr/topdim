package uz.topdim.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import uz.topdim.common.dto.ApiResponse;

import java.util.Map;

@FeignClient(name = "user-service", path = "/api/v1")
public interface UserClient {

    @GetMapping("/users/{id}")
    ApiResponse<Map<String, Object>> getUserById(@PathVariable("id") Long id);
}
