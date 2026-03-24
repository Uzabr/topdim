package uz.topdim.order.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import uz.topdim.order.entity.Order;
import uz.topdim.order.entity.OrderItem;

import java.util.Map;

/**
 * MapStruct маппер для заказов.
 * Order → OrderResponse, CartItem → CartItemResponse.
 */
@Mapper(componentModel = "spring")
public interface OrderMapper {

    default Map<String, Object> toSummary(Order order) {
        return Map.of(
                "id", order.getId(),
                "userId", order.getUserId(),
                "status", order.getStatus().name(),
                "totalAmount", order.getTotalAmount(),
                "itemCount", order.getItems() != null ? order.getItems().size() : 0,
                "createdAt", order.getCreatedAt().toString()
        );
    }
}
