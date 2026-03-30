package uz.topdim.order.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.order.dto.ComplaintResponse;
import uz.topdim.order.dto.CreateComplaintRequest;
import uz.topdim.order.service.ComplaintService;

@RestController
@RequestMapping("/api/v1/complaints")
@RequiredArgsConstructor
public class ComplaintController {

    private final ComplaintService complaintService;

    @PostMapping
    public ResponseEntity<ApiResponse<Long>> submitComplaint(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody CreateComplaintRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Жалоба успешно отправлена", 
                complaintService.createComplaint(userId, request)
        ));
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<Page<ComplaintResponse>>> getMyComplaints(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                complaintService.getMyComplaints(userId, PageRequest.of(page, size))
        ));
    }
}
