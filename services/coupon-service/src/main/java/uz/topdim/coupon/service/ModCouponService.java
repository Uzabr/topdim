package uz.topdim.coupon.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.topdim.common.events.NotificationEvent;


import uz.topdim.coupon.dto.CouponOfferResponse;
import uz.topdim.coupon.dto.ReviewResponse;
import uz.topdim.coupon.entity.CouponOffer;
import uz.topdim.coupon.entity.CouponStatus;
import uz.topdim.coupon.entity.Review;
import uz.topdim.coupon.entity.ReviewStatus;
import uz.topdim.coupon.repository.CouponOfferRepository;
import uz.topdim.coupon.repository.ReviewRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class ModCouponService {

    private final CouponOfferRepository couponOfferRepository;
    private final ReviewRepository reviewRepository;
    private final CouponOfferService couponOfferService;
    private final RabbitTemplate rabbitTemplate;

    @Transactional(readOnly = true)
    public Page<CouponOfferResponse> getPendingCoupons(Pageable pageable) {
        return couponOfferRepository.findAllByStatus(CouponStatus.WAITING_FOR_MERCHANT, pageable)
                .map(couponOfferService::mapToResponse);
    }

    @Transactional
    public void reviewCoupon(Long modId, Long couponId, String decision, String reason) {
        String normalizedReason = reason == null ? "" : reason.trim();
        if (normalizedReason.isBlank()) {
            throw new IllegalArgumentException("Причина служебного решения обязательна");
        }
        String normalizedDecision = decision == null ? "" : decision.trim().toUpperCase(java.util.Locale.ROOT);

        if ("APPROVE".equals(normalizedDecision)) {
            // Делегируем — валидация State Machine (WAITING_FOR_MERCHANT → ACTIVE) внутри
            couponOfferService.approveByMerchant(couponId);
        } else if ("REJECT".equals(normalizedDecision)) {
            // Делегируем — валидация + сохранение revisionComment внутри
            couponOfferService.requestRevisionByMerchant(couponId, normalizedReason);
        } else {
            throw new IllegalArgumentException("Unknown decision: " + decision);
        }

        log.info(
                "Служебное решение по купону: actorId={}, couponId={}, decision={}, reason={}",
                modId,
                couponId,
                normalizedDecision,
                normalizedReason
        );

        // Отправляем уведомление после успешного перехода
        CouponOffer coupon = couponOfferRepository.findById(couponId).orElseThrow();
        if ("APPROVE".equals(normalizedDecision)) {
            sendNotification(coupon.getMerchant().getUserId(), "Купон одобрен",
                    "Ваш купон '" + coupon.getTitle() + "' был успешно промодерирован и опубликован.", "SUCCESS");
        } else {
            sendNotification(coupon.getMerchant().getUserId(), "Купон отклонен",
                    "Ваш купон '" + coupon.getTitle() + "' был отклонен. Причина: " + normalizedReason, "ALERT");
        }
    }

    @Transactional(readOnly = true)
    public Page<ReviewResponse> getPendingReviews(Pageable pageable) {
        return reviewRepository.findByStatusOrderByCreatedAtDesc(ReviewStatus.PENDING, pageable)
                .map(this::mapReview);
    }

    @Transactional
    public void reviewUserReview(Long modId, Long reviewId, String decision, String reason) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new RuntimeException("Отзыв не найден"));

        if ("APPROVE".equalsIgnoreCase(decision)) {
            review.setStatus(ReviewStatus.APPROVED);
        } else if ("REJECT".equalsIgnoreCase(decision)) {
            review.setStatus(ReviewStatus.REJECTED);
            review.setRejectReason(reason);
            // Optional: send notification to user that their review was rejected
            sendNotification(review.getUserId(), "Отзыв отклонен", 
                    "Мы не смогли опубликовать ваш отзыв. Причина: " + reason, "INFO");
        } else {
            throw new IllegalArgumentException("Unknown decision: " + decision);
        }

        reviewRepository.save(review);
    }

    private void sendNotification(Long userId, String title, String message, String type) {
        NotificationEvent event = NotificationEvent.builder()
                .userId(userId)
                .title(title)
                .message(message)
                .type(type)
                .build();
        rabbitTemplate.convertAndSend("notification.exchange", "notification.sent", event);
    }

    private ReviewResponse mapReview(Review review) {
        return ReviewResponse.builder()
                .id(review.getId())
                .userId(review.getUserId())
                .couponOfferId(review.getCouponOffer().getId())
                .rating(review.getRating())
                .comment(review.getComment())
                .status(review.getStatus())
                .rejectReason(review.getRejectReason())
                .createdAt(review.getCreatedAt())
                .build();
    }
}
