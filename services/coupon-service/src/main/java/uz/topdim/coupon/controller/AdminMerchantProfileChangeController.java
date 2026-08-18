package uz.topdim.coupon.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.coupon.dto.merchantprofile.AdminMerchantProfileChangeFilter;
import uz.topdim.coupon.dto.merchantprofile.AssignMerchantProfileChangeRequest;
import uz.topdim.coupon.dto.merchantprofile.MerchantProfileChangeResponse;
import uz.topdim.coupon.dto.merchantprofile.MerchantProfileChangeSummary;
import uz.topdim.coupon.dto.merchantprofile.MerchantProfileChangeHistoryResponse;
import uz.topdim.coupon.dto.merchantprofile.MerchantProfilePreflightResponse;
import uz.topdim.coupon.dto.merchantprofile.ModerationCommentRequest;
import uz.topdim.coupon.entity.MerchantProfileChangeStatus;
import uz.topdim.coupon.service.MerchantProfileModerationService;
import uz.topdim.coupon.client.ModerationAssigneeOption;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/merchant-change-requests")
@RequiredArgsConstructor
public class AdminMerchantProfileChangeController {

    private final MerchantProfileModerationService moderationService;

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @GetMapping("/assignees")
    public ResponseEntity<ApiResponse<List<ModerationAssigneeOption>>> listAssignees() {
        return ResponseEntity.ok(ApiResponse.success(
                moderationService.listModerationAssignees()));
    }

    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN', 'SUPER_ADMIN')")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<MerchantProfileChangeSummary>>> list(
            @RequestParam(required = false) MerchantProfileChangeStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long assigneeUserId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime submittedFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime submittedTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        validateQueueFilter(status, assigneeUserId, submittedFrom, submittedTo, page, size);
        String normalizedSearch = search == null || search.isBlank() ? null : search.trim();
        AdminMerchantProfileChangeFilter filter = new AdminMerchantProfileChangeFilter(
                status,
                normalizedSearch,
                assigneeUserId,
                submittedFrom,
                submittedTo);
        return ResponseEntity.ok(ApiResponse.success(
                moderationService.list(filter, PageRequest.of(page, size))));
    }

    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN', 'SUPER_ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<MerchantProfileChangeResponse>> get(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(ApiResponse.success(moderationService.get(id)));
    }

    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN', 'SUPER_ADMIN')")
    @GetMapping("/{id}/history")
    public ResponseEntity<ApiResponse<List<MerchantProfileChangeHistoryResponse>>> getHistory(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(ApiResponse.success(moderationService.getHistory(id)));
    }

    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN', 'SUPER_ADMIN')")
    @GetMapping("/{id}/preflight")
    public ResponseEntity<ApiResponse<MerchantProfilePreflightResponse>> getPreflight(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(ApiResponse.success(moderationService.getPreflight(id)));
    }

    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN', 'SUPER_ADMIN')")
    @PostMapping("/{id}/take-to-work")
    public ResponseEntity<ApiResponse<MerchantProfileChangeResponse>> takeToWork(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long actorUserId,
            @RequestHeader("X-User-Role") String actorRole
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Заявка взята в работу",
                moderationService.takeToWork(id, actorUserId, actorRole)));
    }

    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN', 'SUPER_ADMIN')")
    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<MerchantProfileChangeResponse>> approve(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long actorUserId,
            @RequestHeader("X-User-Role") String actorRole
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Изменения одобрены и опубликованы",
                moderationService.approve(id, actorUserId, actorRole)));
    }

    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN', 'SUPER_ADMIN')")
    @PostMapping("/{id}/request-revision")
    public ResponseEntity<ApiResponse<MerchantProfileChangeResponse>> requestRevision(
            @PathVariable Long id,
            @Valid @RequestBody ModerationCommentRequest request,
            @RequestHeader("X-User-Id") Long actorUserId,
            @RequestHeader("X-User-Role") String actorRole
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Заявка возвращена на доработку",
                moderationService.requestRevision(
                        id, actorUserId, actorRole, request.comment())));
    }

    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN', 'SUPER_ADMIN')")
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<MerchantProfileChangeResponse>> reject(
            @PathVariable Long id,
            @Valid @RequestBody ModerationCommentRequest request,
            @RequestHeader("X-User-Id") Long actorUserId,
            @RequestHeader("X-User-Role") String actorRole
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Заявка отклонена",
                moderationService.reject(id, actorUserId, actorRole, request.comment())));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @PostMapping("/{id}/release")
    public ResponseEntity<ApiResponse<MerchantProfileChangeResponse>> release(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long actorUserId,
            @RequestHeader("X-User-Role") String actorRole
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Заявка возвращена в очередь",
                moderationService.release(id, actorUserId, actorRole)));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @PostMapping("/{id}/reassign")
    public ResponseEntity<ApiResponse<MerchantProfileChangeResponse>> reassign(
            @PathVariable Long id,
            @Valid @RequestBody AssignMerchantProfileChangeRequest request,
            @RequestHeader("X-User-Id") Long actorUserId,
            @RequestHeader("X-User-Role") String actorRole
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Исполнитель изменён",
                moderationService.reassign(
                        id,
                        request.assigneeUserId(),
                        actorUserId,
                        actorRole)));
    }

    private void validateQueueFilter(
            MerchantProfileChangeStatus status,
            Long assigneeUserId,
            LocalDateTime submittedFrom,
            LocalDateTime submittedTo,
            int page,
            int size
    ) {
        if (status == MerchantProfileChangeStatus.DRAFT) {
            throw new IllegalArgumentException("Черновики партнёра не входят в очередь модерации");
        }
        if (page < 0) {
            throw new IllegalArgumentException("Номер страницы не может быть отрицательным");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("Размер страницы должен быть от 1 до 100");
        }
        if (assigneeUserId != null && assigneeUserId <= 0) {
            throw new IllegalArgumentException("ID исполнителя должен быть положительным");
        }
        if (submittedFrom != null && submittedTo != null && submittedFrom.isAfter(submittedTo)) {
            throw new IllegalArgumentException("Начало периода не может быть позже конца");
        }
    }
}
