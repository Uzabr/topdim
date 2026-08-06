package uz.topdim.order.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.topdim.order.entity.RefundRequest;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Репозиторий запросов на возврат.
 * Поиск по userId, orderId, статусу, purchasedCouponId.
 */
public interface RefundRequestRepository extends JpaRepository<RefundRequest, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT refund FROM RefundRequest refund WHERE refund.id = :id")
    Optional<RefundRequest> findByIdForUpdate(@Param("id") Long id);

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
