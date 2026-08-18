package uz.topdim.coupon.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uz.topdim.coupon.client.IdentityPartnerAccessClient;
import uz.topdim.coupon.entity.Merchant;
import uz.topdim.coupon.entity.MerchantLocation;
import uz.topdim.coupon.entity.MerchantProfileChangeStatus;
import uz.topdim.coupon.repository.MerchantLocationRepository;
import uz.topdim.coupon.repository.MerchantProfileChangeHistoryRepository;
import uz.topdim.coupon.repository.MerchantProfileChangeRequestRepository;
import uz.topdim.coupon.repository.MerchantRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.datasource.hikari.maximum-pool-size=20"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Import({MerchantProfileDraftService.class, MerchantProfileMapper.class})
class MerchantProfileActiveLimitConcurrencyTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private MerchantProfileDraftService service;
    @Autowired private MerchantRepository merchantRepository;
    @Autowired private MerchantLocationRepository locationRepository;
    @Autowired private MerchantProfileChangeRequestRepository requestRepository;
    @Autowired private MerchantProfileChangeHistoryRepository historyRepository;
    @MockBean private IdentityPartnerAccessClient identityClient;
    @MockBean private MerchantProfileOutboxService outboxService;

    private final ExecutorService executor = Executors.newFixedThreadPool(11);

    @AfterEach
    void shutDownExecutor() {
        executor.shutdownNow();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void elevenConcurrentCreatorsProduceAtMostTenActiveDrafts() throws Exception {
        Merchant merchant = merchantRepository.saveAndFlush(Merchant.builder()
                .name("Concurrent merchant")
                .active(true)
                .build());
        locationRepository.saveAndFlush(MerchantLocation.builder()
                .merchant(merchant)
                .title("Main")
                .address("Tashkent")
                .phone("+998901234567")
                .primary(true)
                .active(true)
                .build());

        CountDownLatch ready = new CountDownLatch(11);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        ResolvedPartnerAccess access = new ResolvedPartnerAccess(
                merchant.getId(), "OWNER", null);

        for (int index = 0; index < 11; index++) {
            long actorUserId = 100L + index;
            results.add(executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Concurrent start timed out");
                }
                try {
                    service.createDraft(actorUserId, access);
                    return true;
                } catch (IllegalStateException exception) {
                    if (!exception.getMessage().contains("10 активных заявок")) {
                        throw exception;
                    }
                    return false;
                }
            }));
        }

        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        int successes = 0;
        int conflicts = 0;
        for (Future<Boolean> result : results) {
            if (result.get(20, TimeUnit.SECONDS)) {
                successes++;
            } else {
                conflicts++;
            }
        }

        assertThat(successes).isEqualTo(10);
        assertThat(conflicts).isEqualTo(1);
        assertThat(requestRepository.countByMerchantIdAndStatusIn(
                merchant.getId(), MerchantProfileChangeStatus.activeStatuses()))
                .isEqualTo(10L);
        assertThat(historyRepository.count()).isEqualTo(10L);
    }
}
