package uz.topdim.coupon.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import uz.topdim.coupon.config.SecurityConfig;
import uz.topdim.coupon.exception.GlobalExceptionHandler;
import uz.topdim.coupon.security.RoleHeaderAuthenticationFilter;
import uz.topdim.coupon.service.CouponOfferService;
import uz.topdim.coupon.service.MerchantService;

import java.util.stream.Stream;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminCouponController.class)
@Import({SecurityConfig.class, RoleHeaderAuthenticationFilter.class, GlobalExceptionHandler.class})
class AdminCouponActionSecurityTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private CouponOfferService couponOfferService;
    @MockBean private MerchantService merchantService;

    private record PrivilegedRequest(String name, MockHttpServletRequestBuilder request) {}

    static Stream<PrivilegedRequest> privilegedRequests() {
        return Stream.of(
                new PrivilegedRequest("generic status",
                        patch("/api/v1/admin/coupons/10/status").param("status", "PAUSED")),
                new PrivilegedRequest("archive",
                        post("/api/v1/admin/coupons/10/archive")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"reason\":\"Завершено\"}")),
                new PrivilegedRequest("delete", delete("/api/v1/admin/coupons/10")),
                new PrivilegedRequest("reject request",
                        post("/api/v1/admin/coupons/10/reject-request")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"reason\":\"Нарушение правил\"}"))
        );
    }

    @ParameterizedTest(name = "MODERATOR cannot call {0}")
    @MethodSource("privilegedRequests")
    void moderator_privilegedMutation_returnsForbidden(PrivilegedRequest request) throws Exception {
        mockMvc.perform(withStaff(request.request(), "MODERATOR"))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest(name = "{0} reaches privileged admin mutations")
    @MethodSource("adminRoles")
    void adminRoles_privilegedMutations_reachController(String role) throws Exception {
        for (PrivilegedRequest request : privilegedRequests().toList()) {
            mockMvc.perform(withStaff(request.request(), role))
                    .andExpect(status().isOk());
        }
    }

    static Stream<String> adminRoles() {
        return Stream.of("ADMIN", "SUPER_ADMIN");
    }

    @Test
    void moderator_canTakeLeadAndSendOwnedCouponToApproval() throws Exception {
        mockMvc.perform(withStaff(
                        patch("/api/v1/admin/coupons/10/take-to-work")
                                .header("X-User-Email", "mod@sizbiz.uz"),
                        "MODERATOR"))
                .andExpect(status().isOk());

        mockMvc.perform(withStaff(
                        post("/api/v1/admin/coupons/10/send-to-approval"),
                        "MODERATOR"))
                .andExpect(status().isOk());

        verify(couponOfferService).takeToWork(10L, 42L, "mod@sizbiz.uz");
        verify(couponOfferService).sendToApproval(10L, 42L, "MODERATOR");
    }

    private MockHttpServletRequestBuilder withStaff(
            MockHttpServletRequestBuilder request,
            String role
    ) {
        return request
                .header("X-User-Id", "42")
                .header("X-User-Role", role);
    }
}
