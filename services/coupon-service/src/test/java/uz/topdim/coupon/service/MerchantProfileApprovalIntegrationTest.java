package uz.topdim.coupon.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
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
import uz.topdim.coupon.repository.AbstractIntegrationTest;
import uz.topdim.coupon.repository.MerchantLocationRepository;
import uz.topdim.coupon.repository.MerchantProfileChangeHistoryRepository;
import uz.topdim.coupon.repository.MerchantProfileChangeRequestRepository;
import uz.topdim.coupon.repository.MerchantRepository;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

@DataJpaTest(properties = "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({MerchantProfileModerationService.class, MerchantProfileMapper.class})
class MerchantProfileApprovalIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MerchantProfileModerationService service;
    @Autowired private MerchantRepository merchantRepository;
    @SpyBean private MerchantLocationRepository locationRepository;
    @Autowired private MerchantProfileChangeRequestRepository requestRepository;
    @Autowired private MerchantProfileChangeHistoryRepository historyRepository;
    @MockBean private IdentityPartnerAccessClient identityClient;
    @MockBean private MerchantProfileOutboxService outboxService;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void approvePublishesCompleteSnapshotAndOutdatesAllCompetingRequests() {
        Merchant merchant = merchant("Published name", 1L);
        MerchantLocation main = location(
                merchant, "Old main", "Old address", "+998901111111", true, true);
        MerchantLocation branch = location(
                merchant, "Old branch", "Branch address", "+998902222222", false, true);
        MerchantProfileChangeRequest approved = inReviewRequest(merchant, 41L, 77L);
        approved.setName("Новое название");
        approved.setDescription("Новое описание");
        approved.setLogoUrl(null);
        approved.setCoverUrl("https://cdn.example/new-cover.jpg");
        approved.setEmail("new@example.uz");
        approved.setWebsite("https://new.example.uz");
        approved.setContactPerson("Новый контакт");
        approved.getLocations().add(snapshotLocation(
                approved, main.getId(), "Главный", "Новый адрес", "+998 90 333 33 33",
                true, true, 0));
        approved.getLocations().add(snapshotLocation(
                approved, branch.getId(), "Старый филиал", "Branch address", "+998902222222",
                false, false, 1));
        approved.getLocations().add(snapshotLocation(
                approved, null, "Новый филиал", "Новый филиал адрес", "+998904444444",
                false, true, 2));
        approved = requestRepository.saveAndFlush(approved);

        MerchantProfileChangeRequest pending = request(
                merchant, MerchantProfileChangeStatus.PENDING_REVIEW, 42L, null);
        MerchantProfileChangeRequest revision = request(
                merchant, MerchantProfileChangeStatus.REVISION_REQUESTED, 43L, 88L);
        MerchantProfileChangeRequest otherVersion = request(
                merchant, MerchantProfileChangeStatus.DRAFT, 44L, null);
        otherVersion.setBaseProfileVersion(0L);
        otherVersion = requestRepository.saveAndFlush(otherVersion);
        when(identityClient.getActiveStaffLocationIds(merchant.getId()))
                .thenReturn(ApiResponse.success(Set.of()));

        var response = service.approve(approved.getId(), 77L, "MODERATOR");

        Merchant published = merchantRepository.findById(merchant.getId()).orElseThrow();
        assertThat(published.getName()).isEqualTo("Новое название");
        assertThat(published.getDescription()).isEqualTo("Новое описание");
        assertThat(published.getLogoUrl()).isNull();
        assertThat(published.getCoverUrl()).isEqualTo("https://cdn.example/new-cover.jpg");
        assertThat(published.getEmail()).isEqualTo("new@example.uz");
        assertThat(published.getWebsite()).isEqualTo("https://new.example.uz");
        assertThat(published.getContactPerson()).isEqualTo("Новый контакт");
        assertThat(published.getProfileVersion()).isEqualTo(2L);

        assertThat(locationRepository.findById(main.getId()).orElseThrow())
                .satisfies(updated -> {
                    assertThat(updated.getTitle()).isEqualTo("Главный");
                    assertThat(updated.getAddress()).isEqualTo("Новый адрес");
                    assertThat(updated.getPhone()).isEqualTo("+998903333333");
                    assertThat(updated.isPrimary()).isTrue();
                    assertThat(updated.isActive()).isTrue();
                });
        assertThat(locationRepository.findById(branch.getId()).orElseThrow())
                .satisfies(disabled -> {
                    assertThat(disabled.isActive()).isFalse();
                    assertThat(disabled.isPrimary()).isFalse();
                });
        assertThat(locationRepository.findByMerchantId(merchant.getId()))
                .filteredOn(location -> !location.getId().equals(main.getId())
                        && !location.getId().equals(branch.getId()))
                .singleElement()
                .satisfies(created -> {
                    assertThat(created.getTitle()).isEqualTo("Новый филиал");
                    assertThat(created.isActive()).isTrue();
                    assertThat(created.isPrimary()).isFalse();
                });

        assertThat(response.status()).isEqualTo(MerchantProfileChangeStatus.APPROVED);
        assertThat(response.decidedAt()).isNotNull();
        assertThat(requestRepository.findById(pending.getId()).orElseThrow().getStatus())
                .isEqualTo(MerchantProfileChangeStatus.OUTDATED);
        assertThat(requestRepository.findById(revision.getId()).orElseThrow().getStatus())
                .isEqualTo(MerchantProfileChangeStatus.OUTDATED);
        assertThat(requestRepository.findById(otherVersion.getId()).orElseThrow().getStatus())
                .isEqualTo(MerchantProfileChangeStatus.DRAFT);
        assertThat(historyRepository.findByRequestIdOrderByCreatedAtAsc(approved.getId()))
                .singleElement()
                .satisfies(history -> {
                    assertThat(history.getPreviousStatus())
                            .isEqualTo(MerchantProfileChangeStatus.IN_REVIEW);
                    assertThat(history.getNewStatus())
                            .isEqualTo(MerchantProfileChangeStatus.APPROVED);
                    assertThat(history.getActorUserId()).isEqualTo(77L);
                });
    }

    @Test
    void approveRejectsStaleBaseVersionWithoutChangingPublishedProfile() {
        Merchant merchant = merchant("Current profile", 2L);
        MerchantLocation main = location(
                merchant, "Main", "Current address", "+998901111111", true, true);
        MerchantProfileChangeRequest stale = inReviewRequest(merchant, 51L, 87L);
        stale.setBaseProfileVersion(1L);
        stale.setName("Stale name");
        stale.getLocations().add(snapshotLocation(
                stale, main.getId(), "Main", "Stale address", "+998901111111",
                true, true, 0));
        stale = requestRepository.saveAndFlush(stale);

        Long requestId = stale.getId();
        assertThatThrownBy(() -> service.approve(requestId, 87L, "MODERATOR"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("устарела");

        Merchant unchanged = merchantRepository.findById(merchant.getId()).orElseThrow();
        assertThat(unchanged.getName()).isEqualTo("Current profile");
        assertThat(unchanged.getProfileVersion()).isEqualTo(2L);
        assertThat(locationRepository.findById(main.getId()).orElseThrow().getAddress())
                .isEqualTo("Current address");
        assertThat(requestRepository.findById(requestId).orElseThrow().getStatus())
                .isEqualTo(MerchantProfileChangeStatus.IN_REVIEW);
        assertThat(historyRepository.findByRequestIdOrderByCreatedAtAsc(requestId)).isEmpty();
    }

    @Test
    void approveRechecksActiveCashierAssignmentsBeforeDisablingLocation() {
        Merchant merchant = merchant("Cashier profile", 1L);
        MerchantLocation main = location(
                merchant, "Main", "Main address", "+998901111111", true, true);
        MerchantLocation staffed = location(
                merchant, "Staffed", "Staffed address", "+998902222222", false, true);
        MerchantProfileChangeRequest request = inReviewRequest(merchant, 52L, 88L);
        request.getLocations().add(snapshotLocation(
                request, main.getId(), "Main", "Main address", "+998901111111",
                true, true, 0));
        request.getLocations().add(snapshotLocation(
                request, staffed.getId(), "Staffed", "Staffed address", "+998902222222",
                false, false, 1));
        request = requestRepository.saveAndFlush(request);
        when(identityClient.getActiveStaffLocationIds(merchant.getId()))
                .thenReturn(ApiResponse.success(Set.of(staffed.getId())));

        Long requestId = request.getId();
        assertThatThrownBy(() -> service.approve(requestId, 88L, "ADMIN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("активные кассиры");

        assertThat(locationRepository.findById(staffed.getId()).orElseThrow().isActive())
                .isTrue();
        assertThat(merchantRepository.findById(merchant.getId()).orElseThrow().getProfileVersion())
                .isEqualTo(1L);
        assertThat(requestRepository.findById(requestId).orElseThrow().getStatus())
                .isEqualTo(MerchantProfileChangeStatus.IN_REVIEW);
    }

    @Test
    void preflightIdentifiesLocationsBlockedByActiveCashiersWithoutChangingRequest() {
        Merchant merchant = merchant("Preflight profile", 1L);
        MerchantLocation main = location(
                merchant, "Main", "Main address", "+998901111111", true, true);
        MerchantLocation staffed = location(
                merchant, "Staffed", "Staffed address", "+998902222222", false, true);
        MerchantProfileChangeRequest request = inReviewRequest(merchant, 54L, 88L);
        request.getLocations().add(snapshotLocation(
                request, main.getId(), "Main", "Main address", "+998901111111",
                true, true, 0));
        request.getLocations().add(snapshotLocation(
                request, staffed.getId(), "Staffed", "Staffed address", "+998902222222",
                false, false, 1));
        request = requestRepository.saveAndFlush(request);
        when(identityClient.getActiveStaffLocationIds(merchant.getId()))
                .thenReturn(ApiResponse.success(Set.of(staffed.getId())));

        var preflight = service.getPreflight(request.getId());

        assertThat(preflight.ready()).isFalse();
        assertThat(preflight.blockingLocations()).singleElement()
                .satisfies(location -> {
                    assertThat(location.locationId()).isEqualTo(staffed.getId());
                    assertThat(location.title()).isEqualTo("Staffed");
                });
        assertThat(requestRepository.findById(request.getId()).orElseThrow().getStatus())
                .isEqualTo(MerchantProfileChangeStatus.IN_REVIEW);
    }

    @Test
    void preflightIsReadyWhenNoDisabledLocationHasActiveCashiers() {
        Merchant merchant = merchant("Ready preflight", 1L);
        MerchantLocation main = location(
                merchant, "Main", "Main address", "+998901111111", true, true);
        MerchantProfileChangeRequest request = inReviewRequest(merchant, 55L, 88L);
        request.getLocations().add(snapshotLocation(
                request, main.getId(), "Main", "Main address", "+998901111111",
                true, true, 0));
        request = requestRepository.saveAndFlush(request);
        when(identityClient.getActiveStaffLocationIds(merchant.getId()))
                .thenReturn(ApiResponse.success(Set.of(main.getId())));

        var preflight = service.getPreflight(request.getId());

        assertThat(preflight.ready()).isTrue();
        assertThat(preflight.blockingLocations()).isEmpty();
    }

    @Test
    void preflightFailsClosedWhenCashierAssignmentsCannotBeChecked() {
        Merchant merchant = merchant("Unavailable preflight", 1L);
        MerchantLocation main = location(
                merchant, "Main", "Main address", "+998901111111", true, true);
        MerchantProfileChangeRequest request = inReviewRequest(merchant, 56L, 88L);
        request.getLocations().add(snapshotLocation(
                request, main.getId(), "Main", "Main address", "+998901111111",
                true, true, 0));
        request = requestRepository.saveAndFlush(request);
        when(identityClient.getActiveStaffLocationIds(merchant.getId()))
                .thenThrow(new RuntimeException("timeout"));

        Long requestId = request.getId();
        assertThatThrownBy(() -> service.getPreflight(requestId))
                .isInstanceOf(uz.topdim.coupon.exception.PartnerAccessUnavailableException.class)
                .hasMessageContaining("активных сотрудников");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void failureDuringLocationPublicationRollsBackCompanyLocationsVersionAndStatus() {
        ApprovalFixture fixture = new TransactionTemplate(transactionManager).execute(status -> {
            Merchant merchant = merchant("Rollback profile", 1L);
            MerchantLocation main = location(
                    merchant, "Main", "Old address", "+998901111111", true, true);
            MerchantProfileChangeRequest request = inReviewRequest(merchant, 53L, 89L);
            request.setName("Should roll back");
            request.getLocations().add(snapshotLocation(
                    request, main.getId(), "Main", "Changed address", "+998901111111",
                    true, true, 0));
            request.getLocations().add(snapshotLocation(
                    request, null, "New branch", "New address", "+998902222222",
                    false, true, 1));
            request = requestRepository.saveAndFlush(request);
            return new ApprovalFixture(merchant.getId(), main.getId(), request.getId());
        });
        when(identityClient.getActiveStaffLocationIds(fixture.merchantId()))
                .thenReturn(ApiResponse.success(Set.of()));
        AtomicInteger saves = new AtomicInteger();
        doAnswer(invocation -> {
            if (saves.incrementAndGet() == 2) {
                throw new IllegalStateException("forced location failure");
            }
            MerchantLocation location = invocation.getArgument(0);
            return location;
        }).when(locationRepository).save(org.mockito.ArgumentMatchers.any(MerchantLocation.class));

        assertThatThrownBy(() -> service.approve(
                        fixture.requestId(), 89L, "MODERATOR"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("forced location failure");

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Merchant merchant = merchantRepository.findById(fixture.merchantId()).orElseThrow();
            assertThat(merchant.getName()).isEqualTo("Rollback profile");
            assertThat(merchant.getProfileVersion()).isEqualTo(1L);
            assertThat(locationRepository.findById(fixture.mainLocationId()).orElseThrow().getAddress())
                    .isEqualTo("Old address");
            assertThat(locationRepository.findByMerchantId(fixture.merchantId())).hasSize(1);
            assertThat(requestRepository.findById(fixture.requestId()).orElseThrow().getStatus())
                    .isEqualTo(MerchantProfileChangeStatus.IN_REVIEW);
            assertThat(historyRepository.findByRequestIdOrderByCreatedAtAsc(fixture.requestId()))
                    .isEmpty();
        });
    }

    private Merchant merchant(String name, long version) {
        return merchantRepository.saveAndFlush(Merchant.builder()
                .name(name)
                .description("Published description")
                .active(true)
                .profileVersion(version)
                .build());
    }

    private MerchantLocation location(
            Merchant merchant,
            String title,
            String address,
            String phone,
            boolean primary,
            boolean active
    ) {
        return locationRepository.saveAndFlush(MerchantLocation.builder()
                .merchant(merchant)
                .title(title)
                .address(address)
                .phone(phone)
                .primary(primary)
                .active(active)
                .build());
    }

    private MerchantProfileChangeRequest inReviewRequest(
            Merchant merchant,
            Long authorUserId,
            Long assigneeUserId
    ) {
        return request(merchant, MerchantProfileChangeStatus.IN_REVIEW,
                authorUserId, assigneeUserId);
    }

    private MerchantProfileChangeRequest request(
            Merchant merchant,
            MerchantProfileChangeStatus status,
            Long authorUserId,
            Long assigneeUserId
    ) {
        LocalDateTime submittedAt = LocalDateTime.of(2026, 8, 13, 8, 0);
        return requestRepository.saveAndFlush(MerchantProfileChangeRequest.builder()
                .merchant(merchant)
                .authorUserId(authorUserId)
                .authorRole("OWNER")
                .baseProfileVersion(merchant.getProfileVersion())
                .status(status)
                .assigneeUserId(assigneeUserId)
                .submittedAt(submittedAt)
                .assignedAt(assigneeUserId == null ? null : submittedAt.plusHours(1))
                .name("Requested name")
                .build());
    }

    private MerchantProfileChangeLocation snapshotLocation(
            MerchantProfileChangeRequest request,
            Long sourceLocationId,
            String title,
            String address,
            String phone,
            boolean primary,
            boolean active,
            int sortOrder
    ) {
        return MerchantProfileChangeLocation.builder()
                .request(request)
                .sourceLocationId(sourceLocationId)
                .title(title)
                .address(address)
                .phone(phone)
                .primary(primary)
                .active(active)
                .sortOrder(sortOrder)
                .build();
    }

    private record ApprovalFixture(Long merchantId, Long mainLocationId, Long requestId) {
    }
}
