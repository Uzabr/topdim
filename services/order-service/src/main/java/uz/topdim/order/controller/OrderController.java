package uz.topdim.order.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.order.dto.*;
import uz.topdim.order.entity.*;
import uz.topdim.order.service.OrderService;

import java.util.List;

/**
 * REST контроллер заказов.
 * Endpoints: cart, orders, my-coupons, redeem, refund.
 * Все endpoints требуют JWT (X-User-Id header от Gateway).
 */
@RestController
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    // ==================== Cart ====================

    /** Получить корзину пользователя. */
    @GetMapping("/api/v1/cart")
    public ResponseEntity<ApiResponse<CartResponse>> getCart(@RequestHeader("X-User-Id") Long userId) {
        Cart cart = orderService.getCartByUserId(userId);
        return ResponseEntity.ok(ApiResponse.success(orderService.mapToCartResponse(cart)));
    }

    /** Добавить товар в корзину. */
    @PostMapping("/api/v1/cart/items")
    public ResponseEntity<ApiResponse<CartResponse>> addToCart(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody AddToCartRequest request
    ) {
        Cart cart = orderService.addToCart(
                userId,
                request.getCouponOfferId(),
                request.getCouponOptionId(),
                request.getCouponTitle(),
                request.getOptionTitle(),
                request.getUnitPrice(),
                request.getQuantity(),
                request.isGift(),
                request.getGiftRecipientName(),
                request.getGiftRecipientPhone()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Добавлено в корзину", orderService.mapToCartResponse(cart)));
    }

    /** Удалить товар из корзины. */
    @DeleteMapping("/api/v1/cart/items/{itemId}")
    public ResponseEntity<ApiResponse<Void>> removeFromCart(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long itemId
    ) {
        orderService.removeFromCart(userId, itemId);
        return ResponseEntity.ok(ApiResponse.success("Удалено из корзины", null));
    }

    /** Обновить количество товара в корзине. */
    @PatchMapping("/api/v1/cart/items/{itemId}")
    public ResponseEntity<ApiResponse<CartResponse>> updateCartItemQuantity(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long itemId,
            @Valid @RequestBody UpdateCartItemQuantityRequest request
    ) {
        Cart cart = orderService.updateCartItemQuantity(userId, itemId, request.getQuantity());
        return ResponseEntity.ok(ApiResponse.success("Количество обновлено", orderService.mapToCartResponse(cart)));
    }

    /** Очистить корзину полностью. */
    @DeleteMapping("/api/v1/cart")
    public ResponseEntity<ApiResponse<Void>> clearCart(@RequestHeader("X-User-Id") Long userId) {
        orderService.clearCart(userId);
        return ResponseEntity.ok(ApiResponse.success("Корзина очищена", null));
    }

    // ==================== Orders ====================

    /** Оформить заказ (checkout). */
    @PostMapping("/api/v1/orders")
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody CreateOrderRequest request
    ) {
        Order order = orderService.createOrder(userId, request.getEmail(), request.getPhone());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Заказ создан", orderService.mapToOrderResponse(order)));
    }

    /** Список заказов пользователя (с пагинацией). */
    @GetMapping("/api/v1/orders")
    public ResponseEntity<ApiResponse<Page<OrderResponse>>> getUserOrders(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                orderService.getUserOrders(userId, page, size)));
    }

    /** Получить конкретный заказ по ID (с проверкой владельца). */
    @GetMapping("/api/v1/orders/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrder(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                orderService.getOrderResponseById(id, userId)));
    }

    // ==================== My Coupons ====================

    /** Все купленные купоны пользователя (фильтр по статусу). */
    @GetMapping("/api/v1/orders/my-coupons")
    public ResponseEntity<ApiResponse<List<PurchasedCouponResponse>>> getMyCoupons(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(required = false) PurchasedCouponStatus status
    ) {
        List<PurchasedCoupon> coupons = status != null
                ? orderService.getUserCouponsByStatus(userId, status)
                : orderService.getUserCoupons(userId);
        List<PurchasedCouponResponse> response = coupons.stream()
                .map(orderService::mapToCouponResponse)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** Купоны конкретного заказа (с проверкой владельца). */
    @GetMapping("/api/v1/orders/{orderId}/coupons")
    public ResponseEntity<ApiResponse<List<PurchasedCouponResponse>>> getOrderCoupons(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long orderId
    ) {
        List<PurchasedCouponResponse> response = orderService.getOrderCoupons(orderId, userId).stream()
                .map(orderService::mapToCouponResponse)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ==================== Redemption ====================

    /**
     * Legacy partner-only redemption endpoint.
     * <p>
     * This endpoint is kept for backward compatibility. The main MVP flow uses:
     * - {@code POST /api/v1/partner/redemptions} (PIN-code)
     * - {@code POST /api/v1/partner/redemptions/qr} (QR token)
     * <p>
     * Requires X-Merchant-Id header for partner context.
     * Admins must NOT redeem customer coupons as merchants in MVP.
     * If admin support needs redemption in the future, a separate audited
     * support-mode plan is required — not this endpoint.
     */
    @PreAuthorize("hasAnyRole('PARTNER')")
    @PostMapping("/api/v1/orders/redeem")
    public ResponseEntity<ApiResponse<RedeemCouponResponse>> redeemCoupon(
            @RequestHeader("X-Merchant-Id") Long merchantId,
            @Valid @RequestBody RedeemCouponRequest request
    ) {
        PurchasedCoupon coupon = orderService.redeemCoupon(
                request.getCouponCode(),
                merchantId,
                request.getStaffName()
        );
        return ResponseEntity.ok(ApiResponse.success("Купон использован", orderService.mapToRedeemResponse(coupon)));
    }

    // ==================== Per-Coupon Refund (new) ====================

    /** Создать заявку на возврат per-coupon. */
    @PostMapping("/api/v1/refunds")
    public ResponseEntity<ApiResponse<RefundRequestResponse>> createCouponRefund(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody CreateCouponRefundRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "Заявка на возврат создана",
                orderService.createCouponRefundRequest(userId, request.getPurchasedCouponId(), request.getReason())
        ));
    }

    /** Мои заявки на возврат (per-coupon). */
    @GetMapping("/api/v1/refunds/my")
    public ResponseEntity<ApiResponse<List<RefundRequestResponse>>> getMyRefunds(
            @RequestHeader("X-User-Id") Long userId
    ) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getUserCouponRefundRequests(userId)));
    }

    // ==================== Legacy Refund (order-level) ====================

    /** Создать запрос на возврат (legacy). */
    @PostMapping("/api/v1/orders/{orderId}/refund")
    public ResponseEntity<ApiResponse<RefundRequest>> createRefund(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long orderId,
            @Valid @RequestBody RefundRequestDto request
    ) {
        RefundRequest refund = orderService.createRefundRequest(userId, orderId, request.getReason());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Запрос на возврат создан", refund));
    }

    /** Мои запросы на возврат (legacy). */
    @GetMapping("/api/v1/orders/refunds")
    public ResponseEntity<ApiResponse<List<RefundRequest>>> getUserRefunds(
            @RequestHeader("X-User-Id") Long userId
    ) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getUserRefundRequests(userId)));
    }

    // ==================== Admin Refunds ====================

    /** Все заказы с пагинацией и фильтром (Admin). */
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @GetMapping("/api/v1/admin/orders")
    public ResponseEntity<ApiResponse<Page<AdminOrderResponse>>> getAllOrders(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getAllOrders(status, page, size)));
    }

    /** Детали заказа без проверки владельца (Admin). */
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @GetMapping("/api/v1/admin/orders/{id}")
    public ResponseEntity<ApiResponse<AdminOrderResponse>> getOrderAdmin(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getOrderByIdAdmin(id)));
    }

    /** Список заявок на возврат с фильтром по статусу (Admin). */
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @GetMapping("/api/v1/admin/refunds")
    public ResponseEntity<ApiResponse<Page<RefundRequestResponse>>> getAdminRefunds(
            @RequestParam(required = false) RefundRequest.RefundStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getAdminRefundRequests(status, page, size)));
    }

    /** Одобрить возврат (Admin). */
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @PatchMapping("/api/v1/admin/refunds/{id}/approve")
    public ResponseEntity<ApiResponse<RefundRequestResponse>> approveRefund(
            @PathVariable Long id,
            @Valid @RequestBody RefundDecisionRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Возврат одобрен",
                orderService.approveRefundRequest(id, request.getAdminComment())
        ));
    }

    /** Отклонить возврат (Admin). */
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @PatchMapping("/api/v1/admin/refunds/{id}/reject")
    public ResponseEntity<ApiResponse<RefundRequestResponse>> rejectRefund(
            @PathVariable Long id,
            @Valid @RequestBody RefundDecisionRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Возврат отклонён",
                orderService.rejectRefundRequest(id, request.getAdminComment())
        ));
    }

    /** Завершить возврат (Admin). */
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @PatchMapping("/api/v1/admin/refunds/{id}/complete")
    public ResponseEntity<ApiResponse<RefundRequestResponse>> completeRefund(
            @PathVariable Long id,
            @Valid @RequestBody RefundDecisionRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Возврат завершён",
                orderService.completeRefundRequest(id, request.getAdminComment())
        ));
    }

    /**
     * Admin support: lookup purchased coupon by coupon code.
     * Does NOT expose qrToken for security.
     * Read-only — no redemption or modification.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @GetMapping("/api/v1/admin/purchased-coupons/lookup")
    public ResponseEntity<ApiResponse<AdminPurchasedCouponLookupResponse>> lookupPurchasedCoupon(
            @RequestParam String couponCode
    ) {
        try {
            AdminPurchasedCouponLookupResponse response = orderService.adminLookupByCouponCode(couponCode);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }
}
