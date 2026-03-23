package uz.topdim.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.topdim.payment.entity.Payment;
import uz.topdim.payment.entity.PaymentStatus;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByOrderId(Long orderId);

    Optional<Payment> findByTransactionId(String transactionId);

    List<Payment> findByUserId(Long userId);

    List<Payment> findByStatus(PaymentStatus status);
}
