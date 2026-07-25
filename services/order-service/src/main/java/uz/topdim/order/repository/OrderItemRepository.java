package uz.topdim.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.topdim.order.entity.OrderItem;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    @Query("""
            SELECT item
            FROM OrderItem item
            WHERE item.order.id IN :orderIds
            ORDER BY item.order.id, item.id
            """)
    List<OrderItem> findForOrderIds(@Param("orderIds") List<Long> orderIds);
}
