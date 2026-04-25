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
import uz.topdim.order.service.PartnerMerchantResolver;
import uz.topdim.order.service.PartnerService;

import java.time.LocalDateTime;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PartnerControllerTest {

    @Mock private PartnerService partnerService;
    @Mock private OrderService orderService;
    @Mock private PartnerMerchantResolver partnerMerchantResolver;
    @InjectMocks private PartnerController partnerController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(partnerController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/partner/redemptions: resolves merchant from X-User-Id and returns DTO")
    void createRedemption_resolvesMerchantFromUserHeader() throws Exception {
        PurchasedCoupon coupon = PurchasedCoupon.builder()
                .id(501L)
                .couponOfferId(10L)
                .couponOptionId(20L)
                .couponTitle("SPA")
                .optionTitle("Standard")
                .couponCode("CP-TEST1234")
                .merchantId(77L)
                .merchantName("SPA Oasis")
                .status(PurchasedCouponStatus.USED)
                .usedAt(LocalDateTime.of(2026, 4, 26, 12, 0))
                .build();

        when(partnerMerchantResolver.resolveMerchantId(10L)).thenReturn(77L);
        when(orderService.redeemCoupon("CP-TEST1234", 77L, "Анна")).thenReturn(coupon);
        when(orderService.mapToRedeemResponse(coupon)).thenReturn(
                uz.topdim.order.dto.RedeemCouponResponse.builder()
                        .purchasedCouponId(501L)
                        .couponOfferId(10L)
                        .couponOptionId(20L)
                        .couponTitle("SPA")
                        .optionTitle("Standard")
                        .couponCode("CP-TEST1234")
                        .status("USED")
                        .merchantId(77L)
                        .merchantName("SPA Oasis")
                        .usedAt(LocalDateTime.of(2026, 4, 26, 12, 0))
                        .build()
        );

        mockMvc.perform(post("/api/v1/partner/redemptions")
                        .header("X-User-Id", "10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "couponCode": " cp-test1234 ",
                                  "staffName": "Анна"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.couponCode").value("CP-TEST1234"))
                .andExpect(jsonPath("$.data.status").value("USED"))
                .andExpect(jsonPath("$.data.merchantId").value(77));

        verify(partnerMerchantResolver).resolveMerchantId(10L);
        verify(orderService).redeemCoupon("CP-TEST1234", 77L, "Анна");
    }

    @Test
    @DisplayName("POST /api/v1/partner/redemptions: inactive merchant returns 409")
    void createRedemption_inactiveMerchant_returns409() throws Exception {
        when(partnerMerchantResolver.resolveMerchantId(10L))
                .thenThrow(new IllegalStateException("Мерчант не активен"));

        mockMvc.perform(post("/api/v1/partner/redemptions")
                        .header("X-User-Id", "10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "couponCode": "CP-TEST1234",
                                  "staffName": "Анна"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }
}
