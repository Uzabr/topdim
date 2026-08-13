package uz.topdim.coupon.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.coupon.client.IdentityPartnerAccessClient;
import uz.topdim.coupon.client.ModerationAssigneeContext;
import uz.topdim.coupon.client.ModerationAssigneeOption;
import uz.topdim.coupon.dto.merchantprofile.AdminMerchantProfileChangeFilter;
import uz.topdim.coupon.dto.merchantprofile.MerchantProfileChangeResponse;
import uz.topdim.coupon.dto.merchantprofile.MerchantProfileChangeSummary;
import uz.topdim.coupon.dto.merchantprofile.MerchantProfileChangeHistoryResponse;
import uz.topdim.coupon.dto.merchantprofile.MerchantProfilePreflightResponse;
import uz.topdim.coupon.entity.Merchant;
import uz.topdim.coupon.entity.MerchantLocation;
import uz.topdim.coupon.entity.MerchantProfileChangeHistory;
import uz.topdim.coupon.entity.MerchantProfileChangeLocation;
import uz.topdim.coupon.entity.MerchantProfileChangeRequest;
import uz.topdim.coupon.entity.MerchantProfileChangeStatus;
import uz.topdim.coupon.exception.PartnerAccessUnavailableException;
import uz.topdim.coupon.exception.ResourceNotFoundException;
import uz.topdim.coupon.repository.MerchantLocationRepository;
import uz.topdim.coupon.repository.MerchantProfileChangeHistoryRepository;
import uz.topdim.coupon.repository.MerchantProfileChangeRequestRepository;
import uz.topdim.coupon.repository.MerchantRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static uz.topdim.coupon.util.PhoneUtils.normalize;

@Service
@RequiredArgsConstructor
@Slf4j
public class MerchantProfileModerationService {

    private static final Sort DEFAULT_QUEUE_SORT = Sort.by(
            Sort.Order.desc("submittedAt"),
            Sort.Order.desc("id")
    );

    private final MerchantProfileChangeRequestRepository requestRepository;
    private final MerchantProfileChangeHistoryRepository historyRepository;
    private final MerchantRepository merchantRepository;
    private final MerchantLocationRepository locationRepository;
    private final MerchantProfileMapper mapper;
    private final IdentityPartnerAccessClient identityClient;
    private final MerchantProfileOutboxService outboxService;

    @Transactional(readOnly = true)
    public Page<MerchantProfileChangeSummary> list(
            AdminMerchantProfileChangeFilter filter,
            Pageable pageable
    ) {
        Pageable resolvedPageable = pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), DEFAULT_QUEUE_SORT);
        return requestRepository.findAll(buildSpecification(filter), resolvedPageable)
                .map(mapper::toSummary);
    }

    @Transactional
    public MerchantProfileChangeResponse takeToWork(
            Long requestId,
            Long actorUserId,
            String actorRole
    ) {
        LocalDateTime assignedAt = LocalDateTime.now();
        int claimed = requestRepository.claimPending(
                requestId,
                actorUserId,
                MerchantProfileChangeStatus.PENDING_REVIEW,
                MerchantProfileChangeStatus.IN_REVIEW,
                assignedAt);
        if (claimed == 0) {
            if (requestRepository.existsById(requestId)) {
                throw new IllegalStateException(
                        "Заявка уже взята в работу или не ожидает модерации");
            }
            throw new ResourceNotFoundException("Заявка не найдена");
        }

        MerchantProfileChangeRequest request = requestRepository.findDetailedById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Заявка не найдена"));
        MerchantProfileChangeHistory history = historyRepository.save(
                MerchantProfileChangeHistory.builder()
                        .request(request)
                        .previousStatus(MerchantProfileChangeStatus.PENDING_REVIEW)
                        .newStatus(MerchantProfileChangeStatus.IN_REVIEW)
                        .actorUserId(actorUserId)
                        .actorRole(actorRole)
                        .build());
        outboxService.enqueue(request, history);
        return mapper.toResponse(request);
    }

    @Transactional
    public MerchantProfileChangeResponse release(
            Long requestId,
            Long actorUserId,
            String actorRole
    ) {
        MerchantProfileChangeRequest request = findDetailedForUpdate(requestId);
        if (request.getStatus() != MerchantProfileChangeStatus.IN_REVIEW) {
            throw new IllegalStateException("Заявка не находится в работе");
        }

        request.setStatus(MerchantProfileChangeStatus.PENDING_REVIEW);
        request.setAssigneeUserId(null);
        request.setAssignedAt(null);
        request = requestRepository.save(request);
        appendHistory(
                request,
                MerchantProfileChangeStatus.IN_REVIEW,
                MerchantProfileChangeStatus.PENDING_REVIEW,
                actorUserId,
                actorRole,
                "Заявка освобождена и возвращена в очередь");
        return mapper.toResponse(request);
    }

    @Transactional
    public MerchantProfileChangeResponse reassign(
            Long requestId,
            Long assigneeUserId,
            Long actorUserId,
            String actorRole
    ) {
        if (assigneeUserId == null || assigneeUserId <= 0) {
            throw new IllegalArgumentException("ID исполнителя должен быть положительным");
        }

        MerchantProfileChangeRequest candidate = requestRepository.findDetailedById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Заявка не найдена"));
        validateReassignment(candidate, assigneeUserId);
        validateModerationAssignee(assigneeUserId);

        MerchantProfileChangeRequest request = findDetailedForUpdate(requestId);
        validateReassignment(request, assigneeUserId);
        Long previousAssigneeUserId = request.getAssigneeUserId();
        request.setAssigneeUserId(assigneeUserId);
        request.setAssignedAt(LocalDateTime.now());
        request = requestRepository.save(request);
        appendHistory(
                request,
                MerchantProfileChangeStatus.IN_REVIEW,
                MerchantProfileChangeStatus.IN_REVIEW,
                actorUserId,
                actorRole,
                "Исполнитель изменён с " + previousAssigneeUserId + " на " + assigneeUserId);
        return mapper.toResponse(request);
    }

    @Transactional(readOnly = true)
    public List<ModerationAssigneeOption> listModerationAssignees() {
        try {
            ApiResponse<List<ModerationAssigneeOption>> response =
                    identityClient.getModerationAssignees();
            if (response == null || !response.isSuccess() || response.getData() == null) {
                throw new PartnerAccessUnavailableException(
                        "Список исполнителей временно недоступен");
            }
            return response.getData();
        } catch (PartnerAccessUnavailableException exception) {
            throw exception;
        } catch (Exception exception) {
            log.warn("Identity moderation assignee list unavailable: {}",
                    exception.getClass().getSimpleName());
            throw new PartnerAccessUnavailableException(
                    "Список исполнителей временно недоступен");
        }
    }

    private void validateReassignment(
            MerchantProfileChangeRequest request,
            Long assigneeUserId
    ) {
        if (request.getStatus() != MerchantProfileChangeStatus.IN_REVIEW) {
            throw new IllegalStateException("Переназначить можно только заявку в работе");
        }
        if (assigneeUserId.equals(request.getAssigneeUserId())) {
            throw new IllegalStateException("Заявка уже назначена этому исполнителю");
        }
        if (assigneeUserId.equals(request.getAuthorUserId())) {
            throw new IllegalArgumentException("Нельзя назначить заявку её автору");
        }
    }

    private void validateModerationAssignee(Long assigneeUserId) {
        try {
            ApiResponse<ModerationAssigneeContext> response =
                    identityClient.getModerationAssignee(assigneeUserId);
            if (response == null || !response.isSuccess() || response.getData() == null) {
                throw new PartnerAccessUnavailableException(
                        "Проверка исполнителя временно недоступна");
            }
            ModerationAssigneeContext assignee = response.getData();
            if (!assigneeUserId.equals(assignee.userId())
                    || assignee.role() == null
                    || !assignee.eligible()
                    || !Set.of("MODERATOR", "ADMIN", "SUPER_ADMIN").contains(assignee.role())) {
                throw new IllegalArgumentException(
                        "Исполнитель должен быть активным сотрудником модерации");
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (PartnerAccessUnavailableException exception) {
            throw exception;
        } catch (Exception exception) {
            log.warn("Identity moderation assignee check unavailable for userId={}: {}",
                    assigneeUserId, exception.getClass().getSimpleName());
            throw new PartnerAccessUnavailableException(
                    "Проверка исполнителя временно недоступна");
        }
    }

    @Transactional
    public MerchantProfileChangeResponse requestRevision(
            Long requestId,
            Long actorUserId,
            String actorRole,
            String comment
    ) {
        return decideWithComment(
                requestId,
                actorUserId,
                actorRole,
                comment,
                MerchantProfileChangeStatus.REVISION_REQUESTED);
    }

    @Transactional
    public MerchantProfileChangeResponse reject(
            Long requestId,
            Long actorUserId,
            String actorRole,
            String comment
    ) {
        return decideWithComment(
                requestId,
                actorUserId,
                actorRole,
                comment,
                MerchantProfileChangeStatus.REJECTED);
    }

    @Transactional
    @CacheEvict(value = "catalog", allEntries = true)
    public MerchantProfileChangeResponse approve(
            Long requestId,
            Long actorUserId,
            String actorRole
    ) {
        Long merchantId = requestRepository.findMerchantIdById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Заявка не найдена"));
        Merchant merchant = merchantRepository.findByIdForUpdate(merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Мерчант не найден"));
        MerchantProfileChangeRequest request = findDetailedForUpdate(requestId);
        requireAssignedInReview(request, actorUserId);
        if (request.getBaseProfileVersion() != merchant.getProfileVersion()) {
            throw new IllegalStateException("Заявка устарела: опубликована новая версия профиля");
        }

        List<MerchantLocation> publishedLocations = locationRepository
                .findByMerchantId(merchantId);
        validateSnapshot(
                request,
                publishedLocations.stream().map(MerchantLocation::getId).collect(Collectors.toSet()),
                loadActiveStaffLocationIds(merchantId));

        mapper.applySnapshotToMerchant(request, merchant);
        applyLocationSnapshot(request, merchant, publishedLocations);
        merchant.setProfileVersion(merchant.getProfileVersion() + 1);
        merchantRepository.save(merchant);

        LocalDateTime decidedAt = LocalDateTime.now();
        request.setStatus(MerchantProfileChangeStatus.APPROVED);
        request.setModerationComment(null);
        request.setDecidedAt(decidedAt);
        requestRepository.save(request);
        appendHistory(
                request,
                MerchantProfileChangeStatus.IN_REVIEW,
                MerchantProfileChangeStatus.APPROVED,
                actorUserId,
                actorRole,
                null);
        markCompetingRequestsOutdated(request, decidedAt, actorUserId, actorRole);
        return mapper.toResponse(request);
    }

    @Transactional(readOnly = true)
    public MerchantProfileChangeResponse get(Long requestId) {
        MerchantProfileChangeRequest request = requestRepository.findDetailedById(requestId)
                .filter(candidate -> candidate.getStatus() != MerchantProfileChangeStatus.DRAFT)
                .orElseThrow(() -> new ResourceNotFoundException("Заявка не найдена"));
        return mapper.toResponse(request);
    }

    @Transactional(readOnly = true)
    public List<MerchantProfileChangeHistoryResponse> getHistory(Long requestId) {
        MerchantProfileChangeRequest request = requestRepository.findDetailedById(requestId)
                .filter(candidate -> candidate.getStatus() != MerchantProfileChangeStatus.DRAFT)
                .orElseThrow(() -> new ResourceNotFoundException("Заявка не найдена"));
        return historyRepository.findByRequestIdOrderByCreatedAtAscIdAsc(request.getId())
                .stream()
                .map(history -> new MerchantProfileChangeHistoryResponse(
                        history.getId(),
                        history.getPreviousStatus(),
                        history.getNewStatus(),
                        history.getActorUserId(),
                        history.getActorRole(),
                        history.getComment(),
                        history.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public MerchantProfilePreflightResponse getPreflight(Long requestId) {
        MerchantProfileChangeRequest request = requestRepository.findDetailedById(requestId)
                .filter(candidate -> candidate.getStatus() != MerchantProfileChangeStatus.DRAFT)
                .orElseThrow(() -> new ResourceNotFoundException("Заявка не найдена"));
        Set<Long> activeStaffLocationIds = loadActiveStaffLocationIds(request.getMerchant().getId());
        List<MerchantProfilePreflightResponse.BlockingLocation> blockingLocations =
                request.getLocations().stream()
                        .filter(location -> location.getSourceLocationId() != null)
                        .filter(location -> !location.isActive())
                        .filter(location -> activeStaffLocationIds.contains(location.getSourceLocationId()))
                        .map(location -> new MerchantProfilePreflightResponse.BlockingLocation(
                                location.getSourceLocationId(), location.getTitle()))
                        .toList();
        return new MerchantProfilePreflightResponse(
                blockingLocations.isEmpty(), blockingLocations);
    }

    private MerchantProfileChangeRequest findDetailedForUpdate(Long requestId) {
        return requestRepository.findDetailedByIdForUpdate(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Заявка не найдена"));
    }

    private MerchantProfileChangeResponse decideWithComment(
            Long requestId,
            Long actorUserId,
            String actorRole,
            String comment,
            MerchantProfileChangeStatus newStatus
    ) {
        String normalizedComment = normalizeRequiredComment(comment);
        MerchantProfileChangeRequest request = findDetailedForUpdate(requestId);
        requireAssignedInReview(request, actorUserId);

        request.setStatus(newStatus);
        request.setModerationComment(normalizedComment);
        request.setDecidedAt(LocalDateTime.now());
        request = requestRepository.save(request);
        appendHistory(
                request,
                MerchantProfileChangeStatus.IN_REVIEW,
                newStatus,
                actorUserId,
                actorRole,
                normalizedComment);
        return mapper.toResponse(request);
    }

    private void requireAssignedInReview(
            MerchantProfileChangeRequest request,
            Long actorUserId
    ) {
        if (request.getStatus() != MerchantProfileChangeStatus.IN_REVIEW) {
            throw new IllegalStateException("Заявка не находится в работе");
        }
        if (!actorUserId.equals(request.getAssigneeUserId())) {
            throw new AccessDeniedException("Решение может принять только назначенный исполнитель");
        }
        if (actorUserId.equals(request.getAuthorUserId())) {
            throw new AccessDeniedException("Автор не может принять решение по собственной заявке");
        }
    }

    private String normalizeRequiredComment(String comment) {
        String normalized = comment == null ? null : comment.trim();
        if (normalized == null || normalized.isEmpty()) {
            throw new IllegalArgumentException("Комментарий модератора обязателен");
        }
        if (normalized.length() > 2000) {
            throw new IllegalArgumentException("Комментарий не должен превышать 2000 символов");
        }
        return normalized;
    }

    private void validateSnapshot(
            MerchantProfileChangeRequest request,
            Set<Long> publishedLocationIds,
            Set<Long> activeStaffLocationIds
    ) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new IllegalArgumentException("Название компании обязательно");
        }

        List<Long> requestedSourceIds = request.getLocations().stream()
                .map(MerchantProfileChangeLocation::getSourceLocationId)
                .filter(java.util.Objects::nonNull)
                .toList();
        Set<Long> distinctSourceIds = Set.copyOf(requestedSourceIds);
        if (distinctSourceIds.size() != requestedSourceIds.size()) {
            throw new IllegalArgumentException("Существующий филиал не должен повторяться в снимке");
        }
        if (!publishedLocationIds.containsAll(distinctSourceIds)) {
            throw new IllegalArgumentException("Заявка содержит чужой филиал");
        }
        if (!distinctSourceIds.containsAll(publishedLocationIds)) {
            throw new IllegalArgumentException(
                    "Существующий филиал не должен исчезать из снимка — отключите его явно");
        }

        long activePrimaryCount = request.getLocations().stream()
                .filter(location -> location.isActive() && location.isPrimary())
                .count();
        if (activePrimaryCount != 1L) {
            throw new IllegalArgumentException("В заявке должен быть ровно один активный основной филиал");
        }
        for (MerchantProfileChangeLocation location : request.getLocations()) {
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

    private void applyLocationSnapshot(
            MerchantProfileChangeRequest request,
            Merchant merchant,
            List<MerchantLocation> publishedLocations
    ) {
        Map<Long, MerchantLocation> existingById = publishedLocations.stream()
                .collect(Collectors.toMap(MerchantLocation::getId, Function.identity()));
        request.getLocations().stream()
                .sorted(java.util.Comparator.comparingInt(MerchantProfileChangeLocation::getSortOrder))
                .forEach(snapshot -> {
                    MerchantLocation location = snapshot.getSourceLocationId() == null
                            ? MerchantLocation.builder().merchant(merchant).build()
                            : existingById.get(snapshot.getSourceLocationId());
                    location.setTitle(snapshot.getTitle());
                    location.setAddress(snapshot.getAddress());
                    location.setPhone(normalize(snapshot.getPhone()));
                    location.setWorkingHours(snapshot.getWorkingHours());
                    location.setLatitude(snapshot.getLatitude());
                    location.setLongitude(snapshot.getLongitude());
                    location.setPrimary(snapshot.isPrimary());
                    location.setActive(snapshot.isActive());
                    locationRepository.save(location);
                });
    }

    private void markCompetingRequestsOutdated(
            MerchantProfileChangeRequest approved,
            LocalDateTime decidedAt,
            Long actorUserId,
            String actorRole
    ) {
        List<MerchantProfileChangeRequest> competing = requestRepository
                .findByMerchantIdAndBaseProfileVersionAndStatusIn(
                        approved.getMerchant().getId(),
                        approved.getBaseProfileVersion(),
                        MerchantProfileChangeStatus.activeStatuses());
        for (MerchantProfileChangeRequest request : competing) {
            if (request.getId().equals(approved.getId())) {
                continue;
            }
            MerchantProfileChangeStatus previousStatus = request.getStatus();
            request.setStatus(MerchantProfileChangeStatus.OUTDATED);
            request.setDecidedAt(decidedAt);
            requestRepository.save(request);
            appendHistory(
                    request,
                    previousStatus,
                    MerchantProfileChangeStatus.OUTDATED,
                    actorUserId,
                    actorRole,
                    "Опубликована новая версия профиля");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void appendHistory(
            MerchantProfileChangeRequest request,
            MerchantProfileChangeStatus previousStatus,
            MerchantProfileChangeStatus newStatus,
            Long actorUserId,
            String actorRole,
            String comment
    ) {
        MerchantProfileChangeHistory history = historyRepository.save(
                MerchantProfileChangeHistory.builder()
                        .request(request)
                        .previousStatus(previousStatus)
                        .newStatus(newStatus)
                        .actorUserId(actorUserId)
                        .actorRole(actorRole)
                        .comment(comment)
                        .build());
        outboxService.enqueue(request, history);
    }

    private Specification<MerchantProfileChangeRequest> buildSpecification(
            AdminMerchantProfileChangeFilter filter
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.notEqual(
                    root.get("status"), MerchantProfileChangeStatus.DRAFT));

            if (filter.status() != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), filter.status()));
            }
            if (filter.assigneeUserId() != null) {
                predicates.add(criteriaBuilder.equal(
                        root.get("assigneeUserId"), filter.assigneeUserId()));
            }
            if (filter.submittedFrom() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                        root.get("submittedAt"), filter.submittedFrom()));
            }
            if (filter.submittedTo() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(
                        root.get("submittedAt"), filter.submittedTo()));
            }
            addSearchPredicate(filter.search(), root, criteriaBuilder, predicates);
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private void addSearchPredicate(
            String search,
            jakarta.persistence.criteria.Root<MerchantProfileChangeRequest> root,
            jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder,
            List<Predicate> predicates
    ) {
        if (search == null || search.isBlank()) {
            return;
        }

        String normalized = search.trim().toLowerCase(Locale.ROOT);
        String pattern = "%" + normalized + "%";
        List<Predicate> alternatives = new ArrayList<>();
        alternatives.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), pattern));
        alternatives.add(criteriaBuilder.like(
                criteriaBuilder.lower(root.get("merchant").get("name")), pattern));
        try {
            Long numericSearch = Long.valueOf(normalized);
            alternatives.add(criteriaBuilder.equal(root.get("id"), numericSearch));
            alternatives.add(criteriaBuilder.equal(root.get("authorUserId"), numericSearch));
        } catch (NumberFormatException ignored) {
            // Text search only.
        }
        predicates.add(criteriaBuilder.or(alternatives.toArray(Predicate[]::new)));
    }
}
