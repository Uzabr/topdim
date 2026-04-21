package uz.topdim.coupon.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.topdim.coupon.entity.MerchantLocation;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий локаций мерчанта.
 */
public interface MerchantLocationRepository extends JpaRepository<MerchantLocation, Long> {
    List<MerchantLocation> findByMerchantIdAndActiveTrue(Long merchantId);
    List<MerchantLocation> findByMerchantId(Long merchantId);
    Optional<MerchantLocation> findByMerchantIdAndPrimaryTrue(Long merchantId);
    void deleteAllByMerchantId(Long merchantId);

    // Bot lead: phone lookup across all active locations
    Optional<MerchantLocation> findFirstByPhoneAndActiveTrue(String phone);
}
