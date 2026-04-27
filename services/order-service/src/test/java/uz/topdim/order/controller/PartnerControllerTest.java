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
import uz.topdim.order.client.PartnerAccessContext;
import uz.topdim.order.entity.PurchasedCoupon;
import uz.topdim.order.entity.PurchasedCouponStatus;
import uz.topdim.order.exception.GlobalExceptionHandler;
import uz.topdim.order.service.OrderService;
import uz.topdim.order.service.PartnerAccessResolver;
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
    @Mock private PartnerAccessResolver partnerAccessResolver;
    @InjectMocks private PartnerController partnerController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(partnerController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private PartnerAccessContext ownerContext() {
        return new PartnerAccessContext("OWNER", 77L, null, null, null, true, true);
    }

    private PartnerAccessContext cashierContext() {
        return new PartnerAccessContext("CASHIER", 77L, 200L, 5L, "Кассир Али", false, true);
    }

    @Test
    @DisplayName("POST /api/v1/partner/redemptions: owner resolves context and redeems")
    void createRedemption_ownerResolves() throws Exception {
        PurchasedCoupon coupon = PurchasedCoupon.builder()
                .id(501L).couponOfferId(10L).couponOptionId(20L)
                .couponTitle("SPA").optionTitle("Standard")
                .couponCode("CP-TEST1234").merchantId(77L)
                .merchantName("SPA Oasis").status(PurchasedCouponStatus.USED)
                .usedAt(LocalDateTime.of(2026, 4, 26, 12, 0))
                .build();

        when(partnerAccessResolver.resolveForRedemption(10L)).thenReturn(ownerContext());
        when(orderService.redeemCoupon("CP-TEST1234", 77L, "Анна")).thenReturn(coupon);
        when(orderService.mapToRedeemResponse(coupon)).thenReturn(
                uz.topdim.order.dto.RedeemCouponResponse.builder()
                        .purchasedCouponId(501L).couponOfferId(10L).couponOptionId(20L)
                        .couponTitle("SPA").optionTitle("Standard")
                        .couponCode("CP-TEST1234").status("USED")
                        .merchantId(77L).merchantName("SPA Oasis")
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

        verify(partnerAccessResolver).resolveForRedemption(10L);
        verify(orderService).redeemCoupon("CP-TEST1234", 77L, "Анна");
    }

    @Test
    @DisplayName("POST /api/v1/partner/redemptions: cashier uses own staff name from context")
    void createRedemption_cashierUsesContextStaffName() throws Exception {
        PurchasedCoupon coupon = PurchasedCoupon.builder()
                .id(502L).couponOfferId(10L).couponOptionId(20L)
                .couponTitle("SPA").optionTitle("Standard")
                .couponCode("CP-TEST5678").merchantId(77L)
                .merchantName("SPA Oasis").status(PurchasedCouponStatus.USED)
                .usedAt(LocalDateTime.of(2026, 4, 26, 14, 0))
                .build();

        when(partnerAccessResolver.resolveForRedemption(20L)).thenReturn(cashierContext());
        when(orderService.redeemCoupon("CP-TEST5678", 77L, "Кассир Али")).thenReturn(coupon);
        when(orderService.mapToRedeemResponse(coupon)).thenReturn(
                uz.topdim.order.dto.RedeemCouponResponse.builder()
                        .purchasedCouponId(502L).couponOfferId(10L).couponOptionId(20L)
                        .couponTitle("SPA").optionTitle("Standard")
                        .couponCode("CP-TEST5678").status("USED")
                        .merchantId(77L).merchantName("SPA Oasis")
                        .usedAt(LocalDateTime.of(2026, 4, 26, 14, 0))
                        .build()
        );

        mockMvc.perform(post("/api/v1/partner/redemptions")
                        .header("X-User-Id", "20")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "couponCode": "cp-test5678",
                                  "staffName": "ignored"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.couponCode").value("CP-TEST5678"));

        // Staff name from context, not from request
        verify(orderService).redeemCoupon("CP-TEST5678", 77L, "Кассир Али");
    }

    @Test
    @DisplayName("POST /api/v1/partner/redemptions: inactive merchant returns 409")
    void createRedemption_inactiveMerchant_returns409() throws Exception {
        when(partnerAccessResolver.resolveForRedemption(10L))
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

    @Test
    @DisplayName("GET /api/v1/partner/stats: cashier cannot access dashboard")
    void getStats_cashierForbidden() throws Exception {
        when(partnerAccessResolver.resolveForDashboard(20L))
                .thenThrow(new IllegalStateException("Доступ к дашборду запрещён для роли CASHIER"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/partner/stats")
                        .header("X-User-Id", "20"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }
}
