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
import uz.topdim.order.entity.*;
import uz.topdim.order.repository.*;

import java.math.BigDecimal;
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

    @Test
    @DisplayName("Добавление в корзину: создаёт CartItem")
    void addToCart_createsCartItem() {
        Cart cart = Cart.builder().id(1L).userId(10L).items(new ArrayList<>()).build();
        when(cartRepository.findByUserId(10L)).thenReturn(Optional.of(cart));
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        Cart result = orderService.addToCart(10L, 5L, 3L,
                "SPA купон", "Стандарт", BigDecimal.valueOf(150000), 1, false, null, null);

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getCouponOfferId()).isEqualTo(5L);
    }

    // ==================== Checkout ====================

    @Test
    @DisplayName("Checkout: успешный — создаёт заказ и публикует событие")
    void createOrder_success_createsOrderAndPublishesEvent() {
        CartItem item = CartItem.builder()
                .couponOfferId(1L).couponOptionId(2L)
                .couponTitle("SPA").optionTitle("Стандарт")
                .unitPrice(BigDecimal.valueOf(150000)).quantity(2)
                .gift(false).build();

        Cart cart = Cart.builder().id(1L).userId(10L)
                .items(new ArrayList<>(List.of(item))).build();

        when(cartRepository.findByUserId(10L)).thenReturn(Optional.of(cart));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(100L);
            return o;
        });
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        Order order = orderService.createOrder(10L, "user@test.com", "+998901234567");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(300000));
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

    // ==================== Redemption ====================

    @Test
    @DisplayName("Погашение: активный купон → USED")
    void redeemCoupon_activeCoupon_marksAsUsed() {
        PurchasedCoupon coupon = PurchasedCoupon.builder()
                .id(1L).couponCode("CP-TEST1234").status(PurchasedCouponStatus.ACTIVE).build();

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
