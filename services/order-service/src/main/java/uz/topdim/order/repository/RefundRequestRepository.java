package uz.topdim.order.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.topdim.order.entity.RefundRequest;

import java.util.Collection;
import java.util.List;

/**
 * Репозиторий запросов на возврат.
 * Поиск по userId, orderId, статусу, purchasedCouponId.
 */
public interface RefundRequestRepository extends JpaRepository<RefundRequest, Long> {
    Page<RefundRequest> findByStatus(RefundRequest.RefundStatus status, Pageable pageable);
    List<RefundRequest> findByUserId(Long userId);
    List<RefundRequest> findByOrderId(Long orderId);

    List<RefundRequest> findByUserIdOrderByCreatedAtDesc(Long userId);

    Page<RefundRequest> findByStatusOrderByCreatedAtDesc(RefundRequest.RefundStatus status, Pageable pageable);

    Page<RefundRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);

    boolean existsByPurchasedCouponIdAndStatusIn(
            Long purchasedCouponId,
            Collection<RefundRequest.RefundStatus> statuses
    );

    List<RefundRequest> findByPurchasedCouponIdOrderByCreatedAtDesc(Long purchasedCouponId);
}
