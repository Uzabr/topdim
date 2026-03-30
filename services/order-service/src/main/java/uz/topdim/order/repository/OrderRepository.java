package uz.topdim.order.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.topdim.order.entity.Order;
import uz.topdim.order.entity.OrderStatus;
import java.util.Optional;

/**
 * Репозиторий заказов.
 * Поиск заказов по userId, номеру заказа.
 */
public interface OrderRepository extends JpaRepository<Order, Long> {
    Page<Order> findByUserId(Long userId, Pageable pageable);
    Page<Order> findByStatus(OrderStatus status, Pageable pageable);
    Optional<Order> findByOrderNumber(String orderNumber);
}
