package uz.topdim.order.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.topdim.common.events.OrderCreatedEvent;
import uz.topdim.common.events.CouponPurchasedEvent;
import uz.topdim.common.events.CouponRedeemedEvent;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.order.client.CouponClient;
import uz.topdim.order.client.CouponPurchaseSnapshot;
import uz.topdim.order.client.RegisterSaleRequest;
import uz.topdim.order.dto.AdminPurchasedCouponLookupResponse;
import uz.topdim.order.dto.CartResponse;
import uz.topdim.order.dto.OrderResponse;
import uz.topdim.order.dto.PurchasedCouponResponse;
import uz.topdim.order.dto.RedeemCouponResponse;
import uz.topdim.order.dto.RefundRequestResponse;
import uz.topdim.order.dto.ReviewEligibilityResponse;
import uz.topdim.order.entity.*;
import uz.topdim.order.repository.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Сервис управления заказами.
 * Корзина → Checkout → Заказ → Покупка купонов → Погашение.
 * Публикует события: OrderCreated, CouponPurchased.
 */
@Slf4j
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
    private final CouponClient couponClient;

    // ==================== Cart ====================

    /**
     * Получает корзину пользователя.
     * Если корзина не существует — создаёт пустую.
     *
     * @param userId ID пользователя из JWT
     * @return корзина с товарами
     */
    @Transactional
    public Cart getCartByUserId(Long userId) {
        return cartRepository.findByUserId(userId)
                .orElseGet(() -> {
                    Cart cart = Cart.builder().userId(userId).build();
                    return cartRepository.save(cart);
                });
    }

    /**
     * Добавляет товар в корзину.
     * Если товар уже есть — увеличивает количество.
     *
     * @param userId ID пользователя
     * @param couponOfferId ID купона
     * @param couponOptionId ID опции купона
     * @param quantity количество
     * @param gift true если подарочный купон
     * @return обновлённая корзина
     */
    @Transactional
    public Cart addToCart(Long userId, Long couponOfferId, Long couponOptionId,
                         String couponTitle, String optionTitle, BigDecimal unitPrice,
                         int quantity, boolean isGift, String giftName, String giftPhone) {
        CouponPurchaseSnapshot snapshot = loadPurchaseSnapshot(couponOfferId, couponOptionId);

        // Fail fast: status + expiry before touching cart
        assertPurchasableStatus(snapshot);

        Cart cart = getCartByUserId(userId);
        CartItem existing = cart.getItems().stream()
                .filter(item -> item.getCouponOfferId().equals(couponOfferId)
                        && item.getCouponOptionId().equals(couponOptionId))
                .findFirst()
                .orElse(null);

        int requestedQuantity = quantity + (existing != null ? existing.getQuantity() : 0);
        assertQuantityAvailable(snapshot, requestedQuantity);

        if (existing != null) {
            existing.setCouponTitle(snapshot.getCouponTitle());
            existing.setOptionTitle(snapshot.getOptionTitle());
            existing.setUnitPrice(snapshot.getCouponPrice());
            existing.setQuantity(requestedQuantity);
            existing.setGift(isGift);
            existing.setGiftRecipientName(giftName);
            existing.setGiftRecipientPhone(giftPhone);
            return cartRepository.save(cart);
        }

        CartItem item = CartItem.builder()
                .cart(cart)
                .couponOfferId(snapshot.getCouponOfferId())
                .couponOptionId(snapshot.getCouponOptionId())
                .couponTitle(snapshot.getCouponTitle())
                .optionTitle(snapshot.getOptionTitle())
                .unitPrice(snapshot.getCouponPrice())
                .quantity(quantity)
                .gift(isGift)
                .giftRecipientName(giftName)
                .giftRecipientPhone(giftPhone)
                .build();
        cart.getItems().add(item);
        return cartRepository.save(cart);
    }

    /**
     * Удаляет товар из корзины.
     *
     * @param userId ID пользователя
     * @param cartItemId ID элемента корзины
     */
    @Transactional
    public void removeFromCart(Long userId, Long cartItemId) {
        Cart cart = getCartByUserId(userId);
        cart.getItems().removeIf(item -> item.getId().equals(cartItemId));
        cartRepository.save(cart);
    }

    /**
     * Обновляет количество товара в корзине.
     * Перезагружает purchase snapshot для ревалидации доступности.
     *
     * @param userId ID пользователя
     * @param itemId ID элемента корзины
     * @param quantity новое количество (≥ 1)
     * @return обновлённая корзина
     * @throws IllegalArgumentException если item не найден в корзине пользователя
     * @throws IllegalStateException если купон недоступен или количество превышает остаток
     */
    @Transactional
    public Cart updateCartItemQuantity(Long userId, Long itemId, int quantity) {
        Cart cart = getCartByUserId(userId);
        CartItem item = cart.getItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Элемент корзины не найден"));

        CouponPurchaseSnapshot snapshot = loadPurchaseSnapshot(
                item.getCouponOfferId(), item.getCouponOptionId());
        assertPurchasableStatus(snapshot);
        assertQuantityAvailable(snapshot, quantity);

        item.setQuantity(quantity);
        // Обновляем канонические данные из snapshot
        item.setCouponTitle(snapshot.getCouponTitle());
        item.setOptionTitle(snapshot.getOptionTitle());
        item.setUnitPrice(snapshot.getCouponPrice());

        return cartRepository.save(cart);
    }

    /**
     * Очищает корзину пользователя (удаляет все товары).
     *
     * @param userId ID пользователя
     */
    @Transactional
    public void clearCart(Long userId) {
        Cart cart = getCartByUserId(userId);
        cart.getItems().clear();
        cartRepository.save(cart);
    }

    // ==================== Checkout / Order ====================

    /**
     * Оформление заказа (checkout).
     * Создаёт Order из корзины, публикует OrderCreatedEvent в RabbitMQ.
     * После создания корзина очищается.
     *
     * @param userId ID пользователя
     * @param userEmail email для уведомлений
     * @param userPhone телефон для SMS
     * @return созданный заказ
     * @throws IllegalStateException если корзина пуста
     */
    @Transactional
    public Order createOrder(Long userId, String userEmail, String userPhone) {
        Cart cart = getCartByUserId(userId);
        if (cart.getItems().isEmpty()) {
            throw new IllegalStateException("Корзина пуста");
        }

        // Revalidate every cart line against canonical coupon data
        List<OrderItem> orderItems = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (CartItem cartItem : cart.getItems()) {
            CouponPurchaseSnapshot snapshot = loadPurchaseSnapshot(
                    cartItem.getCouponOfferId(),
                    cartItem.getCouponOptionId()
            );
            assertPurchasableStatus(snapshot);
            assertQuantityAvailable(snapshot, cartItem.getQuantity());

            BigDecimal lineTotal = snapshot.getCouponPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity()));
            totalAmount = totalAmount.add(lineTotal);

            OrderItem orderItem = OrderItem.builder()
                    .couponOfferId(snapshot.getCouponOfferId())
                    .couponOptionId(snapshot.getCouponOptionId())
                    .couponTitle(snapshot.getCouponTitle())
                    .optionTitle(snapshot.getOptionTitle())
                    .unitPrice(snapshot.getCouponPrice())
                    .quantity(cartItem.getQuantity())
                    .merchantId(snapshot.getMerchantId())
                    .merchantName(snapshot.getMerchantName())
                    .merchantAddress(snapshot.getMerchantAddress())
                    .merchantPhone(snapshot.getMerchantPhone())
                    .merchantWorkingHours(snapshot.getMerchantWorkingHours())
                    .expiresAt(snapshot.getUseUntil())
                    .gift(cartItem.isGift())
                    .giftRecipientName(cartItem.getGiftRecipientName())
                    .giftRecipientPhone(cartItem.getGiftRecipientPhone())
                    .build();
            orderItems.add(orderItem);
        }

        Order order = Order.builder()
                .orderNumber(generateOrderNumber())
                .userId(userId)
                .userEmail(userEmail)
                .userPhone(userPhone)
                .totalAmount(totalAmount)
                .status(OrderStatus.PENDING)
                .build();

        for (OrderItem orderItem : orderItems) {
            orderItem.setOrder(order);
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

    /**
     * Генерирует купленные купоны после оплаты.
     * Каждый купон получает уникальный код и QR токен.
     * Публикует CouponPurchasedEvent для notification-service.
     *
     * @param orderId ID оплаченного заказа
     * @return список PurchasedCoupon с кодами
     */
    @Transactional
    public List<PurchasedCoupon> generatePurchasedCoupons(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Заказ не найден"));

        // Idempotency guard: return existing coupons without creating duplicates
        List<PurchasedCoupon> existingCoupons = purchasedCouponRepository.findByOrderId(orderId);
        if (!existingCoupons.isEmpty()) {
            return existingCoupons;
        }

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
                        .merchantId(item.getMerchantId())
                        .merchantName(item.getMerchantName())
                        .merchantAddress(item.getMerchantAddress())
                        .merchantPhone(item.getMerchantPhone())
                        .merchantWorkingHours(item.getMerchantWorkingHours())
                        .expiresAt(item.getExpiresAt())
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

        // Register sales in coupon-service (idempotent)
        for (OrderItem item : order.getItems()) {
            try {
                couponClient.registerSale(
                        item.getCouponOfferId(),
                        item.getCouponOptionId(),
                        RegisterSaleRequest.builder()
                                .orderId(orderId)
                                .quantity(item.getQuantity())
                                .amount(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                                .build()
                );
            } catch (Exception e) {
                // Log but don't fail — coupon generation already succeeded
                log.warn("Не удалось зарегистрировать продажу в coupon-service для orderId={}, couponId={}: {}",
                        orderId, item.getCouponOfferId(), e.getMessage());
            }
        }

        return coupons;
    }

    // ==================== User Orders ====================

    /**
     * Получает заказы пользователя с пагинацией.
     *
     * @param userId ID пользователя
     * @param page номер страницы
     * @param size размер страницы
     * @return страница заказов
     */
    @Transactional(readOnly = true)
    public Page<Order> getUserOrders(Long userId, int page, int size) {
        return orderRepository.findByUserId(userId, PageRequest.of(page, size, Sort.by("createdAt").descending()));
    }

    // ==================== Admin Orders ====================

    /**
     * Получает все заказы с пагинацией (Admin).
     * Опциональный фильтр по статусу.
     *
     * @param status фильтр по статусу (null = все)
     * @param page номер страницы
     * @param size размер страницы
     * @return страница заказов
     */
    @Transactional(readOnly = true)
    public Page<Order> getAllOrders(OrderStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        if (status != null) {
            return orderRepository.findByStatus(status, pageable);
        }
        return orderRepository.findAll(pageable);
    }

    /**
     * Получает заказ по ID без проверки владельца (Admin).
     */
    @Transactional(readOnly = true)
    public Order getOrderByIdAdmin(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Заказ не найден"));
    }

    /**
     * Получает конкретный заказ по ID с проверкой владельца.
     *
     * @param orderId ID заказа
     * @param userId ID пользователя (проверка владельца)
     * @return заказ
     * @throws IllegalArgumentException если заказ не найден
     * @throws IllegalStateException если заказ не принадлежит пользователю
     */
    @Transactional(readOnly = true)
    public Order getOrderById(Long orderId, Long userId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Заказ не найден"));
        if (!order.getUserId().equals(userId)) {
            throw new IllegalStateException("Заказ не принадлежит пользователю");
        }
        return order;
    }

    /**
     * Получает купоны конкретного заказа с проверкой владельца.
     *
     * @param orderId ID заказа
     * @param userId ID пользователя
     * @return список купленных купонов заказа
     */
    @Transactional(readOnly = true)
    public List<PurchasedCoupon> getOrderCoupons(Long orderId, Long userId) {
        Order order = getOrderById(orderId, userId);
        return purchasedCouponRepository.findByOrderId(orderId);
    }

    /**
     * Получает все купленные купоны пользователя.
     *
     * @param userId ID пользователя
     * @return список купонов (всех статусов)
     */
    @Transactional
    public List<PurchasedCoupon> getUserCoupons(Long userId) {
        expireOverduePurchasedCoupons();
        return purchasedCouponRepository.findByUserId(userId);
    }

    /**
     * Получает все купленные купоны пользователя.
     *
     * @param userId ID пользователя
     * @return список купонов (всех статусов)
     */
    @Transactional
    public List<PurchasedCoupon> getUserCouponsByStatus(Long userId, PurchasedCouponStatus status) {
        expireOverduePurchasedCoupons();
        return purchasedCouponRepository.findByUserIdAndStatus(userId, status);
    }

    // ==================== Redemption ====================

    /**
     * Погашение купона по PIN-коду (legacy — без access context).
     */
    @Transactional
    public PurchasedCoupon redeemCoupon(String couponCode, Long merchantId, String staffName) {
        return redeemCoupon(couponCode, merchantId, staffName, null, null, "PIN");
    }

    /**
     * Погашение купона по PIN-коду с полным access context.
     */
    @Transactional
    public PurchasedCoupon redeemCoupon(String couponCode, Long merchantId, String staffName,
                                         Long merchantLocationId, Long staffId, String redeemMethod) {
        expireOverduePurchasedCoupons();
        PurchasedCoupon coupon = purchasedCouponRepository.findByCouponCode(couponCode)
                .orElseThrow(() -> new IllegalArgumentException("Купон не найден"));

        return processRedemption(coupon, merchantId, staffName, merchantLocationId, staffId, redeemMethod);
    }

    /**
     * Погашение купона по QR-токену (legacy — без access context).
     */
    @Transactional
    public PurchasedCoupon redeemByQrToken(String qrToken, Long merchantId, String staffName) {
        return redeemByQrToken(qrToken, merchantId, staffName, null, null);
    }

    /**
     * Погашение купона по QR-токену с полным access context.
     */
    @Transactional
    public PurchasedCoupon redeemByQrToken(String qrToken, Long merchantId, String staffName,
                                             Long merchantLocationId, Long staffId) {
        expireOverduePurchasedCoupons();
        PurchasedCoupon coupon = purchasedCouponRepository.findByQrToken(qrToken)
                .orElseThrow(() -> new IllegalArgumentException("Купон по QR-токену не найден"));

        return processRedemption(coupon, merchantId, staffName, merchantLocationId, staffId, "QR");
    }

    /**
     * Общая логика погашения — валидация статуса, expiry, merchant, создание Redemption.
     * Сохраняет merchantLocationId, staffId и redeemMethod из access context.
     */
    private PurchasedCoupon processRedemption(PurchasedCoupon coupon, Long merchantId, String staffName,
                                               Long merchantLocationId, Long staffId, String redeemMethod) {
        if (coupon.getStatus() != PurchasedCouponStatus.ACTIVE) {
            throw new IllegalStateException("Купон не может быть использован. Статус: " + coupon.getStatus());
        }

        if (coupon.getExpiresAt() != null && coupon.getExpiresAt().isBefore(LocalDateTime.now())) {
            coupon.setStatus(PurchasedCouponStatus.EXPIRED);
            purchasedCouponRepository.save(coupon);
            throw new IllegalStateException("Срок действия купона истёк");
        }

        if (coupon.getMerchantId() == null) {
            throw new IllegalStateException("У купона не указан мерчант");
        }

        if (!coupon.getMerchantId().equals(merchantId)) {
            throw new IllegalStateException("Купон принадлежит другому мерчанту");
        }

        coupon.setStatus(PurchasedCouponStatus.USED);
        coupon.setUsedAt(LocalDateTime.now());
        purchasedCouponRepository.save(coupon);

        Redemption redemption = Redemption.builder()
                .purchasedCoupon(coupon)
                .redemptionCode(UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .merchantId(merchantId)
                .merchantLocationId(merchantLocationId)
                .staffId(staffId)
                .redeemedByStaff(staffName)
                .redeemMethod(redeemMethod)
                .redeemedAt(LocalDateTime.now())
                .build();
        redemptionRepository.save(redemption);

        // Publish event for coupon-service to sync redeemedCount
        CouponRedeemedEvent event = CouponRedeemedEvent.builder()
                .purchasedCouponId(coupon.getId())
                .orderId(coupon.getOrder() != null ? coupon.getOrder().getId() : null)
                .couponOfferId(coupon.getCouponOfferId())
                .couponOptionId(coupon.getCouponOptionId())
                .merchantId(merchantId)
                .redeemedAt(LocalDateTime.now())
                .build();
        rabbitTemplate.convertAndSend("coupon.exchange", "coupon.redeemed", event);

        return coupon;
    }

    // ==================== Refund Requests (per-coupon) ====================

    /**
     * Создаёт запрос на возврат per purchased coupon.
     * Бизнес-правила:
     * - только ACTIVE и не просроченный
     * - только свой купон
     * - блокировка дублей (PENDING, APPROVED_PROCESSING, REFUNDED)
     * - переводит купон в REFUND_PENDING
     */
    @Transactional
    public RefundRequestResponse createCouponRefundRequest(Long userId, Long purchasedCouponId, String reason) {
        expireOverduePurchasedCoupons();

        PurchasedCoupon coupon = purchasedCouponRepository.findById(purchasedCouponId)
                .orElseThrow(() -> new IllegalArgumentException("Купон не найден"));

        if (!coupon.getUserId().equals(userId)) {
            throw new IllegalStateException("Купон не принадлежит пользователю");
        }

        // Check expiry first
        if (coupon.getExpiresAt() != null && coupon.getExpiresAt().isBefore(LocalDateTime.now())) {
            if (coupon.getStatus() == PurchasedCouponStatus.ACTIVE) {
                coupon.setStatus(PurchasedCouponStatus.EXPIRED);
                purchasedCouponRepository.save(coupon);
            }
            throw new IllegalStateException("Возврат доступен только для активного неиспользованного купона");
        }

        if (coupon.getStatus() != PurchasedCouponStatus.ACTIVE) {
            throw new IllegalStateException("Возврат доступен только для активного неиспользованного купона");
        }

        // Block duplicate
        boolean hasDuplicate = refundRequestRepository.existsByPurchasedCouponIdAndStatusIn(
                purchasedCouponId,
                List.of(RefundRequest.RefundStatus.PENDING,
                        RefundRequest.RefundStatus.APPROVED_PROCESSING,
                        RefundRequest.RefundStatus.REFUNDED)
        );
        if (hasDuplicate) {
            throw new IllegalStateException("Заявка на возврат для этого купона уже существует");
        }

        // Calculate refund amount from order item
        BigDecimal refundAmount = BigDecimal.ZERO;
        if (coupon.getOrder() != null && coupon.getOrder().getItems() != null) {
            refundAmount = coupon.getOrder().getItems().stream()
                    .filter(i -> i.getCouponOfferId().equals(coupon.getCouponOfferId())
                            && i.getCouponOptionId().equals(coupon.getCouponOptionId()))
                    .map(OrderItem::getUnitPrice)
                    .findFirst()
                    .orElse(BigDecimal.ZERO);
        }

        RefundRequest refund = RefundRequest.builder()
                .order(coupon.getOrder())
                .purchasedCoupon(coupon)
                .userId(userId)
                .reason(reason.trim())
                .refundAmount(refundAmount)
                .build();

        coupon.setStatus(PurchasedCouponStatus.REFUND_PENDING);
        purchasedCouponRepository.save(coupon);
        refund = refundRequestRepository.save(refund);

        sendNotification(userId, "Заявка на возврат создана",
                "Ваша заявка на возврат купона «" + coupon.getCouponTitle() + "» принята и находится на рассмотрении.",
                "INFO");

        return mapToRefundResponse(refund);
    }

    /**
     * Получает запросы на возврат пользователя (новая версия, sorted DESC).
     */
    @Transactional(readOnly = true)
    public List<RefundRequestResponse> getUserCouponRefundRequests(Long userId) {
        return refundRequestRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::mapToRefundResponse)
                .toList();
    }

    /**
     * Получает запросы на возврат для админа с пагинацией.
     */
    @Transactional(readOnly = true)
    public Page<RefundRequestResponse> getAdminRefundRequests(RefundRequest.RefundStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        Page<RefundRequest> result = status != null
                ? refundRequestRepository.findByStatusOrderByCreatedAtDesc(status, pageable)
                : refundRequestRepository.findAllByOrderByCreatedAtDesc(pageable);
        return result.map(this::mapToRefundResponse);
    }

    /**
     * Одобряет возврат (PENDING → APPROVED_PROCESSING).
     * Купон остаётся REFUND_PENDING.
     * Устанавливает expectedRefundAt = +5 рабочих дней.
     */
    @Transactional
    public RefundRequestResponse approveRefundRequest(Long requestId, String adminComment) {
        RefundRequest request = refundRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Запрос не найден"));

        if (request.getStatus() != RefundRequest.RefundStatus.PENDING) {
            throw new IllegalStateException("Одобрение возможно только из статуса PENDING");
        }

        request.setStatus(RefundRequest.RefundStatus.APPROVED_PROCESSING);
        request.setAdminComment(adminComment);
        request.setResolvedAt(LocalDateTime.now());
        request.setExpectedRefundAt(addWorkingDays(LocalDateTime.now(), 5));
        refundRequestRepository.save(request);

        sendNotification(request.getUserId(), "Возврат одобрен",
                "Ваш возврат за купон «" + getCouponTitle(request) + "» одобрен. Деньги вернутся в течение до 5 рабочих дней.",
                "SUCCESS");

        return mapToRefundResponse(request);
    }

    /**
     * Отклоняет возврат (PENDING → REJECTED).
     * Возвращает купон в ACTIVE (если не истёк) или EXPIRED.
     */
    @Transactional
    public RefundRequestResponse rejectRefundRequest(Long requestId, String adminComment) {
        RefundRequest request = refundRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Запрос не найден"));

        if (request.getStatus() != RefundRequest.RefundStatus.PENDING) {
            throw new IllegalStateException("Отклонение возможно только из статуса PENDING");
        }

        request.setStatus(RefundRequest.RefundStatus.REJECTED);
        request.setAdminComment(adminComment);
        request.setResolvedAt(LocalDateTime.now());
        refundRequestRepository.save(request);

        // Restore coupon status
        PurchasedCoupon coupon = request.getPurchasedCoupon();
        if (coupon != null && coupon.getStatus() == PurchasedCouponStatus.REFUND_PENDING) {
            if (coupon.getExpiresAt() != null && coupon.getExpiresAt().isBefore(LocalDateTime.now())) {
                coupon.setStatus(PurchasedCouponStatus.EXPIRED);
            } else {
                coupon.setStatus(PurchasedCouponStatus.ACTIVE);
            }
            purchasedCouponRepository.save(coupon);
        }

        sendNotification(request.getUserId(), "Возврат отклонён",
                "Ваш возврат за купон «" + getCouponTitle(request) + "» был отклонён."
                        + (adminComment != null ? " Комментарий: " + adminComment : ""),
                "INFO");

        return mapToRefundResponse(request);
    }

    /**
     * Завершает возврат (APPROVED_PROCESSING → REFUNDED).
     * Помечает купон как REFUNDED.
     */
    @Transactional
    public RefundRequestResponse completeRefundRequest(Long requestId, String adminComment) {
        RefundRequest request = refundRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Запрос не найден"));

        if (request.getStatus() != RefundRequest.RefundStatus.APPROVED_PROCESSING) {
            throw new IllegalStateException("Завершение возможно только из статуса APPROVED_PROCESSING");
        }

        request.setStatus(RefundRequest.RefundStatus.REFUNDED);
        request.setCompletedAt(LocalDateTime.now());
        if (request.getResolvedAt() == null) {
            request.setResolvedAt(LocalDateTime.now());
        }
        if (adminComment != null && !adminComment.isBlank()) {
            request.setAdminComment(adminComment);
        }
        refundRequestRepository.save(request);

        PurchasedCoupon coupon = request.getPurchasedCoupon();
        if (coupon != null) {
            coupon.setStatus(PurchasedCouponStatus.REFUNDED);
            purchasedCouponRepository.save(coupon);
        }

        sendNotification(request.getUserId(), "Возврат завершён",
                "Возврат за купон «" + getCouponTitle(request) + "» завершён. Деньги зачислены.",
                "SUCCESS");

        return mapToRefundResponse(request);
    }

    // ==================== Legacy Refund (order-level, backward compat) ====================

    /**
     * Создаёт запрос на возврат средств (legacy order-level).
     */
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

    /**
     * Получает запросы на возврат пользователя (legacy).
     */
    @Transactional(readOnly = true)
    public List<RefundRequest> getUserRefundRequests(Long userId) {
        return refundRequestRepository.findByUserId(userId);
    }

    /**
     * Одобряет или отклоняет запрос на возврат (legacy Admin).
     */
    @Transactional
    public RefundRequest resolveRefundRequest(Long requestId, boolean approved, String adminComment) {
        RefundRequest request = refundRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Запрос не найден"));

        // Legacy only supports APPROVED/REJECTED (old status names mapped)
        request.setStatus(approved ? RefundRequest.RefundStatus.APPROVED_PROCESSING : RefundRequest.RefundStatus.REJECTED);
        request.setAdminComment(adminComment);
        request.setResolvedAt(LocalDateTime.now());
        return refundRequestRepository.save(request);
    }

    // ==================== Refund Helpers ====================

    private LocalDateTime addWorkingDays(LocalDateTime start, int workingDays) {
        LocalDateTime result = start;
        int added = 0;
        while (added < workingDays) {
            result = result.plusDays(1);
            java.time.DayOfWeek day = result.getDayOfWeek();
            if (day != java.time.DayOfWeek.SATURDAY && day != java.time.DayOfWeek.SUNDAY) {
                added++;
            }
        }
        return result;
    }

    private String getCouponTitle(RefundRequest request) {
        if (request.getPurchasedCoupon() != null) {
            return request.getPurchasedCoupon().getCouponTitle();
        }
        return "купон";
    }

    private void sendNotification(Long userId, String title, String message, String type) {
        try {
            uz.topdim.common.events.NotificationEvent event = uz.topdim.common.events.NotificationEvent.builder()
                    .userId(userId)
                    .title(title)
                    .message(message)
                    .type(type)
                    .build();
            rabbitTemplate.convertAndSend("notification.exchange", "notification.sent", event);
        } catch (Exception e) {
            log.warn("Не удалось отправить уведомление userId={}: {}", userId, e.getMessage());
        }
    }

    private RefundRequestResponse mapToRefundResponse(RefundRequest r) {
        PurchasedCoupon coupon = r.getPurchasedCoupon();
        return RefundRequestResponse.builder()
                .id(r.getId())
                .orderId(r.getOrder() != null ? r.getOrder().getId() : null)
                .purchasedCouponId(coupon != null ? coupon.getId() : null)
                .userId(r.getUserId())
                .couponTitle(coupon != null ? coupon.getCouponTitle() : null)
                .optionTitle(coupon != null ? coupon.getOptionTitle() : null)
                .couponCode(coupon != null ? coupon.getCouponCode() : null)
                .merchantName(coupon != null ? coupon.getMerchantName() : null)
                .refundAmount(r.getRefundAmount())
                .reason(r.getReason())
                .status(r.getStatus())
                .adminComment(r.getAdminComment())
                .createdAt(r.getCreatedAt())
                .resolvedAt(r.getResolvedAt())
                .expectedRefundAt(r.getExpectedRefundAt())
                .completedAt(r.getCompletedAt())
                .build();
    }

    // ==================== Helpers ====================

    private String generateOrderNumber() {
        return "TD-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 1000);
    }

    private String generateCouponCode() {
        return "CP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    // ==================== Expiry ====================

    private void expireOverduePurchasedCoupons() {
        purchasedCouponRepository.expireActiveCouponsBefore(LocalDateTime.now());
    }

    // ==================== Purchase validation ====================

    private CouponPurchaseSnapshot loadPurchaseSnapshot(Long couponOfferId, Long couponOptionId) {
        ApiResponse<CouponPurchaseSnapshot> response = couponClient.getPurchaseSnapshot(couponOfferId, couponOptionId);
        if (response == null || response.getData() == null) {
            throw new IllegalStateException("Купон недоступен для покупки");
        }
        return response.getData();
    }

    private void assertPurchasableStatus(CouponPurchaseSnapshot snapshot) {
        if (!"ACTIVE".equals(snapshot.getCouponStatus())) {
            throw new IllegalStateException("Купон недоступен для покупки");
        }
        if (!"ACTIVE".equals(snapshot.getOptionStatus())) {
            throw new IllegalStateException("Опция купона недоступна для покупки");
        }
        if (snapshot.getBuyUntil() != null && snapshot.getBuyUntil().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("Срок покупки купона истёк");
        }
    }

    private void assertQuantityAvailable(CouponPurchaseSnapshot snapshot, int quantity) {
        if (snapshot.getQuantityLimit() != null && snapshot.getQuantityLimit() > 0) {
            int remaining = snapshot.getQuantityLimit() - snapshot.getQuantitySold();
            if (quantity > remaining) {
                throw new IllegalStateException("Недостаточно купонов в наличии");
            }
        }
    }

    // ==================== Mapping ====================

    /**
     * Маппит Cart entity в storefront-safe CartResponse DTO.
     * Вычисляет totalAmount и subtotal для каждого item.
     */
    public CartResponse mapToCartResponse(Cart cart) {
        List<CartResponse.CartItemResponse> itemResponses = cart.getItems().stream()
                .map(item -> CartResponse.CartItemResponse.builder()
                        .id(item.getId())
                        .couponOfferId(item.getCouponOfferId())
                        .couponOptionId(item.getCouponOptionId())
                        .couponTitle(item.getCouponTitle())
                        .optionTitle(item.getOptionTitle())
                        .unitPrice(item.getUnitPrice())
                        .quantity(item.getQuantity())
                        .subtotal(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                        .gift(item.isGift())
                        .giftRecipientName(item.getGiftRecipientName())
                        .giftRecipientPhone(item.getGiftRecipientPhone())
                        .build())
                .toList();

        BigDecimal totalAmount = itemResponses.stream()
                .map(CartResponse.CartItemResponse::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return CartResponse.builder()
                .id(cart.getId())
                .userId(cart.getUserId())
                .items(itemResponses)
                .totalAmount(totalAmount)
                .totalItems(itemResponses.size())
                .build();
    }

    /**
     * Маппит Order entity в storefront-safe OrderResponse DTO.
     * Гарантирует наличие id, status, totalAmount.
     */
    public OrderResponse mapToOrderResponse(Order order) {
        return OrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus().name())
                .userEmail(order.getUserEmail())
                .userPhone(order.getUserPhone())
                .itemCount(order.getItems() != null ? order.getItems().size() : 0)
                .createdAt(order.getCreatedAt())
                .paidAt(order.getPaidAt())
                .build();
    }

    /**
     * Маппит PurchasedCoupon entity в PurchasedCouponResponse DTO.
     * Исключает JPA-связи (Order) из сериализации.
     */
    public PurchasedCouponResponse mapToCouponResponse(PurchasedCoupon coupon) {
        PurchasedCouponResponse.PurchasedCouponResponseBuilder builder = PurchasedCouponResponse.builder()
                .id(coupon.getId())
                .couponOfferId(coupon.getCouponOfferId())
                .couponOptionId(coupon.getCouponOptionId())
                .couponTitle(coupon.getCouponTitle())
                .optionTitle(coupon.getOptionTitle())
                .couponCode(coupon.getCouponCode())
                .qrToken(coupon.getQrToken())
                .status(coupon.getStatus().name())
                .merchantId(coupon.getMerchantId())
                .merchantName(coupon.getMerchantName())
                .merchantAddress(coupon.getMerchantAddress())
                .merchantPhone(coupon.getMerchantPhone())
                .merchantWorkingHours(coupon.getMerchantWorkingHours())
                .purchasedAt(coupon.getPurchasedAt())
                .expiresAt(coupon.getExpiresAt())
                .usedAt(coupon.getUsedAt());

        // Enrich with latest refund request data
        List<RefundRequest> refunds = refundRequestRepository
                .findByPurchasedCouponIdOrderByCreatedAtDesc(coupon.getId());
        if (!refunds.isEmpty()) {
            RefundRequest latest = refunds.get(0);
            builder.refundRequestId(latest.getId())
                    .refundStatus(latest.getStatus().name())
                    .refundExpectedAt(latest.getExpectedRefundAt());
        }

        return builder.build();
    }

    /**
     * Маппит PurchasedCoupon в RedeemCouponResponse для партнёрского UI.
     */
    public RedeemCouponResponse mapToRedeemResponse(PurchasedCoupon coupon) {
        return RedeemCouponResponse.builder()
                .purchasedCouponId(coupon.getId())
                .couponOfferId(coupon.getCouponOfferId())
                .couponOptionId(coupon.getCouponOptionId())
                .couponTitle(coupon.getCouponTitle())
                .optionTitle(coupon.getOptionTitle())
                .couponCode(coupon.getCouponCode())
                .status(coupon.getStatus().name())
                .merchantId(coupon.getMerchantId())
                .merchantName(coupon.getMerchantName())
                .purchasedAt(coupon.getPurchasedAt())
                .expiresAt(coupon.getExpiresAt())
                .usedAt(coupon.getUsedAt())
                .build();
    }

    // ==================== Admin Lookup ====================

    /**
     * Admin-only lookup: find purchased coupon by coupon code.
     * Does NOT return qrToken for security.
     */
    @Transactional(readOnly = true)
    public AdminPurchasedCouponLookupResponse adminLookupByCouponCode(String couponCode) {
        PurchasedCoupon pc = purchasedCouponRepository.findByCouponCode(couponCode.trim().toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("Купон с кодом '" + couponCode + "' не найден"));
        return AdminPurchasedCouponLookupResponse.builder()
                .purchasedCouponId(pc.getId())
                .orderId(pc.getOrder() != null ? pc.getOrder().getId() : null)
                .userId(pc.getUserId())
                .couponOfferId(pc.getCouponOfferId())
                .couponOptionId(pc.getCouponOptionId())
                .couponTitle(pc.getCouponTitle())
                .optionTitle(pc.getOptionTitle())
                .couponCode(pc.getCouponCode())
                .status(pc.getStatus().name())
                .merchantId(pc.getMerchantId())
                .merchantName(pc.getMerchantName())
                .merchantAddress(pc.getMerchantAddress())
                .purchasedAt(pc.getPurchasedAt())
                .expiresAt(pc.getExpiresAt())
                .usedAt(pc.getUsedAt())
                .build();
    }

    // ==================== Review Eligibility ====================

    /**
     * Check if user can write a review for this couponOfferId.
     * Only allowed when user has at least one USED purchased coupon.
     */
    @Transactional(readOnly = true)
    public ReviewEligibilityResponse getReviewEligibility(Long userId, Long couponOfferId) {
        return purchasedCouponRepository
                .findFirstByUserIdAndCouponOfferIdAndStatusOrderByUsedAtDesc(
                        userId, couponOfferId, PurchasedCouponStatus.USED)
                .map(pc -> ReviewEligibilityResponse.builder()
                        .eligible(true)
                        .reason("USED_COUPON_FOUND")
                        .purchasedCouponId(pc.getId())
                        .usedAt(pc.getUsedAt())
                        .build())
                .orElseGet(() -> ReviewEligibilityResponse.builder()
                        .eligible(false)
                        .reason("REVIEW_ALLOWED_AFTER_COUPON_USAGE")
                        .build());
    }
}
