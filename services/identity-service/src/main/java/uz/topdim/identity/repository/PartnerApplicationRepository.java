package uz.topdim.identity.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uz.topdim.identity.entity.ApplicationStatus;
import uz.topdim.identity.entity.PartnerApplication;

import java.util.Collection;
import java.util.Optional;

@Repository
public interface PartnerApplicationRepository extends JpaRepository<PartnerApplication, Long> {
    Page<PartnerApplication> findByStatus(ApplicationStatus status, Pageable pageable);
    boolean existsByPhoneAndStatusIn(String phone, Collection<ApplicationStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT application FROM PartnerApplication application WHERE application.id = :id")
    Optional<PartnerApplication> findByIdForUpdate(@Param("id") Long id);
}
