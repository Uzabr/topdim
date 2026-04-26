package uz.topdim.identity.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.topdim.identity.dto.ApprovePartnerApplicationRequest;
import uz.topdim.identity.dto.PartnerApplicationResponse;
import uz.topdim.identity.dto.RejectPartnerApplicationRequest;
import uz.topdim.identity.entity.ApplicationStatus;
import uz.topdim.identity.service.PartnerApplicationService;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/partner-applications")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminPartnerApplicationController {

    private final PartnerApplicationService service;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAll(
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<PartnerApplicationResponse> result = service.getAll(
                status, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return ResponseEntity.ok(Map.of("success", true, "data", result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(Map.of("success", true, "data", service.getById(id)));
    }

    @PatchMapping("/{id}/approve")
    public ResponseEntity<Map<String, Object>> approve(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long adminId,
            @Valid @RequestBody ApprovePartnerApplicationRequest request
    ) {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Application approved",
                "data", service.approve(id, adminId, request)
        ));
    }

    @PatchMapping("/{id}/reject")
    public ResponseEntity<Map<String, Object>> reject(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long adminId,
            @Valid @RequestBody RejectPartnerApplicationRequest request
    ) {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Application rejected",
                "data", service.reject(id, adminId, request)
        ));
    }
}
