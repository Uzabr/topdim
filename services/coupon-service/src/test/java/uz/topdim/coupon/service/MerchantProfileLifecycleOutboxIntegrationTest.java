package uz.topdim.coupon.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.coupon.client.IdentityPartnerAccessClient;
import uz.topdim.coupon.entity.Merchant;
import uz.topdim.coupon.entity.MerchantLocation;
import uz.topdim.coupon.entity.MerchantProfileChangeLocation;
import uz.topdim.coupon.entity.MerchantProfileChangeRequest;
import uz.topdim.coupon.entity.MerchantProfileChangeStatus;
import uz.topdim.coupon.entity.NotificationOutbox;
import uz.topdim.coupon.repository.AbstractIntegrationTest;
import uz.topdim.coupon.repository.MerchantLocationRepository;
import uz.topdim.coupon.repository.MerchantProfileChangeRequestRepository;
import uz.topdim.coupon.repository.MerchantRepository;
import uz.topdim.coupon.repository.NotificationOutboxRepository;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        MerchantProfileDraftService.class,
        MerchantProfileModerationService.class,
        MerchantProfileMapper.class,
        MerchantProfileOutboxService.class,
        MerchantProfileLifecycleOutboxIntegrationTest.JacksonConfig.class
})
class MerchantProfileLifecycleOutboxIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MerchantProfileDraftService draftService;
    @Autowired private MerchantProfileModerationService moderationService;
    @Autowired private MerchantRepository merchantRepository;
    @Autowired private MerchantLocationRepository locationRepository;
    @Autowired private MerchantProfileChangeRequestRepository requestRepository;
    @Autowired private NotificationOutboxRepository outboxRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockBean private IdentityPartnerAccessClient identityClient;

    @Test
    void submitAndWithdrawEachCreateOneEventForManagerAndOwner() {
        Merchant merchant = merchant(7L);
        MerchantLocation main = mainLocation(merchant);
        MerchantProfileChangeRequest request = request(
                merchant, 41L, "MANAGER", MerchantProfileChangeStatus.DRAFT, null);
        request.getLocations().add(snapshot(request, main));
        request = requestRepository.saveAndFlush(request);
        when(identityClient.getActiveStaffLocationIds(merchant.getId()))
                .thenReturn(ApiResponse.success(Set.of()));
        ResolvedPartnerAccess managerAccess =
                new ResolvedPartnerAccess(merchant.getId(), "MANAGER", 3L);

        draftService.submit(request.getId(), 41L, managerAccess);
        draftService.withdraw(request.getId(), 41L, "Больше не актуально", managerAccess);

        assertThat(outboxRepository.findAll())
                .extracting(NotificationOutbox::getRecipientUserId)
                .containsExactlyInAnyOrder(41L, 7L, 41L, 7L);
        assertThat(outboxRepository.findAll())
                .extracting(NotificationOutbox::getEventKey)
                .doesNotHaveDuplicates();
    }

    @Test
    void moderationTransitionsAndOutdatedCompetitorCreateEventsForEveryHistoryRow() {
        Merchant merchant = merchant(7L);
        MerchantLocation main = mainLocation(merchant);
        MerchantProfileChangeRequest approved = request(
                merchant, 41L, "MANAGER", MerchantProfileChangeStatus.IN_REVIEW, 77L);
        approved.getLocations().add(snapshot(approved, main));
        approved = requestRepository.saveAndFlush(approved);
        MerchantProfileChangeRequest competitor = request(
                merchant, 42L, "MANAGER", MerchantProfileChangeStatus.PENDING_REVIEW, null);
        when(identityClient.getActiveStaffLocationIds(merchant.getId()))
                .thenReturn(ApiResponse.success(Set.of()));

        moderationService.approve(approved.getId(), 77L, "MODERATOR");

        assertThat(outboxRepository.findAll())
                .extracting(NotificationOutbox::getRecipientUserId)
                .containsExactlyInAnyOrder(41L, 7L, 42L, 7L);
        assertThat(outboxRepository.findAll())
                .extracting(NotificationOutbox::getPayload)
                .anySatisfy(payload -> assertThat(payload).contains("APPROVED"))
                .anySatisfy(payload -> assertThat(payload).contains("OUTDATED"));
        assertThat(requestRepository.findById(competitor.getId()).orElseThrow().getStatus())
                .isEqualTo(MerchantProfileChangeStatus.OUTDATED);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void rollingBackDecisionAlsoRollsBackItsOutboxRows() {
        Long requestId = new TransactionTemplate(transactionManager).execute(status -> {
            Merchant merchant = merchant(7L);
            return request(
                    merchant, 41L, "MANAGER", MerchantProfileChangeStatus.IN_REVIEW, 77L)
                    .getId();
        });

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            moderationService.reject(
                    requestId, 77L, "MODERATOR", "Нарушает правила");
            status.setRollbackOnly();
        });

        assertThat(outboxRepository.findAll()).isEmpty();
        assertThat(requestRepository.findById(requestId).orElseThrow().getStatus())
                .isEqualTo(MerchantProfileChangeStatus.IN_REVIEW);
    }

    private Merchant merchant(Long ownerUserId) {
        return merchantRepository.saveAndFlush(Merchant.builder()
                .name("Lifecycle merchant")
                .userId(ownerUserId)
                .active(true)
                .build());
    }

    private MerchantLocation mainLocation(Merchant merchant) {
        return locationRepository.saveAndFlush(MerchantLocation.builder()
                .merchant(merchant)
                .title("Main")
                .address("Main address")
                .phone("+998901111111")
                .primary(true)
                .active(true)
                .build());
    }

    private MerchantProfileChangeRequest request(
            Merchant merchant,
            Long authorUserId,
            String authorRole,
            MerchantProfileChangeStatus status,
            Long assigneeUserId
    ) {
        LocalDateTime submittedAt = LocalDateTime.of(2026, 8, 13, 9, 0);
        return requestRepository.saveAndFlush(MerchantProfileChangeRequest.builder()
                .merchant(merchant)
                .authorUserId(authorUserId)
                .authorRole(authorRole)
                .baseProfileVersion(merchant.getProfileVersion())
                .status(status)
                .assigneeUserId(assigneeUserId)
                .assignedAt(assigneeUserId == null ? null : submittedAt.plusHours(1))
                .submittedAt(status == MerchantProfileChangeStatus.DRAFT ? null : submittedAt)
                .name("Updated company")
                .build());
    }

    private MerchantProfileChangeLocation snapshot(
            MerchantProfileChangeRequest request,
            MerchantLocation source
    ) {
        return MerchantProfileChangeLocation.builder()
                .request(request)
                .sourceLocationId(source.getId())
                .title(source.getTitle())
                .address(source.getAddress())
                .phone(source.getPhone())
                .primary(true)
                .active(true)
                .sortOrder(0)
                .build();
    }

    @TestConfiguration
    static class JacksonConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }
    }
}
