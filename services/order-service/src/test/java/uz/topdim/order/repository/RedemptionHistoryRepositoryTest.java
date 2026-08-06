package uz.topdim.order.repository;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uz.topdim.order.entity.Order;
import uz.topdim.order.entity.OrderStatus;
import uz.topdim.order.entity.PurchasedCoupon;
import uz.topdim.order.entity.PurchasedCouponStatus;
import uz.topdim.order.entity.Redemption;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.data.domain.Sort.Order.desc;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class RedemptionHistoryRepositoryTest {

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
    @Autowired private PurchasedCouponRepository purchasedCouponRepository;
    @Autowired private RedemptionRepository redemptionRepository;
    @Autowired private EntityManagerFactory entityManagerFactory;

    @BeforeEach
    void seedHistory() {
        seedRedemption(77L, 5L, "CP-VIP-001", "VIP-1",
                LocalDateTime.of(2026, 8, 6, 12, 30));
        seedRedemption(77L, 6L, "CP-VIP-STAFF-6", "VIP-6",
                LocalDateTime.of(2026, 8, 5, 11, 0));
        seedRedemption(88L, 5L, "CP-VIP-OTHER", "VIP-OTHER",
                LocalDateTime.of(2026, 8, 5, 10, 0));
        seedRedemption(77L, 5L, "LITERAL%_CODE", "LITERAL",
                LocalDateTime.of(2026, 8, 4, 9, 0));
        seedRedemption(77L, 5L, "CP-START", "START",
                LocalDateTime.of(2026, 8, 1, 0, 0));
        seedRedemption(77L, 5L, "CP-END", "END",
                LocalDateTime.of(2026, 8, 6, 23, 59, 59));
        seedRedemption(77L, 5L, "CP-BEFORE", "BEFORE",
                LocalDateTime.of(2026, 7, 31, 23, 59, 59));
        seedRedemption(77L, 5L, "CP-AFTER", "AFTER",
                LocalDateTime.of(2026, 8, 7, 0, 0));
        seedRedemption(77L, 5L, "CP-TIE-1", "TIE-1",
                LocalDateTime.of(2026, 8, 3, 12, 0));
        seedRedemption(77L, 5L, "CP-TIE-2", "TIE-2",
                LocalDateTime.of(2026, 8, 3, 12, 0));
    }

    @Test
    @DisplayName("history query keeps merchant and cashier scope while filtering code")
    void findHistory_scopesMerchantAndStaffAndMatchesCodeIgnoringCase() {
        Page<Redemption> result = redemptionRepository.findHistory(
                77L, 5L, "cp-vip", null, null,
                PageRequest.of(0, 20, newestFirst()));

        assertThat(result.getContent())
                .extracting(r -> r.getPurchasedCoupon().getCouponCode())
                .containsExactly("CP-VIP-001");
    }

    @Test
    @DisplayName("history query treats percent and underscore as literal characters")
    void findHistory_escapedWildcardsAreLiteral() {
        Page<Redemption> result = redemptionRepository.findHistory(
                77L, null, "literal!%!_code", null, null,
                PageRequest.of(0, 20));

        assertThat(result.getContent())
                .extracting(r -> r.getPurchasedCoupon().getCouponCode())
                .containsExactly("LITERAL%_CODE");
    }

    @Test
    @DisplayName("history query includes both selected calendar-date boundaries")
    void findHistory_filtersInclusiveRange() {
        Page<Redemption> result = redemptionRepository.findHistory(
                77L, null, null,
                LocalDateTime.of(2026, 8, 1, 0, 0),
                LocalDateTime.of(2026, 8, 7, 0, 0),
                PageRequest.of(0, 20, newestFirst()));

        assertThat(result.getContent())
                .extracting(r -> r.getPurchasedCoupon().getCouponCode())
                .contains("CP-START", "CP-END")
                .doesNotContain("CP-BEFORE", "CP-AFTER");
    }

    @Test
    @DisplayName("history query orders equal timestamps by newest id")
    void findHistory_ordersEqualTimestampsById() {
        Page<Redemption> result = redemptionRepository.findHistory(
                77L, null, "cp-tie", null, null,
                PageRequest.of(0, 20, newestFirst()));

        assertThat(result.getContent())
                .extracting(r -> r.getPurchasedCoupon().getCouponCode())
                .containsExactly("CP-TIE-2", "CP-TIE-1");
    }

    @Test
    @DisplayName("history query combines filters and eagerly loads coupon data")
    void findHistory_combinesFiltersAndLoadsPurchasedCoupon() {
        Page<Redemption> result = redemptionRepository.findHistory(
                77L, null, "cp-vip",
                LocalDateTime.of(2026, 8, 1, 0, 0),
                LocalDateTime.of(2026, 8, 7, 0, 0),
                PageRequest.of(0, 20, newestFirst()));

        assertThat(result.getContent())
                .extracting(r -> r.getPurchasedCoupon().getCouponCode())
                .containsExactly("CP-VIP-001", "CP-VIP-STAFF-6");
        assertThat(result.getContent())
                .allMatch(r -> entityManagerFactory.getPersistenceUnitUtil()
                        .isLoaded(r, "purchasedCoupon"));
    }

    private Sort newestFirst() {
        return Sort.by(desc("redeemedAt"), desc("id"));
    }

    private Redemption seedRedemption(
            long merchantId, Long staffId, String couponCode,
            String suffix, LocalDateTime redeemedAt) {
        Order order = orderRepository.save(Order.builder()
                .orderNumber("ORD-HISTORY-" + suffix)
                .userId(10L)
                .totalAmount(BigDecimal.valueOf(100_000))
                .status(OrderStatus.PAID)
                .build());
        PurchasedCoupon coupon = purchasedCouponRepository.save(PurchasedCoupon.builder()
                .userId(10L)
                .order(order)
                .couponOfferId(20L)
                .couponOptionId(30L)
                .couponTitle("History coupon " + suffix)
                .optionTitle("VIP")
                .couponCode(couponCode)
                .qrToken("history-qr-" + suffix)
                .merchantId(merchantId)
                .status(PurchasedCouponStatus.USED)
                .build());
        return redemptionRepository.saveAndFlush(Redemption.builder()
                .purchasedCoupon(coupon)
                .redemptionCode("RED-" + suffix)
                .merchantId(merchantId)
                .staffId(staffId)
                .redeemedByStaff("Cashier " + staffId)
                .redeemMethod("PIN")
                .redeemedAt(redeemedAt)
                .build());
    }
}
