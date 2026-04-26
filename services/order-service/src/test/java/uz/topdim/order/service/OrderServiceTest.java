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
import org.springframework.data.domain.Pageable;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.order.client.CouponClient;
import uz.topdim.order.client.CouponPurchaseSnapshot;
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

        when(purchasedCouponRepository.findByCouponCode("CP-TEST1234")).thenReturn(Optional.of(coupon));
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

        when(purchasedCouponRepository.findByCouponCode("CP-USED1234")).thenReturn(Optional.of(coupon));

        assertThatThrownBy(() -> orderService.redeemCoupon("CP-USED1234", 5L, "Анна"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("не может быть использован");
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

        when(purchasedCouponRepository.findByCouponCode("CP-TEST1234")).thenReturn(Optional.of(coupon));

        assertThatThrownBy(() -> orderService.redeemCoupon("CP-TEST1234", 88L, "Анна"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Купон принадлежит другому мерчанту");

        verify(redemptionRepository, never()).save(any(Redemption.class));
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

        when(purchasedCouponRepository.findByCouponCode("CP-ORPHAN1")).thenReturn(Optional.of(coupon));

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

        when(purchasedCouponRepository.findByCouponCode("CP-OLD1234")).thenReturn(Optional.of(coupon));
        when(purchasedCouponRepository.save(any(PurchasedCoupon.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> orderService.redeemCoupon("CP-OLD1234", 77L, "Анна"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Срок действия купона истёк");

        assertThat(coupon.getStatus()).isEqualTo(PurchasedCouponStatus.EXPIRED);
        verify(redemptionRepository, never()).save(any(Redemption.class));
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

        Page<Order> result = orderService.getAllOrders(null, 0, 20);

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

        Page<Order> result = orderService.getAllOrders(OrderStatus.PAID, 0, 20);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("Admin: заказ по ID — возвращает без проверки владельца")
    void getOrderByIdAdmin_found_returnsWithoutOwnerCheck() {
        Order order = Order.builder().id(100L).userId(99L).status(OrderStatus.PAID).build();
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));

        Order result = orderService.getOrderByIdAdmin(100L);

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
}
