package uz.topdim.identity.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.topdim.identity.dto.AuditLogFilterRequest;
import uz.topdim.identity.dto.AuditLogResponse;
import uz.topdim.identity.entity.AuditLog;
import uz.topdim.identity.entity.User;
import uz.topdim.identity.repository.AuditLogRepository;
import uz.topdim.identity.repository.UserRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    @Transactional
    public void logAction(Long userId, String action, String entityName, Long entityId, String details) {
        User actor = userId != null ? userRepository.findById(userId).orElse(null) : null;

        AuditLog log = AuditLog.builder()
                .userId(userId)
                .userEmail(actor != null ? actor.getEmail() : null)
                .userName(actor != null ? formatName(actor) : null)
                .userRole(actor != null && actor.getRole() != null ? actor.getRole().name() : null)
                .action(action)
                .entityName(entityName)
                .entityId(entityId)
                .details(details)
                .build();
        auditLogRepository.save(log);
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> search(AuditLogFilterRequest filter, Pageable pageable) {
        Specification<AuditLog> spec = buildSpec(filter);
        return auditLogRepository.findAll(spec, pageable).map(this::toResponse);
    }

    private Specification<AuditLog> buildSpec(AuditLogFilterRequest filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter == null) {
                return cb.conjunction();
            }
            if (filter.getUserId() != null) {
                predicates.add(cb.equal(root.get("userId"), filter.getUserId()));
            }
            if (filter.getAction() != null && !filter.getAction().isBlank()) {
                predicates.add(cb.equal(root.get("action"), filter.getAction().trim()));
            }
            if (filter.getEntityName() != null && !filter.getEntityName().isBlank()) {
                predicates.add(cb.equal(root.get("entityName"), filter.getEntityName().trim()));
            }
            if (filter.getFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.getFrom()));
            }
            if (filter.getTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), filter.getTo()));
            }
            if (filter.getSearch() != null && !filter.getSearch().isBlank()) {
                String pattern = "%" + filter.getSearch().trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("userEmail")), pattern),
                        cb.like(cb.lower(root.get("userName")), pattern),
                        cb.like(cb.lower(root.get("details")), pattern)
                ));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private AuditLogResponse toResponse(AuditLog log) {
        return AuditLogResponse.builder()
                .id(log.getId())
                .userId(log.getUserId())
                .userEmail(log.getUserEmail())
                .userName(log.getUserName())
                .userRole(log.getUserRole())
                .action(log.getAction())
                .entityName(log.getEntityName())
                .entityId(log.getEntityId())
                .details(log.getDetails())
                .createdAt(log.getCreatedAt())
                .build();
    }

    private static String formatName(User user) {
        String first = user.getFirstName() != null ? user.getFirstName().trim() : "";
        String last = user.getLastName() != null ? user.getLastName().trim() : "";
        String full = (first + " " + last).trim();
        return full.isEmpty() ? null : full;
    }
}
