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
import uz.topdim.coupon.dto.CreateReviewRequest;
import uz.topdim.coupon.dto.ReviewResponse;
import uz.topdim.coupon.entity.*;
import uz.topdim.coupon.repository.CouponOfferRepository;
import uz.topdim.coupon.repository.ReviewRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock private ReviewRepository reviewRepository;
    @Mock private CouponOfferRepository couponOfferRepository;

    @InjectMocks
    private ReviewService reviewService;

    private CouponOffer createTestCoupon() {
        return CouponOffer.builder()
                .id(1L)
                .title("Тестовый купон")
                .status(CouponStatus.ACTIVE)
                .build();
    }

    // ==================== Create Review ====================

    @Test
    @DisplayName("Создание отзыва: успешно → статус PENDING, сохранён userName")
    void createReview_success_statusPending() {
        CouponOffer coupon = createTestCoupon();
        when(couponOfferRepository.findById(1L)).thenReturn(Optional.of(coupon));

        CreateReviewRequest request = new CreateReviewRequest();
        request.setCouponOfferId(1L);
        request.setRating(5);
        request.setComment("Отличный купон!");

        reviewService.createReview(42L, "Иван", request);

        ArgumentCaptor<Review> captor = ArgumentCaptor.forClass(Review.class);
        verify(reviewRepository).save(captor.capture());

        Review saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(42L);
        assertThat(saved.getUserName()).isEqualTo("Иван");
        assertThat(saved.getRating()).isEqualTo(5);
        assertThat(saved.getComment()).isEqualTo("Отличный купон!");
        assertThat(saved.getStatus()).isEqualTo(ReviewStatus.PENDING);
        assertThat(saved.getCouponOffer()).isEqualTo(coupon);
    }

    @Test
    @DisplayName("Создание отзыва: купон не найден → RuntimeException")
    void createReview_couponNotFound_throwsException() {
        when(couponOfferRepository.findById(999L)).thenReturn(Optional.empty());

        CreateReviewRequest request = new CreateReviewRequest();
        request.setCouponOfferId(999L);
        request.setRating(3);
        request.setComment("Не найду купон");

        assertThatThrownBy(() -> reviewService.createReview(1L, "User", request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("не найден");
    }

    // ==================== Get Reviews ====================

    @Test
    @DisplayName("Получение одобренных отзывов купона: маппинг корректен")
    void getApprovedReviewsForCoupon_mapsCorrectly() {
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
