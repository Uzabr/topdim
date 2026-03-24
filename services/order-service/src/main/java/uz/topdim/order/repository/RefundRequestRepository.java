package uz.topdim.order.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.topdim.order.entity.RefundRequest;

import java.util.List;

/**
 * Репозиторий запросов на возврат.
 * Поиск по userId, orderId, статусу.
 */
public interface RefundRequestRepository extends JpaRepository<RefundRequest, Long> {
    Page<RefundRequest> findByStatus(RefundRequest.RefundStatus status, Pageable pageable);
    List<RefundRequest> findByUserId(Long userId);
    List<RefundRequest> findByOrderId(Long orderId);
}
