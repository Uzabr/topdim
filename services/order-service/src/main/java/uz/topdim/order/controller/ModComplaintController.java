package uz.topdim.order.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.order.dto.ComplaintResponse;
import uz.topdim.order.dto.ResolveComplaintRequest;
import uz.topdim.order.service.ComplaintService;

@RestController
@RequestMapping("/api/v1/mod/complaints")
@PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN', 'SUPER_ADMIN')")
@RequiredArgsConstructor
public class ModComplaintController {

    private final ComplaintService complaintService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<ComplaintResponse>>> getPendingComplaints(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                complaintService.getPendingComplaints(PageRequest.of(page, size))
        ));
    }

    @PatchMapping("/{id}/resolve")
    public ResponseEntity<ApiResponse<Void>> resolveComplaint(
            @RequestHeader("X-User-Id") Long modId,
            @PathVariable Long id,
            @Valid @RequestBody ResolveComplaintRequest request
    ) {
        complaintService.resolveComplaint(modId, id, request);
        return ResponseEntity.ok(ApiResponse.success("Жалоба обработана", null));
    }
}
