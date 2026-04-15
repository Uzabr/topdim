package uz.topdim.identity.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.topdim.identity.entity.Staff;

import java.util.List;
import java.util.Optional;

public interface StaffRepository extends JpaRepository<Staff, Long> {

    List<Staff> findByUserId(Long userId);

    Optional<Staff> findByUserIdAndId(Long userId, Long id);
}
