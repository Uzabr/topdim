package uz.topdim.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.topdim.common.events.OrderCreatedEvent;
import uz.topdim.common.events.CouponPurchasedEvent;
import uz.topdim.order.entity.*;
import uz.topdim.order.repository.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final OrderRepository orderRepository;
    private final PurchasedCouponRepository purchasedCouponRepository;
    private final RedemptionRepository redemptionRepository;
    private final RefundRequestRepository refundRequestRepository;
    private final RabbitTemplate rabbitTemplate;

    // ==================== Cart ====================

    @Transactional(readOnly = true)
    public Cart getCartByUserId(Long userId) {
        return cartRepository.findByUserId(userId)
                .orElseGet(() -> {
                    Cart cart = Cart.builder().userId(userId).build();
                    return cartRepository.save(cart);
                });
    }

    @Transactional
    public Cart addToCart(Long userId, Long couponOfferId, Long couponOptionId,
                         String couponTitle, String optionTitle, BigDecimal unitPrice,
                         int quantity, boolean isGift, String giftName, String giftPhone) {
        Cart cart = getCartByUserId(userId);
        CartItem item = CartItem.builder()
                .cart(cart)
                .couponOfferId(couponOfferId)
                .couponOptionId(couponOptionId)
                .couponTitle(couponTitle)
                .optionTitle(optionTitle)
                .unitPrice(unitPrice)
                .quantity(quantity)
                .gift(isGift)
                .giftRecipientName(giftName)
                .giftRecipientPhone(giftPhone)
                .build();
        cart.getItems().add(item);
        return cartRepository.save(cart);
    }

    @Transactional
    public void removeFromCart(Long userId, Long cartItemId) {
        Cart cart = getCartByUserId(userId);
        cart.getItems().removeIf(item -> item.getId().equals(cartItemId));
        cartRepository.save(cart);
    }

    @Transactional
    public void clearCart(Long userId) {
        Cart cart = getCartByUserId(userId);
        cart.getItems().clear();
        cartRepository.save(cart);
    }

    // ==================== Checkout / Order ====================

    @Transactional
    public Order createOrder(Long userId, String userEmail, String userPhone) {
        Cart cart = getCartByUserId(userId);
        if (cart.getItems().isEmpty()) {
            throw new IllegalStateException("Корзина пуста");
        }

        BigDecimal totalAmount = cart.getItems().stream()
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Order order = Order.builder()
                .orderNumber(generateOrderNumber())
                .userId(userId)
                .userEmail(userEmail)
                .userPhone(userPhone)
                .totalAmount(totalAmount)
                .status(OrderStatus.PENDING)
                .build();

        List<OrderItem> orderItems = new ArrayList<>();
        for (CartItem cartItem : cart.getItems()) {
            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .couponOfferId(cartItem.getCouponOfferId())
                    .couponOptionId(cartItem.getCouponOptionId())
                    .couponTitle(cartItem.getCouponTitle())
                    .optionTitle(cartItem.getOptionTitle())
                    .unitPrice(cartItem.getUnitPrice())
                    .quantity(cartItem.getQuantity())
                    .gift(cartItem.isGift())
                    .giftRecipientName(cartItem.getGiftRecipientName())
                    .giftRecipientPhone(cartItem.getGiftRecipientPhone())
                    .build();
            orderItems.add(orderItem);
        }
        order.setItems(orderItems);
        order = orderRepository.save(order);

        // Clear cart
        cart.getItems().clear();
        cartRepository.save(cart);

        // Publish event for payment
        OrderCreatedEvent event = OrderCreatedEvent.builder()
                .orderId(order.getId())
                .userId(userId)
                .totalAmount(totalAmount)
                .currency("UZS")
                .createdAt(LocalDateTime.now())
                .build();
        rabbitTemplate.convertAndSend("order.exchange", "order.created", event);

        return order;
    }

    // ==================== After Payment ====================

    @Transactional
    public List<PurchasedCoupon> generatePurchasedCoupons(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Заказ не найден"));

        order.setStatus(OrderStatus.PAID);
        order.setPaidAt(LocalDateTime.now());
        orderRepository.save(order);

        List<PurchasedCoupon> coupons = new ArrayList<>();
        for (OrderItem item : order.getItems()) {
            for (int i = 0; i < item.getQuantity(); i++) {
                String couponCode = generateCouponCode();
                PurchasedCoupon coupon = PurchasedCoupon.builder()
                        .userId(order.getUserId())
                        .order(order)
                        .couponOfferId(item.getCouponOfferId())
                        .couponOptionId(item.getCouponOptionId())
                        .couponTitle(item.getCouponTitle())
                        .optionTitle(item.getOptionTitle())
                        .couponCode(couponCode)
                        .qrToken(UUID.randomUUID().toString())
                        .status(PurchasedCouponStatus.ACTIVE)
                        .gift(item.isGift())
                        .giftRecipientName(item.getGiftRecipientName())
                        .giftRecipientPhone(item.getGiftRecipientPhone())
                        .build();
                coupons.add(purchasedCouponRepository.save(coupon));

                // Publish event
                CouponPurchasedEvent event = CouponPurchasedEvent.builder()
                        .purchasedCouponId(coupon.getId())
                        .userId(order.getUserId())
                        .orderId(orderId)
                        .couponCode(couponCode)
                        .userEmail(order.getUserEmail())
                        .userPhone(order.getUserPhone())
                        .couponTitle(item.getCouponTitle())
                        .purchasedAt(LocalDateTime.now())
                        .build();
                rabbitTemplate.convertAndSend("coupon.exchange", "coupon.purchased", event);
            }
        }

        return coupons;
    }

    // ==================== User Orders ====================

    @Transactional(readOnly = true)
    public Page<Order> getUserOrders(Long userId, int page, int size) {
        return orderRepository.findByUserId(userId, PageRequest.of(page, size, Sort.by("createdAt").descending()));
    }

    @Transactional(readOnly = true)
    public List<PurchasedCoupon> getUserCoupons(Long userId) {
        return purchasedCouponRepository.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public List<PurchasedCoupon> getUserCouponsByStatus(Long userId, PurchasedCouponStatus status) {
        return purchasedCouponRepository.findByUserIdAndStatus(userId, status);
    }

    // ==================== Redemption ====================

    @Transactional
    public PurchasedCoupon redeemCoupon(String couponCode, Long merchantId, String staffName) {
        PurchasedCoupon coupon = purchasedCouponRepository.findByCouponCode(couponCode)
                .orElseThrow(() -> new IllegalArgumentException("Купон не найден"));

        if (coupon.getStatus() != PurchasedCouponStatus.ACTIVE) {
            throw new IllegalStateException("Купон не может быть использован. Статус: " + coupon.getStatus());
        }

        coupon.setStatus(PurchasedCouponStatus.USED);
        coupon.setUsedAt(LocalDateTime.now());
        purchasedCouponRepository.save(coupon);

        // Create redemption record
        Redemption redemption = Redemption.builder()
                .purchasedCoupon(coupon)
                .redemptionCode(UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .merchantId(merchantId != null ? merchantId : 0L)
                .redeemedByStaff(staffName)
                .redeemedAt(LocalDateTime.now())
                .build();
        redemptionRepository.save(redemption);

        return coupon;
    }

    // ==================== Refund Requests ====================

    @Transactional
    public RefundRequest createRefundRequest(Long userId, Long orderId, String reason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Заказ не найден"));

        if (!order.getUserId().equals(userId)) {
            throw new IllegalStateException("Заказ не принадлежит пользователю");
        }

        return refundRequestRepository.save(RefundRequest.builder()
                .order(order)
                .userId(userId)
                .reason(reason)
                .build());
    }

    @Transactional(readOnly = true)
    public List<RefundRequest> getUserRefundRequests(Long userId) {
        return refundRequestRepository.findByUserId(userId);
    }

    @Transactional
    public RefundRequest resolveRefundRequest(Long requestId, boolean approved, String adminComment) {
        RefundRequest request = refundRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Запрос не найден"));

        request.setStatus(approved ? RefundRequest.RefundStatus.APPROVED : RefundRequest.RefundStatus.REJECTED);
        request.setAdminComment(adminComment);
        request.setResolvedAt(LocalDateTime.now());
        return refundRequestRepository.save(request);
    }

    // ==================== Helpers ====================

    private String generateOrderNumber() {
        return "TD-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 1000);
    }

    private String generateCouponCode() {
        return "CP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
