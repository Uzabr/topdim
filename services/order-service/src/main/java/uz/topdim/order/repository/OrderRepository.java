package uz.topdim.order.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.topdim.order.entity.Order;
import uz.topdim.order.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Репозиторий заказов.
 * Поиск заказов по userId, номеру заказа.
 */
public interface OrderRepository extends JpaRepository<Order, Long> {
    Page<Order> findByUserId(Long userId, Pageable pageable);
    Page<Order> findByStatus(OrderStatus status, Pageable pageable);
    Optional<Order> findByOrderNumber(String orderNumber);

    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            LocalDateTime from,
            LocalDateTime to
    );

    List<Order> findTop5ByOrderByCreatedAtDesc();

    @Query("""
            select cast(o.paidAt as LocalDate) as date,
                   count(o) as orders,
                   coalesce(sum(o.totalAmount), 0) as revenue
            from Order o
            where o.paidAt >= :from
              and o.paidAt < :to
              and o.status in :statuses
            group by cast(o.paidAt as LocalDate)
            order by cast(o.paidAt as LocalDate)
            """)
    List<DailySalesProjection> findPaidSalesByDay(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("statuses") Set<OrderStatus> statuses
    );

    interface DailySalesProjection {
        LocalDate getDate();
        long getOrders();
        BigDecimal getRevenue();
    }
}
