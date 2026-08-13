package uz.topdim.identity.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import uz.topdim.identity.config.SecurityConfig;
import uz.topdim.identity.exception.GlobalExceptionHandler;
import uz.topdim.identity.security.CustomUserDetailsService;
import uz.topdim.identity.security.RoleHeaderAuthenticationFilter;
import uz.topdim.identity.service.PartnerStaffService;

import java.util.Set;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = InternalPartnerAccessController.class)
@AutoConfigureMockMvc(addFilters = true)
@Import({SecurityConfig.class, RoleHeaderAuthenticationFilter.class, GlobalExceptionHandler.class})
class InternalPartnerAccessControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private PartnerStaffService partnerStaffService;
    @MockBean private CustomUserDetailsService customUserDetailsService;

    @Test
    void activeStaffLocationIdsAreAvailableToInternalClientsWithoutGatewayHeaders() throws Exception {
        when(partnerStaffService.getActiveStaffLocationIds(7L)).thenReturn(Set.of(11L, 12L));

        mockMvc.perform(get(
                        "/api/v1/internal/partner-access/merchants/7/active-staff-location-ids"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.containsInAnyOrder(11, 12)));
    }
}
