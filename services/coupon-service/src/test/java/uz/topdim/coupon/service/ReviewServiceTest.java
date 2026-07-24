package uz.topdim.coupon.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.coupon.client.OrderReviewEligibilityClient;
import uz.topdim.coupon.client.ReviewEligibilityResponse;
import uz.topdim.coupon.dto.CreateReviewRequest;
import uz.topdim.coupon.dto.ReviewResponse;
import uz.topdim.coupon.entity.*;
import uz.topdim.coupon.repository.CouponOfferRepository;
import uz.topdim.coupon.repository.ReviewRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock private ReviewRepository reviewRepository;
    @Mock private CouponOfferRepository couponOfferRepository;
    @Mock private OrderReviewEligibilityClient orderReviewEligibilityClient;

    @InjectMocks
    private ReviewService reviewService;

    private CouponOffer createTestCoupon() {
        return CouponOffer.builder()
                .id(1L)
                .title("Тестовый купон")
                .status(CouponStatus.ACTIVE)
                .build();
    }

    private ReviewEligibilityResponse eligibleResponse() {
        var r = new ReviewEligibilityResponse();
        r.setEligible(true);
        r.setReason("USED_COUPON_FOUND");
        r.setPurchasedCouponId(42L);
        r.setUsedAt(LocalDateTime.now().minusDays(1));
        return r;
    }

    private ReviewEligibilityResponse notEligibleResponse() {
        var r = new ReviewEligibilityResponse();
        r.setEligible(false);
        r.setReason("REVIEW_ALLOWED_AFTER_COUPON_USAGE");
        return r;
    }

    private CreateReviewRequest reviewRequest(Long couponId, int rating, String comment) {
        CreateReviewRequest req = new CreateReviewRequest();
        req.setCouponOfferId(couponId);
        req.setRating(rating);
        req.setComment(comment);
        return req;
    }

    // ==================== Create Review ====================

    @Test
    @DisplayName("Создание отзыва: USED купон → статус PENDING, сохранён userName")
    void createReview_usedCoupon_createsPendingReview() {
        CouponOffer coupon = createTestCoupon();
        when(couponOfferRepository.findById(1L)).thenReturn(Optional.of(coupon));
        when(reviewRepository.existsByUserIdAndCouponOfferIdAndStatusIn(
                eq(42L), eq(1L), any())).thenReturn(false);
        when(orderReviewEligibilityClient.getReviewEligibility(42L, 1L))
                .thenReturn(ApiResponse.success(eligibleResponse()));
        when(reviewRepository.findFirstByUserIdAndCouponOfferIdOrderByCreatedAtDesc(42L, 1L))
                .thenReturn(Optional.empty());

        reviewService.createReview(42L, "Иван", reviewRequest(1L, 5, "Отличный купон, рекомендую!"));

        ArgumentCaptor<Review> captor = ArgumentCaptor.forClass(Review.class);
        verify(reviewRepository).save(captor.capture());

        Review saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(42L);
        assertThat(saved.getUserName()).isEqualTo("Иван");
        assertThat(saved.getRating()).isEqualTo(5);
        assertThat(saved.getComment()).isEqualTo("Отличный купон, рекомендую!");
        assertThat(saved.getStatus()).isEqualTo(ReviewStatus.PENDING);
        assertThat(saved.getCouponOffer()).isEqualTo(coupon);
    }

    @Test
    @DisplayName("Создание отзыва только с рейтингом: null-комментарий сохраняется пустой строкой")
    void createReview_ratingOnly_normalizesNullCommentToEmptyString() {
        CouponOffer coupon = createTestCoupon();
        when(couponOfferRepository.findById(1L)).thenReturn(Optional.of(coupon));
        when(reviewRepository.existsByUserIdAndCouponOfferIdAndStatusIn(
                eq(42L), eq(1L), any())).thenReturn(false);
        when(orderReviewEligibilityClient.getReviewEligibility(42L, 1L))
                .thenReturn(ApiResponse.success(eligibleResponse()));
        when(reviewRepository.findFirstByUserIdAndCouponOfferIdOrderByCreatedAtDesc(42L, 1L))
                .thenReturn(Optional.empty());

        reviewService.createReview(42L, "Иван", reviewRequest(1L, 5, null));

        ArgumentCaptor<Review> captor = ArgumentCaptor.forClass(Review.class);
        verify(reviewRepository).save(captor.capture());
        assertThat(captor.getValue().getComment()).isEmpty();
    }

    @Test
    @DisplayName("Создание отзыва: купон не найден → IllegalArgumentException")
    void createReview_couponNotFound_throwsException() {
        when(couponOfferRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.createReview(1L, "User", reviewRequest(999L, 3, "Не найду купон.")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("не найден");
    }

    @Test
    @DisplayName("Создание отзыва: нет USED купона → IllegalStateException")
    void createReview_noUsedCoupon_throwsNotEligible() {
        CouponOffer coupon = createTestCoupon();
        when(couponOfferRepository.findById(1L)).thenReturn(Optional.of(coupon));
        when(reviewRepository.existsByUserIdAndCouponOfferIdAndStatusIn(
                eq(42L), eq(1L), any())).thenReturn(false);
        when(orderReviewEligibilityClient.getReviewEligibility(42L, 1L))
                .thenReturn(ApiResponse.success(notEligibleResponse()));

        assertThatThrownBy(() -> reviewService.createReview(42L, "Иван", reviewRequest(1L, 4, "Хороший купон, спасибо")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("после использования купона");
    }

    @Test
    @DisplayName("Создание отзыва: PENDING отзыв уже есть → IllegalStateException (дубликат)")
    void createReview_pendingExists_throwsDuplicate() {
        CouponOffer coupon = createTestCoupon();
        when(couponOfferRepository.findById(1L)).thenReturn(Optional.of(coupon));
        when(reviewRepository.existsByUserIdAndCouponOfferIdAndStatusIn(
                42L, 1L, Set.of(ReviewStatus.PENDING, ReviewStatus.APPROVED))).thenReturn(true);

        assertThatThrownBy(() -> reviewService.createReview(42L, "Иван", reviewRequest(1L, 5, "Ещё один отзыв.")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("уже оставили отзыв");
    }

    @Test
    @DisplayName("Создание отзыва: APPROVED отзыв уже есть → IllegalStateException (дубликат)")
    void createReview_approvedExists_throwsDuplicate() {
        CouponOffer coupon = createTestCoupon();
        when(couponOfferRepository.findById(1L)).thenReturn(Optional.of(coupon));
        when(reviewRepository.existsByUserIdAndCouponOfferIdAndStatusIn(
                42L, 1L, Set.of(ReviewStatus.PENDING, ReviewStatus.APPROVED))).thenReturn(true);

        assertThatThrownBy(() -> reviewService.createReview(42L, "Иван", reviewRequest(1L, 5, "Дублирую одобренный")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("уже оставили отзыв");
    }

    @Test
    @DisplayName("Создание отзыва: REJECTED отзыв → переподача как PENDING")
    void createReview_rejectedExists_resubmitsAsPending() {
        CouponOffer coupon = createTestCoupon();
        Review rejected = Review.builder()
                .id(99L).userId(42L).couponOffer(coupon)
                .rating(2).comment("Старый текст")
                .status(ReviewStatus.REJECTED).rejectReason("Спам")
                .build();

        when(couponOfferRepository.findById(1L)).thenReturn(Optional.of(coupon));
        when(reviewRepository.existsByUserIdAndCouponOfferIdAndStatusIn(
                eq(42L), eq(1L), any())).thenReturn(false);
        when(orderReviewEligibilityClient.getReviewEligibility(42L, 1L))
                .thenReturn(ApiResponse.success(eligibleResponse()));
        when(reviewRepository.findFirstByUserIdAndCouponOfferIdOrderByCreatedAtDesc(42L, 1L))
                .thenReturn(Optional.of(rejected));

        Long result = reviewService.createReview(42L, "Иван", reviewRequest(1L, 4, "Исправленный отзыв!!!"));

        assertThat(result).isEqualTo(99L);
        assertThat(rejected.getStatus()).isEqualTo(ReviewStatus.PENDING);
        assertThat(rejected.getRating()).isEqualTo(4);
        assertThat(rejected.getComment()).isEqualTo("Исправленный отзыв!!!");
        assertThat(rejected.getRejectReason()).isNull();
        assertThat(rejected.getUserName()).isEqualTo("Иван");
        verify(reviewRepository).save(rejected);
    }

    @Test
    @DisplayName("Переподача REJECTED отзыва только с рейтингом: null-комментарий сохраняется пустой строкой")
    void createReview_rejectedRatingOnly_normalizesNullCommentToEmptyString() {
        CouponOffer coupon = createTestCoupon();
        Review rejected = Review.builder()
                .id(99L).userId(42L).couponOffer(coupon)
                .rating(2).comment("Старый текст")
                .status(ReviewStatus.REJECTED).rejectReason("Спам")
                .build();

        when(couponOfferRepository.findById(1L)).thenReturn(Optional.of(coupon));
        when(reviewRepository.existsByUserIdAndCouponOfferIdAndStatusIn(
                eq(42L), eq(1L), any())).thenReturn(false);
        when(orderReviewEligibilityClient.getReviewEligibility(42L, 1L))
                .thenReturn(ApiResponse.success(eligibleResponse()));
        when(reviewRepository.findFirstByUserIdAndCouponOfferIdOrderByCreatedAtDesc(42L, 1L))
                .thenReturn(Optional.of(rejected));

        reviewService.createReview(42L, "Иван", reviewRequest(1L, 4, null));

        assertThat(rejected.getComment()).isEmpty();
        assertThat(rejected.getStatus()).isEqualTo(ReviewStatus.PENDING);
        verify(reviewRepository).save(rejected);
    }

    // ==================== Get Reviews ====================

    @Test
    @DisplayName("Получение одобренных отзывов купона: возвращает только APPROVED")
    void getApprovedReviewsForCoupon_returnsOnlyApproved() {
        CouponOffer coupon = createTestCoupon();
        Review review = Review.builder()
                .id(10L).userId(1L).userName("Анна")
                .couponOffer(coupon).rating(4)
                .comment("Хороший").status(ReviewStatus.APPROVED)
                .createdAt(LocalDateTime.of(2026, 1, 1, 12, 0))
                .build();

        Page<Review> page = new PageImpl<>(List.of(review));
        when(reviewRepository.findByCouponOfferIdAndStatusOrderByCreatedAtDesc(
                eq(1L), eq(ReviewStatus.APPROVED), any(Pageable.class))).thenReturn(page);

        Page<ReviewResponse> result = reviewService.getApprovedReviewsForCoupon(1L, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        ReviewResponse dto = result.getContent().get(0);
        assertThat(dto.getId()).isEqualTo(10L);
        assertThat(dto.getUserName()).isEqualTo("Анна");
        assertThat(dto.getRating()).isEqualTo(4);
        assertThat(dto.getCouponOfferId()).isEqualTo(1L);
        assertThat(dto.getStatus()).isEqualTo(ReviewStatus.APPROVED);
    }

    @Test
    @DisplayName("Получение отзывов пользователя: пустая страница")
    void getMyReviews_empty_returnsEmptyPage() {
        when(reviewRepository.findByUserIdOrderByCreatedAtDesc(eq(1L), any(Pageable.class)))
                .thenReturn(Page.empty());

        Page<ReviewResponse> result = reviewService.getMyReviews(1L, PageRequest.of(0, 10));

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(0);
    }
}
