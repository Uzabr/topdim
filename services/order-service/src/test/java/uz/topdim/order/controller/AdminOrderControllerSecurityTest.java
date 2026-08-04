package uz.topdim.order.controller;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.test.web.servlet.MockMvc;
import uz.topdim.order.config.SecurityConfig;
import uz.topdim.order.dto.AdminPurchasedCouponLookupResponse;
import uz.topdim.order.exception.GlobalExceptionHandler;
import uz.topdim.order.security.RoleHeaderAuthenticationFilter;
import uz.topdim.order.service.OrderService;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@Import({SecurityConfig.class, RoleHeaderAuthenticationFilter.class, GlobalExceptionHandler.class})
class AdminOrderControllerSecurityTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private OrderService orderService;

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "SUPER_ADMIN"})
    void adminRoles_canReadOrdersAndLookupPurchasedCoupons(String role) throws Exception {
        when(orderService.getAllOrders(null, 0, 20)).thenReturn(Page.empty());
        when(orderService.adminLookupByCouponCode("CP-ABCD1234")).thenReturn(
                AdminPurchasedCouponLookupResponse.builder()
                        .purchasedCouponId(51L)
                        .couponCode("CP-ABCD1234")
                        .build()
        );

        mockMvc.perform(get("/api/v1/admin/orders")
                        .header("X-User-Id", "42")
                        .header("X-User-Role", role))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/purchased-coupons/lookup")
                        .param("couponCode", "CP-ABCD1234")
                        .header("X-User-Id", "42")
                        .header("X-User-Role", role))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.couponCode").value("CP-ABCD1234"))
                .andExpect(jsonPath("$..qrToken").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(strings = {"MODERATOR", "PARTNER", "USER"})
    void nonAdminRoles_cannotReadOrdersOrLookupPurchasedCoupons(String role) throws Exception {
        mockMvc.perform(get("/api/v1/admin/orders")
                        .header("X-User-Id", "42")
                        .header("X-User-Role", role))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/purchased-coupons/lookup")
                        .param("couponCode", "CP-ABCD1234")
                        .header("X-User-Id", "42")
                        .header("X-User-Role", role))
                .andExpect(status().isForbidden());
    }
}
