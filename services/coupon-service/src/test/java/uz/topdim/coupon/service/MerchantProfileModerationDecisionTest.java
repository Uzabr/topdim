package uz.topdim.coupon.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import uz.topdim.coupon.client.IdentityPartnerAccessClient;
import uz.topdim.coupon.entity.Merchant;
import uz.topdim.coupon.entity.MerchantProfileChangeRequest;
import uz.topdim.coupon.entity.MerchantProfileChangeStatus;
import uz.topdim.coupon.repository.AbstractIntegrationTest;
import uz.topdim.coupon.repository.MerchantProfileChangeHistoryRepository;
import uz.topdim.coupon.repository.MerchantProfileChangeRequestRepository;
import uz.topdim.coupon.repository.MerchantRepository;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({MerchantProfileModerationService.class, MerchantProfileMapper.class})
class MerchantProfileModerationDecisionTest extends AbstractIntegrationTest {

    @Autowired private MerchantProfileModerationService service;
    @Autowired private MerchantRepository merchantRepository;
    @Autowired private MerchantProfileChangeRequestRepository requestRepository;
    @Autowired private MerchantProfileChangeHistoryRepository historyRepository;
    @MockBean private IdentityPartnerAccessClient identityClient;

    @Test
    void requestRevisionRequiresNonBlankTrimmedComment() {
        MerchantProfileChangeRequest request = inReviewRequest(41L, 77L);

        assertThatThrownBy(() -> service.requestRevision(
                        request.getId(), 77L, "MODERATOR", "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Комментарий");
        assertThat(requestRepository.findById(request.getId()).orElseThrow().getStatus())
                .isEqualTo(MerchantProfileChangeStatus.IN_REVIEW);
        assertThat(historyRepository.findByRequestIdOrderByCreatedAtAsc(request.getId()))
                .isEmpty();
    }

    @Test
    void rejectRequiresCommentWithinDatabaseLimit() {
        MerchantProfileChangeRequest request = inReviewRequest(42L, 78L);

        assertThatThrownBy(() -> service.reject(
                        request.getId(), 78L, "ADMIN", "x".repeat(2001)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2000");
        assertThat(requestRepository.findById(request.getId()).orElseThrow().getStatus())
                .isEqualTo(MerchantProfileChangeStatus.IN_REVIEW);
    }

    @Test
    void onlyAssignedActorCanMakeDecision() {
        MerchantProfileChangeRequest request = inReviewRequest(43L, 79L);

        assertThatThrownBy(() -> service.reject(
                        request.getId(), 80L, "MODERATOR", "Дубликат"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("назначенный исполнитель");
        assertThat(historyRepository.findByRequestIdOrderByCreatedAtAsc(request.getId()))
                .isEmpty();
    }

    @Test
    void authorCannotDecideOwnRequestEvenWhenAssigned() {
        MerchantProfileChangeRequest request = inReviewRequest(81L, 81L);

        assertThatThrownBy(() -> service.requestRevision(
                        request.getId(), 81L, "ADMIN", "Уточните адрес"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Автор");
        assertThat(requestRepository.findById(request.getId()).orElseThrow().getStatus())
                .isEqualTo(MerchantProfileChangeStatus.IN_REVIEW);
    }

    @Test
    void requestRevisionStoresTrimmedCommentAndAuditTransition() {
        MerchantProfileChangeRequest request = inReviewRequest(44L, 82L);

        var response = service.requestRevision(
                request.getId(), 82L, "MODERATOR", "  Уточните часы работы  ");

        assertThat(response.status())
                .isEqualTo(MerchantProfileChangeStatus.REVISION_REQUESTED);
        assertThat(response.moderationComment()).isEqualTo("Уточните часы работы");
        assertThat(response.decidedAt()).isNotNull();
        assertThat(response.assigneeUserId()).isEqualTo(82L);
        assertThat(historyRepository.findByRequestIdOrderByCreatedAtAsc(request.getId()))
                .singleElement()
                .satisfies(history -> {
                    assertThat(history.getPreviousStatus())
                            .isEqualTo(MerchantProfileChangeStatus.IN_REVIEW);
                    assertThat(history.getNewStatus())
                            .isEqualTo(MerchantProfileChangeStatus.REVISION_REQUESTED);
                    assertThat(history.getActorUserId()).isEqualTo(82L);
                    assertThat(history.getActorRole()).isEqualTo("MODERATOR");
                    assertThat(history.getComment()).isEqualTo("Уточните часы работы");
                });
    }

    @Test
    void rejectIsTerminalAndRecordsDecision() {
        MerchantProfileChangeRequest request = inReviewRequest(45L, 83L);

        var response = service.reject(
                request.getId(), 83L, "SUPER_ADMIN", "  Нарушает правила  ");

        assertThat(response.status()).isEqualTo(MerchantProfileChangeStatus.REJECTED);
        assertThat(response.moderationComment()).isEqualTo("Нарушает правила");
        assertThat(response.decidedAt()).isNotNull();
        assertThat(historyRepository.findByRequestIdOrderByCreatedAtAsc(request.getId()))
                .singleElement()
                .satisfies(history -> {
                    assertThat(history.getNewStatus())
                            .isEqualTo(MerchantProfileChangeStatus.REJECTED);
                    assertThat(history.getComment()).isEqualTo("Нарушает правила");
                });
    }

    @Test
    void terminalOrPendingRequestCannotBeDecidedAgain() {
        MerchantProfileChangeRequest request = inReviewRequest(46L, 84L);
        request.setStatus(MerchantProfileChangeStatus.PENDING_REVIEW);
        requestRepository.saveAndFlush(request);

        assertThatThrownBy(() -> service.reject(
                        request.getId(), 84L, "MODERATOR", "Причина"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("не находится в работе");
    }

    private MerchantProfileChangeRequest inReviewRequest(Long authorUserId, Long assigneeUserId) {
        Merchant merchant = merchantRepository.saveAndFlush(Merchant.builder()
                .name("Decision merchant " + authorUserId)
                .active(true)
                .build());
        LocalDateTime assignedAt = LocalDateTime.of(2026, 8, 13, 9, 0);
        return requestRepository.saveAndFlush(MerchantProfileChangeRequest.builder()
                .merchant(merchant)
                .authorUserId(authorUserId)
                .authorRole("OWNER")
                .baseProfileVersion(merchant.getProfileVersion())
                .status(MerchantProfileChangeStatus.IN_REVIEW)
                .assigneeUserId(assigneeUserId)
                .assignedAt(assignedAt)
                .submittedAt(assignedAt.minusHours(1))
                .name("Updated merchant")
                .build());
    }
}
