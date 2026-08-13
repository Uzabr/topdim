package uz.topdim.coupon.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.topdim.coupon.dto.merchantprofile.MerchantProfileChangePayload;
import uz.topdim.coupon.dto.merchantprofile.MerchantProfileChangeResponse;
import uz.topdim.coupon.dto.merchantprofile.MerchantProfileLocationPayload;
import uz.topdim.coupon.client.IdentityPartnerAccessClient;
import uz.topdim.coupon.entity.Merchant;
import uz.topdim.coupon.entity.MerchantLocation;
import uz.topdim.coupon.entity.MerchantProfileChangeHistory;
import uz.topdim.coupon.entity.MerchantProfileChangeLocation;
import uz.topdim.coupon.entity.MerchantProfileChangeRequest;
import uz.topdim.coupon.entity.MerchantProfileChangeStatus;
import uz.topdim.coupon.exception.ResourceNotFoundException;
import uz.topdim.coupon.repository.MerchantLocationRepository;
import uz.topdim.coupon.repository.MerchantProfileChangeHistoryRepository;
import uz.topdim.coupon.repository.MerchantProfileChangeRequestRepository;
import uz.topdim.coupon.repository.MerchantRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MerchantProfileDraftServiceTest {

    @Mock private MerchantRepository merchantRepository;
    @Mock private MerchantLocationRepository merchantLocationRepository;
    @Mock private MerchantProfileChangeRequestRepository requestRepository;
    @Mock private MerchantProfileChangeHistoryRepository historyRepository;
    @Mock private IdentityPartnerAccessClient identityClient;

    private MerchantProfileDraftService service;

    private final ResolvedPartnerAccess ownerAccess =
            new ResolvedPartnerAccess(7L, "OWNER", null);

    @BeforeEach
    void setUp() {
        service = new MerchantProfileDraftService(
                merchantRepository,
                merchantLocationRepository,
                requestRepository,
                historyRepository,
                new MerchantProfileMapper(),
                identityClient
        );
    }

    @Test
    void createDraftCopiesPublishedCompanyAndEveryLocation() {
        Merchant merchant = publishedMerchant();
        List<MerchantLocation> publishedLocations = List.of(
                publishedLocation(11L, "Main", true, true),
                publishedLocation(12L, "Archived", false, false)
        );
        when(merchantRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(merchant));
        when(requestRepository.countByMerchantIdAndStatusIn(
                7L, MerchantProfileChangeStatus.activeStatuses())).thenReturn(2L);
        when(merchantLocationRepository.findByMerchantId(7L)).thenReturn(publishedLocations);
        when(requestRepository.save(any())).thenAnswer(invocation -> {
            MerchantProfileChangeRequest request = invocation.getArgument(0);
            request.setId(100L);
            return request;
        });

        MerchantProfileChangeResponse draft = service.createDraft(41L, ownerAccess);

        assertThat(draft.id()).isEqualTo(100L);
        assertThat(draft.status()).isEqualTo(MerchantProfileChangeStatus.DRAFT);
        assertThat(draft.baseProfileVersion()).isEqualTo(4L);
        assertThat(draft.authorUserId()).isEqualTo(41L);
        assertThat(draft.name()).isEqualTo("Published company");
        assertThat(draft.locations())
                .extracting(location -> location.sourceLocationId())
                .containsExactly(11L, 12L);
        assertThat(draft.locations())
                .extracting(location -> location.active())
                .containsExactly(true, false);
        assertThat(draft.locations())
                .extracting(location -> location.sortOrder())
                .containsExactly(0, 1);
        verify(historyRepository).save(any(MerchantProfileChangeHistory.class));
    }

    @Test
    void createEleventhActiveRequestIsRejectedBeforeSnapshotWork() {
        when(merchantRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(publishedMerchant()));
        when(requestRepository.countByMerchantIdAndStatusIn(
                7L, MerchantProfileChangeStatus.activeStatuses())).thenReturn(10L);

        assertThatThrownBy(() -> service.createDraft(41L, ownerAccess))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("10 активных заявок");

        verifyNoInteractions(merchantLocationRepository, historyRepository);
        verify(requestRepository, never()).save(any());
    }

    @Test
    void getForeignRequestReturnsNotFoundWithoutFallbackLookup() {
        when(requestRepository.findDetailedByIdAndMerchantId(200L, 7L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(200L, ownerAccess))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Заявка не найдена");

        verify(requestRepository, never()).findById(200L);
    }

    @Test
    void updateReplacesOwnedEditableSnapshotAndNormalizesFields() {
        MerchantProfileChangeRequest draft = existingRequest(MerchantProfileChangeStatus.DRAFT);
        draft.getLocations().add(existingSnapshotLocation(draft, 11L));
        when(requestRepository.findDetailedByIdAndMerchantIdForUpdate(100L, 7L))
                .thenReturn(Optional.of(draft));
        when(requestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MerchantProfileChangePayload payload = new MerchantProfileChangePayload(
                "  Updated company  ",
                " Updated description ",
                null,
                " https://cdn.sizbiz.uz/cover.jpg ",
                " Contact@SizBiz.Uz ",
                " https://sizbiz.uz/company ",
                " Director ",
                List.of(
                        new MerchantProfileLocationPayload(
                                11L, " Main updated ", " New address ", "+998901234567",
                                " 08:00-20:00 ", 41.3, 69.2, true, true),
                        new MerchantProfileLocationPayload(
                                null, " New branch ", " Second address ", "+998909876543",
                                null, null, null, false, true)
                )
        );

        MerchantProfileChangeResponse updated = service.update(100L, payload, ownerAccess);

        assertThat(updated.name()).isEqualTo("Updated company");
        assertThat(updated.email()).isEqualTo("contact@sizbiz.uz");
        assertThat(updated.coverUrl()).isEqualTo("https://cdn.sizbiz.uz/cover.jpg");
        assertThat(updated.locations()).hasSize(2);
        assertThat(updated.locations()).extracting(location -> location.sortOrder())
                .containsExactly(0, 1);
        assertThat(updated.locations().get(1).sourceLocationId()).isNull();
    }

    @Test
    void updateRejectsNonEditableStatus() {
        MerchantProfileChangeRequest pending = existingRequest(MerchantProfileChangeStatus.PENDING_REVIEW);
        when(requestRepository.findDetailedByIdAndMerchantIdForUpdate(100L, 7L))
                .thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.update(100L, minimalPayload(), ownerAccess))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("нельзя редактировать");

        verify(requestRepository, never()).save(any());
    }

    @Test
    void deleteOnlyRemovesOwnedDraft() {
        MerchantProfileChangeRequest draft = existingRequest(MerchantProfileChangeStatus.DRAFT);
        when(requestRepository.findDetailedByIdAndMerchantIdForUpdate(100L, 7L))
                .thenReturn(Optional.of(draft));

        service.deleteDraft(100L, ownerAccess);

        verify(requestRepository).delete(draft);
    }

    @Test
    void deleteRejectsSubmittedRequest() {
        MerchantProfileChangeRequest pending = existingRequest(MerchantProfileChangeStatus.PENDING_REVIEW);
        when(requestRepository.findDetailedByIdAndMerchantIdForUpdate(100L, 7L))
                .thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.deleteDraft(100L, ownerAccess))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("только черновик");

        verify(requestRepository, never()).delete(any(MerchantProfileChangeRequest.class));
    }

    private Merchant publishedMerchant() {
        return Merchant.builder()
                .id(7L)
                .name("Published company")
                .description("Published description")
                .logoUrl("https://cdn.sizbiz.uz/logo.jpg")
                .email("company@sizbiz.uz")
                .website("https://sizbiz.uz")
                .contactPerson("Owner")
                .active(true)
                .profileVersion(4L)
                .build();
    }

    private MerchantLocation publishedLocation(
            Long id,
            String title,
            boolean primary,
            boolean active
    ) {
        return MerchantLocation.builder()
                .id(id)
                .merchant(publishedMerchant())
                .title(title)
                .address("Tashkent")
                .phone("+998901234567")
                .workingHours("09:00-18:00")
                .latitude(41.3)
                .longitude(69.2)
                .primary(primary)
                .active(active)
                .build();
    }

    private MerchantProfileChangeRequest existingRequest(MerchantProfileChangeStatus status) {
        return MerchantProfileChangeRequest.builder()
                .id(100L)
                .merchant(publishedMerchant())
                .authorUserId(41L)
                .authorRole("OWNER")
                .baseProfileVersion(4L)
                .status(status)
                .name("Draft company")
                .build();
    }

    private MerchantProfileChangeLocation existingSnapshotLocation(
            MerchantProfileChangeRequest request,
            Long sourceLocationId
    ) {
        return MerchantProfileChangeLocation.builder()
                .id(500L)
                .request(request)
                .sourceLocationId(sourceLocationId)
                .title("Old snapshot")
                .primary(true)
                .active(true)
                .sortOrder(0)
                .build();
    }

    private MerchantProfileChangePayload minimalPayload() {
        return new MerchantProfileChangePayload(
                "Company", null, null, null, null, null, null, List.of()
        );
    }
}
