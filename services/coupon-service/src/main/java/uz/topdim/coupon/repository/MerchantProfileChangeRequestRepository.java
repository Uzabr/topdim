package uz.topdim.coupon.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.topdim.coupon.entity.MerchantProfileChangeRequest;
import uz.topdim.coupon.entity.MerchantProfileChangeStatus;

import java.util.Collection;
import java.util.Optional;

public interface MerchantProfileChangeRequestRepository
        extends JpaRepository<MerchantProfileChangeRequest, Long> {

    @EntityGraph(attributePaths = "locations")
    @Query("SELECT request FROM MerchantProfileChangeRequest request WHERE request.id = :id")
    Optional<MerchantProfileChangeRequest> findDetailedById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "locations")
    @Query("SELECT request FROM MerchantProfileChangeRequest request WHERE request.id = :id")
    Optional<MerchantProfileChangeRequest> findDetailedByIdForUpdate(@Param("id") Long id);

    long countByMerchantIdAndStatusIn(
            Long merchantId,
            Collection<MerchantProfileChangeStatus> statuses
    );
}
