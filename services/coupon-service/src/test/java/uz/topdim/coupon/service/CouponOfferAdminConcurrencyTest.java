package uz.topdim.coupon.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uz.topdim.coupon.dto.CouponOfferResponse;
import uz.topdim.coupon.entity.CouponOffer;
import uz.topdim.coupon.entity.CouponStatus;
import uz.topdim.coupon.entity.Merchant;
import uz.topdim.coupon.repository.CouponOfferRepository;
import uz.topdim.coupon.repository.MerchantRepository;
import uz.topdim.coupon.repository.AbstractIntegrationTest;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(CouponOfferService.class)
class CouponOfferAdminConcurrencyTest extends AbstractIntegrationTest {

    @Autowired private CouponOfferService couponOfferService;
    @Autowired private EntityManager entityManager;
    @SpyBean private CouponOfferRepository couponOfferRepository;
    @Autowired private MerchantRepository merchantRepository;
    @MockBean private TelegramPreviewService telegramPreviewService;
    @MockBean private CouponCoverFallbackService couponCoverFallbackService;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @AfterEach
    void shutDownExecutor() {
        executor.shutdownNow();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void takeToWork_twoConcurrentModerators_hasOneWinnerAndOneConflict() throws Exception {
        Merchant merchant = merchantRepository.save(Merchant.builder()
                .name("Concurrent merchant")
                .active(true)
                .build());
        CouponOffer lead = couponOfferRepository.save(CouponOffer.builder()
                .title("Concurrent lead")
                .offerDescription("Claim exactly once")
                .merchant(merchant)
                .status(CouponStatus.LEAD)
                .build());

        CountDownLatch bothReadLead = new CountDownLatch(2);
        doAnswer(invocation -> {
            Optional<CouponOffer> result = Optional.ofNullable(
                    entityManager.find(CouponOffer.class, invocation.getArgument(0)));
            if (result.filter(offer -> offer.getStatus() == CouponStatus.LEAD).isPresent()) {
                bothReadLead.countDown();
                assertThat(bothReadLead.await(5, TimeUnit.SECONDS))
                        .as("both transactions must observe the same pre-claim LEAD")
                        .isTrue();
            }
            return result;
        }).when(couponOfferRepository).findById(lead.getId());

        CountDownLatch start = new CountDownLatch(1);
        Future<ClaimOutcome> first = executor.submit(() -> claim(start, lead.getId(), 101L));
        Future<ClaimOutcome> second = executor.submit(() -> claim(start, lead.getId(), 202L));
        start.countDown();

        ClaimOutcome firstOutcome = first.get(10, TimeUnit.SECONDS);
        ClaimOutcome secondOutcome = second.get(10, TimeUnit.SECONDS);

        var outcomes = java.util.List.of(firstOutcome, secondOutcome);
        assertThat(outcomes)
                .as("claim outcomes: %s", outcomes)
                .filteredOn(ClaimOutcome::succeeded)
                .hasSize(1);
        assertThat(outcomes)
                .filteredOn(outcome -> outcome.error() instanceof IllegalStateException)
                .hasSize(1);

        CouponOffer claimed = couponOfferRepository.findById(lead.getId()).orElseThrow();
        assertThat(claimed.getStatus()).isEqualTo(CouponStatus.DRAFT);
        assertThat(claimed.getAssignedModeratorId()).isIn(101L, 202L);
    }

    private ClaimOutcome claim(CountDownLatch start, Long couponId, Long moderatorId) {
        try {
            assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
            CouponOfferResponse response = couponOfferService.takeToWork(
                    couponId, moderatorId, "moderator-" + moderatorId + "@sizbiz.uz");
            return new ClaimOutcome(response, null);
        } catch (Throwable error) {
            return new ClaimOutcome(null, error);
        }
    }

    private record ClaimOutcome(CouponOfferResponse response, Throwable error) {
        boolean succeeded() {
            return response != null && error == null;
        }
    }
}
