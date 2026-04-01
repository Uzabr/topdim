package uz.topdim.user.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.topdim.user.dto.PartnerApplicationResponse;
import uz.topdim.user.entity.ApplicationStatus;
import uz.topdim.user.service.PartnerApplicationService;

import java.util.Map;

/**
 * Админский контроллер: просмотр и управление заявками партнёров.
 * Доступ: ADMIN, SUPER_ADMIN.
 */
@RestController
@RequestMapping("/api/v1/admin/partner-applications")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminPartnerApplicationController {

    private final PartnerApplicationService service;

    /**
     * Список заявок с фильтром по статусу и пагинацией.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAll(
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<PartnerApplicationResponse> result = service.getAll(
                status,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", result
        ));
    }

    /**
     * Детали одной заявки.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", service.getById(id)
        ));
    }

    /**
     * Одобрить заявку (после этого админ создаёт пользователя-партнёра).
     */
    @PatchMapping("/{id}/approve")
    public ResponseEntity<Map<String, Object>> approve(@PathVariable Long id) {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Заявка одобрена",
                "data", service.approve(id)
        ));
    }

    /**
     * Отклонить заявку.
     */
    @PatchMapping("/{id}/reject")
    public ResponseEntity<Map<String, Object>> reject(@PathVariable Long id) {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Заявка отклонена",
                "data", service.reject(id)
        ));
    }
}
