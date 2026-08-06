package uz.topdim.order.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.topdim.order.entity.Complaint;
import uz.topdim.order.entity.ComplaintStatus;

import java.util.Optional;

public interface ComplaintRepository extends JpaRepository<Complaint, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT complaint FROM Complaint complaint WHERE complaint.id = :id")
    Optional<Complaint> findByIdForUpdate(@Param("id") Long id);

    boolean existsByPurchasedCouponIdAndStatus(Long purchasedCouponId, ComplaintStatus status);

    long countByStatus(ComplaintStatus status);

    Page<Complaint> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    Page<Complaint> findByStatusOrderByCreatedAtDesc(ComplaintStatus status, Pageable pageable);
}
