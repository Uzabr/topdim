package uz.topdim.identity.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ВРЕМЕННЫЙ тест PR-гейта — НЕ мержить.
 *
 * Намеренно нарушает правила проекта, чтобы проверить, что pr-gatekeeper
 * выносит FAIL и делегирует специалистам:
 *  - request DTO без Jakarta Validation (@Valid / @NotBlank) — вход как есть;
 *  - новый публичный endpoint без rate-limit;
 *  - бизнес-логики нет, но и теста нет.
 */
@RestController
@RequestMapping("/api/v1/gatekeeper-selftest")
public class GatekeeperSelfTestController {

    @PostMapping
    public String echo(@RequestBody SelfTestRequest request) {
        return "echo: " + request.value;
    }

    public static class SelfTestRequest {
        public String value;
    }
}
