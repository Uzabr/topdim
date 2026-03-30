package uz.topdim.order.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.order.dto.*;
import uz.topdim.order.entity.*;
import uz.topdim.order.service.OrderService;

import java.util.List;
import java.util.Map;

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
    public ResponseEntity<ApiResponse<Cart>> getCart(@RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getCartByUserId(userId)));
    }

    /** Добавить товар в корзину. */
    @PostMapping("/api/v1/cart/items")
    public ResponseEntity<ApiResponse<Cart>> addToCart(
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
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Добавлено в корзину", cart));
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

    /** Очистить корзину полностью. */
    @DeleteMapping("/api/v1/cart")
    public ResponseEntity<ApiResponse<Void>> clearCart(@RequestHeader("X-User-Id") Long userId) {
        orderService.clearCart(userId);
        return ResponseEntity.ok(ApiResponse.success("Корзина очищена", null));
    }

    // ==================== Orders ====================

    /** Оформить заказ (checkout). */
    @PostMapping("/api/v1/orders")
    public ResponseEntity<ApiResponse<Order>> createOrder(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody CreateOrderRequest request
    ) {
        Order order = orderService.createOrder(userId, request.getEmail(), request.getPhone());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Заказ создан", order));
    }

    /** Список заказов пользователя (с пагинацией). */
    @GetMapping("/api/v1/orders")
    public ResponseEntity<ApiResponse<Page<Order>>> getUserOrders(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getUserOrders(userId, page, size)));
    }

    /** Получить конкретный заказ по ID (с проверкой владельца). */
    @GetMapping("/api/v1/orders/{id}")
    public ResponseEntity<ApiResponse<Order>> getOrder(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getOrderById(id, userId)));
    }

    // ==================== My Coupons ====================

    /** Все купленные купоны пользователя (фильтр по статусу). */
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

    /** Купоны конкретного заказа (с проверкой владельца). */
    @GetMapping("/api/v1/orders/{orderId}/coupons")
    public ResponseEntity<ApiResponse<List<PurchasedCoupon>>> getOrderCoupons(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long orderId
    ) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getOrderCoupons(orderId, userId)));
    }

    // ==================== Redemption ====================

    /** Погашение купона (QR / код). */
    @PostMapping("/api/v1/orders/redeem")
    public ResponseEntity<ApiResponse<PurchasedCoupon>> redeemCoupon(
            @Valid @RequestBody RedeemCouponRequest request
    ) {
        PurchasedCoupon coupon = orderService.redeemCoupon(
                request.getCouponCode(),
                request.getMerchantId(),
                request.getStaffName()
        );
        return ResponseEntity.ok(ApiResponse.success("Купон использован", coupon));
    }

    // ==================== Refund Requests ====================

    /** Создать запрос на возврат. */
    @PostMapping("/api/v1/orders/{orderId}/refund")
    public ResponseEntity<ApiResponse<RefundRequest>> createRefund(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long orderId,
            @Valid @RequestBody RefundRequestDto request
    ) {
        RefundRequest refund = orderService.createRefundRequest(userId, orderId, request.getReason());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Запрос на возврат создан", refund));
    }

    /** Мои запросы на возврат. */
    @GetMapping("/api/v1/orders/refunds")
    public ResponseEntity<ApiResponse<List<RefundRequest>>> getUserRefunds(
            @RequestHeader("X-User-Id") Long userId
    ) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getUserRefundRequests(userId)));
    }

    // ==================== Admin ====================

    /** Все заказы с пагинацией и фильтром (Admin). */
    @GetMapping("/api/v1/admin/orders")
    public ResponseEntity<ApiResponse<Page<Order>>> getAllOrders(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getAllOrders(status, page, size)));
    }

    /** Детали заказа без проверки владельца (Admin). */
    @GetMapping("/api/v1/admin/orders/{id}")
    public ResponseEntity<ApiResponse<Order>> getOrderAdmin(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getOrderByIdAdmin(id)));
    }

    /** Решение по возврату (Admin). */
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
