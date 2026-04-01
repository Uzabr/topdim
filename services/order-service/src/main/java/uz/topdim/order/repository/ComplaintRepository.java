package uz.topdim.order.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.topdim.order.entity.Complaint;
import uz.topdim.order.entity.ComplaintStatus;

public interface ComplaintRepository extends JpaRepository<Complaint, Long> {
    Page<Complaint> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    Page<Complaint> findByStatusOrderByCreatedAtDesc(ComplaintStatus status, Pageable pageable);
}
