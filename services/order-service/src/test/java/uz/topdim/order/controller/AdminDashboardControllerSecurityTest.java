package uz.topdim.order.controller;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import uz.topdim.order.config.SecurityConfig;
import uz.topdim.order.dto.AdminDashboardResponse;
import uz.topdim.order.exception.GlobalExceptionHandler;
import uz.topdim.order.security.RoleHeaderAuthenticationFilter;
import uz.topdim.order.service.AdminDashboardService;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminDashboardController.class)
@Import({SecurityConfig.class, RoleHeaderAuthenticationFilter.class, GlobalExceptionHandler.class})
class AdminDashboardControllerSecurityTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private AdminDashboardService adminDashboardService;

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "SUPER_ADMIN"})
    void adminRoles_canReadDashboard(String role) throws Exception {
        when(adminDashboardService.getDashboard()).thenReturn(
                new AdminDashboardResponse(3, new BigDecimal("410000"), 2, List.of(), List.of())
        );

        mockMvc.perform(get("/api/v1/admin/dashboard")
                        .header("X-User-Id", "42")
                        .header("X-User-Role", role))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ordersToday").value(3))
                .andExpect(jsonPath("$.data.paidRevenueToday").value(410000))
                .andExpect(jsonPath("$.data.pendingComplaints").value(2));
    }

    @ParameterizedTest
    @ValueSource(strings = {"MODERATOR", "PARTNER", "USER"})
    void nonAdminRoles_cannotReadDashboard(String role) throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard")
                        .header("X-User-Id", "42")
                        .header("X-User-Role", role))
                .andExpect(status().isForbidden());
    }
}
