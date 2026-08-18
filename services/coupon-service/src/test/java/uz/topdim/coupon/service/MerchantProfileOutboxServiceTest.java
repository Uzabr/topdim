package uz.topdim.coupon.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import uz.topdim.common.events.NotificationEvent;
import uz.topdim.coupon.entity.Merchant;
import uz.topdim.coupon.entity.MerchantProfileChangeHistory;
import uz.topdim.coupon.entity.MerchantProfileChangeRequest;
import uz.topdim.coupon.entity.MerchantProfileChangeStatus;
import uz.topdim.coupon.entity.NotificationOutbox;
import uz.topdim.coupon.repository.AbstractIntegrationTest;
import uz.topdim.coupon.repository.MerchantProfileChangeHistoryRepository;
import uz.topdim.coupon.repository.MerchantProfileChangeRequestRepository;
import uz.topdim.coupon.repository.MerchantRepository;
import uz.topdim.coupon.repository.NotificationOutboxRepository;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({MerchantProfileOutboxService.class, MerchantProfileOutboxServiceTest.JacksonConfig.class})
class MerchantProfileOutboxServiceTest extends AbstractIntegrationTest {

    @Autowired private MerchantProfileOutboxService service;
    @Autowired private NotificationOutboxRepository outboxRepository;
    @Autowired private MerchantRepository merchantRepository;
    @Autowired private MerchantProfileChangeRequestRepository requestRepository;
    @Autowired private MerchantProfileChangeHistoryRepository historyRepository;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void managerAuthoredDecisionTargetsAuthorAndOwnerWithoutDuplicates() throws Exception {
        MerchantProfileChangeRequest request = request(41L, 7L, "MANAGER");
        MerchantProfileChangeHistory history = history(
                request, MerchantProfileChangeStatus.IN_REVIEW,
                MerchantProfileChangeStatus.APPROVED, "MODERATOR", null);

        service.enqueue(request, history);

        assertThat(outboxRepository.findAll())
                .extracting(NotificationOutbox::getRecipientUserId)
                .containsExactlyInAnyOrder(41L, 7L);
        NotificationOutbox authorEvent = outboxRepository.findAll().stream()
                .filter(row -> row.getRecipientUserId().equals(41L))
                .findFirst()
                .orElseThrow();
        assertThat(authorEvent.getEventKey()).isEqualTo(
                "merchant-profile:" + request.getId() + ":" + history.getId() + ":41");
        NotificationEvent payload = objectMapper.readValue(
                authorEvent.getPayload(), NotificationEvent.class);
        assertThat(payload.getEventKey()).isEqualTo(authorEvent.getEventKey());
        assertThat(payload.getUserId()).isEqualTo(41L);
        assertThat(payload.getDeepLink())
                .isEqualTo("/company/requests/" + request.getId());
        assertThat(payload.getMessage()).contains("APPROVED", request.getId().toString());
    }

    @Test
    void ownerAuthoredDecisionAndDuplicateEnqueueCreateOneRow() {
        MerchantProfileChangeRequest request = request(7L, 7L, "OWNER");
        MerchantProfileChangeHistory history = history(
                request, MerchantProfileChangeStatus.IN_REVIEW,
                MerchantProfileChangeStatus.REJECTED, "ADMIN", "Нарушает правила");

        service.enqueue(request, history);
        service.enqueue(request, history);

        assertThat(outboxRepository.findAll())
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.getRecipientUserId()).isEqualTo(7L);
                    assertThat(row.getEventKey()).isEqualTo(
                            "merchant-profile:" + request.getId() + ":"
                                    + history.getId() + ":7");
                    assertThat(row.getAttemptCount()).isZero();
                    assertThat(row.getPublishedAt()).isNull();
                    assertThat(row.getNextAttemptAt()).isNotNull();
                });
    }

    private MerchantProfileChangeRequest request(
            Long authorUserId,
            Long ownerUserId,
            String authorRole
    ) {
        Merchant merchant = merchantRepository.saveAndFlush(Merchant.builder()
                .name("Outbox merchant " + authorUserId)
                .userId(ownerUserId)
                .active(true)
                .build());
        return requestRepository.saveAndFlush(MerchantProfileChangeRequest.builder()
                .merchant(merchant)
                .authorUserId(authorUserId)
                .authorRole(authorRole)
                .baseProfileVersion(merchant.getProfileVersion())
                .status(MerchantProfileChangeStatus.IN_REVIEW)
                .submittedAt(LocalDateTime.of(2026, 8, 13, 9, 0))
                .name("Updated company")
                .build());
    }

    private MerchantProfileChangeHistory history(
            MerchantProfileChangeRequest request,
            MerchantProfileChangeStatus previousStatus,
            MerchantProfileChangeStatus newStatus,
            String actorRole,
            String comment
    ) {
        return historyRepository.saveAndFlush(MerchantProfileChangeHistory.builder()
                .request(request)
                .previousStatus(previousStatus)
                .newStatus(newStatus)
                .actorUserId(77L)
                .actorRole(actorRole)
                .comment(comment)
                .build());
    }

    @TestConfiguration
    static class JacksonConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }
    }
}
