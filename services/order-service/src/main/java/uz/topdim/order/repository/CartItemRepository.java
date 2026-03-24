package uz.topdim.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.topdim.order.entity.CartItem;

/**
 * Репозиторий элементов корзины.
 * CRUD операции для CartItem.
 */
public interface CartItemRepository extends JpaRepository<CartItem, Long> {
}
