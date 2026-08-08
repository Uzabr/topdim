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
import uz.topdim.identity.service.AuditLogService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminAuditLogController.class)
@AutoConfigureMockMvc(addFilters = true)
@Import({SecurityConfig.class, RoleHeaderAuthenticationFilter.class, GlobalExceptionHandler.class})
class AdminAuditLogControllerSecurityTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private AuditLogService auditLogService;
    @MockBean private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("ADMIN может читать журнал действий")
    void getAuditLogs_adminAllowed() throws Exception {
        when(auditLogService.search(any(), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .header("X-User-Id", "8")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk());

        verify(auditLogService).search(any(), any());
    }

    @Test
    @DisplayName("SUPER_ADMIN может читать журнал действий")
    void getAuditLogs_superAdminAllowed() throws Exception {
        when(auditLogService.search(any(), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .header("X-User-Id", "7")
                        .header("X-User-Role", "SUPER_ADMIN"))
                .andExpect(status().isOk());

        verify(auditLogService).search(any(), any());
    }

    @Test
    @DisplayName("MODERATOR не может читать журнал действий")
    void getAuditLogs_moderatorForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .header("X-User-Id", "9")
                        .header("X-User-Role", "MODERATOR"))
                .andExpect(status().isForbidden());

        verify(auditLogService, never()).search(any(), any());
    }

    @Test
    @DisplayName("getAuditLogs ограничивает размер страницы")
    void getAuditLogs_oversizedPage_badRequest() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .param("size", "101")
                        .header("X-User-Id", "8")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Размер страницы должен быть от 1 до 100"));

        verify(auditLogService, never()).search(any(), any());
    }
}
