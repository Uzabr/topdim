package uz.topdim.coupon.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.coupon.client.IdentityPartnerAccessClient;
import uz.topdim.coupon.dto.merchantprofile.MerchantProfileChangePayload;
import uz.topdim.coupon.dto.merchantprofile.MerchantProfileChangeResponse;
import uz.topdim.coupon.dto.merchantprofile.MerchantProfileChangeSummary;
import uz.topdim.coupon.entity.Merchant;
import uz.topdim.coupon.entity.MerchantLocation;
import uz.topdim.coupon.entity.MerchantProfileChangeHistory;
import uz.topdim.coupon.entity.MerchantProfileChangeRequest;
import uz.topdim.coupon.entity.MerchantProfileChangeStatus;
import uz.topdim.coupon.exception.ResourceNotFoundException;
import uz.topdim.coupon.exception.PartnerAccessUnavailableException;
import uz.topdim.coupon.repository.MerchantLocationRepository;
import uz.topdim.coupon.repository.MerchantProfileChangeHistoryRepository;
import uz.topdim.coupon.repository.MerchantProfileChangeRequestRepository;
import uz.topdim.coupon.repository.MerchantRepository;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MerchantProfileDraftService {

    private static final long MAX_ACTIVE_REQUESTS = 10L;

    private final MerchantRepository merchantRepository;
    private final MerchantLocationRepository merchantLocationRepository;
    private final MerchantProfileChangeRequestRepository requestRepository;
    private final MerchantProfileChangeHistoryRepository historyRepository;
    private final MerchantProfileMapper mapper;
    private final IdentityPartnerAccessClient identityClient;

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

    @Transactional
    public MerchantProfileChangeResponse submit(
            Long requestId,
            Long actorUserId,
            ResolvedPartnerAccess access
    ) {
        Merchant merchant = merchantRepository.findByIdForUpdate(access.merchantId())
                .orElseThrow(() -> new ResourceNotFoundException("Мерчант не найден"));
        requireActiveMerchant(merchant);

        MerchantProfileChangeRequest request = findOwnedForUpdate(requestId, access);
        MerchantProfileChangeStatus previousStatus = request.getStatus();
        if (previousStatus != MerchantProfileChangeStatus.DRAFT
                && previousStatus != MerchantProfileChangeStatus.REVISION_REQUESTED) {
            throw new IllegalStateException("Заявку в текущем статусе нельзя отправить");
        }
        if (request.getBaseProfileVersion() != merchant.getProfileVersion()) {
            throw new IllegalStateException(
                    "Заявка устарела: создайте копию на базе текущего профиля");
        }

        long activeCount = requestRepository.countByMerchantIdAndStatusIn(
                merchant.getId(), MerchantProfileChangeStatus.activeStatuses());
        if (activeCount > MAX_ACTIVE_REQUESTS) {
            throw new IllegalStateException("У компании уже есть более 10 активных заявок");
        }

        Set<Long> publishedLocationIds = merchantLocationRepository
                .findByMerchantId(merchant.getId()).stream()
                .map(MerchantLocation::getId)
                .collect(Collectors.toSet());
        validateSnapshot(
                request,
                publishedLocationIds,
                loadActiveStaffLocationIds(merchant.getId()));

        request.setStatus(MerchantProfileChangeStatus.PENDING_REVIEW);
        request.setAssigneeUserId(null);
        request.setAssignedAt(null);
        request.setSubmittedAt(LocalDateTime.now());
        request = requestRepository.save(request);
        appendHistory(request, previousStatus, MerchantProfileChangeStatus.PENDING_REVIEW,
                actorUserId, access.role(), null);
        return mapper.toResponse(request);
    }

    @Transactional
    public MerchantProfileChangeResponse withdraw(
            Long requestId,
            Long actorUserId,
            String reason,
            ResolvedPartnerAccess access
    ) {
        MerchantProfileChangeRequest request = findOwnedForUpdate(requestId, access);
        MerchantProfileChangeStatus previousStatus = request.getStatus();
        if (previousStatus != MerchantProfileChangeStatus.PENDING_REVIEW
                && previousStatus != MerchantProfileChangeStatus.IN_REVIEW
                && previousStatus != MerchantProfileChangeStatus.REVISION_REQUESTED) {
            throw new IllegalStateException("Заявку в текущем статусе нельзя отозвать");
        }

        String normalizedReason = reason == null ? null : reason.trim();
        if (normalizedReason == null || normalizedReason.isEmpty()) {
            throw new IllegalArgumentException("Для отзыва заявки обязательна причина");
        }
        if (normalizedReason.length() > 2000) {
            throw new IllegalArgumentException("Причина отзыва не должна превышать 2000 символов");
        }

        request.setStatus(MerchantProfileChangeStatus.WITHDRAWN);
        request.setAssigneeUserId(null);
        request.setAssignedAt(null);
        request.setWithdrawnAt(LocalDateTime.now());
        request = requestRepository.save(request);
        appendHistory(request, previousStatus, MerchantProfileChangeStatus.WITHDRAWN,
                actorUserId, access.role(), normalizedReason);
        return mapper.toResponse(request);
    }

    @Transactional
    public MerchantProfileChangeResponse copy(
            Long sourceRequestId,
            Long actorUserId,
            ResolvedPartnerAccess access
    ) {
        Merchant merchant = merchantRepository.findByIdForUpdate(access.merchantId())
                .orElseThrow(() -> new ResourceNotFoundException("Мерчант не найден"));
        requireActiveMerchant(merchant);

        MerchantProfileChangeRequest source = findOwned(sourceRequestId, access);
        if (!isTerminal(source.getStatus())) {
            throw new IllegalStateException("Копировать можно только терминальную заявку");
        }

        long activeCount = requestRepository.countByMerchantIdAndStatusIn(
                merchant.getId(), MerchantProfileChangeStatus.activeStatuses());
        if (activeCount >= MAX_ACTIVE_REQUESTS) {
            throw new IllegalStateException("У компании уже есть 10 активных заявок");
        }

        List<MerchantLocation> publishedLocations = merchantLocationRepository
                .findByMerchantId(merchant.getId())
                .stream()
                .sorted(Comparator.comparing(MerchantLocation::getId))
                .toList();
        MerchantProfileChangeRequest copy = mapper.copyRebased(
                source, merchant, publishedLocations, actorUserId, access);
        copy = requestRepository.save(copy);
        appendHistory(copy, null, MerchantProfileChangeStatus.DRAFT,
                actorUserId, access.role(), null);
        return mapper.toResponse(copy);
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

    private Set<Long> loadActiveStaffLocationIds(Long merchantId) {
        try {
            ApiResponse<Set<Long>> response = identityClient.getActiveStaffLocationIds(merchantId);
            if (response == null || !response.isSuccess() || response.getData() == null) {
                throw new PartnerAccessUnavailableException(
                        "Не удалось проверить активных сотрудников компании");
            }
            return response.getData();
        } catch (PartnerAccessUnavailableException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PartnerAccessUnavailableException(
                    "Не удалось проверить активных сотрудников компании");
        }
    }

    private void validateSnapshot(
            MerchantProfileChangeRequest request,
            Set<Long> publishedLocationIds,
            Set<Long> activeStaffLocationIds
    ) {
        List<Long> requestedSourceLocationIds = request.getLocations().stream()
                .map(location -> location.getSourceLocationId())
                .filter(java.util.Objects::nonNull)
                .toList();
        Set<Long> distinctRequestedSourceIds = new HashSet<>(requestedSourceLocationIds);
        if (distinctRequestedSourceIds.size() != requestedSourceLocationIds.size()) {
            throw new IllegalArgumentException(
                    "Существующий филиал не должен повторяться в снимке");
        }
        if (!publishedLocationIds.containsAll(distinctRequestedSourceIds)) {
            throw new IllegalArgumentException(
                    "Заявка содержит филиал, который не принадлежит компании");
        }
        if (!distinctRequestedSourceIds.containsAll(publishedLocationIds)) {
            throw new IllegalArgumentException(
                    "Существующий филиал не должен исчезать из снимка — отключите его явно");
        }

        long activePrimaryCount = request.getLocations().stream()
                .filter(location -> location.isActive() && location.isPrimary())
                .count();
        if (activePrimaryCount != 1L) {
            throw new IllegalArgumentException(
                    "В заявке должен быть ровно один активный основной филиал");
        }

        for (var location : request.getLocations()) {
            if (location.isActive()
                    && (isBlank(location.getAddress()) || isBlank(location.getPhone()))) {
                throw new IllegalArgumentException(
                        "Для каждого активного филиала обязательны адрес и телефон");
            }

            boolean hasLatitude = location.getLatitude() != null;
            boolean hasLongitude = location.getLongitude() != null;
            if (hasLatitude != hasLongitude
                    || hasLatitude && (!Double.isFinite(location.getLatitude())
                    || !Double.isFinite(location.getLongitude())
                    || location.getLatitude() < -90.0
                    || location.getLatitude() > 90.0
                    || location.getLongitude() < -180.0
                    || location.getLongitude() > 180.0)) {
                throw new IllegalArgumentException(
                        "Координаты филиала должны быть указаны парой и находиться в допустимом диапазоне");
            }
        }

        boolean disablesStaffLocation = activeStaffLocationIds.stream()
                .anyMatch(staffLocationId -> request.getLocations().stream()
                        .noneMatch(location -> staffLocationId.equals(location.getSourceLocationId())
                                && location.isActive()));
        if (disablesStaffLocation) {
            throw new IllegalArgumentException(
                    "Нельзя отключить филиал, в котором есть активные кассиры");
        }
    }

    private void appendHistory(
            MerchantProfileChangeRequest request,
            MerchantProfileChangeStatus previousStatus,
            MerchantProfileChangeStatus newStatus,
            Long actorUserId,
            String actorRole,
            String comment
    ) {
        historyRepository.save(MerchantProfileChangeHistory.builder()
                .request(request)
                .previousStatus(previousStatus)
                .newStatus(newStatus)
                .actorUserId(actorUserId)
                .actorRole(actorRole)
                .comment(comment)
                .build());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean isTerminal(MerchantProfileChangeStatus status) {
        return status == MerchantProfileChangeStatus.APPROVED
                || status == MerchantProfileChangeStatus.REJECTED
                || status == MerchantProfileChangeStatus.WITHDRAWN
                || status == MerchantProfileChangeStatus.OUTDATED;
    }
}
