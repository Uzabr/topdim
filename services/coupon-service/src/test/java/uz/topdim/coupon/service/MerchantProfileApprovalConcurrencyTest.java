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
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.coupon.client.IdentityPartnerAccessClient;
import uz.topdim.coupon.entity.Merchant;
import uz.topdim.coupon.entity.MerchantLocation;
import uz.topdim.coupon.entity.MerchantProfileChangeLocation;
import uz.topdim.coupon.entity.MerchantProfileChangeRequest;
import uz.topdim.coupon.entity.MerchantProfileChangeStatus;
import uz.topdim.coupon.repository.MerchantLocationRepository;
import uz.topdim.coupon.repository.MerchantProfileChangeHistoryRepository;
import uz.topdim.coupon.repository.MerchantProfileChangeRequestRepository;
import uz.topdim.coupon.repository.MerchantRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.datasource.hikari.maximum-pool-size=10"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Import({MerchantProfileModerationService.class, MerchantProfileMapper.class})
class MerchantProfileApprovalConcurrencyTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private MerchantProfileModerationService service;
    @Autowired private MerchantRepository merchantRepository;
    @Autowired private MerchantLocationRepository locationRepository;
    @Autowired private MerchantProfileChangeRequestRepository requestRepository;
    @Autowired private MerchantProfileChangeHistoryRepository historyRepository;
    @MockBean private IdentityPartnerAccessClient identityClient;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @AfterEach
    void shutDownExecutor() {
        executor.shutdownNow();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void simultaneousApprovalsPublishExactlyOneVersionAndOutdateTheLoser() throws Exception {
        Merchant merchant = merchantRepository.saveAndFlush(Merchant.builder()
                .name("Published")
                .active(true)
                .profileVersion(1L)
                .build());
        MerchantLocation main = locationRepository.saveAndFlush(MerchantLocation.builder()
                .merchant(merchant)
                .title("Main")
                .address("Old address")
                .phone("+998901111111")
                .primary(true)
                .active(true)
                .build());
        MerchantProfileChangeRequest first = request(merchant, main, 41L, 77L, "First");
        MerchantProfileChangeRequest second = request(merchant, main, 42L, 88L, "Second");
        when(identityClient.getActiveStaffLocationIds(merchant.getId()))
                .thenReturn(ApiResponse.success(Set.of()));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<DecisionOutcome> firstResult = executor.submit(
                () -> approve(ready, start, first.getId(), 77L));
        Future<DecisionOutcome> secondResult = executor.submit(
                () -> approve(ready, start, second.getId(), 88L));
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        List<DecisionOutcome> outcomes = List.of(
                firstResult.get(20, TimeUnit.SECONDS),
                secondResult.get(20, TimeUnit.SECONDS));
        assertThat(outcomes).filteredOn(DecisionOutcome::approved).hasSize(1);
        assertThat(outcomes)
                .filteredOn(outcome -> outcome.error() instanceof IllegalStateException)
                .hasSize(1);

        Merchant published = merchantRepository.findById(merchant.getId()).orElseThrow();
        assertThat(published.getProfileVersion()).isEqualTo(2L);
        assertThat(published.getName()).isIn("First", "Second");
        assertThat(List.of(
                requestRepository.findById(first.getId()).orElseThrow().getStatus(),
                requestRepository.findById(second.getId()).orElseThrow().getStatus()))
                .containsExactlyInAnyOrder(
                        MerchantProfileChangeStatus.APPROVED,
                        MerchantProfileChangeStatus.OUTDATED);
        assertThat(historyRepository.count()).isEqualTo(2L);
    }

    private DecisionOutcome approve(
            CountDownLatch ready,
            CountDownLatch start,
            Long requestId,
            Long actorId
    ) {
        try {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent approval start timed out");
            }
            service.approve(requestId, actorId, "MODERATOR");
            return new DecisionOutcome(true, null);
        } catch (Throwable error) {
            return new DecisionOutcome(false, error);
        }
    }

    private MerchantProfileChangeRequest request(
            Merchant merchant,
            MerchantLocation main,
            Long authorId,
            Long assigneeId,
            String name
    ) {
        MerchantProfileChangeRequest request = MerchantProfileChangeRequest.builder()
                .merchant(merchant)
                .authorUserId(authorId)
                .authorRole("OWNER")
                .baseProfileVersion(1L)
                .status(MerchantProfileChangeStatus.IN_REVIEW)
                .assigneeUserId(assigneeId)
                .submittedAt(LocalDateTime.now().minusHours(1))
                .assignedAt(LocalDateTime.now())
                .name(name)
                .build();
        request.getLocations().add(MerchantProfileChangeLocation.builder()
                .request(request)
                .sourceLocationId(main.getId())
                .title("Main")
                .address(name + " address")
                .phone("+998901111111")
                .primary(true)
                .active(true)
                .sortOrder(0)
                .build());
        return requestRepository.saveAndFlush(request);
    }

    private record DecisionOutcome(boolean approved, Throwable error) {
    }
}
