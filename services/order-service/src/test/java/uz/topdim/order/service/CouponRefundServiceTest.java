package uz.topdim.order.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import uz.topdim.order.entity.*;
import uz.topdim.order.repository.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Тесты бизнес-логики возвратов per-coupon.
 * Покрывают: happy path, негативные сценарии, edge cases, состояния.
 */
@ExtendWith(MockitoExtension.class)
class CouponRefundServiceTest {

    @Mock private CartRepository cartRepository;
    @Mock private CartItemRepository cartItemRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private PurchasedCouponRepository purchasedCouponRepository;
    @Mock private RedemptionRepository redemptionRepository;
    @Mock private RefundRequestRepository refundRequestRepository;
    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private uz.topdim.order.client.CouponClient couponClient;

    @InjectMocks
    private OrderService orderService;

    private PurchasedCoupon buildActiveCoupon(Long id, Long userId, Long couponOfferId, Long couponOptionId) {
        Order order = Order.builder()
                .id(100L)
                .userId(userId)
                .items(List.of(
                        OrderItem.builder()
                                .couponOfferId(couponOfferId)
                                .couponOptionId(couponOptionId)
                                .unitPrice(new BigDecimal("50000"))
                                .build()
                ))
                .build();
        return PurchasedCoupon.builder()
                .id(id)
                .userId(userId)
                .order(order)
                .couponOfferId(couponOfferId)
                .couponOptionId(couponOptionId)
                .couponTitle("Тест Купон")
                .optionTitle("Стандарт")
                .couponCode("CP-TEST123")
                .status(PurchasedCouponStatus.ACTIVE)
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();
    }

    // ═══ CREATE REFUND ═══

    @Test
    @DisplayName("Возврат: ACTIVE купон — создаёт PENDING заявку и блокирует купон")
    void createCouponRefund_activeCoupon_createsPendingRequestAndLocksCoupon() {
        PurchasedCoupon coupon = buildActiveCoupon(1L, 10L, 100L, 200L);
        when(purchasedCouponRepository.findById(1L)).thenReturn(Optional.of(coupon));
        when(refundRequestRepository.existsByPurchasedCouponIdAndStatusIn(eq(1L), any())).thenReturn(false);
        when(refundRequestRepository.save(any(RefundRequest.class))).thenAnswer(i -> {
            RefundRequest rr = i.getArgument(0);
            rr.setId(1L);
            return rr;
        });
        when(purchasedCouponRepository.save(any(PurchasedCoupon.class))).thenAnswer(i -> i.getArgument(0));

        var result = orderService.createCouponRefundRequest(10L, 1L, "Не нужен больше этот купон");

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(RefundRequest.RefundStatus.PENDING);
        assertThat(result.getRefundAmount()).isEqualTo(new BigDecimal("50000"));
        assertThat(coupon.getStatus()).isEqualTo(PurchasedCouponStatus.REFUND_PENDING);
        verify(purchasedCouponRepository).save(coupon);
    }

    @Test
    @DisplayName("Возврат: USED купон — отклоняет с ошибкой")
    void createCouponRefund_usedCoupon_throws() {
        PurchasedCoupon coupon = buildActiveCoupon(2L, 10L, 100L, 200L);
        coupon.setStatus(PurchasedCouponStatus.USED);
        when(purchasedCouponRepository.findById(2L)).thenReturn(Optional.of(coupon));

        assertThatThrownBy(() -> orderService.createCouponRefundRequest(10L, 2L, "Хочу вернуть деньги"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Возврат доступен только для активного");

        verify(refundRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("Возврат: просроченный купон — помечает EXPIRED и отклоняет")
    void createCouponRefund_expiredCoupon_marksExpiredAndThrows() {
        PurchasedCoupon coupon = buildActiveCoupon(3L, 10L, 100L, 200L);
        coupon.setExpiresAt(LocalDateTime.now().minusDays(1));
        when(purchasedCouponRepository.findById(3L)).thenReturn(Optional.of(coupon));
        when(purchasedCouponRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        assertThatThrownBy(() -> orderService.createCouponRefundRequest(10L, 3L, "Хочу вернуть деньги"))
                .isInstanceOf(IllegalStateException.class);

        assertThat(coupon.getStatus()).isEqualTo(PurchasedCouponStatus.EXPIRED);
    }

    @Test
    @DisplayName("Возврат: чужой купон — запрещает")
    void createCouponRefund_otherUserCoupon_throws() {
        PurchasedCoupon coupon = buildActiveCoupon(4L, 20L, 100L, 200L);
        when(purchasedCouponRepository.findById(4L)).thenReturn(Optional.of(coupon));

        assertThatThrownBy(() -> orderService.createCouponRefundRequest(10L, 4L, "Хочу вернуть деньги"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("не принадлежит");
    }

    @Test
    @DisplayName("Возврат: дублирующая заявка — блокируется")
    void createCouponRefund_duplicateRequest_throws() {
        PurchasedCoupon coupon = buildActiveCoupon(5L, 10L, 100L, 200L);
        when(purchasedCouponRepository.findById(5L)).thenReturn(Optional.of(coupon));
        when(refundRequestRepository.existsByPurchasedCouponIdAndStatusIn(eq(5L), any())).thenReturn(true);

        assertThatThrownBy(() -> orderService.createCouponRefundRequest(10L, 5L, "Хочу вернуть деньги"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("уже существует");
    }

    @Test
    @DisplayName("Возврат: купон не найден — исключение")
    void createCouponRefund_notFound_throws() {
        when(purchasedCouponRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.createCouponRefundRequest(10L, 99L, "Хочу вернуть деньги"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("не найден");
    }

    // ═══ APPROVE REFUND ═══

    @Test
    @DisplayName("Одобрение: PENDING → APPROVED_PROCESSING, expectedRefundAt +5 рабочих дней")
    void approveRefund_pending_setsProcessingAndExpectedRefundAt() {
        PurchasedCoupon coupon = buildActiveCoupon(1L, 10L, 100L, 200L);
        coupon.setStatus(PurchasedCouponStatus.REFUND_PENDING);
        RefundRequest rr = RefundRequest.builder().id(1L).userId(10L).purchasedCoupon(coupon)
                .order(coupon.getOrder()).status(RefundRequest.RefundStatus.PENDING).reason("test").build();
        when(refundRequestRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(rr));
        when(refundRequestRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = orderService.approveRefundRequest(1L, "Одобряю");

        assertThat(result.getStatus()).isEqualTo(RefundRequest.RefundStatus.APPROVED_PROCESSING);
        assertThat(result.getExpectedRefundAt()).isNotNull();
        assertThat(result.getExpectedRefundAt()).isAfter(LocalDateTime.now().plusDays(4));
    }

    @Test
    @DisplayName("Одобрение: не PENDING — ошибка")
    void approveRefund_notPending_throws() {
        RefundRequest rr = RefundRequest.builder().id(1L).userId(10L)
                .status(RefundRequest.RefundStatus.REJECTED).reason("test").build();
        when(refundRequestRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(rr));

        assertThatThrownBy(() -> orderService.approveRefundRequest(1L, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PENDING");
    }

    // ═══ REJECT REFUND ═══

    @Test
    @DisplayName("Отклонение: PENDING → REJECTED, купон ACTIVE если не истёк")
    void rejectRefund_pending_reactivatesCoupon() {
        PurchasedCoupon coupon = buildActiveCoupon(1L, 10L, 100L, 200L);
        coupon.setStatus(PurchasedCouponStatus.REFUND_PENDING);
        RefundRequest rr = RefundRequest.builder().id(1L).userId(10L).purchasedCoupon(coupon)
                .order(coupon.getOrder()).status(RefundRequest.RefundStatus.PENDING).reason("test").build();
        when(refundRequestRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(rr));
        when(refundRequestRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(purchasedCouponRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        orderService.rejectRefundRequest(1L, "Отказ");

        assertThat(rr.getStatus()).isEqualTo(RefundRequest.RefundStatus.REJECTED);
        assertThat(coupon.getStatus()).isEqualTo(PurchasedCouponStatus.ACTIVE);
    }

    @Test
    @DisplayName("Отклонение: купон истёк во время рассмотрения → EXPIRED")
    void rejectRefund_couponExpiredDuringReview_marksExpired() {
        PurchasedCoupon coupon = buildActiveCoupon(1L, 10L, 100L, 200L);
        coupon.setStatus(PurchasedCouponStatus.REFUND_PENDING);
        coupon.setExpiresAt(LocalDateTime.now().minusHours(1));
        RefundRequest rr = RefundRequest.builder().id(1L).userId(10L).purchasedCoupon(coupon)
                .order(coupon.getOrder()).status(RefundRequest.RefundStatus.PENDING).reason("test").build();
        when(refundRequestRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(rr));
        when(refundRequestRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(purchasedCouponRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        orderService.rejectRefundRequest(1L, "Отказ");

        assertThat(coupon.getStatus()).isEqualTo(PurchasedCouponStatus.EXPIRED);
    }

    // ═══ COMPLETE REFUND ═══

    @Test
    @DisplayName("Завершение: APPROVED_PROCESSING → REFUNDED, купон REFUNDED")
    void completeRefund_processing_marksRefunded() {
        PurchasedCoupon coupon = buildActiveCoupon(1L, 10L, 100L, 200L);
        coupon.setStatus(PurchasedCouponStatus.REFUND_PENDING);
        RefundRequest rr = RefundRequest.builder().id(1L).userId(10L).purchasedCoupon(coupon)
                .order(coupon.getOrder()).status(RefundRequest.RefundStatus.APPROVED_PROCESSING).reason("test").build();
        when(refundRequestRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(rr));
        when(refundRequestRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(purchasedCouponRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = orderService.completeRefundRequest(1L, null);

        assertThat(result.getStatus()).isEqualTo(RefundRequest.RefundStatus.REFUNDED);
        assertThat(coupon.getStatus()).isEqualTo(PurchasedCouponStatus.REFUNDED);
        assertThat(result.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("Завершение: из PENDING — ошибка (недопустимый переход)")
    void completeRefund_pendingRequest_throws() {
        RefundRequest rr = RefundRequest.builder().id(1L).userId(10L)
                .status(RefundRequest.RefundStatus.PENDING).reason("test").build();
        when(refundRequestRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(rr));

        assertThatThrownBy(() -> orderService.completeRefundRequest(1L, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APPROVED_PROCESSING");
    }

    @Test
    @DisplayName("Завершение: из REJECTED — ошибка")
    void completeRefund_rejectedRequest_throws() {
        RefundRequest rr = RefundRequest.builder().id(1L).userId(10L)
                .status(RefundRequest.RefundStatus.REJECTED).reason("test").build();
        when(refundRequestRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(rr));

        assertThatThrownBy(() -> orderService.completeRefundRequest(1L, null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Отклонение: причина обязательна")
    void rejectRefund_blankReason_rejected() {
        assertThatThrownBy(() -> orderService.rejectRefundRequest(1L, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("причину");

        verify(refundRequestRepository, never()).findByIdForUpdate(any());
        verify(refundRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("Решение: отсутствующий запрос возвращает not found")
    void approveRefund_missingRequest_throwsNotFound() {
        when(refundRequestRepository.findByIdForUpdate(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.approveRefundRequest(404L, null))
                .isInstanceOf(uz.topdim.order.exception.ResourceNotFoundException.class)
                .hasMessageContaining("404");
    }
}
