package uz.topdim.coupon.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import uz.topdim.coupon.config.SecurityConfig;
import uz.topdim.coupon.exception.GlobalExceptionHandler;
import uz.topdim.coupon.security.RoleHeaderAuthenticationFilter;
import uz.topdim.coupon.service.ModCouponService;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ModCouponController.class)
@Import({SecurityConfig.class, RoleHeaderAuthenticationFilter.class, GlobalExceptionHandler.class})
class ModCouponReviewSecurityTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private ModCouponService modCouponService;

    @Test
    void moderator_supportCouponReview_returnsForbidden() throws Exception {
        mockMvc.perform(couponReview("MODERATOR", "APPROVE", "SUP-42: подтверждено по телефону"))
                .andExpect(status().isForbidden());

        verify(modCouponService, never()).reviewCoupon(anyLong(), anyLong(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "SUPER_ADMIN"})
    void adminRoles_supportCouponReview_forwardsActorAndReason(String role) throws Exception {
        mockMvc.perform(couponReview(role, "APPROVE", "  SUP-42: подтверждено по телефону  "))
                .andExpect(status().isOk());

        verify(modCouponService).reviewCoupon(
                42L,
                10L,
                "APPROVE",
                "SUP-42: подтверждено по телефону"
        );
    }

    @Test
    void supportCouponReview_blankReason_returnsBadRequest() throws Exception {
        mockMvc.perform(couponReview("ADMIN", "REJECT", "   "))
                .andExpect(status().isBadRequest());

        verify(modCouponService, never()).reviewCoupon(anyLong(), anyLong(), any(), any());
    }

    @Test
    void moderator_canStillReviewUserContent() throws Exception {
        mockMvc.perform(patch("/api/v1/mod/reviews/50/review")
                        .header("X-User-Id", "42")
                        .header("X-User-Role", "MODERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPROVE\",\"reason\":null}"))
                .andExpect(status().isOk());

        verify(modCouponService).reviewUserReview(42L, 50L, "APPROVE", null);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder couponReview(
            String role,
            String status,
            String reason
    ) {
        return patch("/api/v1/mod/coupons/10/review")
                .header("X-User-Id", "42")
                .header("X-User-Role", role)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + status + "\",\"reason\":\"" + reason + "\"}");
    }
}
