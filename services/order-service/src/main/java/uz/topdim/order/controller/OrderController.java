package uz.topdim.order.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.order.entity.*;
import uz.topdim.order.service.OrderService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * REST контроллер заказов.
 * Endpoints: cart, orders, my-coupons, redeem, refund.
 * Все endpoints требуют JWT (X-User-Id header).
 */
@RestController
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    // ==================== Cart ====================

    @GetMapping("/api/v1/cart")
    public ResponseEntity<ApiResponse<Cart>> getCart(@RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getCartByUserId(userId)));
    }

    @PostMapping("/api/v1/cart/items")
    public ResponseEntity<ApiResponse<Cart>> addToCart(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody Map<String, Object> request
    ) {
        Cart cart = orderService.addToCart(
                userId,
                Long.valueOf(request.get("couponOfferId").toString()),
                Long.valueOf(request.get("couponOptionId").toString()),
                (String) request.get("couponTitle"),
                (String) request.get("optionTitle"),
                new BigDecimal(request.get("unitPrice").toString()),
                Integer.parseInt(request.getOrDefault("quantity", "1").toString()),
                Boolean.parseBoolean(request.getOrDefault("isGift", "false").toString()),
                (String) request.get("giftRecipientName"),
                (String) request.get("giftRecipientPhone")
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Добавлено в корзину", cart));
    }

    @DeleteMapping("/api/v1/cart/items/{itemId}")
    public ResponseEntity<ApiResponse<Void>> removeFromCart(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long itemId
    ) {
        orderService.removeFromCart(userId, itemId);
        return ResponseEntity.ok(ApiResponse.success("Удалено из корзины", null));
    }

    // ==================== Orders ====================

    @PostMapping("/api/v1/orders")
    public ResponseEntity<ApiResponse<Order>> createOrder(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody Map<String, String> request
    ) {
        Order order = orderService.createOrder(userId, request.get("email"), request.get("phone"));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Заказ создан", order));
    }

    @GetMapping("/api/v1/orders")
    public ResponseEntity<ApiResponse<Page<Order>>> getUserOrders(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getUserOrders(userId, page, size)));
    }

    @GetMapping("/api/v1/orders/{id}")
    public ResponseEntity<ApiResponse<Order>> getOrder(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getUserOrders(0L, 0, 1).getContent().getFirst()));
    }

    // ==================== My Coupons ====================

    @GetMapping("/api/v1/orders/my-coupons")
    public ResponseEntity<ApiResponse<List<PurchasedCoupon>>> getMyCoupons(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(required = false) PurchasedCouponStatus status
    ) {
        List<PurchasedCoupon> coupons = status != null
                ? orderService.getUserCouponsByStatus(userId, status)
                : orderService.getUserCoupons(userId);
        return ResponseEntity.ok(ApiResponse.success(coupons));
    }

    // ==================== Redemption ====================

    @PostMapping("/api/v1/orders/redeem")
    public ResponseEntity<ApiResponse<PurchasedCoupon>> redeemCoupon(@RequestBody Map<String, String> request) {
        PurchasedCoupon coupon = orderService.redeemCoupon(
                request.get("couponCode"),
                request.get("merchantId") != null ? Long.valueOf(request.get("merchantId")) : null,
                request.get("staffName")
        );
        return ResponseEntity.ok(ApiResponse.success("Купон использован", coupon));
    }

    @GetMapping("/api/v1/orders/{orderId}/coupons")
    public ResponseEntity<ApiResponse<List<PurchasedCoupon>>> getOrderCoupons(@PathVariable Long orderId) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getUserCoupons(0L)));
    }

    // ==================== Refund Requests ====================

    @PostMapping("/api/v1/orders/{orderId}/refund")
    public ResponseEntity<ApiResponse<RefundRequest>> createRefund(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long orderId,
            @RequestBody Map<String, String> request
    ) {
        RefundRequest refund = orderService.createRefundRequest(userId, orderId, request.get("reason"));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Запрос на возврат создан", refund));
    }

    @GetMapping("/api/v1/orders/refunds")
    public ResponseEntity<ApiResponse<List<RefundRequest>>> getUserRefunds(
            @RequestHeader("X-User-Id") Long userId
    ) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getUserRefundRequests(userId)));
    }

    @PatchMapping("/api/v1/admin/refunds/{id}")
    public ResponseEntity<ApiResponse<RefundRequest>> resolveRefund(
            @PathVariable Long id,
            @RequestBody Map<String, String> request
    ) {
        boolean approved = "APPROVED".equals(request.get("status"));
        RefundRequest refund = orderService.resolveRefundRequest(id, approved, request.get("comment"));
        return ResponseEntity.ok(ApiResponse.success("Запрос обработан", refund));
    }
}
