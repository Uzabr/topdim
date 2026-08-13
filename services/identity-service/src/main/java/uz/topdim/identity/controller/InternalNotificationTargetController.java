package uz.topdim.identity.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.identity.dto.InternalNotificationTargetResponse;
import uz.topdim.identity.entity.User;
import uz.topdim.identity.exception.ResourceNotFoundException;
import uz.topdim.identity.repository.UserRepository;

@RestController
@RequestMapping("/api/v1/internal/notification-targets")
@RequiredArgsConstructor
public class InternalNotificationTargetController {

    private final UserRepository userRepository;

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<InternalNotificationTargetResponse>> getTarget(
            @PathVariable Long userId
    ) {
        User user = userRepository.findById(userId)
                .filter(candidate -> !candidate.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Пользователь не найден"));
        boolean externalDeliveryAllowed = user.isEnabled();
        boolean telegramLinked = externalDeliveryAllowed
                && user.getTelegramChatId() != null
                && user.getTelegramLinkedAt() != null;
        return ResponseEntity.ok(ApiResponse.success(new InternalNotificationTargetResponse(
                user.getId(),
                externalDeliveryAllowed ? user.getEmail() : null,
                externalDeliveryAllowed && user.isEmailVerified(),
                externalDeliveryAllowed ? user.getTelegramChatId() : null,
                telegramLinked
        )));
    }
}
