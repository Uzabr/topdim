package uz.topdim.coupon.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.topdim.coupon.entity.PromoCode;

import java.util.Optional;

public interface PromoCodeRepository extends JpaRepository<PromoCode, Long> {
    Optional<PromoCode> findByCode(String code);
    Page<PromoCode> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
