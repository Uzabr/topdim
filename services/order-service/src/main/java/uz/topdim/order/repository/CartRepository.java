package uz.topdim.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.topdim.order.entity.Cart;
import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {
    Optional<Cart> findByUserId(Long userId);
}
