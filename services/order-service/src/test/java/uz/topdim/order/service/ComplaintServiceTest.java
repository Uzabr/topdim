package uz.topdim.order.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import uz.topdim.common.events.NotificationEvent;
import uz.topdim.order.dto.CreateComplaintRequest;
import uz.topdim.order.entity.Complaint;
import uz.topdim.order.entity.ComplaintStatus;
import uz.topdim.order.entity.Order;
import uz.topdim.order.entity.PurchasedCoupon;
import uz.topdim.order.repository.ComplaintRepository;
import uz.topdim.order.repository.OrderRepository;
import uz.topdim.order.repository.PurchasedCouponRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplaintServiceTest {

    @Mock
    private ComplaintRepository complaintRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PurchasedCouponRepository purchasedCouponRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private ComplaintService complaintService;

    @Test
    @DisplayName("Первое обращение по купону создаётся в PENDING и публикует уведомление после flush")
    void createComplaint_firstComplaint_persistsAndNotifiesAfterFlush() {
        PurchasedCoupon coupon = purchasedCoupon(41L, 7L);
        CreateComplaintRequest request = requestForCoupon(41L);
        when(purchasedCouponRepository.findById(41L)).thenReturn(Optional.of(coupon));
        when(complaintRepository.existsByPurchasedCouponIdAndStatus(41L, ComplaintStatus.PENDING))
                .thenReturn(false);
        when(complaintRepository.saveAndFlush(any(Complaint.class))).thenAnswer(invocation -> {
            Complaint complaint = invocation.getArgument(0);
            complaint.setId(501L);
            return complaint;
        });

        Long result = complaintService.createComplaint(7L, request);

        assertThat(result).isEqualTo(501L);
        ArgumentCaptor<Complaint> complaintCaptor = ArgumentCaptor.forClass(Complaint.class);
        InOrder persistenceThenNotification = inOrder(complaintRepository, rabbitTemplate);
        persistenceThenNotification.verify(complaintRepository).saveAndFlush(complaintCaptor.capture());
        persistenceThenNotification.verify(rabbitTemplate).convertAndSend(
                eq("notification.exchange"),
                eq("notification.sent"),
                any(NotificationEvent.class));

        Complaint saved = complaintCaptor.getValue();
        assertThat(saved.getUserId()).isEqualTo(7L);
        assertThat(saved.getOrder()).isSameAs(coupon.getOrder());
        assertThat(saved.getPurchasedCoupon()).isSameAs(coupon);
        assertThat(saved.getStatus()).isEqualTo(ComplaintStatus.PENDING);
        assertThat(saved.getSubject()).isEqualTo("Проблема с купоном");
        assertThat(saved.getDescription()).isEqualTo("Купон не принимают");
    }

    @Test
    @DisplayName("Повторное PENDING обращение по тому же купону отклоняется")
    void createComplaint_existingPendingComplaint_rejected() {
        PurchasedCoupon coupon = purchasedCoupon(42L, 7L);
        when(purchasedCouponRepository.findById(42L)).thenReturn(Optional.of(coupon));
        when(complaintRepository.existsByPurchasedCouponIdAndStatus(42L, ComplaintStatus.PENDING))
                .thenReturn(true);

        assertThatThrownBy(() -> complaintService.createComplaint(7L, requestForCoupon(42L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("По этому купону уже есть открытое обращение");

        verify(complaintRepository, never()).saveAndFlush(any(Complaint.class));
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    @DisplayName("После закрытого обращения можно создать новое PENDING обращение по купону")
    void createComplaint_onlyResolvedComplaintExists_createsNewPendingComplaint() {
        PurchasedCoupon coupon = purchasedCoupon(43L, 7L);
        when(purchasedCouponRepository.findById(43L)).thenReturn(Optional.of(coupon));
        when(complaintRepository.existsByPurchasedCouponIdAndStatus(43L, ComplaintStatus.PENDING))
                .thenReturn(false);
        when(complaintRepository.saveAndFlush(any(Complaint.class))).thenAnswer(invocation -> {
            Complaint complaint = invocation.getArgument(0);
            complaint.setId(503L);
            return complaint;
        });

        Long result = complaintService.createComplaint(7L, requestForCoupon(43L));

        assertThat(result).isEqualTo(503L);
        verify(complaintRepository).existsByPurchasedCouponIdAndStatus(43L, ComplaintStatus.PENDING);
        verify(complaintRepository).saveAndFlush(any(Complaint.class));
    }

    @Test
    @DisplayName("Чужой купон нельзя использовать для создания обращения")
    void createComplaint_foreignCoupon_rejected() {
        when(purchasedCouponRepository.findById(44L))
                .thenReturn(Optional.of(purchasedCoupon(44L, 99L)));

        assertThatThrownBy(() -> complaintService.createComplaint(7L, requestForCoupon(44L)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Купон не принадлежит пользователю");

        verifyNoInteractions(complaintRepository, rabbitTemplate);
    }

    @Test
    @DisplayName("Обращение по отсутствующему купону отклоняется")
    void createComplaint_missingCoupon_rejected() {
        when(purchasedCouponRepository.findById(45L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> complaintService.createComplaint(7L, requestForCoupon(45L)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Купон не найден");

        verifyNoInteractions(complaintRepository, rabbitTemplate);
    }

    @Test
    @DisplayName("При конфликте уникальности во время flush уведомление не публикуется")
    void createComplaint_concurrentDuplicateAtFlush_doesNotPublishNotification() {
        PurchasedCoupon coupon = purchasedCoupon(46L, 7L);
        when(purchasedCouponRepository.findById(46L)).thenReturn(Optional.of(coupon));
        when(complaintRepository.existsByPurchasedCouponIdAndStatus(46L, ComplaintStatus.PENDING))
                .thenReturn(false);
        when(complaintRepository.saveAndFlush(any(Complaint.class)))
                .thenThrow(new DataIntegrityViolationException("uq_complaints_pending_coupon"));

        assertThatThrownBy(() -> complaintService.createComplaint(7L, requestForCoupon(46L)))
                .isInstanceOf(DataIntegrityViolationException.class);

        verify(rabbitTemplate, never()).convertAndSend(
                eq("notification.exchange"),
                eq("notification.sent"),
                any(NotificationEvent.class));
    }

    private PurchasedCoupon purchasedCoupon(Long couponId, Long userId) {
        Order order = Order.builder()
                .id(100L + couponId)
                .userId(userId)
                .build();
        return PurchasedCoupon.builder()
                .id(couponId)
                .userId(userId)
                .order(order)
                .build();
    }

    private CreateComplaintRequest requestForCoupon(Long couponId) {
        CreateComplaintRequest request = new CreateComplaintRequest();
        request.setPurchasedCouponId(couponId);
        request.setSubject("Проблема с купоном");
        request.setDescription("Купон не принимают");
        return request;
    }
}
