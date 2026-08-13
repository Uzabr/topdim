package uz.topdim.coupon.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.topdim.coupon.dto.merchantprofile.AdminMerchantProfileChangeFilter;
import uz.topdim.coupon.dto.merchantprofile.MerchantProfileChangeResponse;
import uz.topdim.coupon.dto.merchantprofile.MerchantProfileChangeSummary;
import uz.topdim.coupon.entity.MerchantProfileChangeRequest;
import uz.topdim.coupon.entity.MerchantProfileChangeHistory;
import uz.topdim.coupon.entity.MerchantProfileChangeStatus;
import uz.topdim.coupon.exception.ResourceNotFoundException;
import uz.topdim.coupon.repository.MerchantProfileChangeHistoryRepository;
import uz.topdim.coupon.repository.MerchantProfileChangeRequestRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class MerchantProfileModerationService {

    private static final Sort DEFAULT_QUEUE_SORT = Sort.by(
            Sort.Order.desc("submittedAt"),
            Sort.Order.desc("id")
    );

    private final MerchantProfileChangeRequestRepository requestRepository;
    private final MerchantProfileChangeHistoryRepository historyRepository;
    private final MerchantProfileMapper mapper;

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
        historyRepository.save(MerchantProfileChangeHistory.builder()
                .request(request)
                .previousStatus(MerchantProfileChangeStatus.PENDING_REVIEW)
                .newStatus(MerchantProfileChangeStatus.IN_REVIEW)
                .actorUserId(actorUserId)
                .actorRole(actorRole)
                .build());
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

        MerchantProfileChangeRequest request = findDetailedForUpdate(requestId);
        if (request.getStatus() != MerchantProfileChangeStatus.IN_REVIEW) {
            throw new IllegalStateException("Переназначить можно только заявку в работе");
        }
        Long previousAssigneeUserId = request.getAssigneeUserId();
        if (assigneeUserId.equals(previousAssigneeUserId)) {
            throw new IllegalStateException("Заявка уже назначена этому исполнителю");
        }

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
    public MerchantProfileChangeResponse get(Long requestId) {
        MerchantProfileChangeRequest request = requestRepository.findDetailedById(requestId)
                .filter(candidate -> candidate.getStatus() != MerchantProfileChangeStatus.DRAFT)
                .orElseThrow(() -> new ResourceNotFoundException("Заявка не найдена"));
        return mapper.toResponse(request);
    }

    private MerchantProfileChangeRequest findDetailedForUpdate(Long requestId) {
        return requestRepository.findDetailedByIdForUpdate(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Заявка не найдена"));
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
