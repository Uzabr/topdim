package uz.topdim.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.topdim.auth.entity.AuditLog;
import uz.topdim.auth.repository.AuditLogRepository;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    @Transactional
    public void logAction(Long userId, String action, String entityName, Long entityId, String details) {
        AuditLog log = AuditLog.builder()
                .userId(userId)
                .action(action)
                .entityName(entityName)
                .entityId(entityId)
                .details(details)
                .build();
        auditLogRepository.save(log);
    }
}
