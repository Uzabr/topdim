package uz.topdim.order.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uz.topdim.order.entity.Order;
import uz.topdim.order.entity.OrderItem;
import uz.topdim.order.entity.OrderStatus;
import uz.topdim.order.entity.PurchasedCoupon;
import uz.topdim.order.entity.PurchasedCouponStatus;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class PartnerRevenueRepositoryTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private PurchasedCouponRepository purchasedCouponRepository;

    @Test
    @DisplayName("Revenue uses paid coupons, excludes unpaid orders and completed refunds")
    void sumRevenueByMerchantId_countsOnlyNonRefundedPurchasedCoupons() {
        Long merchantId = 77L;
        Order pendingOrder = saveOrder("REV-PENDING", OrderStatus.PENDING);
        saveOrderItem(pendingOrder, merchantId, "10000", 2);

        Order paidOrder = saveOrder("REV-PAID", OrderStatus.PAID);
        saveOrderItem(paidOrder, merchantId, "5000", 1);
        saveCoupon(paidOrder, merchantId, "ACTIVE", "700", PurchasedCouponStatus.ACTIVE);
        saveCoupon(paidOrder, merchantId, "USED", "800", PurchasedCouponStatus.USED);
        saveCoupon(paidOrder, merchantId, "REFUND-PENDING", "900", PurchasedCouponStatus.REFUND_PENDING);
        saveCoupon(paidOrder, merchantId, "REFUNDED", "1000", PurchasedCouponStatus.REFUNDED);
        saveCoupon(paidOrder, merchantId, "CANCELLED", "1100", PurchasedCouponStatus.CANCELLED);

        BigDecimal revenue = purchasedCouponRepository.sumRevenueByMerchantId(merchantId);

        assertThat(revenue).isEqualByComparingTo("2400");
    }

    @Test
    @DisplayName("Revenue is zero when merchant has only refunded or cancelled coupons")
    void sumRevenueByMerchantId_onlyReversedCoupons_returnsZero() {
        Long merchantId = 88L;
        Order paidOrder = saveOrder("REV-REVERSED", OrderStatus.PAID);
        saveCoupon(paidOrder, merchantId, "REFUNDED-ONLY", "1000", PurchasedCouponStatus.REFUNDED);
        saveCoupon(paidOrder, merchantId, "CANCELLED-ONLY", "1100", PurchasedCouponStatus.CANCELLED);

        BigDecimal revenue = purchasedCouponRepository.sumRevenueByMerchantId(merchantId);

        assertThat(revenue).isEqualByComparingTo(BigDecimal.ZERO);
    }

    private Order saveOrder(String orderNumber, OrderStatus status) {
        return orderRepository.save(Order.builder()
                .orderNumber(orderNumber)
                .userId(10L)
                .totalAmount(BigDecimal.valueOf(100_000))
                .status(status)
                .build());
    }

    private void saveOrderItem(Order order, Long merchantId, String price, int quantity) {
        orderItemRepository.save(OrderItem.builder()
                .order(order)
                .couponOfferId(20L)
                .couponOptionId(30L)
                .unitPrice(new BigDecimal(price))
                .quantity(quantity)
                .merchantId(merchantId)
                .build());
    }

    private void saveCoupon(Order order, Long merchantId, String suffix, String price,
                            PurchasedCouponStatus status) {
        purchasedCouponRepository.save(PurchasedCoupon.builder()
                .userId(10L)
                .order(order)
                .couponOfferId(20L)
                .couponOptionId(30L)
                .couponCode("CP-" + suffix)
                .qrToken("qr-" + suffix)
                .pricePaid(new BigDecimal(price))
                .merchantId(merchantId)
                .status(status)
                .build());
    }
}
