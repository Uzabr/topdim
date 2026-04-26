package uz.topdim.identity.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.topdim.identity.entity.ApplicationStatus;
import uz.topdim.identity.entity.PartnerApplication;

@Repository
public interface PartnerApplicationRepository extends JpaRepository<PartnerApplication, Long> {
    Page<PartnerApplication> findByStatus(ApplicationStatus status, Pageable pageable);
    boolean existsByPhoneAndStatus(String phone, ApplicationStatus status);
}
