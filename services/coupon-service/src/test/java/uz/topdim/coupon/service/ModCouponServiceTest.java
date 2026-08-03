package uz.topdim.coupon.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import uz.topdim.common.events.NotificationEvent;
import uz.topdim.coupon.dto.CouponOfferResponse;
import uz.topdim.coupon.dto.ReviewResponse;
import uz.topdim.coupon.entity.CouponOffer;
import uz.topdim.coupon.entity.CouponStatus;
import uz.topdim.coupon.entity.Merchant;
import uz.topdim.coupon.entity.Review;
import uz.topdim.coupon.entity.ReviewStatus;
import uz.topdim.coupon.repository.CouponOfferRepository;
import uz.topdim.coupon.repository.ReviewRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModCouponServiceTest {

    @Mock private CouponOfferRepository couponOfferRepository;
    @Mock private ReviewRepository reviewRepository;
    @Mock private CouponOfferService couponOfferService;
    @Mock private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private ModCouponService modCouponService;

    private CouponOffer createCoupon(CouponStatus status) {
        Merchant merchant = Merchant.builder()
                .id(5L)
                .userId(77L)
                .name("Merchant")
                .build();

        return CouponOffer.builder()
                .id(10L)
                .title("Весенний купон")
                .status(status)
                .merchant(merchant)
                .build();
    }

    @Test
    @DisplayName("getPendingCoupons: возвращает купоны со статусом WAITING_FOR_MERCHANT")
    void getPendingCoupons_returnsMappedPage() {
        CouponOffer coupon = createCoupon(CouponStatus.WAITING_FOR_MERCHANT);
        CouponOfferResponse response = CouponOfferResponse.builder()
                .id(10L)
                .title("Весенний купон")
                .status("WAITING_FOR_MERCHANT")
                .build();

        when(couponOfferRepository.findAllByStatus(CouponStatus.WAITING_FOR_MERCHANT, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(java.util.List.of(coupon)));
        when(couponOfferService.mapToResponse(coupon)).thenReturn(response);

        Page<CouponOfferResponse> result = modCouponService.getPendingCoupons(PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("Весенний купон");
    }

    @Test
    @DisplayName("reviewCoupon: APPROVE вызывает approveByMerchant и отправляет success notification")
    void reviewCoupon_approve_sendsSuccessNotification() {
        CouponOffer coupon = createCoupon(CouponStatus.ACTIVE);

        when(couponOfferRepository.findById(10L)).thenReturn(Optional.of(coupon));

        modCouponService.reviewCoupon(3L, 10L, "APPROVE", "SUP-42: подтверждено");

        verify(couponOfferService).approveByMerchant(10L);

        ArgumentCaptor<NotificationEvent> eventCaptor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(rabbitTemplate).convertAndSend(
                org.mockito.Mockito.eq("notification.exchange"),
                org.mockito.Mockito.eq("notification.sent"),
                eventCaptor.capture()
        );

        NotificationEvent event = eventCaptor.getValue();
        assertThat(event.getUserId()).isEqualTo(77L);
        assertThat(event.getTitle()).isEqualTo("Купон одобрен");
        assertThat(event.getType()).isEqualTo("SUCCESS");
        assertThat(event.getMessage()).contains("Весенний купон");
    }

    @Test
    @DisplayName("reviewCoupon: REJECT вызывает requestRevisionByMerchant и отправляет alert notification с причиной")
    void reviewCoupon_reject_sendsAlertNotification() {
        CouponOffer coupon = createCoupon(CouponStatus.REVISION_REQUESTED);

        when(couponOfferRepository.findById(10L)).thenReturn(Optional.of(coupon));

        modCouponService.reviewCoupon(3L, 10L, "REJECT", "  Добавьте фото  ");

        verify(couponOfferService).requestRevisionByMerchant(10L, "Добавьте фото");

        ArgumentCaptor<NotificationEvent> eventCaptor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(rabbitTemplate).convertAndSend(
                org.mockito.Mockito.eq("notification.exchange"),
                org.mockito.Mockito.eq("notification.sent"),
                eventCaptor.capture()
        );

        NotificationEvent event = eventCaptor.getValue();
        assertThat(event.getTitle()).isEqualTo("Купон отклонен");
        assertThat(event.getType()).isEqualTo("ALERT");
        assertThat(event.getMessage()).contains("Добавьте фото");
    }

    @Test
    @DisplayName("reviewCoupon: неизвестное решение отклоняется без вызова state transition")
    void reviewCoupon_unknownDecision_throws() {
        assertThatThrownBy(() -> modCouponService.reviewCoupon(3L, 10L, "HOLD", "SUP-42"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown decision");

        verify(couponOfferService, never()).approveByMerchant(any());
        verify(couponOfferService, never()).requestRevisionByMerchant(any(), any());
        verify(rabbitTemplate, never()).convertAndSend(any(), any(), any(NotificationEvent.class));
    }

    @Test
    @DisplayName("reviewCoupon: пустая бизнес-причина отклоняется до state transition")
    void reviewCoupon_blankReason_throwsBeforeTransition() {
        assertThatThrownBy(() -> modCouponService.reviewCoupon(3L, 10L, "APPROVE", "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Причина");

        verify(couponOfferService, never()).approveByMerchant(any());
        verify(couponOfferService, never()).requestRevisionByMerchant(any(), any());
        verify(rabbitTemplate, never()).convertAndSend(any(), any(), any(NotificationEvent.class));
    }

    @Test
    @DisplayName("reviewCoupon: approve failure does not send success notification")
    void reviewCoupon_approveFailure_doesNotSendNotification() {
        CouponOffer coupon = createCoupon(CouponStatus.WAITING_FOR_MERCHANT);
        org.mockito.Mockito.lenient().when(couponOfferRepository.findById(10L)).thenReturn(Optional.of(coupon));

        org.mockito.Mockito.doThrow(new IllegalStateException("Нельзя публиковать купон без active primary location у мерчанта"))
                .when(couponOfferService).approveByMerchant(10L);

        assertThatThrownBy(() -> modCouponService.reviewCoupon(3L, 10L, "APPROVE", "SUP-42"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("primary location");

        verify(rabbitTemplate, never()).convertAndSend(any(), any(), any(NotificationEvent.class));
    }

    @Test
    @DisplayName("reviewUserReview: REJECT сохраняет причину и отправляет notification пользователю")
    void reviewUserReview_reject_setsReasonAndSendsNotification() {
        Review review = Review.builder()
                .id(44L)
                .userId(55L)
                .couponOffer(createCoupon(CouponStatus.ACTIVE))
                .rating(2)
                .comment("Сомнительный отзыв")
                .status(ReviewStatus.PENDING)
                .build();

        when(reviewRepository.findById(44L)).thenReturn(Optional.of(review));
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));

        modCouponService.reviewUserReview(3L, 44L, "REJECT", "Нарушение правил");

        assertThat(review.getStatus()).isEqualTo(ReviewStatus.REJECTED);
        assertThat(review.getRejectReason()).isEqualTo("Нарушение правил");

        ArgumentCaptor<NotificationEvent> eventCaptor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(rabbitTemplate).convertAndSend(
                org.mockito.Mockito.eq("notification.exchange"),
                org.mockito.Mockito.eq("notification.sent"),
                eventCaptor.capture()
        );
        assertThat(eventCaptor.getValue().getUserId()).isEqualTo(55L);
        assertThat(eventCaptor.getValue().getTitle()).isEqualTo("Отзыв отклонен");
    }

    @Test
    @DisplayName("getPendingReviews: возвращает только pending reviews с корректным маппингом")
    void getPendingReviews_mapsReviewPage() {
        Review review = Review.builder()
                .id(44L)
                .userId(55L)
                .couponOffer(createCoupon(CouponStatus.ACTIVE))
                .rating(5)
                .comment("Отлично")
                .status(ReviewStatus.PENDING)
                .build();

        when(reviewRepository.findByStatusOrderByCreatedAtDesc(ReviewStatus.PENDING, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(java.util.List.of(review)));

        Page<ReviewResponse> result = modCouponService.getPendingReviews(PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getUserId()).isEqualTo(55L);
        assertThat(result.getContent().get(0).getStatus()).isEqualTo(ReviewStatus.PENDING);
    }
}
