package uz.topdim.coupon.client;

public record ModerationAssigneeOption(
        Long userId,
        String name,
        String email,
        String role
) {
}
