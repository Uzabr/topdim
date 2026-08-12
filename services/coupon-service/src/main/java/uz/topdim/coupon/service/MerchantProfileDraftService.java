package uz.topdim.coupon.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.topdim.coupon.dto.merchantprofile.MerchantProfileChangePayload;
import uz.topdim.coupon.dto.merchantprofile.MerchantProfileChangeResponse;
import uz.topdim.coupon.dto.merchantprofile.MerchantProfileChangeSummary;
import uz.topdim.coupon.entity.Merchant;
import uz.topdim.coupon.entity.MerchantLocation;
import uz.topdim.coupon.entity.MerchantProfileChangeHistory;
import uz.topdim.coupon.entity.MerchantProfileChangeRequest;
import uz.topdim.coupon.entity.MerchantProfileChangeStatus;
import uz.topdim.coupon.exception.ResourceNotFoundException;
import uz.topdim.coupon.repository.MerchantLocationRepository;
import uz.topdim.coupon.repository.MerchantProfileChangeHistoryRepository;
import uz.topdim.coupon.repository.MerchantProfileChangeRequestRepository;
import uz.topdim.coupon.repository.MerchantRepository;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MerchantProfileDraftService {

    private static final long MAX_ACTIVE_REQUESTS = 10L;

    private final MerchantRepository merchantRepository;
    private final MerchantLocationRepository merchantLocationRepository;
    private final MerchantProfileChangeRequestRepository requestRepository;
    private final MerchantProfileChangeHistoryRepository historyRepository;
    private final MerchantProfileMapper mapper;

    @Transactional
    public MerchantProfileChangeResponse createDraft(
            Long authorUserId,
            ResolvedPartnerAccess access
    ) {
        Merchant merchant = merchantRepository.findByIdForUpdate(access.merchantId())
                .orElseThrow(() -> new ResourceNotFoundException("Мерчант не найден"));
        requireActiveMerchant(merchant);

        long activeCount = requestRepository.countByMerchantIdAndStatusIn(
                merchant.getId(),
                MerchantProfileChangeStatus.activeStatuses()
        );
        if (activeCount >= MAX_ACTIVE_REQUESTS) {
            throw new IllegalStateException("У компании уже есть 10 активных заявок");
        }

        List<MerchantLocation> publishedLocations = merchantLocationRepository
                .findByMerchantId(merchant.getId())
                .stream()
                .sorted(Comparator.comparing(MerchantLocation::getId))
                .toList();
        MerchantProfileChangeRequest request = mapper.fromPublished(
                merchant,
                publishedLocations,
                authorUserId,
                access
        );
        request = requestRepository.save(request);

        historyRepository.save(MerchantProfileChangeHistory.builder()
                .request(request)
                .previousStatus(null)
                .newStatus(MerchantProfileChangeStatus.DRAFT)
                .actorUserId(authorUserId)
                .actorRole(access.role())
                .build());

        return mapper.toResponse(request);
    }

    @Transactional(readOnly = true)
    public Page<MerchantProfileChangeSummary> list(
            MerchantProfileChangeStatus status,
            Pageable pageable,
            ResolvedPartnerAccess access
    ) {
        Page<MerchantProfileChangeRequest> requests = status == null
                ? requestRepository.findByMerchantIdOrderByUpdatedAtDesc(access.merchantId(), pageable)
                : requestRepository.findByMerchantIdAndStatusOrderByUpdatedAtDesc(
                        access.merchantId(), status, pageable);
        return requests.map(mapper::toSummary);
    }

    @Transactional(readOnly = true)
    public MerchantProfileChangeResponse get(Long requestId, ResolvedPartnerAccess access) {
        return mapper.toResponse(findOwned(requestId, access));
    }

    @Transactional
    public MerchantProfileChangeResponse update(
            Long requestId,
            MerchantProfileChangePayload payload,
            ResolvedPartnerAccess access
    ) {
        MerchantProfileChangeRequest request = findOwnedForUpdate(requestId, access);
        if (request.getStatus() != MerchantProfileChangeStatus.DRAFT
                && request.getStatus() != MerchantProfileChangeStatus.REVISION_REQUESTED) {
            throw new IllegalStateException("Заявку в текущем статусе нельзя редактировать");
        }

        mapper.applyPayload(request, payload);
        return mapper.toResponse(requestRepository.save(request));
    }

    @Transactional
    public void deleteDraft(Long requestId, ResolvedPartnerAccess access) {
        MerchantProfileChangeRequest request = findOwnedForUpdate(requestId, access);
        if (request.getStatus() != MerchantProfileChangeStatus.DRAFT) {
            throw new IllegalStateException("Удалить можно только черновик заявки");
        }
        requestRepository.delete(request);
    }

    private MerchantProfileChangeRequest findOwned(
            Long requestId,
            ResolvedPartnerAccess access
    ) {
        return requestRepository.findDetailedByIdAndMerchantId(requestId, access.merchantId())
                .orElseThrow(() -> new ResourceNotFoundException("Заявка не найдена"));
    }

    private MerchantProfileChangeRequest findOwnedForUpdate(
            Long requestId,
            ResolvedPartnerAccess access
    ) {
        return requestRepository
                .findDetailedByIdAndMerchantIdForUpdate(requestId, access.merchantId())
                .orElseThrow(() -> new ResourceNotFoundException("Заявка не найдена"));
    }

    private void requireActiveMerchant(Merchant merchant) {
        if (!merchant.isActive()) {
            throw new IllegalStateException("Мерчант не активен");
        }
    }
}
