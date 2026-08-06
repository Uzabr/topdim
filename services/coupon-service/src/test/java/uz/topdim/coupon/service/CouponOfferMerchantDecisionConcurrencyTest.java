package uz.topdim.coupon.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uz.topdim.coupon.dto.CouponOfferResponse;
import uz.topdim.coupon.entity.CouponOffer;
import uz.topdim.coupon.entity.CouponStatus;
import uz.topdim.coupon.entity.Merchant;
import uz.topdim.coupon.entity.MerchantLocation;
import uz.topdim.coupon.repository.CouponOfferRepository;
import uz.topdim.coupon.repository.MerchantLocationRepository;
import uz.topdim.coupon.repository.MerchantRepository;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(CouponOfferService.class)
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CouponOfferMerchantDecisionConcurrencyTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private CouponOfferService couponOfferService;
    @SpyBean private CouponOfferRepository couponOfferRepository;
    @Autowired private MerchantRepository merchantRepository;
    @Autowired private MerchantLocationRepository merchantLocationRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockBean private TelegramPreviewService telegramPreviewService;
    @MockBean private CouponCoverFallbackService couponCoverFallbackService;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @AfterEach
    void shutDownExecutor() {
        executor.shutdownNow();
    }

    @Test
    @DisplayName("Concurrent approve and revision have exactly one successful transition")
    void approveAndRevision_concurrently_haveOneWinnerAndOneConflict() throws Exception {
        Long offerId = seedWaitingOffer();
        CountDownLatch bothReadWaiting = new CountDownLatch(2);
        doAnswer(invocation -> {
            Optional<CouponOffer> result = Optional.ofNullable(
                    entityManager.find(CouponOffer.class, invocation.getArgument(0)));
            if (result.filter(offer -> offer.getStatus() == CouponStatus.WAITING_FOR_MERCHANT).isPresent()) {
                bothReadWaiting.countDown();
                assertThat(bothReadWaiting.await(5, TimeUnit.SECONDS))
                        .as("both transactions must observe WAITING before deciding")
                        .isTrue();
            }
            return result;
        }).when(couponOfferRepository).findById(offerId);

        CountDownLatch start = new CountDownLatch(1);
        Future<DecisionOutcome> approve = executor.submit(() -> decide(start, offerId, true));
        Future<DecisionOutcome> revision = executor.submit(() -> decide(start, offerId, false));
        start.countDown();

        List<DecisionOutcome> outcomes = List.of(
                approve.get(10, TimeUnit.SECONDS),
                revision.get(10, TimeUnit.SECONDS));

        assertThat(outcomes).filteredOn(DecisionOutcome::succeeded).hasSize(1);
        assertThat(outcomes)
                .filteredOn(outcome -> outcome.error() instanceof IllegalStateException)
                .hasSize(1);

        CouponStatus finalStatus = couponOfferRepository.findById(offerId).orElseThrow().getStatus();
        assertThat(finalStatus).isIn(CouponStatus.ACTIVE, CouponStatus.REVISION_REQUESTED);
    }

    private Long seedWaitingOffer() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return transaction.execute(status -> {
            Merchant merchant = merchantRepository.save(Merchant.builder()
                    .name("Concurrent merchant decision")
                    .active(true)
                    .build());
            merchantLocationRepository.save(MerchantLocation.builder()
                    .merchant(merchant)
                    .title("Главный филиал")
                    .address("Ташкент, ул. Амира Темура, 10")
                    .primary(true)
                    .active(true)
                    .build());
            return couponOfferRepository.save(CouponOffer.builder()
                    .title("Concurrent merchant offer")
                    .offerDescription("Approve or return for revision exactly once")
                    .merchant(merchant)
                    .status(CouponStatus.WAITING_FOR_MERCHANT)
                    .build()).getId();
        });
    }

    private DecisionOutcome decide(CountDownLatch start, Long offerId, boolean approve) {
        try {
            assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
            CouponOfferResponse response = approve
                    ? couponOfferService.approveByMerchant(offerId)
                    : couponOfferService.requestRevisionByMerchant(offerId, "Исправить цену");
            return new DecisionOutcome(response, null);
        } catch (Throwable error) {
            return new DecisionOutcome(null, error);
        }
    }

    private record DecisionOutcome(CouponOfferResponse response, Throwable error) {
        boolean succeeded() {
            return response != null && error == null;
        }
    }
}
