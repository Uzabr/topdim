package uz.topdim.coupon.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.coupon.client.IdentityPartnerAccessClient;
import uz.topdim.coupon.entity.Merchant;
import uz.topdim.coupon.entity.MerchantLocation;
import uz.topdim.coupon.entity.MerchantProfileChangeHistory;
import uz.topdim.coupon.entity.MerchantProfileChangeLocation;
import uz.topdim.coupon.entity.MerchantProfileChangeRequest;
import uz.topdim.coupon.entity.MerchantProfileChangeStatus;
import uz.topdim.coupon.exception.PartnerAccessUnavailableException;
import uz.topdim.coupon.repository.MerchantLocationRepository;
import uz.topdim.coupon.repository.MerchantProfileChangeHistoryRepository;
import uz.topdim.coupon.repository.MerchantProfileChangeRequestRepository;
import uz.topdim.coupon.repository.MerchantRepository;

import java.util.Optional;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MerchantProfileSubmissionTest {

    @Mock private MerchantRepository merchantRepository;
    @Mock private MerchantLocationRepository merchantLocationRepository;
    @Mock private MerchantProfileChangeRequestRepository requestRepository;
    @Mock private MerchantProfileChangeHistoryRepository historyRepository;
    @Mock private IdentityPartnerAccessClient identityClient;
    @Mock private MerchantProfileOutboxService outboxService;
    @Spy private MerchantProfileMapper mapper = new MerchantProfileMapper();
    @InjectMocks private MerchantProfileDraftService service;

    private final ResolvedPartnerAccess ownerAccess =
            new ResolvedPartnerAccess(7L, "OWNER", null);

    private Merchant merchant;
    private MerchantProfileChangeRequest request;

    @BeforeEach
    void setUp() {
        merchant = Merchant.builder()
                .id(7L)
                .name("Published company")
                .active(true)
                .profileVersion(4L)
                .build();
        request = MerchantProfileChangeRequest.builder()
                .id(100L)
                .merchant(merchant)
                .authorUserId(41L)
                .authorRole("OWNER")
                .baseProfileVersion(4L)
                .status(MerchantProfileChangeStatus.DRAFT)
                .name("Company update")
                .build();
    }

    @Test
    void submitAcceptsExactlyOneActivePrimaryAndOptionalImages() {
        request.setLogoUrl(null);
        request.setCoverUrl(null);
        request.getLocations().add(location(11L, true, true, "Main address", "+998901111111"));
        request.getLocations().add(location(12L, false, true, "Branch address", "+998902222222"));
        arrangeValidation(Set.of());
        when(requestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.submit(100L, 41L, ownerAccess);

        assertThat(response.status()).isEqualTo(MerchantProfileChangeStatus.PENDING_REVIEW);
        assertThat(response.logoUrl()).isNull();
        assertThat(response.coverUrl()).isNull();
        assertThat(response.submittedAt()).isNotNull();
        assertThat(request.getAssigneeUserId()).isNull();
        assertThat(request.getAssignedAt()).isNull();

        ArgumentCaptor<MerchantProfileChangeHistory> history =
                ArgumentCaptor.forClass(MerchantProfileChangeHistory.class);
        verify(historyRepository).save(history.capture());
        assertThat(history.getValue().getPreviousStatus())
                .isEqualTo(MerchantProfileChangeStatus.DRAFT);
        assertThat(history.getValue().getNewStatus())
                .isEqualTo(MerchantProfileChangeStatus.PENDING_REVIEW);
    }

    @Test
    void submitRejectsDisabledPrimary() {
        request.getLocations().add(location(11L, true, false, "Main", "+998901111111"));
        arrangeValidation(Set.of());

        assertThatThrownBy(() -> service.submit(100L, 41L, ownerAccess))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("активный основной филиал");

        assertThat(request.getStatus()).isEqualTo(MerchantProfileChangeStatus.DRAFT);
        verify(requestRepository, never()).save(any());
    }

    @Test
    void submitRejectsMoreThanOneActivePrimary() {
        request.getLocations().add(location(11L, true, true, "Main", "+998901111111"));
        request.getLocations().add(location(12L, true, true, "Branch", "+998902222222"));
        arrangeValidation(Set.of());

        assertThatThrownBy(() -> service.submit(100L, 41L, ownerAccess))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ровно один активный основной филиал");
    }

    @Test
    void submitRejectsActiveLocationWithoutAddressOrPhone() {
        request.getLocations().add(location(11L, true, true, " ", null));
        arrangeValidation(Set.of());

        assertThatThrownBy(() -> service.submit(100L, 41L, ownerAccess))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("адрес и телефон");
    }

    @Test
    void submitRejectsPartialCoordinates() {
        MerchantProfileChangeLocation main =
                location(11L, true, true, "Main", "+998901111111");
        main.setLatitude(41.3);
        main.setLongitude(null);
        request.getLocations().add(main);
        arrangeValidation(Set.of());

        assertThatThrownBy(() -> service.submit(100L, 41L, ownerAccess))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Координаты");
    }

    @Test
    void submitRejectsNonFiniteCoordinates() {
        MerchantProfileChangeLocation main =
                location(11L, true, true, "Main", "+998901111111");
        main.setLatitude(Double.NaN);
        main.setLongitude(69.2);
        request.getLocations().add(main);
        arrangeValidation(Set.of());

        assertThatThrownBy(() -> service.submit(100L, 41L, ownerAccess))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Координаты");
    }

    @Test
    void submitRejectsOmittedPublishedLocationInsteadOfDeletingIt() {
        request.getLocations().add(location(11L, true, true, "Main", "+998901111111"));
        arrangeLockedRequest();
        when(requestRepository.countByMerchantIdAndStatusIn(
                7L, MerchantProfileChangeStatus.activeStatuses())).thenReturn(1L);
        when(merchantLocationRepository.findByMerchantId(7L)).thenReturn(List.of(
                publishedLocation(11L, "Main", true),
                publishedLocation(12L, "Branch", false)
        ));
        when(identityClient.getActiveStaffLocationIds(7L))
                .thenReturn(ApiResponse.success(Set.of()));

        assertThatThrownBy(() -> service.submit(100L, 41L, ownerAccess))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("не должен исчезать");

    }

    @Test
    void submitRejectsLocationOwnedByAnotherMerchant() {
        request.getLocations().add(location(11L, true, true, "Main", "+998901111111"));
        request.getLocations().add(location(99L, false, true, "Foreign", "+998902222222"));
        arrangeLockedRequest();
        when(requestRepository.countByMerchantIdAndStatusIn(
                7L, MerchantProfileChangeStatus.activeStatuses())).thenReturn(1L);
        when(merchantLocationRepository.findByMerchantId(7L)).thenReturn(List.of(
                publishedLocation(11L, "Main", true)
        ));
        when(identityClient.getActiveStaffLocationIds(7L))
                .thenReturn(ApiResponse.success(Set.of()));

        assertThatThrownBy(() -> service.submit(100L, 41L, ownerAccess))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("не принадлежит компании");

    }

    @Test
    void submitRejectsDisablingLocationWithActiveStaff() {
        request.getLocations().add(location(11L, true, true, "Main", "+998901111111"));
        request.getLocations().add(location(12L, false, false, null, null));
        arrangeValidation(Set.of(12L));

        assertThatThrownBy(() -> service.submit(100L, 41L, ownerAccess))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("активные кассиры");
    }

    @Test
    void submitFailsClosedWhenActiveStaffLocationsCannotBeLoaded() {
        request.getLocations().add(location(11L, true, true, "Main", "+998901111111"));
        arrangeLockedRequest();
        when(requestRepository.countByMerchantIdAndStatusIn(
                7L, MerchantProfileChangeStatus.activeStatuses())).thenReturn(1L);
        when(identityClient.getActiveStaffLocationIds(7L))
                .thenThrow(new RuntimeException("identity unavailable"));

        assertThatThrownBy(() -> service.submit(100L, 41L, ownerAccess))
                .isInstanceOf(PartnerAccessUnavailableException.class)
                .hasMessageContaining("сотрудник");

        assertThat(request.getStatus()).isEqualTo(MerchantProfileChangeStatus.DRAFT);
    }

    @Test
    void submitRejectsStaleBaseProfileVersion() {
        request.setBaseProfileVersion(3L);
        request.getLocations().add(location(11L, true, true, "Main", "+998901111111"));
        arrangeLockedRequest();

        assertThatThrownBy(() -> service.submit(100L, 41L, ownerAccess))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("устарела");

        verify(identityClient, never()).getActiveStaffLocationIds(any());
    }

    @Test
    void submitRejectsCorruptedEleventhActiveRequest() {
        request.getLocations().add(location(11L, true, true, "Main", "+998901111111"));
        arrangeLockedRequest();
        when(requestRepository.countByMerchantIdAndStatusIn(
                7L, MerchantProfileChangeStatus.activeStatuses())).thenReturn(11L);

        assertThatThrownBy(() -> service.submit(100L, 41L, ownerAccess))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("10 активных заявок");
    }

    @Test
    void resubmitRevisionClearsPreviousAssignment() {
        request.setStatus(MerchantProfileChangeStatus.REVISION_REQUESTED);
        request.setAssigneeUserId(77L);
        request.setAssignedAt(java.time.LocalDateTime.now().minusDays(1));
        request.getLocations().add(location(11L, true, true, "Main", "+998901111111"));
        arrangeValidation(Set.of());
        when(requestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.submit(100L, 41L, ownerAccess);

        assertThat(response.status()).isEqualTo(MerchantProfileChangeStatus.PENDING_REVIEW);
        assertThat(response.assigneeUserId()).isNull();
        assertThat(response.assignedAt()).isNull();
    }

    @Test
    void withdrawSubmittedRequestStoresTrimmedReasonInHistory() {
        request.setStatus(MerchantProfileChangeStatus.IN_REVIEW);
        arrangeOwnedRequest();
        when(requestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.withdraw(100L, 41L, "  Company data changed  ", ownerAccess);

        assertThat(response.status()).isEqualTo(MerchantProfileChangeStatus.WITHDRAWN);
        assertThat(response.withdrawnAt()).isNotNull();
        ArgumentCaptor<MerchantProfileChangeHistory> history =
                ArgumentCaptor.forClass(MerchantProfileChangeHistory.class);
        verify(historyRepository).save(history.capture());
        assertThat(history.getValue().getPreviousStatus())
                .isEqualTo(MerchantProfileChangeStatus.IN_REVIEW);
        assertThat(history.getValue().getNewStatus())
                .isEqualTo(MerchantProfileChangeStatus.WITHDRAWN);
        assertThat(history.getValue().getComment()).isEqualTo("Company data changed");
    }

    @Test
    void withdrawRejectsBlankReasonWithoutChangingStatus() {
        request.setStatus(MerchantProfileChangeStatus.PENDING_REVIEW);
        arrangeOwnedRequest();

        assertThatThrownBy(() -> service.withdraw(100L, 41L, "   ", ownerAccess))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("причина");

        assertThat(request.getStatus()).isEqualTo(MerchantProfileChangeStatus.PENDING_REVIEW);
        verify(requestRepository, never()).save(any());
    }

    @Test
    void withdrawRejectsDraft() {
        arrangeOwnedRequest();

        assertThatThrownBy(() -> service.withdraw(100L, 41L, "No longer needed", ownerAccess))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("нельзя отозвать");
    }

    @Test
    void withdrawRejectsReasonLongerThanHistoryColumn() {
        request.setStatus(MerchantProfileChangeStatus.PENDING_REVIEW);
        arrangeOwnedRequest();

        assertThatThrownBy(() -> service.withdraw(100L, 41L, "x".repeat(2001), ownerAccess))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2000");
    }

    @Test
    void copyTerminalRequestRebasesLocationsOnCurrentPublishedVersion() {
        merchant.setProfileVersion(6L);
        request.setStatus(MerchantProfileChangeStatus.OUTDATED);
        request.setName("Requested company name");
        request.getLocations().add(sourceLocation(11L, "Requested main", true, true));
        request.getLocations().add(sourceLocation(99L, "Deleted branch", false, true));
        request.getLocations().add(sourceLocation(null, "Requested new branch", false, true));

        when(merchantRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(merchant));
        when(requestRepository.findDetailedByIdAndMerchantId(100L, 7L))
                .thenReturn(Optional.of(request));
        when(requestRepository.countByMerchantIdAndStatusIn(
                7L, MerchantProfileChangeStatus.activeStatuses())).thenReturn(9L);
        when(merchantLocationRepository.findByMerchantId(7L)).thenReturn(List.of(
                publishedLocation(11L, "Published main", true),
                publishedLocation(12L, "New published branch", false)
        ));
        when(requestRepository.save(any())).thenAnswer(invocation -> {
            MerchantProfileChangeRequest copy = invocation.getArgument(0);
            copy.setId(101L);
            return copy;
        });

        var response = service.copy(100L, 41L, ownerAccess);

        assertThat(response.id()).isEqualTo(101L);
        assertThat(response.status()).isEqualTo(MerchantProfileChangeStatus.DRAFT);
        assertThat(response.baseProfileVersion()).isEqualTo(6L);
        assertThat(response.name()).isEqualTo("Requested company name");
        assertThat(response.authorUserId()).isEqualTo(41L);
        assertThat(response.locations())
                .extracting(location -> location.sourceLocationId())
                .containsExactly(11L, 12L, null);
        assertThat(response.locations())
                .extracting(location -> location.title())
                .containsExactly("Requested main", "New published branch", "Requested new branch");
    }

    @Test
    void copyRejectsNonTerminalSource() {
        request.setStatus(MerchantProfileChangeStatus.PENDING_REVIEW);
        when(merchantRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(merchant));
        when(requestRepository.findDetailedByIdAndMerchantId(100L, 7L))
                .thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.copy(100L, 41L, ownerAccess))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("терминальную");

        verify(requestRepository, never()).save(any());
    }

    @Test
    void copyRejectsEleventhActiveRequestBeforeLoadingLocations() {
        request.setStatus(MerchantProfileChangeStatus.REJECTED);
        when(merchantRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(merchant));
        when(requestRepository.findDetailedByIdAndMerchantId(100L, 7L))
                .thenReturn(Optional.of(request));
        when(requestRepository.countByMerchantIdAndStatusIn(
                7L, MerchantProfileChangeStatus.activeStatuses())).thenReturn(10L);

        assertThatThrownBy(() -> service.copy(100L, 41L, ownerAccess))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("10 активных заявок");

        verify(merchantLocationRepository, never()).findByMerchantId(any());
    }

    private void arrangeLockedRequest() {
        when(merchantRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(merchant));
        arrangeOwnedRequest();
    }

    private void arrangeOwnedRequest() {
        when(requestRepository.findDetailedByIdAndMerchantIdForUpdate(100L, 7L))
                .thenReturn(Optional.of(request));
    }

    private void arrangeValidation(Set<Long> activeStaffLocationIds) {
        arrangeLockedRequest();
        when(requestRepository.countByMerchantIdAndStatusIn(
                7L, MerchantProfileChangeStatus.activeStatuses())).thenReturn(1L);
        when(merchantLocationRepository.findByMerchantId(7L)).thenReturn(
                request.getLocations().stream()
                        .map(MerchantProfileChangeLocation::getSourceLocationId)
                        .filter(java.util.Objects::nonNull)
                        .distinct()
                        .map(id -> publishedLocation(id, "Published " + id, false))
                        .toList());
        when(identityClient.getActiveStaffLocationIds(7L))
                .thenReturn(ApiResponse.success(activeStaffLocationIds));
    }

    private MerchantProfileChangeLocation location(
            Long sourceId,
            boolean primary,
            boolean active,
            String address,
            String phone
    ) {
        return MerchantProfileChangeLocation.builder()
                .request(request)
                .sourceLocationId(sourceId)
                .title("Location " + sourceId)
                .address(address)
                .phone(phone)
                .primary(primary)
                .active(active)
                .sortOrder(request.getLocations().size())
                .build();
    }

    private MerchantProfileChangeLocation sourceLocation(
            Long sourceId,
            String title,
            boolean primary,
            boolean active
    ) {
        return MerchantProfileChangeLocation.builder()
                .request(request)
                .sourceLocationId(sourceId)
                .title(title)
                .address("Tashkent")
                .phone("+998901234567")
                .primary(primary)
                .active(active)
                .sortOrder(request.getLocations().size())
                .build();
    }

    private MerchantLocation publishedLocation(Long id, String title, boolean primary) {
        return MerchantLocation.builder()
                .id(id)
                .merchant(merchant)
                .title(title)
                .address("Tashkent")
                .phone("+998909999999")
                .primary(primary)
                .active(true)
                .build();
    }
}
