package uz.topdim.coupon.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.topdim.coupon.entity.MerchantProfileChangeHistory;

import java.util.List;

public interface MerchantProfileChangeHistoryRepository
        extends JpaRepository<MerchantProfileChangeHistory, Long> {

    List<MerchantProfileChangeHistory> findByRequestIdOrderByCreatedAtAscIdAsc(Long requestId);

    default List<MerchantProfileChangeHistory> findByRequestIdOrderByCreatedAtAsc(Long requestId) {
        return findByRequestIdOrderByCreatedAtAscIdAsc(requestId);
    }
}
