package uz.topdim.identity.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.test.web.servlet.MockMvc;
import uz.topdim.identity.config.SecurityConfig;
import uz.topdim.identity.exception.GlobalExceptionHandler;
import uz.topdim.identity.security.CustomUserDetailsService;
import uz.topdim.identity.security.RoleHeaderAuthenticationFilter;
import uz.topdim.identity.service.PartnerApplicationService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminPartnerApplicationController.class)
@AutoConfigureMockMvc(addFilters = true)
@Import({SecurityConfig.class, RoleHeaderAuthenticationFilter.class, GlobalExceptionHandler.class})
class AdminPartnerApplicationControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PartnerApplicationService service;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("admin can list partner applications")
    void getAll_adminAllowed() throws Exception {
        when(service.getAll(any(), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/admin/partner-applications")
                        .header("X-User-Id", "7")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk());

        verify(service).getAll(any(), any());
    }

    @Test
    @DisplayName("moderator cannot list partner applications")
    void getAll_moderatorForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/partner-applications")
                        .header("X-User-Id", "8")
                        .header("X-User-Role", "MODERATOR"))
                .andExpect(status().isForbidden());

        verify(service, never()).getAll(any(), any());
    }

    @Test
    @DisplayName("anonymous cannot list partner applications")
    void getAll_anonymousForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/partner-applications"))
                .andExpect(status().isForbidden());

        verify(service, never()).getAll(any(), any());
    }
}
