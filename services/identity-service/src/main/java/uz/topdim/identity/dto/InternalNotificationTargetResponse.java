package uz.topdim.identity.dto;

public record InternalNotificationTargetResponse(
        Long userId,
        String email,
        boolean emailVerified,
        Long telegramChatId,
        boolean telegramLinked
) {
}
