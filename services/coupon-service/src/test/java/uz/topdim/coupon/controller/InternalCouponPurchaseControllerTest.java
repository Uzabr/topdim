package uz.topdim.coupon.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import uz.topdim.coupon.dto.CouponPurchaseSnapshotResponse;
import uz.topdim.coupon.exception.GlobalExceptionHandler;
import uz.topdim.coupon.exception.ResourceNotFoundException;
import uz.topdim.coupon.service.CouponOfferService;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class InternalCouponPurchaseControllerTest {

    @Mock private CouponOfferService couponOfferService;
    @InjectMocks private InternalCouponController internalCouponController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(internalCouponController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET internal purchase snapshot: returns coupon and selected option without using public detail")
    void getPurchaseSnapshot_returnsCanonicalPurchaseData() throws Exception {
        when(couponOfferService.getPurchaseSnapshot(10L, 20L)).thenReturn(
                CouponPurchaseSnapshotResponse.builder()
                        .couponOfferId(10L)
                        .couponOptionId(20L)
                        .couponTitle("Canonical coupon")
                        .optionTitle("Canonical option")
                        .couponStatus("ACTIVE")
                        .optionStatus("ACTIVE")
                        .couponPrice(BigDecimal.valueOf(99000))
                        .quantityLimit(5)
                        .quantitySold(2)
                        .merchantId(77L)
                        .buyUntil(LocalDateTime.of(2027, 6, 1, 12, 0))
                        .useUntil(LocalDateTime.of(2027, 7, 1, 12, 0))
                        .build()
        );

        mockMvc.perform(get("/api/v1/internal/coupons/10/options/20/purchase-snapshot"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.couponOfferId").value(10))
                .andExpect(jsonPath("$.data.couponOptionId").value(20))
                .andExpect(jsonPath("$.data.couponTitle").value("Canonical coupon"))
                .andExpect(jsonPath("$.data.optionTitle").value("Canonical option"))
                .andExpect(jsonPath("$.data.couponPrice").value(99000))
                .andExpect(jsonPath("$.data.merchantId").value(77))
                .andExpect(jsonPath("$.data.useUntil").exists());
    }

    @Test
    @DisplayName("GET internal purchase snapshot: missing coupon or option returns 404")
    void getPurchaseSnapshot_missingOption_returns404() throws Exception {
        when(couponOfferService.getPurchaseSnapshot(10L, 99L))
                .thenThrow(new ResourceNotFoundException("Опция купона не найдена"));

        mockMvc.perform(get("/api/v1/internal/coupons/10/options/99/purchase-snapshot"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Опция купона не найдена"));
    }
}
