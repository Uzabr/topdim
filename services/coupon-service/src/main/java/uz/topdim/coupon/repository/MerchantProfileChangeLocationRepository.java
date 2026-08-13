package uz.topdim.coupon.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.topdim.coupon.entity.MerchantProfileChangeLocation;

import java.util.List;

public interface MerchantProfileChangeLocationRepository
        extends JpaRepository<MerchantProfileChangeLocation, Long> {

    List<MerchantProfileChangeLocation> findByRequestIdOrderBySortOrderAsc(Long requestId);
}
