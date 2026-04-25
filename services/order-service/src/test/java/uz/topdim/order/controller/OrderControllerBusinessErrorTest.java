package uz.topdim.order.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import uz.topdim.order.exception.GlobalExceptionHandler;
import uz.topdim.order.service.OrderService;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrderControllerBusinessErrorTest {

    @Mock private OrderService orderService;
    @InjectMocks private OrderController orderController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(orderController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/cart/items: unavailable coupon returns 409 with business message")
    void addToCart_unavailableCoupon_returns409() throws Exception {
        when(orderService.addToCart(
                eq(10L), eq(5L), eq(3L), anyString(), anyString(), any(BigDecimal.class),
                eq(1), eq(false), isNull(), isNull()
        )).thenThrow(new IllegalStateException("Купон недоступен для покупки"));

        mockMvc.perform(post("/api/v1/cart/items")
                        .header("X-User-Id", "10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "couponOfferId": 5,
                                  "couponOptionId": 3,
                                  "couponTitle": "SPA",
                                  "optionTitle": "Standard",
                                  "unitPrice": 99000,
                                  "quantity": 1
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Купон недоступен для покупки"));
    }

    @Test
    @DisplayName("POST /api/v1/orders: stale cart returns 409 and keeps message")
    void createOrder_staleCart_returns409() throws Exception {
        when(orderService.createOrder(eq(10L), eq("user@test.com"), eq("+998901234567")))
                .thenThrow(new IllegalStateException("Срок покупки купона истёк"));

        mockMvc.perform(post("/api/v1/orders")
                        .header("X-User-Id", "10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user@test.com",
                                  "phone": "+998901234567"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Срок покупки купона истёк"));
    }
}
