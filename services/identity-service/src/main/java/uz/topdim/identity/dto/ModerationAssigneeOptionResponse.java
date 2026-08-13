package uz.topdim.identity.dto;

public record ModerationAssigneeOptionResponse(
        Long userId,
        String name,
        String email,
        String role
) {
}
