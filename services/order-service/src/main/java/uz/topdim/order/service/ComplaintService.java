package uz.topdim.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.topdim.common.events.NotificationEvent;
import uz.topdim.order.dto.ComplaintResponse;
import uz.topdim.order.dto.CreateComplaintRequest;
import uz.topdim.order.dto.ResolveComplaintRequest;
import uz.topdim.order.entity.*;
import uz.topdim.order.repository.ComplaintRepository;
import uz.topdim.order.repository.OrderRepository;
import uz.topdim.order.repository.PurchasedCouponRepository;

@Service
@RequiredArgsConstructor
public class ComplaintService {

    private final ComplaintRepository complaintRepository;
    private final OrderRepository orderRepository;
    private final PurchasedCouponRepository purchasedCouponRepository;
    private final RabbitTemplate rabbitTemplate;

    @Transactional
    public Long createComplaint(Long userId, CreateComplaintRequest request) {
        Order order;
        PurchasedCoupon purchasedCoupon = null;

        if (request.getPurchasedCouponId() != null) {
            // Per-coupon complaint flow
            purchasedCoupon = purchasedCouponRepository.findById(request.getPurchasedCouponId())
                    .orElseThrow(() -> new RuntimeException("Купон не найден"));

            if (!purchasedCoupon.getUserId().equals(userId)) {
                throw new RuntimeException("Купон не принадлежит пользователю");
            }

            order = purchasedCoupon.getOrder();
            if (order == null) {
                throw new RuntimeException("Заказ для купона не найден");
            }
        } else if (request.getOrderId() != null) {
            // Legacy order-level complaint flow
            order = orderRepository.findById(request.getOrderId())
                    .orElseThrow(() -> new RuntimeException("Заказ не найден"));

            if (!order.getUserId().equals(userId)) {
                throw new RuntimeException("Нет доступа к этому заказу");
            }
        } else {
            throw new RuntimeException("Укажите purchasedCouponId или orderId");
        }

        Complaint complaint = Complaint.builder()
                .userId(userId)
                .order(order)
                .purchasedCoupon(purchasedCoupon)
                .subject(request.getSubject())
                .description(request.getDescription())
                .status(ComplaintStatus.PENDING)
                .build();

        Long complaintId = complaintRepository.save(complaint).getId();

        sendNotification(userId, "Обращение создано",
                "Ваше обращение «" + request.getSubject() + "» принято и находится на рассмотрении.",
                "INFO");

        return complaintId;
    }

    @Transactional(readOnly = true)
    public Page<ComplaintResponse> getMyComplaints(Long userId, Pageable pageable) {
        return complaintRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public Page<ComplaintResponse> getPendingComplaints(Pageable pageable) {
        return complaintRepository.findByStatusOrderByCreatedAtDesc(ComplaintStatus.PENDING, pageable)
                .map(this::mapToResponse);
    }

    @Transactional
    public void resolveComplaint(Long modId, Long complaintId, ResolveComplaintRequest request) {
        Complaint complaint = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new RuntimeException("Жалоба не найдена"));

        if ("RESOLVE".equalsIgnoreCase(request.getDecision())) {
            complaint.setStatus(ComplaintStatus.RESOLVED);
            sendNotification(complaint.getUserId(), "Жалоба рассмотрена", 
                    "Ваша жалоба (Тема: " + complaint.getSubject() + ") была рассмотрена. Решение: " + request.getResolution(), "SUCCESS");
        } else if ("REJECT".equalsIgnoreCase(request.getDecision())) {
            complaint.setStatus(ComplaintStatus.REJECTED);
            sendNotification(complaint.getUserId(), "Жалоба отклонена", 
                    "К сожалению, ваша жалоба (Тема: " + complaint.getSubject() + ") была отклонена. Причина: " + request.getResolution(), "INFO");
        } else {
            throw new IllegalArgumentException("Unknown decision: " + request.getDecision());
        }

        complaint.setResolution(request.getResolution());
        complaintRepository.save(complaint);
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

    private ComplaintResponse mapToResponse(Complaint c) {
        ComplaintResponse.ComplaintResponseBuilder builder = ComplaintResponse.builder()
                .id(c.getId())
                .userId(c.getUserId())
                .orderId(c.getOrder().getId())
                .subject(c.getSubject())
                .description(c.getDescription())
                .status(c.getStatus())
                .resolution(c.getResolution())
                .createdAt(c.getCreatedAt());

        if (c.getPurchasedCoupon() != null) {
            PurchasedCoupon pc = c.getPurchasedCoupon();
            builder.purchasedCouponId(pc.getId())
                    .couponTitle(pc.getCouponTitle())
                    .optionTitle(pc.getOptionTitle())
                    .couponCode(pc.getCouponCode())
                    .merchantName(pc.getMerchantName());
        }

        return builder.build();
    }
}
