package uz.topdim.user.dto;

import lombok.*;

import java.time.LocalDateTime;

/**
 * DTO ответа избранного.
 * Поля: id, couponOfferId, addedAt.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FavoriteResponse {
    private Long id;
    private Long couponOfferId;
    private LocalDateTime createdAt;
}
