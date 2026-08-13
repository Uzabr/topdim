package uz.topdim.coupon.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.topdim.coupon.entity.MerchantProfileChangeRequest;
import uz.topdim.coupon.entity.MerchantProfileChangeStatus;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;

public interface MerchantProfileChangeRequestRepository
        extends JpaRepository<MerchantProfileChangeRequest, Long>,
        JpaSpecificationExecutor<MerchantProfileChangeRequest> {

    @EntityGraph(attributePaths = "locations")
    @Query("SELECT request FROM MerchantProfileChangeRequest request WHERE request.id = :id")
    Optional<MerchantProfileChangeRequest> findDetailedById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "locations")
    @Query("SELECT request FROM MerchantProfileChangeRequest request WHERE request.id = :id")
    Optional<MerchantProfileChangeRequest> findDetailedByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = "locations")
    @Query("""
            SELECT request FROM MerchantProfileChangeRequest request
            WHERE request.id = :id AND request.merchant.id = :merchantId
            """)
    Optional<MerchantProfileChangeRequest> findDetailedByIdAndMerchantId(
            @Param("id") Long id,
            @Param("merchantId") Long merchantId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "locations")
    @Query("""
            SELECT request FROM MerchantProfileChangeRequest request
            WHERE request.id = :id AND request.merchant.id = :merchantId
            """)
    Optional<MerchantProfileChangeRequest> findDetailedByIdAndMerchantIdForUpdate(
            @Param("id") Long id,
            @Param("merchantId") Long merchantId
    );

    Page<MerchantProfileChangeRequest> findByMerchantIdOrderByUpdatedAtDesc(
            Long merchantId,
            Pageable pageable
    );

    Page<MerchantProfileChangeRequest> findByMerchantIdAndStatusOrderByUpdatedAtDesc(
            Long merchantId,
            MerchantProfileChangeStatus status,
            Pageable pageable
    );

    long countByMerchantIdAndStatusIn(
            Long merchantId,
            Collection<MerchantProfileChangeStatus> statuses
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE MerchantProfileChangeRequest request
               SET request.status = :inReview,
                   request.assigneeUserId = :actorUserId,
                   request.assignedAt = :assignedAt,
                   request.updatedAt = :assignedAt,
                   request.lockVersion = request.lockVersion + 1
             WHERE request.id = :requestId
               AND request.status = :pendingReview
            """)
    int claimPending(
            @Param("requestId") Long requestId,
            @Param("actorUserId") Long actorUserId,
            @Param("pendingReview") MerchantProfileChangeStatus pendingReview,
            @Param("inReview") MerchantProfileChangeStatus inReview,
            @Param("assignedAt") LocalDateTime assignedAt
    );
}
