package uz.topdim.order.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import uz.topdim.order.dto.OrderResponse;
import uz.topdim.order.dto.AdminOrderResponse;
import uz.topdim.order.entity.OrderStatus;
import uz.topdim.order.service.OrderService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrderControllerResponseTest {

    @Mock private OrderService orderService;
    @InjectMocks private OrderController orderController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(orderController).build();
    }

    @Test
    void getUserOrders_returnsMappedOrderResponsePage() throws Exception {
        OrderResponse response = OrderResponse.builder()
                .id(11L)
                .orderNumber("ORD-11")
                .title("Spa package")
                .itemCount(2)
                .build();
        when(orderService.getUserOrders(7L, 0, 20))
                .thenReturn(new PageImpl<>(List.of(response), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/orders")
                        .header("X-User-Id", 7L)
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(11))
                .andExpect(jsonPath("$.data.content[0].title").value("Spa package"))
                .andExpect(jsonPath("$.data.content[0].itemCount").value(2))
                .andExpect(jsonPath("$.data.content[0].items").doesNotExist());
    }

    @Test
    void getOrder_returnsResponseMappedInsideServiceTransaction() throws Exception {
        OrderResponse response = OrderResponse.builder()
                .id(11L)
                .orderNumber("ORD-11")
                .title("Spa package")
                .itemCount(2)
                .build();
        when(orderService.getOrderResponseById(11L, 7L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/orders/11")
                        .header("X-User-Id", 7L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(11))
                .andExpect(jsonPath("$.data.title").value("Spa package"))
                .andExpect(jsonPath("$.data.itemCount").value(2))
                .andExpect(jsonPath("$.data.items").doesNotExist());
    }

    @Test
    void getAdminOrders_returnsMinimalResponseWithoutRedemptionSecrets() throws Exception {
        AdminOrderResponse order = AdminOrderResponse.builder()
                .id(11L)
                .orderNumber("ORD-2026-0011")
                .userId(7L)
                .userEmail("user@example.com")
                .userPhone("+998901234567")
                .status(OrderStatus.PAID)
                .totalAmount(new BigDecimal("150000"))
                .createdAt(LocalDateTime.of(2026, 8, 4, 10, 30))
                .build();
        when(orderService.getAllOrders(null, 0, 20))
                .thenReturn(new PageImpl<>(List.of(order), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/admin/orders")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(11))
                .andExpect(jsonPath("$.data.content[0].orderNumber").value("ORD-2026-0011"))
                .andExpect(jsonPath("$.data.content[0].purchasedCoupons").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].items").doesNotExist())
                .andExpect(jsonPath("$..qrToken").doesNotExist());
    }
}
