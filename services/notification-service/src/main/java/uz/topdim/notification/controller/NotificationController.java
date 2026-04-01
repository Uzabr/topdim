package uz.topdim.notification.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.notification.dto.NotificationResponse;
import uz.topdim.notification.service.NotificationService;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<NotificationResponse>>> getMyNotifications(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(required = false) Boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                notificationService.getUserNotifications(userId, unreadOnly, PageRequest.of(page, size))
        ));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<String>> markAsRead(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long id
    ) {
        notificationService.markAsRead(userId, id);
        return ResponseEntity.ok(ApiResponse.success("Уведомление прочитано", null));
    }
}
