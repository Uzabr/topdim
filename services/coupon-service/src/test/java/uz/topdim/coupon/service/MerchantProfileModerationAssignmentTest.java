package uz.topdim.coupon.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uz.topdim.coupon.dto.merchantprofile.AdminMerchantProfileChangeFilter;
import uz.topdim.coupon.client.IdentityPartnerAccessClient;
import uz.topdim.coupon.entity.Merchant;
import uz.topdim.coupon.entity.MerchantProfileChangeRequest;
import uz.topdim.coupon.entity.MerchantProfileChangeStatus;
import uz.topdim.coupon.repository.AbstractIntegrationTest;
import uz.topdim.coupon.repository.MerchantProfileChangeHistoryRepository;
import uz.topdim.coupon.repository.MerchantProfileChangeRequestRepository;
import uz.topdim.coupon.repository.MerchantRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({MerchantProfileModerationService.class, MerchantProfileMapper.class})
class MerchantProfileModerationAssignmentTest extends AbstractIntegrationTest {

    @Autowired private MerchantProfileModerationService service;
    @Autowired private MerchantRepository merchantRepository;
    @Autowired private MerchantProfileChangeRequestRepository requestRepository;
    @Autowired private MerchantProfileChangeHistoryRepository historyRepository;
    @MockBean private IdentityPartnerAccessClient identityClient;
    @MockBean private MerchantProfileOutboxService outboxService;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @AfterEach
    void shutDownExecutor() {
        executor.shutdownNow();
    }

    @Test
    void unfilteredQueueExcludesPrivateDraftsAndReturnsAssigneeContext() {
        Merchant merchant = merchant("Alpha Market");
        request(merchant, MerchantProfileChangeStatus.DRAFT, 501L, null,
                LocalDateTime.of(2026, 8, 10, 9, 0));
        MerchantProfileChangeRequest pending = request(
                merchant, MerchantProfileChangeStatus.PENDING_REVIEW, 502L, null,
                LocalDateTime.of(2026, 8, 11, 9, 0));
        MerchantProfileChangeRequest inReview = request(
                merchant, MerchantProfileChangeStatus.IN_REVIEW, 503L, 77L,
                LocalDateTime.of(2026, 8, 12, 9, 0));

        var page = service.list(
                new AdminMerchantProfileChangeFilter(null, null, null, null, null),
                PageRequest.of(0, 20));

        assertThat(page.getContent())
                .extracting(summary -> summary.id())
                .containsExactly(inReview.getId(), pending.getId());
        assertThat(page.getContent().getFirst().merchantId()).isEqualTo(merchant.getId());
        assertThat(page.getContent().getFirst().assigneeUserId()).isEqualTo(77L);
    }

    @Test
    void queueCombinesStatusAssigneeAndSubmittedPeriod() {
        Merchant merchant = merchant("Beta Market");
        request(merchant, MerchantProfileChangeStatus.IN_REVIEW, 601L, 77L,
                LocalDateTime.of(2026, 8, 8, 9, 0));
        MerchantProfileChangeRequest expected = request(
                merchant, MerchantProfileChangeStatus.IN_REVIEW, 602L, 77L,
                LocalDateTime.of(2026, 8, 12, 9, 0));
        request(merchant, MerchantProfileChangeStatus.IN_REVIEW, 603L, 88L,
                LocalDateTime.of(2026, 8, 12, 10, 0));
        request(merchant, MerchantProfileChangeStatus.PENDING_REVIEW, 604L, null,
                LocalDateTime.of(2026, 8, 12, 11, 0));

        var page = service.list(new AdminMerchantProfileChangeFilter(
                        MerchantProfileChangeStatus.IN_REVIEW,
                        null,
                        77L,
                        LocalDateTime.of(2026, 8, 10, 0, 0),
                        LocalDateTime.of(2026, 8, 13, 0, 0)),
                PageRequest.of(0, 20));

        assertThat(page.getContent())
                .extracting(summary -> summary.id())
                .containsExactly(expected.getId());
    }

    @Test
    void queueSearchMatchesCompanyAuthorAndRequestId() {
        Merchant alpha = merchant("Alpha Market");
        Merchant beta = merchant("Beta Shop");
        MerchantProfileChangeRequest byCompany = request(
                alpha, MerchantProfileChangeStatus.PENDING_REVIEW, 701L, null,
                LocalDateTime.of(2026, 8, 12, 9, 0));
        MerchantProfileChangeRequest byAuthor = request(
                beta, MerchantProfileChangeStatus.PENDING_REVIEW, 702L, null,
                LocalDateTime.of(2026, 8, 12, 10, 0));

        assertThat(service.list(filterWithSearch("alpha"), PageRequest.of(0, 20)).getContent())
                .extracting(summary -> summary.id())
                .containsExactly(byCompany.getId());
        assertThat(service.list(filterWithSearch("702"), PageRequest.of(0, 20)).getContent())
                .extracting(summary -> summary.id())
                .containsExactly(byAuthor.getId());
        assertThat(service.list(filterWithSearch(byCompany.getId().toString()),
                        PageRequest.of(0, 20)).getContent())
                .extracting(summary -> summary.id())
                .containsExactly(byCompany.getId());
    }

    @Test
    void takeToWorkClaimsPendingRequestExactlyOnceAndAppendsHistory() {
        MerchantProfileChangeRequest pending = request(
                merchant("Claim Market"),
                MerchantProfileChangeStatus.PENDING_REVIEW,
                801L,
                null,
                LocalDateTime.of(2026, 8, 13, 9, 0));

        var claimed = service.takeToWork(pending.getId(), 77L, "MODERATOR");

        assertThat(claimed.status()).isEqualTo(MerchantProfileChangeStatus.IN_REVIEW);
        assertThat(claimed.assigneeUserId()).isEqualTo(77L);
        assertThat(claimed.assignedAt()).isNotNull();
        assertThat(claimed.updatedAt()).isEqualTo(claimed.assignedAt());
        assertThat(claimed.lockVersion()).isEqualTo(1L);
        assertThat(historyRepository.findByRequestIdOrderByCreatedAtAsc(pending.getId()))
                .singleElement()
                .satisfies(history -> {
                    assertThat(history.getPreviousStatus())
                            .isEqualTo(MerchantProfileChangeStatus.PENDING_REVIEW);
                    assertThat(history.getNewStatus())
                            .isEqualTo(MerchantProfileChangeStatus.IN_REVIEW);
                    assertThat(history.getActorUserId()).isEqualTo(77L);
                    assertThat(history.getActorRole()).isEqualTo("MODERATOR");
                });

        assertThatThrownBy(() -> service.takeToWork(pending.getId(), 88L, "MODERATOR"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("уже взята");
        assertThat(historyRepository.findByRequestIdOrderByCreatedAtAsc(pending.getId()))
                .hasSize(1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void takeToWorkConcurrentModeratorsHaveOneWinnerAndOneConflict() throws Exception {
        MerchantProfileChangeRequest pending = request(
                merchant("Concurrent Claim Market"),
                MerchantProfileChangeStatus.PENDING_REVIEW,
                901L,
                null,
                LocalDateTime.of(2026, 8, 13, 9, 30));
        CountDownLatch start = new CountDownLatch(1);

        Future<ClaimOutcome> first = executor.submit(
                () -> claim(start, pending.getId(), 77L));
        Future<ClaimOutcome> second = executor.submit(
                () -> claim(start, pending.getId(), 88L));
        start.countDown();

        List<ClaimOutcome> outcomes = List.of(
                first.get(10, TimeUnit.SECONDS),
                second.get(10, TimeUnit.SECONDS));
        assertThat(outcomes).filteredOn(ClaimOutcome::succeeded).hasSize(1);
        assertThat(outcomes)
                .filteredOn(outcome -> outcome.error() instanceof IllegalStateException)
                .hasSize(1);

        MerchantProfileChangeRequest reloaded = requestRepository
                .findDetailedById(pending.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(MerchantProfileChangeStatus.IN_REVIEW);
        assertThat(reloaded.getAssigneeUserId()).isIn(77L, 88L);
        assertThat(historyRepository.findByRequestIdOrderByCreatedAtAsc(pending.getId()))
                .hasSize(1);
    }

    @Test
    void releaseReturnsInReviewRequestToQueueAndAppendsHistory() {
        MerchantProfileChangeRequest inReview = request(
                merchant("Release Market"),
                MerchantProfileChangeStatus.IN_REVIEW,
                1001L,
                77L,
                LocalDateTime.of(2026, 8, 13, 10, 0));

        var released = service.release(inReview.getId(), 5L, "ADMIN");

        assertThat(released.status()).isEqualTo(MerchantProfileChangeStatus.PENDING_REVIEW);
        assertThat(released.assigneeUserId()).isNull();
        assertThat(released.assignedAt()).isNull();
        assertThat(historyRepository.findByRequestIdOrderByCreatedAtAsc(inReview.getId()))
                .singleElement()
                .satisfies(history -> {
                    assertThat(history.getPreviousStatus())
                            .isEqualTo(MerchantProfileChangeStatus.IN_REVIEW);
                    assertThat(history.getNewStatus())
                            .isEqualTo(MerchantProfileChangeStatus.PENDING_REVIEW);
                    assertThat(history.getActorUserId()).isEqualTo(5L);
                    assertThat(history.getActorRole()).isEqualTo("ADMIN");
                });
    }

    @Test
    void releaseRejectsRequestThatIsNotInReview() {
        MerchantProfileChangeRequest pending = request(
                merchant("Pending Market"),
                MerchantProfileChangeStatus.PENDING_REVIEW,
                1002L,
                null,
                LocalDateTime.of(2026, 8, 13, 10, 30));

        assertThatThrownBy(() -> service.release(pending.getId(), 5L, "ADMIN"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("не находится в работе");
        assertThat(historyRepository.findByRequestIdOrderByCreatedAtAsc(pending.getId()))
                .isEmpty();
    }

    @Test
    void reassignChangesAssigneeAndRecordsPreviousAndNewIds() {
        MerchantProfileChangeRequest inReview = request(
                merchant("Reassign Market"),
                MerchantProfileChangeStatus.IN_REVIEW,
                1101L,
                77L,
                LocalDateTime.of(2026, 8, 13, 11, 0));
        LocalDateTime previousAssignedAt = inReview.getAssignedAt();

        var reassigned = service.reassign(inReview.getId(), 88L, 5L, "SUPER_ADMIN");

        assertThat(reassigned.status()).isEqualTo(MerchantProfileChangeStatus.IN_REVIEW);
        assertThat(reassigned.assigneeUserId()).isEqualTo(88L);
        assertThat(reassigned.assignedAt()).isNotNull().isNotEqualTo(previousAssignedAt);
        assertThat(historyRepository.findByRequestIdOrderByCreatedAtAsc(inReview.getId()))
                .singleElement()
                .satisfies(history -> {
                    assertThat(history.getPreviousStatus())
                            .isEqualTo(MerchantProfileChangeStatus.IN_REVIEW);
                    assertThat(history.getNewStatus())
                            .isEqualTo(MerchantProfileChangeStatus.IN_REVIEW);
                    assertThat(history.getComment()).contains("77", "88");
                    assertThat(history.getActorRole()).isEqualTo("SUPER_ADMIN");
                });
    }

    @Test
    void getDoesNotExposePartnerPrivateDraftToModeration() {
        MerchantProfileChangeRequest draft = request(
                merchant("Private Draft Market"),
                MerchantProfileChangeStatus.DRAFT,
                1201L,
                null,
                LocalDateTime.of(2026, 8, 13, 12, 30));

        assertThatThrownBy(() -> service.get(draft.getId()))
                .isInstanceOf(uz.topdim.coupon.exception.ResourceNotFoundException.class)
                .hasMessageContaining("не найдена");
    }

    @Test
    void reassignRejectsSameAssigneeWithoutWritingHistory() {
        MerchantProfileChangeRequest inReview = request(
                merchant("Same Assignee Market"),
                MerchantProfileChangeStatus.IN_REVIEW,
                1102L,
                77L,
                LocalDateTime.of(2026, 8, 13, 11, 30));

        assertThatThrownBy(() -> service.reassign(
                        inReview.getId(), 77L, 5L, "ADMIN"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("уже назначена");
        assertThat(historyRepository.findByRequestIdOrderByCreatedAtAsc(inReview.getId()))
                .isEmpty();
    }

    @Test
    void reassignRejectsNonPositiveAssignee() {
        MerchantProfileChangeRequest inReview = request(
                merchant("Invalid Assignee Market"),
                MerchantProfileChangeStatus.IN_REVIEW,
                1103L,
                77L,
                LocalDateTime.of(2026, 8, 13, 12, 0));

        assertThatThrownBy(() -> service.reassign(
                        inReview.getId(), 0L, 5L, "ADMIN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("положительным");
        assertThat(historyRepository.findByRequestIdOrderByCreatedAtAsc(inReview.getId()))
                .isEmpty();
    }

    private AdminMerchantProfileChangeFilter filterWithSearch(String search) {
        return new AdminMerchantProfileChangeFilter(null, search, null, null, null);
    }

    private ClaimOutcome claim(CountDownLatch start, Long requestId, Long moderatorId) {
        try {
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent claim start timed out");
            }
            return new ClaimOutcome(
                    service.takeToWork(requestId, moderatorId, "MODERATOR"), null);
        } catch (Throwable error) {
            return new ClaimOutcome(null, error);
        }
    }

    private record ClaimOutcome(
            uz.topdim.coupon.dto.merchantprofile.MerchantProfileChangeResponse response,
            Throwable error
    ) {
        boolean succeeded() {
            return response != null && error == null;
        }
    }

    private Merchant merchant(String name) {
        return merchantRepository.save(Merchant.builder()
                .name(name)
                .active(true)
                .build());
    }

    private MerchantProfileChangeRequest request(
            Merchant merchant,
            MerchantProfileChangeStatus status,
            Long authorUserId,
            Long assigneeUserId,
            LocalDateTime submittedAt
    ) {
        return requestRepository.saveAndFlush(MerchantProfileChangeRequest.builder()
                .merchant(merchant)
                .authorUserId(authorUserId)
                .authorRole("OWNER")
                .baseProfileVersion(merchant.getProfileVersion())
                .status(status)
                .assigneeUserId(assigneeUserId)
                .name(merchant.getName() + " update")
                .submittedAt(submittedAt)
                .assignedAt(assigneeUserId == null ? null : submittedAt.plusHours(1))
                .build());
    }
}
