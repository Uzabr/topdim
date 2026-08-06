package uz.topdim.order.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uz.topdim.order.entity.Order;
import uz.topdim.order.entity.OrderStatus;
import uz.topdim.order.entity.PurchasedCoupon;
import uz.topdim.order.entity.PurchasedCouponStatus;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PurchasedCouponRepositoryLockTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private PurchasedCouponRepository purchasedCouponRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("PIN lookup serializes concurrent redemption attempts")
    void findByCouponCodeForUpdate_serializesConcurrentTransactions() throws Exception {
        seedCoupon("LOCK-PIN-1", "lock-qr-pin-1");

        assertSerializesConcurrentLookup(
                () -> purchasedCouponRepository.findByCouponCodeForUpdate("LOCK-PIN-1"));
    }

    @Test
    @DisplayName("QR lookup serializes concurrent redemption attempts")
    void findByQrTokenForUpdate_serializesConcurrentTransactions() throws Exception {
        seedCoupon("LOCK-QR-1", "lock-qr-token-1");

        assertSerializesConcurrentLookup(
                () -> purchasedCouponRepository.findByQrTokenForUpdate("lock-qr-token-1"));
    }

    private void seedCoupon(String couponCode, String qrToken) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            Order order = orderRepository.saveAndFlush(Order.builder()
                    .orderNumber("ORD-" + couponCode)
                    .userId(10L)
                    .totalAmount(BigDecimal.valueOf(100_000))
                    .status(OrderStatus.PAID)
                    .build());
            purchasedCouponRepository.saveAndFlush(PurchasedCoupon.builder()
                    .userId(10L)
                    .order(order)
                    .couponOfferId(20L)
                    .couponOptionId(30L)
                    .couponCode(couponCode)
                    .qrToken(qrToken)
                    .merchantId(40L)
                    .status(PurchasedCouponStatus.ACTIVE)
                    .build());
        });
    }

    private void assertSerializesConcurrentLookup(
            Supplier<Optional<PurchasedCoupon>> lockedLookup) throws Exception {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        CountDownLatch firstHasLock = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);

        try {
            var first = executor.submit(() -> {
                transaction.executeWithoutResult(status -> {
                    PurchasedCoupon coupon = lockedLookup.get().orElseThrow();
                    coupon.setStatus(PurchasedCouponStatus.USED);
                    firstHasLock.countDown();
                    await(releaseFirst);
                });
                return null;
            });

            assertThat(firstHasLock.await(5, TimeUnit.SECONDS)).isTrue();

            var second = executor.submit(() -> {
                secondStarted.countDown();
                return transaction.execute(status ->
                        lockedLookup.get().orElseThrow().getStatus());
            });

            assertThat(secondStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> second.get(300, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            releaseFirst.countDown();
            first.get(5, TimeUnit.SECONDS);
            assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo(PurchasedCouponStatus.USED);
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for concurrent transaction");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrent redemption test was interrupted", exception);
        }
    }
}
