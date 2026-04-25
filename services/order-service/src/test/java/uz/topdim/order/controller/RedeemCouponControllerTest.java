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
import uz.topdim.order.entity.PurchasedCoupon;
import uz.topdim.order.entity.PurchasedCouponStatus;
import uz.topdim.order.exception.GlobalExceptionHandler;
import uz.topdim.order.service.OrderService;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RedeemCouponControllerTest {

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
    @DisplayName("POST /api/v1/orders/redeem: uses X-Merchant-Id header instead of request merchantId")
    void redeemCoupon_usesTrustedMerchantHeader() throws Exception {
        PurchasedCoupon coupon = PurchasedCoupon.builder()
                .id(1L)
                .couponCode("CP-TEST1234")
                .status(PurchasedCouponStatus.USED)
                .build();
        when(orderService.redeemCoupon("CP-TEST1234", 77L, "Анна")).thenReturn(coupon);

        mockMvc.perform(post("/api/v1/orders/redeem")
                        .header("X-Merchant-Id", "77")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "couponCode": "CP-TEST1234",
                                  "merchantId": 999,
                                  "staffName": "Анна"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Verify controller passed header value (77), not body value (999)
        verify(orderService).redeemCoupon("CP-TEST1234", 77L, "Анна");
    }

    @Test
    @DisplayName("POST /api/v1/orders/redeem: wrong merchant returns 409")
    void redeemCoupon_wrongMerchant_returns409() throws Exception {
        when(orderService.redeemCoupon("CP-TEST1234", 88L, "Анна"))
                .thenThrow(new IllegalStateException("Купон принадлежит другому мерчанту"));

        mockMvc.perform(post("/api/v1/orders/redeem")
                        .header("X-Merchant-Id", "88")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "couponCode": "CP-TEST1234",
                                  "staffName": "Анна"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Купон принадлежит другому мерчанту"));
    }
}
