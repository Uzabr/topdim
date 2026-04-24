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
import uz.topdim.coupon.dto.CouponOfferResponse;
import uz.topdim.coupon.exception.GlobalExceptionHandler;
import uz.topdim.coupon.exception.ResourceNotFoundException;
import uz.topdim.coupon.service.CouponOfferService;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CouponPublicVisibilityTest {

    @Mock private CouponOfferService couponOfferService;
    @InjectMocks private CouponController couponController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(couponController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/coupons/{id}: ACTIVE coupon returns 200")
    void getById_activeCoupon_returns200() throws Exception {
        when(couponOfferService.getById(7L)).thenReturn(
                CouponOfferResponse.builder().id(7L).title("Active coupon").status("ACTIVE").build()
        );

        mockMvc.perform(get("/api/v1/coupons/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(7));
    }

    @Test
    @DisplayName("GET /api/v1/coupons/{id}: non-public coupon returns 404")
    void getById_nonPublicCoupon_returns404() throws Exception {
        when(couponOfferService.getById(8L))
                .thenThrow(new ResourceNotFoundException("Купон не найден"));

        mockMvc.perform(get("/api/v1/coupons/8"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Купон не найден"));
    }
}
