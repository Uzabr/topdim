package uz.topdim.order.service;

import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.topdim.common.events.NotificationEvent;
import uz.topdim.order.dto.ComplaintResponse;
import uz.topdim.order.dto.CreateComplaintRequest;
import uz.topdim.order.dto.ResolveComplaintRequest;
import uz.topdim.order.entity.*;
import uz.topdim.order.exception.ResourceNotFoundException;
import uz.topdim.order.repository.ComplaintRepository;
import uz.topdim.order.repository.OrderRepository;
import uz.topdim.order.repository.PurchasedCouponRepository;

@Service
@RequiredArgsConstructor
public class ComplaintService {

    private static final String PENDING_COMPLAINT_CONSTRAINT = "uq_complaints_pending_coupon";
    private static final String DUPLICATE_PENDING_COMPLAINT_MESSAGE =
            "По этому купону уже есть открытое обращение";

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

            if (complaintRepository.existsByPurchasedCouponIdAndStatus(
                    purchasedCoupon.getId(), ComplaintStatus.PENDING)) {
                throw new IllegalStateException(DUPLICATE_PENDING_COMPLAINT_MESSAGE);
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

        Long complaintId;
        try {
            complaintId = complaintRepository.saveAndFlush(complaint).getId();
        } catch (DataIntegrityViolationException exception) {
            if (isPendingComplaintConstraintViolation(exception)) {
                throw new IllegalStateException(DUPLICATE_PENDING_COMPLAINT_MESSAGE, exception);
            }
            throw exception;
        }

        sendNotification(userId, "Обращение создано",
                "Ваше обращение «" + request.getSubject() + "» принято и находится на рассмотрении.",
                "INFO");

        return complaintId;
    }

    private boolean isPendingComplaintConstraintViolation(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException constraintViolation
                    && PENDING_COMPLAINT_CONSTRAINT.equals(constraintViolation.getConstraintName())) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
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
        String decision = request.getDecision() == null
                ? ""
                : request.getDecision().trim().toUpperCase(java.util.Locale.ROOT);
        if (!"RESOLVE".equals(decision) && !"REJECT".equals(decision)) {
            throw new IllegalArgumentException("Неизвестное решение: " + request.getDecision());
        }

        String resolution = request.getResolution() == null ? "" : request.getResolution().trim();
        if (resolution.isBlank()) {
            throw new IllegalArgumentException("Укажите ответ по обращению");
        }

        Complaint complaint = complaintRepository.findByIdForUpdate(complaintId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Обращение не найдено: " + complaintId));
        if (complaint.getStatus() != ComplaintStatus.PENDING) {
            throw new IllegalStateException("Обращение уже обработано");
        }

        if ("RESOLVE".equals(decision)) {
            complaint.setStatus(ComplaintStatus.RESOLVED);
        } else {
            complaint.setStatus(ComplaintStatus.REJECTED);
        }
        complaint.setResolution(resolution);
        complaintRepository.save(complaint);

        if ("RESOLVE".equals(decision)) {
            sendNotification(complaint.getUserId(), "Жалоба рассмотрена",
                    "Ваша жалоба (Тема: " + complaint.getSubject()
                            + ") была рассмотрена. Решение: " + resolution,
                    "SUCCESS");
        } else {
            sendNotification(complaint.getUserId(), "Жалоба отклонена",
                    "К сожалению, ваша жалоба (Тема: " + complaint.getSubject()
                            + ") была отклонена. Причина: " + resolution,
                    "INFO");
        }
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
