package uz.topdim.order.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.order.client.CouponClient;
import uz.topdim.order.client.CouponPurchaseSnapshot;
import uz.topdim.order.dto.AdminOrderResponse;
import uz.topdim.order.dto.AdminPurchasedCouponLookupResponse;
import uz.topdim.order.dto.PurchasedCouponResponse;
import uz.topdim.order.entity.*;
import uz.topdim.order.repository.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private CartRepository cartRepository;
    @Mock private CartItemRepository cartItemRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private PurchasedCouponRepository purchasedCouponRepository;
    @Mock private RedemptionRepository redemptionRepository;
    @Mock private RefundRequestRepository refundRequestRepository;
    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private CouponClient couponClient;

    @InjectMocks
    private OrderService orderService;

    // ==================== Cart ====================

    @Test
    @DisplayName("Корзина: существующая — возвращает без создания")
    void getCart_existingUser_returnsExistingCart() {
        Cart cart = Cart.builder().id(1L).userId(10L).items(new ArrayList<>()).build();
        when(cartRepository.findByUserId(10L)).thenReturn(Optional.of(cart));

        Cart result = orderService.getCartByUserId(10L);

        assertThat(result.getId()).isEqualTo(1L);
        verify(cartRepository, never()).save(any());
    }

    @Test
    @DisplayName("Корзина: новый юзер — создаёт пустую")
    void getCart_newUser_createsEmptyCart() {
        when(cartRepository.findByUserId(99L)).thenReturn(Optional.empty());
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> {
            Cart c = inv.getArgument(0);
            c.setId(2L);
            return c;
        });

        Cart result = orderService.getCartByUserId(99L);

        assertThat(result.getUserId()).isEqualTo(99L);
        verify(cartRepository).save(any(Cart.class));
    }

    // ==================== Snapshot helpers ====================

    private CouponPurchaseSnapshot activeSnapshot() {
        return CouponPurchaseSnapshot.builder()
                .couponOfferId(5L)
                .couponOptionId(3L)
                .couponTitle("Canonical SPA")
                .optionTitle("Canonical Standard")
                .couponStatus("ACTIVE")
                .optionStatus("ACTIVE")
                .couponPrice(BigDecimal.valueOf(99000))
                .quantityLimit(10)
                .quantitySold(2)
                .buyUntil(LocalDateTime.now().plusDays(3))
                .useUntil(LocalDateTime.now().plusDays(30))
                .build();
    }

    private ApiResponse<CouponPurchaseSnapshot> snapshotResponse(CouponPurchaseSnapshot snapshot) {
        return ApiResponse.success(snapshot);
    }

    // ==================== UpdateCartItemQuantity ====================

    @Test
    @DisplayName("Обновление количества в корзине: успешное обновление с ревалидацией snapshot")
    void updateCartItemQuantity_success_updatesQuantityAndRevalidates() {
        CartItem item = CartItem.builder()
                .id(10L).couponOfferId(5L).couponOptionId(3L)
                .couponTitle("SPA").optionTitle("Standard")
                .unitPrice(BigDecimal.valueOf(99000)).quantity(1)
                .gift(false).build();
        Cart cart = Cart.builder().id(1L).userId(10L)
                .items(new ArrayList<>(List.of(item))).build();
        item.setCart(cart);

        when(cartRepository.findByUserId(10L)).thenReturn(Optional.of(cart));
        when(couponClient.getPurchaseSnapshot(5L, 3L)).thenReturn(snapshotResponse(activeSnapshot()));
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        Cart result = orderService.updateCartItemQuantity(10L, 10L, 3);

        assertThat(result.getItems().get(0).getQuantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("Обновление количества: item не принадлежит корзине юзера → IllegalArgumentException")
    void updateCartItemQuantity_itemNotInUserCart_throws() {
        Cart cart = Cart.builder().id(1L).userId(10L).items(new ArrayList<>()).build();
        when(cartRepository.findByUserId(10L)).thenReturn(Optional.of(cart));

        assertThatThrownBy(() -> orderService.updateCartItemQuantity(10L, 999L, 2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Обновление количества: превышает доступный остаток → IllegalStateException")
    void updateCartItemQuantity_exceedsAvailable_throws() {
        CouponPurchaseSnapshot snapshot = activeSnapshot();
        snapshot.setQuantityLimit(3);
        snapshot.setQuantitySold(2);

        CartItem item = CartItem.builder()
                .id(10L).couponOfferId(5L).couponOptionId(3L)
                .couponTitle("SPA").optionTitle("Standard")
                .unitPrice(BigDecimal.valueOf(99000)).quantity(1)
                .gift(false).build();
        Cart cart = Cart.builder().id(1L).userId(10L)
                .items(new ArrayList<>(List.of(item))).build();
        item.setCart(cart);

        when(cartRepository.findByUserId(10L)).thenReturn(Optional.of(cart));
        when(couponClient.getPurchaseSnapshot(5L, 3L)).thenReturn(snapshotResponse(snapshot));

        assertThatThrownBy(() -> orderService.updateCartItemQuantity(10L, 10L, 5))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Недостаточно купонов");
    }

    // ==================== AddToCart ====================

    @Test
    @DisplayName("Добавление в корзину: создаёт CartItem")
    void addToCart_createsCartItem() {
        Cart cart = Cart.builder().id(1L).userId(10L).items(new ArrayList<>()).build();
        when(cartRepository.findByUserId(10L)).thenReturn(Optional.of(cart));
        when(couponClient.getPurchaseSnapshot(5L, 3L)).thenReturn(snapshotResponse(activeSnapshot()));
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        Cart result = orderService.addToCart(10L, 5L, 3L,
                "SPA купон", "Стандарт", BigDecimal.valueOf(150000), 1, false, null, null);

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getCouponOfferId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("Добавление в корзину: сервер использует канонические цену и названия из coupon-service")
    void addToCart_usesCanonicalCouponSnapshot() {
        Cart cart = Cart.builder().id(1L).userId(10L).items(new ArrayList<>()).build();
        when(cartRepository.findByUserId(10L)).thenReturn(Optional.of(cart));
        when(couponClient.getPurchaseSnapshot(5L, 3L)).thenReturn(snapshotResponse(activeSnapshot()));
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        Cart result = orderService.addToCart(
                10L, 5L, 3L,
                "Fake title", "Fake option", BigDecimal.ONE,
                1, false, null, null
        );

        CartItem item = result.getItems().get(0);
        assertThat(item.getCouponTitle()).isEqualTo("Canonical SPA");
        assertThat(item.getOptionTitle()).isEqualTo("Canonical Standard");
        assertThat(item.getUnitPrice()).isEqualByComparingTo(BigDecimal.valueOf(99000));
    }

    @Test
    @DisplayName("Добавление в корзину: купон не ACTIVE → IllegalStateException")
    void addToCart_nonActiveCoupon_throwsException() {
        CouponPurchaseSnapshot snapshot = activeSnapshot();
        snapshot.setCouponStatus("SOLD_OUT");
        when(couponClient.getPurchaseSnapshot(5L, 3L)).thenReturn(snapshotResponse(snapshot));

        assertThatThrownBy(() -> orderService.addToCart(
                10L, 5L, 3L,
                "SPA", "Standard", BigDecimal.valueOf(99000),
                1, false, null, null
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Купон недоступен для покупки");
    }

    @Test
    @DisplayName("Добавление в корзину: срок покупки истёк → IllegalStateException")
    void addToCart_expiredBuyUntil_throwsException() {
        CouponPurchaseSnapshot snapshot = activeSnapshot();
        snapshot.setBuyUntil(LocalDateTime.now().minusMinutes(1));
        when(couponClient.getPurchaseSnapshot(5L, 3L)).thenReturn(snapshotResponse(snapshot));

        assertThatThrownBy(() -> orderService.addToCart(
                10L, 5L, 3L,
                "SPA", "Standard", BigDecimal.valueOf(99000),
                1, false, null, null
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Срок покупки купона истёк");
    }

    @Test
    @DisplayName("Добавление в корзину: запрошенное количество превышает остаток → IllegalStateException")
    void addToCart_quantityExceedsRemainingLimit_throwsException() {
        CouponPurchaseSnapshot snapshot = activeSnapshot();
        snapshot.setQuantityLimit(3);
        snapshot.setQuantitySold(2);
        when(couponClient.getPurchaseSnapshot(5L, 3L)).thenReturn(snapshotResponse(snapshot));

        Cart cart = Cart.builder().id(1L).userId(10L).items(new ArrayList<>()).build();
        when(cartRepository.findByUserId(10L)).thenReturn(Optional.of(cart));

        assertThatThrownBy(() -> orderService.addToCart(
                10L, 5L, 3L,
                "SPA", "Standard", BigDecimal.valueOf(99000),
                2, false, null, null
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Недостаточно купонов в наличии");
    }

    @Test
    @DisplayName("Добавление в корзину: повторная та же опция валидирует общий quantity и увеличивает существующую строку")
    void addToCart_existingSameOption_incrementsExistingLineAfterCombinedValidation() {
        CartItem existing = CartItem.builder()
                .couponOfferId(5L).couponOptionId(3L)
                .couponTitle("Old title").optionTitle("Old option")
                .unitPrice(BigDecimal.valueOf(50000))
                .quantity(1)
                .gift(false)
                .build();
        Cart cart = Cart.builder().id(1L).userId(10L)
                .items(new ArrayList<>(List.of(existing))).build();

        when(cartRepository.findByUserId(10L)).thenReturn(Optional.of(cart));
        when(couponClient.getPurchaseSnapshot(5L, 3L)).thenReturn(snapshotResponse(activeSnapshot()));
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        Cart result = orderService.addToCart(
                10L, 5L, 3L,
                "Fake title", "Fake option", BigDecimal.ONE,
                2, false, null, null
        );

        assertThat(result.getItems()).hasSize(1);
        CartItem item = result.getItems().get(0);
        assertThat(item.getQuantity()).isEqualTo(3);
        assertThat(item.getCouponTitle()).isEqualTo("Canonical SPA");
        assertThat(item.getOptionTitle()).isEqualTo("Canonical Standard");
        assertThat(item.getUnitPrice()).isEqualByComparingTo(BigDecimal.valueOf(99000));
    }

    // ==================== Checkout ====================

    @Test
    @DisplayName("Checkout: успешный — создаёт заказ и публикует событие")
    void createOrder_success_createsOrderAndPublishesEvent() {
        CartItem item = CartItem.builder()
                .couponOfferId(5L).couponOptionId(3L)
                .couponTitle("SPA").optionTitle("Стандарт")
                .unitPrice(BigDecimal.valueOf(150000)).quantity(2)
                .gift(false).build();

        Cart cart = Cart.builder().id(1L).userId(10L)
                .items(new ArrayList<>(List.of(item))).build();

        when(cartRepository.findByUserId(10L)).thenReturn(Optional.of(cart));
        when(couponClient.getPurchaseSnapshot(5L, 3L)).thenReturn(snapshotResponse(activeSnapshot()));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(100L);
            return o;
        });
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        Order order = orderService.createOrder(10L, "user@test.com", "+998901234567");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(198000)); // 99000 * 2 from canonical snapshot
        verify(rabbitTemplate).convertAndSend(eq("order.exchange"), eq("order.created"), any(Object.class));
    }

    @Test
    @DisplayName("Checkout: пустая корзина → IllegalStateException")
    void createOrder_emptyCart_throwsException() {
        Cart cart = Cart.builder().id(1L).userId(10L).items(new ArrayList<>()).build();
        when(cartRepository.findByUserId(10L)).thenReturn(Optional.of(cart));

        assertThatThrownBy(() -> orderService.createOrder(10L, "a@b.com", "123"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Корзина пуста");
    }

    @Test
    @DisplayName("Checkout: stale cart item became unavailable → rejects without creating order")
    void createOrder_staleUnavailableCartItem_rejectsWithoutSideEffects() {
        CartItem item = CartItem.builder()
                .couponOfferId(5L).couponOptionId(3L)
                .couponTitle("Old title").optionTitle("Old option")
                .unitPrice(BigDecimal.valueOf(99000)).quantity(1)
                .gift(false).build();
        Cart cart = Cart.builder().id(1L).userId(10L)
                .items(new ArrayList<>(List.of(item))).build();

        CouponPurchaseSnapshot snapshot = activeSnapshot();
        snapshot.setCouponStatus("SOLD_OUT");

        when(cartRepository.findByUserId(10L)).thenReturn(Optional.of(cart));
        when(couponClient.getPurchaseSnapshot(5L, 3L)).thenReturn(snapshotResponse(snapshot));

        assertThatThrownBy(() -> orderService.createOrder(10L, "user@test.com", "+998901234567"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Купон недоступен для покупки");

        verify(orderRepository, never()).save(any());
        verify(cartRepository, never()).save(argThat(savedCart -> savedCart.getItems().isEmpty()));
        verify(rabbitTemplate, never()).convertAndSend(eq("order.exchange"), eq("order.created"), any(Object.class));
    }

    @Test
    @DisplayName("Checkout: canonical price changed after add-to-cart → creates order with current server price")
    void createOrder_priceChanged_usesCurrentCanonicalPrice() {
        CartItem item = CartItem.builder()
                .couponOfferId(5L).couponOptionId(3L)
                .couponTitle("Old title").optionTitle("Old option")
                .unitPrice(BigDecimal.valueOf(99000)).quantity(2)
                .gift(false).build();
        Cart cart = Cart.builder().id(1L).userId(10L)
                .items(new ArrayList<>(List.of(item))).build();

        CouponPurchaseSnapshot snapshot = activeSnapshot();
        snapshot.setCouponPrice(BigDecimal.valueOf(120000));

        when(cartRepository.findByUserId(10L)).thenReturn(Optional.of(cart));
        when(couponClient.getPurchaseSnapshot(5L, 3L)).thenReturn(snapshotResponse(snapshot));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(100L);
            return o;
        });
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        Order order = orderService.createOrder(10L, "user@test.com", "+998901234567");

        assertThat(order.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(240000));
        assertThat(order.getItems()).hasSize(1);
        assertThat(order.getItems().get(0).getCouponTitle()).isEqualTo("Canonical SPA");
        assertThat(order.getItems().get(0).getOptionTitle()).isEqualTo("Canonical Standard");
        assertThat(order.getItems().get(0).getUnitPrice()).isEqualByComparingTo(BigDecimal.valueOf(120000));
    }

    @Test
    @DisplayName("Checkout: order item stores merchantId and expiresAt from canonical coupon snapshot")
    void createOrder_storesMerchantAndExpiresAtOnOrderItem() {
        CartItem item = CartItem.builder()
                .couponOfferId(5L).couponOptionId(3L)
                .couponTitle("Old title").optionTitle("Old option")
                .unitPrice(BigDecimal.valueOf(99000)).quantity(1)
                .gift(false).build();
        Cart cart = Cart.builder().id(1L).userId(10L)
                .items(new ArrayList<>(List.of(item))).build();

        CouponPurchaseSnapshot snapshot = activeSnapshot();
        snapshot.setMerchantId(77L);
        snapshot.setUseUntil(LocalDateTime.of(2027, 7, 1, 12, 0));

        when(cartRepository.findByUserId(10L)).thenReturn(Optional.of(cart));
        when(couponClient.getPurchaseSnapshot(5L, 3L)).thenReturn(snapshotResponse(snapshot));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(100L);
            return o;
        });
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        Order order = orderService.createOrder(10L, "user@test.com", "+998901234567");

        assertThat(order.getItems()).hasSize(1);
        assertThat(order.getItems().get(0).getMerchantId()).isEqualTo(77L);
        assertThat(order.getItems().get(0).getExpiresAt())
                .isEqualTo(LocalDateTime.of(2027, 7, 1, 12, 0));
    }

    // ==================== Generate Coupons ====================

    @Test
    @DisplayName("Генерация купонов: ставит PAID и создаёт коды")
    void generateCoupons_setsStatusPaidAndCreatesCoupons() {
        OrderItem item = OrderItem.builder()
                .couponOfferId(1L).couponOptionId(2L)
                .couponTitle("SPA").optionTitle("Стандарт")
                .unitPrice(BigDecimal.valueOf(150000)).quantity(2)
                .gift(false).build();

        Order order = Order.builder().id(100L).userId(10L)
                .userEmail("a@b.com").userPhone("123")
                .items(List.of(item)).status(OrderStatus.PENDING).build();

        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(purchasedCouponRepository.findByOrderId(100L)).thenReturn(List.of());
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(purchasedCouponRepository.save(any(PurchasedCoupon.class))).thenAnswer(inv -> {
            PurchasedCoupon c = inv.getArgument(0);
            c.setId((long) (Math.random() * 1000));
            return c;
        });

        List<PurchasedCoupon> coupons = orderService.generatePurchasedCoupons(100L);

        assertThat(coupons).hasSize(2); // quantity=2
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(rabbitTemplate, times(2)).convertAndSend(eq("coupon.exchange"), eq("coupon.purchased"), any(Object.class));
    }

    @Test
    @DisplayName("Генерация купонов: повторный вызов для уже оплаченного заказа возвращает существующие купоны без дублей")
    void generateCoupons_existingCoupons_returnsExistingWithoutCreatingDuplicates() {
        Order order = Order.builder().id(100L).userId(10L)
                .status(OrderStatus.PAID).items(List.of()).build();
        PurchasedCoupon existing = PurchasedCoupon.builder()
                .id(1L)
                .order(order)
                .couponCode("CP-EXISTING")
                .status(PurchasedCouponStatus.ACTIVE)
                .build();

        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(purchasedCouponRepository.findByOrderId(100L)).thenReturn(List.of(existing));

        List<PurchasedCoupon> result = orderService.generatePurchasedCoupons(100L);

        assertThat(result).containsExactly(existing);
        verify(purchasedCouponRepository, never()).save(any(PurchasedCoupon.class));
        verify(rabbitTemplate, never()).convertAndSend(eq("coupon.exchange"), eq("coupon.purchased"), any(Object.class));
    }

    @Test
    @DisplayName("Генерация купонов: копирует merchantId и expiresAt из order item")
    void generateCoupons_copiesMerchantAndExpiresAt() {
        OrderItem item = OrderItem.builder()
                .couponOfferId(1L).couponOptionId(2L)
                .couponTitle("SPA").optionTitle("Standard")
                .unitPrice(BigDecimal.valueOf(150000)).quantity(1)
                .merchantId(77L)
                .merchantName("SPA Oasis")
                .merchantAddress("Ташкент, ул. Амира Темура, 10")
                .merchantPhone("+998901234567")
                .merchantWorkingHours("10:00-22:00")
                .expiresAt(LocalDateTime.of(2027, 7, 1, 12, 0))
                .gift(false).build();
        Order order = Order.builder().id(100L).userId(10L)
                .userEmail("a@b.com").userPhone("123")
                .items(List.of(item)).status(OrderStatus.PENDING).build();

        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(purchasedCouponRepository.findByOrderId(100L)).thenReturn(List.of());
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(purchasedCouponRepository.save(any(PurchasedCoupon.class))).thenAnswer(inv -> {
            PurchasedCoupon c = inv.getArgument(0);
            c.setId(1L);
            return c;
        });

        List<PurchasedCoupon> result = orderService.generatePurchasedCoupons(100L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMerchantId()).isEqualTo(77L);
        assertThat(result.get(0).getMerchantName()).isEqualTo("SPA Oasis");
        assertThat(result.get(0).getMerchantAddress()).isEqualTo("Ташкент, ул. Амира Темура, 10");
        assertThat(result.get(0).getMerchantPhone()).isEqualTo("+998901234567");
        assertThat(result.get(0).getMerchantWorkingHours()).isEqualTo("10:00-22:00");
        assertThat(result.get(0).getExpiresAt()).isEqualTo(LocalDateTime.of(2027, 7, 1, 12, 0));
    }

    @Test
    @DisplayName("Purchased coupon response: exposes usage context for profile")
    void mapToCouponResponse_includesUsageContext() {
        PurchasedCoupon coupon = PurchasedCoupon.builder()
                .id(501L)
                .couponOfferId(10L)
                .couponOptionId(20L)
                .couponTitle("SPA")
                .optionTitle("Standard")
                .couponCode("CP-1234")
                .qrToken("qr-token")
                .status(PurchasedCouponStatus.ACTIVE)
                .merchantId(77L)
                .merchantName("SPA Oasis")
                .merchantAddress("Ташкент, ул. Амира Темура, 10")
                .merchantPhone("+998901234567")
                .merchantWorkingHours("10:00-22:00")
                .expiresAt(LocalDateTime.of(2027, 7, 1, 12, 0))
                .build();

        PurchasedCouponResponse response = orderService.mapToCouponResponse(coupon);

        assertThat(response.getCouponOfferId()).isEqualTo(10L);
        assertThat(response.getCouponOptionId()).isEqualTo(20L);
        assertThat(response.getMerchantId()).isEqualTo(77L);
        assertThat(response.getMerchantName()).isEqualTo("SPA Oasis");
        assertThat(response.getMerchantAddress()).isEqualTo("Ташкент, ул. Амира Темура, 10");
        assertThat(response.getMerchantPhone()).isEqualTo("+998901234567");
        assertThat(response.getMerchantWorkingHours()).isEqualTo("10:00-22:00");
    }

    // ==================== User Coupons Read ====================

    @Test
    @DisplayName("Мои купоны: перед чтением переводит просроченные ACTIVE в EXPIRED")
    void getUserCouponsByStatus_expiresOverdueCouponsBeforeRead() {
        when(purchasedCouponRepository.expireActiveCouponsBefore(any(LocalDateTime.class))).thenReturn(2);
        when(purchasedCouponRepository.findByUserIdAndStatus(10L, PurchasedCouponStatus.ACTIVE)).thenReturn(List.of());

        List<PurchasedCoupon> result = orderService.getUserCouponsByStatus(10L, PurchasedCouponStatus.ACTIVE);

        assertThat(result).isEmpty();
        verify(purchasedCouponRepository).expireActiveCouponsBefore(any(LocalDateTime.class));
        verify(purchasedCouponRepository).findByUserIdAndStatus(10L, PurchasedCouponStatus.ACTIVE);
    }

    // ==================== Redemption ====================

    @Test
    @DisplayName("Погашение: активный купон → USED")
    void redeemCoupon_activeCoupon_marksAsUsed() {
        PurchasedCoupon coupon = PurchasedCoupon.builder()
                .id(1L).couponCode("CP-TEST1234")
                .merchantId(5L)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .status(PurchasedCouponStatus.ACTIVE).build();

        when(purchasedCouponRepository.findByCouponCodeForUpdate("CP-TEST1234")).thenReturn(Optional.of(coupon));
        when(purchasedCouponRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(redemptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PurchasedCoupon result = orderService.redeemCoupon("CP-TEST1234", 5L, "Анна");

        assertThat(result.getStatus()).isEqualTo(PurchasedCouponStatus.USED);
        assertThat(result.getUsedAt()).isNotNull();
        verify(redemptionRepository).save(any(Redemption.class));
    }

    @Test
    @DisplayName("Погашение: уже использованный → IllegalStateException")
    void redeemCoupon_usedCoupon_throwsException() {
        PurchasedCoupon coupon = PurchasedCoupon.builder()
                .id(1L).couponCode("CP-USED1234").status(PurchasedCouponStatus.USED).build();

        when(purchasedCouponRepository.findByCouponCodeForUpdate("CP-USED1234")).thenReturn(Optional.of(coupon));

        assertThatThrownBy(() -> orderService.redeemCoupon("CP-USED1234", 5L, "Анна"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("не может быть использован");

        // Must NOT publish CouponRedeemedEvent
        verify(rabbitTemplate, never()).convertAndSend(eq("coupon.exchange"), eq("coupon.redeemed"), any(Object.class));
    }

    @Test
    @DisplayName("Погашение: чужой merchantId → IllegalStateException")
    void redeemCoupon_wrongMerchant_throwsException() {
        PurchasedCoupon coupon = PurchasedCoupon.builder()
                .id(1L)
                .couponCode("CP-TEST1234")
                .merchantId(77L)
                .expiresAt(LocalDateTime.now().plusDays(1))
                .status(PurchasedCouponStatus.ACTIVE)
                .build();

        when(purchasedCouponRepository.findByCouponCodeForUpdate("CP-TEST1234")).thenReturn(Optional.of(coupon));

        assertThatThrownBy(() -> orderService.redeemCoupon("CP-TEST1234", 88L, "Анна"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Купон принадлежит другому мерчанту");

        verify(redemptionRepository, never()).save(any(Redemption.class));
        // Must NOT publish CouponRedeemedEvent
        verify(rabbitTemplate, never()).convertAndSend(eq("coupon.exchange"), eq("coupon.redeemed"), any(Object.class));
    }

    @Test
    @DisplayName("Погашение: купон без merchantId не может быть использован")
    void redeemCoupon_missingMerchantId_throwsException() {
        PurchasedCoupon coupon = PurchasedCoupon.builder()
                .id(1L)
                .couponCode("CP-ORPHAN1")
                .expiresAt(LocalDateTime.now().plusDays(1))
                .status(PurchasedCouponStatus.ACTIVE)
                .build();

        when(purchasedCouponRepository.findByCouponCodeForUpdate("CP-ORPHAN1")).thenReturn(Optional.of(coupon));

        assertThatThrownBy(() -> orderService.redeemCoupon("CP-ORPHAN1", 77L, "Анна"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("У купона не указан мерчант");

        verify(purchasedCouponRepository, never()).save(any(PurchasedCoupon.class));
        verify(redemptionRepository, never()).save(any(Redemption.class));
    }

    @Test
    @DisplayName("Погашение: expired coupon переводится в EXPIRED и не погашается")
    void redeemCoupon_expiredCoupon_marksExpiredAndThrows() {
        PurchasedCoupon coupon = PurchasedCoupon.builder()
                .id(1L)
                .couponCode("CP-OLD1234")
                .merchantId(77L)
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .status(PurchasedCouponStatus.ACTIVE)
                .build();

        when(purchasedCouponRepository.findByCouponCodeForUpdate("CP-OLD1234")).thenReturn(Optional.of(coupon));
        when(purchasedCouponRepository.save(any(PurchasedCoupon.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> orderService.redeemCoupon("CP-OLD1234", 77L, "Анна"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Срок действия купона истёк");

        assertThat(coupon.getStatus()).isEqualTo(PurchasedCouponStatus.EXPIRED);
        verify(redemptionRepository, never()).save(any(Redemption.class));
        // Must NOT publish CouponRedeemedEvent
        verify(rabbitTemplate, never()).convertAndSend(eq("coupon.exchange"), eq("coupon.redeemed"), any(Object.class));
    }

    // ==================== QR Token Redemption ====================

    @Test
    @DisplayName("Погашение по QR-токену: активный купон → USED")
    void redeemByQrToken_activeCoupon_marksAsUsed() {
        PurchasedCoupon coupon = PurchasedCoupon.builder()
                .id(1L).couponCode("CP-QR1234")
                .qrToken("qr-token-abc123")
                .merchantId(5L)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .status(PurchasedCouponStatus.ACTIVE).build();

        when(purchasedCouponRepository.findByQrTokenForUpdate("qr-token-abc123")).thenReturn(Optional.of(coupon));
        when(purchasedCouponRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(redemptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PurchasedCoupon result = orderService.redeemByQrToken("qr-token-abc123", 5L, "Анна");

        assertThat(result.getStatus()).isEqualTo(PurchasedCouponStatus.USED);
        assertThat(result.getUsedAt()).isNotNull();
        verify(redemptionRepository).save(any(Redemption.class));
    }

    @Test
    @DisplayName("Погашение по QR-токену: несуществующий токен → IllegalArgumentException")
    void redeemByQrToken_notFound_throwsException() {
        when(purchasedCouponRepository.findByQrTokenForUpdate("invalid-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.redeemByQrToken("invalid-token", 5L, "Анна"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("не найден");
    }

    @Test
    @DisplayName("Погашение по QR-токену: уже использованный → IllegalStateException")
    void redeemByQrToken_alreadyUsed_throwsException() {
        PurchasedCoupon coupon = PurchasedCoupon.builder()
                .id(1L).couponCode("CP-USED").qrToken("qr-used")
                .merchantId(5L).status(PurchasedCouponStatus.USED).build();

        when(purchasedCouponRepository.findByQrTokenForUpdate("qr-used")).thenReturn(Optional.of(coupon));

        assertThatThrownBy(() -> orderService.redeemByQrToken("qr-used", 5L, "Анна"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("не может быть использован");
    }

    @Test
    @DisplayName("Погашение по QR-токену: чужой merchantId → IllegalStateException")
    void redeemByQrToken_wrongMerchant_throwsException() {
        PurchasedCoupon coupon = PurchasedCoupon.builder()
                .id(1L).couponCode("CP-QR999").qrToken("qr-token-xyz")
                .merchantId(77L)
                .expiresAt(LocalDateTime.now().plusDays(1))
                .status(PurchasedCouponStatus.ACTIVE).build();

        when(purchasedCouponRepository.findByQrTokenForUpdate("qr-token-xyz")).thenReturn(Optional.of(coupon));

        assertThatThrownBy(() -> orderService.redeemByQrToken("qr-token-xyz", 88L, "Анна"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("другому мерчанту");
    }

    // ==================== Refund ====================

    @Test
    @DisplayName("Возврат: свой заказ — создаёт запрос PENDING")
    void createRefundRequest_ownOrder_success() {
        Order order = Order.builder().id(100L).userId(10L).build();
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(refundRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RefundRequest result = orderService.createRefundRequest(10L, 100L, "Не успел использовать");

        assertThat(result.getReason()).isEqualTo("Не успел использовать");
        verify(refundRequestRepository).save(any(RefundRequest.class));
    }

    @Test
    @DisplayName("Возврат: чужой заказ → IllegalStateException")
    void createRefundRequest_notOwnOrder_throwsException() {
        Order order = Order.builder().id(100L).userId(20L).build();
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.createRefundRequest(10L, 100L, "test"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("не принадлежит");
    }

    // ==================== Get Order By ID ====================

    @Test
    @DisplayName("Заказ по ID: найден и принадлежит — возвращает")
    void getOrderById_found_returnsOrder() {
        Order order = Order.builder().id(100L).userId(10L).status(OrderStatus.PAID).build();
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));

        Order result = orderService.getOrderById(100L, 10L);

        assertThat(result.getId()).isEqualTo(100L);
        assertThat(result.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("Заказ по ID: storefront DTO строится внутри сервисной транзакции")
    void getOrderResponseById_mapsOwnedOrderInService() {
        Order order = Order.builder()
                .id(100L)
                .orderNumber("ORD-100")
                .userId(10L)
                .status(OrderStatus.PAID)
                .totalAmount(BigDecimal.valueOf(49000))
                .items(List.of(
                        OrderItem.builder().couponTitle("Пицца").build(),
                        OrderItem.builder().couponTitle("Кофе").build()))
                .build();
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));

        var result = orderService.getOrderResponseById(100L, 10L);

        assertThat(result.getId()).isEqualTo(100L);
        assertThat(result.getTitle()).isEqualTo("Пицца и ещё 1");
        assertThat(result.getItemCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Заказ по ID: не найден → IllegalArgumentException")
    void getOrderById_notFound_throwsException() {
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrderById(999L, 10L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("не найден");
    }

    @Test
    @DisplayName("Заказ по ID: чужой → IllegalStateException")
    void getOrderById_notOwner_throwsException() {
        Order order = Order.builder().id(100L).userId(20L).build();
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.getOrderById(100L, 10L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("не принадлежит");
    }

    @Test
    @DisplayName("Купоны заказа: возвращает список по orderId")
    void getOrderCoupons_returnsList() {
        Order order = Order.builder().id(100L).userId(10L).build();
        PurchasedCoupon c1 = PurchasedCoupon.builder().id(1L).couponCode("CP-001").build();
        PurchasedCoupon c2 = PurchasedCoupon.builder().id(2L).couponCode("CP-002").build();

        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(purchasedCouponRepository.findByOrderId(100L)).thenReturn(List.of(c1, c2));

        List<PurchasedCoupon> result = orderService.getOrderCoupons(100L, 10L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getCouponCode()).isEqualTo("CP-001");
    }

    // ==================== Admin Orders ====================

    @Test
    @DisplayName("Admin: все заказы без фильтра — возвращает все")
    void getAllOrders_noFilter_returnsAll() {
        Order o1 = Order.builder().id(1L).userId(10L).status(OrderStatus.PAID).build();
        Order o2 = Order.builder().id(2L).userId(20L).status(OrderStatus.PENDING).build();
        Page<Order> page = new PageImpl<>(List.of(o1, o2));

        when(orderRepository.findAll(any(Pageable.class))).thenReturn(page);

        Page<AdminOrderResponse> result = orderService.getAllOrders(null, 0, 20);

        assertThat(result.getContent()).hasSize(2);
        verify(orderRepository).findAll(any(Pageable.class));
        verify(orderRepository, never()).findByStatus(any(), any());
    }

    @Test
    @DisplayName("Admin: заказы с фильтром по статусу PAID")
    void getAllOrders_withStatus_filtersCorrectly() {
        Order o1 = Order.builder().id(1L).status(OrderStatus.PAID).build();
        Page<Order> page = new PageImpl<>(List.of(o1));

        when(orderRepository.findByStatus(eq(OrderStatus.PAID), any(Pageable.class))).thenReturn(page);

        Page<AdminOrderResponse> result = orderService.getAllOrders(OrderStatus.PAID, 0, 20);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("Admin: заказ по ID — возвращает без проверки владельца")
    void getOrderByIdAdmin_found_returnsWithoutOwnerCheck() {
        Order order = Order.builder().id(100L).userId(99L).status(OrderStatus.PAID).build();
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));

        AdminOrderResponse result = orderService.getOrderByIdAdmin(100L);

        assertThat(result.getId()).isEqualTo(100L);
        assertThat(result.getUserId()).isEqualTo(99L); // не проверяем владельца
    }

    @Test
    @DisplayName("Admin: заказ по ID — не найден → IllegalArgumentException")
    void getOrderByIdAdmin_notFound_throws() {
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrderByIdAdmin(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("не найден");
    }

    @Test
    @DisplayName("Admin lookup: нормализует код и возвращает только support-поля без qrToken")
    void adminLookupByCouponCode_normalizesAndExcludesQrToken() {
        Order order = Order.builder().id(100L).build();
        PurchasedCoupon purchasedCoupon = PurchasedCoupon.builder()
                .id(501L)
                .order(order)
                .userId(10L)
                .couponOfferId(20L)
                .couponOptionId(30L)
                .couponTitle("SPA")
                .optionTitle("90 минут")
                .couponCode("CP-ABCD1234")
                .qrToken("secret-redemption-token")
                .status(PurchasedCouponStatus.ACTIVE)
                .merchantId(77L)
                .merchantName("SPA Oasis")
                .merchantAddress("Ташкент")
                .purchasedAt(LocalDateTime.of(2026, 8, 4, 10, 30))
                .build();
        when(purchasedCouponRepository.findByCouponCode("CP-ABCD1234"))
                .thenReturn(Optional.of(purchasedCoupon));

        AdminPurchasedCouponLookupResponse result =
                orderService.adminLookupByCouponCode("  cp-abcd1234  ");

        assertThat(result.getPurchasedCouponId()).isEqualTo(501L);
        assertThat(result.getOrderId()).isEqualTo(100L);
        assertThat(result.getCouponCode()).isEqualTo("CP-ABCD1234");
        assertThat(result.getMerchantId()).isEqualTo(77L);
        assertThat(AdminPurchasedCouponLookupResponse.class.getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .doesNotContain("qrToken");
        verify(purchasedCouponRepository).findByCouponCode("CP-ABCD1234");
    }

    @Test
    @DisplayName("Admin lookup: неизвестный нормализованный код отклоняется")
    void adminLookupByCouponCode_notFound_throws() {
        when(purchasedCouponRepository.findByCouponCode("CP-NOTFOUND"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.adminLookupByCouponCode(" cp-notfound "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("не найден");

        verify(purchasedCouponRepository).findByCouponCode("CP-NOTFOUND");
    }

    // ==================== Task 1: Checkout Revalidation Regression ====================

    @Test
    @DisplayName("Checkout: coupon-service returns empty snapshot -> rejects without creating order")
    void createOrder_missingSnapshot_rejectsWithoutSideEffects() {
        CartItem item = CartItem.builder()
                .couponOfferId(5L).couponOptionId(3L)
                .couponTitle("Old title").optionTitle("Old option")
                .unitPrice(BigDecimal.valueOf(99000)).quantity(1)
                .gift(false).build();
        Cart cart = Cart.builder().id(1L).userId(10L)
                .items(new ArrayList<>(List.of(item))).build();

        when(cartRepository.findByUserId(10L)).thenReturn(Optional.of(cart));
        when(couponClient.getPurchaseSnapshot(5L, 3L)).thenReturn(ApiResponse.success(null));

        assertThatThrownBy(() -> orderService.createOrder(10L, "user@test.com", "+998901234567"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Купон недоступен для покупки");

        verify(orderRepository, never()).save(any(Order.class));
        verify(cartRepository, never()).save(argThat(savedCart -> savedCart.getItems().isEmpty()));
        verify(rabbitTemplate, never()).convertAndSend(eq("order.exchange"), eq("order.created"), any(Object.class));
    }

    @Test
    @DisplayName("Checkout: option became inactive -> rejects without creating order")
    void createOrder_optionInactive_rejectsWithoutSideEffects() {
        CartItem item = CartItem.builder()
                .couponOfferId(5L).couponOptionId(3L)
                .couponTitle("Old title").optionTitle("Old option")
                .unitPrice(BigDecimal.valueOf(99000)).quantity(1)
                .gift(false).build();
        Cart cart = Cart.builder().id(1L).userId(10L)
                .items(new ArrayList<>(List.of(item))).build();

        CouponPurchaseSnapshot snapshot = activeSnapshot();
        snapshot.setOptionStatus("INACTIVE");

        when(cartRepository.findByUserId(10L)).thenReturn(Optional.of(cart));
        when(couponClient.getPurchaseSnapshot(5L, 3L)).thenReturn(snapshotResponse(snapshot));

        assertThatThrownBy(() -> orderService.createOrder(10L, "user@test.com", "+998901234567"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Опция купона недоступна для покупки");

        verify(orderRepository, never()).save(any(Order.class));
        verify(cartRepository, never()).save(argThat(savedCart -> savedCart.getItems().isEmpty()));
        verify(rabbitTemplate, never()).convertAndSend(eq("order.exchange"), eq("order.created"), any(Object.class));
    }

    @Test
    @DisplayName("Checkout: buyUntil already passed -> rejects without creating order")
    void createOrder_buyUntilExpired_rejectsWithoutSideEffects() {
        CartItem item = CartItem.builder()
                .couponOfferId(5L).couponOptionId(3L)
                .couponTitle("SPA").optionTitle("Standard")
                .unitPrice(BigDecimal.valueOf(99000)).quantity(1)
                .gift(false).build();
        Cart cart = Cart.builder().id(1L).userId(10L)
                .items(new ArrayList<>(List.of(item))).build();

        CouponPurchaseSnapshot snapshot = activeSnapshot();
        snapshot.setBuyUntil(LocalDateTime.now().minusMinutes(1));

        when(cartRepository.findByUserId(10L)).thenReturn(Optional.of(cart));
        when(couponClient.getPurchaseSnapshot(5L, 3L)).thenReturn(snapshotResponse(snapshot));

        assertThatThrownBy(() -> orderService.createOrder(10L, "user@test.com", "+998901234567"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Срок покупки купона истёк");

        verify(orderRepository, never()).save(any(Order.class));
        verify(rabbitTemplate, never()).convertAndSend(eq("order.exchange"), eq("order.created"), any(Object.class));
    }

    @Test
    @DisplayName("Checkout: requested quantity exceeds remaining limit -> rejects without creating order")
    void createOrder_quantityLimitExceeded_rejectsWithoutSideEffects() {
        CartItem item = CartItem.builder()
                .couponOfferId(5L).couponOptionId(3L)
                .couponTitle("SPA").optionTitle("Standard")
                .unitPrice(BigDecimal.valueOf(99000)).quantity(9)
                .gift(false).build();
        Cart cart = Cart.builder().id(1L).userId(10L)
                .items(new ArrayList<>(List.of(item))).build();

        CouponPurchaseSnapshot snapshot = activeSnapshot();
        snapshot.setQuantityLimit(10);
        snapshot.setQuantitySold(2);

        when(cartRepository.findByUserId(10L)).thenReturn(Optional.of(cart));
        when(couponClient.getPurchaseSnapshot(5L, 3L)).thenReturn(snapshotResponse(snapshot));

        assertThatThrownBy(() -> orderService.createOrder(10L, "user@test.com", "+998901234567"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Недостаточно купонов в наличии");

        verify(orderRepository, never()).save(any(Order.class));
        verify(rabbitTemplate, never()).convertAndSend(eq("order.exchange"), eq("order.created"), any(Object.class));
    }

    // ==================== Task 2: Coupon Generation & Sale Registration ====================

    @Test
    @DisplayName("Генерация купонов: registerSale failure logs but still returns created purchased coupons")
    void generateCoupons_registerSaleFailure_stillReturnsPurchasedCoupons() {
        OrderItem item = OrderItem.builder()
                .couponOfferId(1L).couponOptionId(2L)
                .couponTitle("SPA").optionTitle("Standard")
                .unitPrice(BigDecimal.valueOf(150000)).quantity(1)
                .merchantId(77L)
                .expiresAt(LocalDateTime.now().plusDays(10))
                .gift(false).build();
        Order order = Order.builder().id(100L).userId(10L)
                .userEmail("a@b.com").userPhone("+998901234567")
                .items(List.of(item)).status(OrderStatus.PENDING).build();

        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(purchasedCouponRepository.findByOrderId(100L)).thenReturn(List.of());
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(purchasedCouponRepository.save(any(PurchasedCoupon.class))).thenAnswer(inv -> {
            PurchasedCoupon coupon = inv.getArgument(0);
            coupon.setId(501L);
            return coupon;
        });
        doThrow(new RuntimeException("coupon-service unavailable"))
                .when(couponClient)
                .registerSale(eq(1L), eq(2L), any());

        List<PurchasedCoupon> result = orderService.generatePurchasedCoupons(100L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(PurchasedCouponStatus.ACTIVE);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(rabbitTemplate).convertAndSend(eq("coupon.exchange"), eq("coupon.purchased"), any(Object.class));
    }

    @Test
    @DisplayName("Генерация купонов: existing purchased coupons skips registerSale to avoid duplicate sale count")
    void generateCoupons_existingCoupons_skipsSaleRegistration() {
        Order order = Order.builder().id(100L).userId(10L)
                .status(OrderStatus.PAID).items(List.of()).build();
        PurchasedCoupon existing = PurchasedCoupon.builder()
                .id(1L)
                .order(order)
                .couponCode("CP-EXISTING")
                .status(PurchasedCouponStatus.ACTIVE)
                .build();

        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(purchasedCouponRepository.findByOrderId(100L)).thenReturn(List.of(existing));

        List<PurchasedCoupon> result = orderService.generatePurchasedCoupons(100L);

        assertThat(result).containsExactly(existing);
        verify(couponClient, never()).registerSale(anyLong(), anyLong(), any());
        verify(rabbitTemplate, never()).convertAndSend(eq("coupon.exchange"), eq("coupon.purchased"), any(Object.class));
    }

    // ==================== Task 4: Redemption Access Context ====================

    @Test
    @DisplayName("Погашение: сохраняет staffId, merchantLocationId and method from access context")
    void redeemCoupon_withAccessContext_savesRedemptionContext() {
        PurchasedCoupon coupon = PurchasedCoupon.builder()
                .id(1L)
                .couponCode("CP-CONTEXT1")
                .merchantId(77L)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .status(PurchasedCouponStatus.ACTIVE)
                .build();

        when(purchasedCouponRepository.findByCouponCodeForUpdate("CP-CONTEXT1")).thenReturn(Optional.of(coupon));
        when(purchasedCouponRepository.save(any(PurchasedCoupon.class))).thenAnswer(inv -> inv.getArgument(0));
        when(redemptionRepository.save(any(Redemption.class))).thenAnswer(inv -> inv.getArgument(0));

        orderService.redeemCoupon("CP-CONTEXT1", 77L, "Кассир Али", 200L, 5L, "PIN");

        verify(redemptionRepository).save(argThat(redemption ->
                redemption.getMerchantId().equals(77L)
                        && redemption.getMerchantLocationId().equals(200L)
                        && redemption.getStaffId().equals(5L)
                        && redemption.getRedeemedByStaff().equals("Кассир Али")
                        && redemption.getRedeemMethod().equals("PIN")
        ));
    }

    // ==================== Review Eligibility ====================

    @Test
    @DisplayName("Review eligibility: USED coupon → eligible with purchasedCouponId")
    void getReviewEligibility_usedCoupon_returnsEligible() {
        PurchasedCoupon pc = PurchasedCoupon.builder()
                .id(42L).userId(10L).couponOfferId(5L)
                .status(PurchasedCouponStatus.USED)
                .usedAt(LocalDateTime.now().minusDays(1))
                .build();

        when(purchasedCouponRepository.findFirstByUserIdAndCouponOfferIdAndStatusOrderByUsedAtDesc(
                10L, 5L, PurchasedCouponStatus.USED)).thenReturn(Optional.of(pc));

        var result = orderService.getReviewEligibility(10L, 5L);

        assertThat(result.isEligible()).isTrue();
        assertThat(result.getReason()).isEqualTo("USED_COUPON_FOUND");
        assertThat(result.getPurchasedCouponId()).isEqualTo(42L);
        assertThat(result.getUsedAt()).isNotNull();
    }

    @Test
    @DisplayName("Review eligibility: ACTIVE coupon → not eligible (not used yet)")
    void getReviewEligibility_activeCoupon_returnsNotEligible() {
        // ACTIVE coupon exists but is not USED — query returns empty
        when(purchasedCouponRepository.findFirstByUserIdAndCouponOfferIdAndStatusOrderByUsedAtDesc(
                10L, 5L, PurchasedCouponStatus.USED)).thenReturn(Optional.empty());

        var result = orderService.getReviewEligibility(10L, 5L);

        assertThat(result.isEligible()).isFalse();
        assertThat(result.getReason()).isEqualTo("REVIEW_ALLOWED_AFTER_COUPON_USAGE");
        assertThat(result.getPurchasedCouponId()).isNull();
    }

    @Test
    @DisplayName("Review eligibility: other user's USED coupon → not eligible")
    void getReviewEligibility_otherUserCoupon_returnsNotEligible() {
        // User 10 asks about coupon used by user 99
        when(purchasedCouponRepository.findFirstByUserIdAndCouponOfferIdAndStatusOrderByUsedAtDesc(
                10L, 5L, PurchasedCouponStatus.USED)).thenReturn(Optional.empty());

        var result = orderService.getReviewEligibility(10L, 5L);

        assertThat(result.isEligible()).isFalse();
    }

    @Test
    @DisplayName("Review eligibility: no purchased coupon at all → not eligible")
    void getReviewEligibility_noPurchase_returnsNotEligible() {
        when(purchasedCouponRepository.findFirstByUserIdAndCouponOfferIdAndStatusOrderByUsedAtDesc(
                10L, 999L, PurchasedCouponStatus.USED)).thenReturn(Optional.empty());

        var result = orderService.getReviewEligibility(10L, 999L);

        assertThat(result.isEligible()).isFalse();
        assertThat(result.getReason()).isEqualTo("REVIEW_ALLOWED_AFTER_COUPON_USAGE");
    }

    // ==================== Profile-facing fields (title, pricePaid) ====================

    @Test
    @DisplayName("История заказов: DTO строятся в сервисе, позиции загружаются одним batch-запросом")
    void getUserOrders_mapsInsideServiceWithSingleItemsQuery() {
        Order first = Order.builder()
                .id(11L).orderNumber("ORD-11").userId(7L).status(OrderStatus.PAID)
                .totalAmount(BigDecimal.valueOf(49000))
                .createdAt(LocalDateTime.of(2026, 7, 25, 10, 0))
                .build();
        Order second = Order.builder()
                .id(12L).orderNumber("ORD-12").userId(7L).status(OrderStatus.COMPLETED)
                .totalAmount(BigDecimal.valueOf(90000))
                .createdAt(LocalDateTime.of(2026, 7, 24, 10, 0))
                .build();
        OrderItem firstItem = OrderItem.builder().id(101L).order(first).couponTitle("Пицца").build();
        OrderItem secondItem = OrderItem.builder().id(102L).order(first).couponTitle("Кофе").build();
        OrderItem thirdItem = OrderItem.builder().id(103L).order(second).couponTitle("Спа").build();
        PageRequest pageRequest = PageRequest.of(
                0, 20, org.springframework.data.domain.Sort.by("createdAt").descending());
        when(orderRepository.findByUserId(7L, pageRequest))
                .thenReturn(new PageImpl<>(List.of(first, second), pageRequest, 2));
        when(orderItemRepository.findForOrderIds(List.of(11L, 12L)))
                .thenReturn(List.of(firstItem, secondItem, thirdItem));

        Page<uz.topdim.order.dto.OrderResponse> result =
                orderService.getUserOrders(7L, 0, 20);

        assertThat(result.getContent()).extracting(
                uz.topdim.order.dto.OrderResponse::getTitle,
                uz.topdim.order.dto.OrderResponse::getItemCount)
                .containsExactly(
                        tuple("Пицца и ещё 1", 2),
                        tuple("Спа", 1));
        verify(orderItemRepository).findForOrderIds(List.of(11L, 12L));
    }

    @Test
    @DisplayName("История заказов: пустая страница не запускает запрос позиций")
    void getUserOrders_emptyPage_skipsItemsQuery() {
        PageRequest pageRequest = PageRequest.of(
                0, 20, org.springframework.data.domain.Sort.by("createdAt").descending());
        when(orderRepository.findByUserId(7L, pageRequest))
                .thenReturn(Page.empty(pageRequest));

        Page<uz.topdim.order.dto.OrderResponse> result =
                orderService.getUserOrders(7L, 0, 20);

        assertThat(result).isEmpty();
        verifyNoInteractions(orderItemRepository);
    }

    @Test
    @DisplayName("OrderResponse.title: одна позиция → её название без «и ещё»")
    void mapToOrderResponse_singleItem_titleIsItemName() {
        Order order = Order.builder()
                .id(1L).orderNumber("ORD-1").status(OrderStatus.PAID)
                .totalAmount(BigDecimal.valueOf(49000))
                .items(List.of(OrderItem.builder().couponTitle("Пицца 30см").build()))
                .createdAt(LocalDateTime.now())
                .build();

        assertThat(orderService.mapToOrderResponse(order).getTitle()).isEqualTo("Пицца 30см");
    }

    @Test
    @DisplayName("OrderResponse.title: несколько позиций → «первая и ещё N»")
    void mapToOrderResponse_multipleItems_titleHasRemainderCount() {
        Order order = Order.builder()
                .id(2L).orderNumber("ORD-2").status(OrderStatus.PAID)
                .totalAmount(BigDecimal.valueOf(90000))
                .items(List.of(
                        OrderItem.builder().couponTitle("Пицца").build(),
                        OrderItem.builder().couponTitle("Суши").build(),
                        OrderItem.builder().couponTitle("Кофе").build()))
                .createdAt(LocalDateTime.now())
                .build();

        assertThat(orderService.mapToOrderResponse(order).getTitle()).isEqualTo("Пицца и ещё 2");
    }

    @Test
    @DisplayName("OrderResponse.title: заказ без позиций → null (фронт откатится на «Заказ №»)")
    void mapToOrderResponse_noItems_titleIsNull() {
        Order order = Order.builder()
                .id(3L).orderNumber("ORD-3").status(OrderStatus.PAID)
                .totalAmount(BigDecimal.ZERO)
                .items(new ArrayList<>())
                .createdAt(LocalDateTime.now())
                .build();

        assertThat(orderService.mapToOrderResponse(order).getTitle()).isNull();
    }

    @Test
    @DisplayName("PurchasedCouponResponse.pricePaid: пробрасывается из entity")
    void mapToCouponResponse_exposesPricePaid() {
        PurchasedCoupon coupon = PurchasedCoupon.builder()
                .id(5L).couponOfferId(1L).couponOptionId(2L)
                .couponTitle("Пицца").optionTitle("30см")
                .pricePaid(BigDecimal.valueOf(49000))
                .couponCode("ABC-123").qrToken("qr")
                .status(PurchasedCouponStatus.ACTIVE)
                .build();
        when(refundRequestRepository.findByPurchasedCouponIdOrderByCreatedAtDesc(5L))
                .thenReturn(List.of());

        PurchasedCouponResponse response = orderService.mapToCouponResponse(coupon);

        assertThat(response.getPricePaid()).isEqualByComparingTo(BigDecimal.valueOf(49000));
    }
}
