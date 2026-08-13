package uz.topdim.notification.client;

public record InternalNotificationTargetResponse(
        Long userId,
        String email,
        boolean emailVerified,
        Long telegramChatId,
        boolean telegramLinked
) {
}
