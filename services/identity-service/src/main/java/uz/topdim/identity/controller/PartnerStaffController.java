package uz.topdim.identity.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.identity.dto.PartnerStaffResponse;
import uz.topdim.identity.dto.CreateStaffRequest;
import uz.topdim.identity.service.PartnerStaffService;
import java.util.List;

@RestController
@RequestMapping("/api/v1/partner/staff")
@PreAuthorize("hasAnyRole('PARTNER', 'ADMIN', 'SUPER_ADMIN')")
@RequiredArgsConstructor
public class PartnerStaffController {

    private final PartnerStaffService partnerStaffService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<PartnerStaffResponse>>> getMyStaff(
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(ApiResponse.success(partnerStaffService.getMyStaff(userId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PartnerStaffResponse>> addStaff(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody CreateStaffRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Сотрудник добавлен", partnerStaffService.addStaff(userId, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> removeStaff(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long id) {
        partnerStaffService.removeStaff(userId, id);
        return ResponseEntity.ok(ApiResponse.success("Сотрудник удалён"));
    }
}
