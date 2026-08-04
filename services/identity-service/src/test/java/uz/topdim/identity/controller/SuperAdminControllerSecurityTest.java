package uz.topdim.identity.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import uz.topdim.identity.config.SecurityConfig;
import uz.topdim.identity.exception.GlobalExceptionHandler;
import uz.topdim.identity.security.CustomUserDetailsService;
import uz.topdim.identity.security.RoleHeaderAuthenticationFilter;
import uz.topdim.identity.service.SuperAdminService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SuperAdminController.class)
@AutoConfigureMockMvc(addFilters = true)
@Import({SecurityConfig.class, RoleHeaderAuthenticationFilter.class, GlobalExceptionHandler.class})
class SuperAdminControllerSecurityTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private SuperAdminService service;
    @MockBean private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("SUPER_ADMIN может читать список сотрудников")
    void getStaff_superAdminAllowed() throws Exception {
        when(service.getStaffByRole(any(), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/super/staff")
                        .header("X-User-Id", "7")
                        .header("X-User-Role", "SUPER_ADMIN"))
                .andExpect(status().isOk());

        verify(service).getStaffByRole(any(), any());
    }

    @Test
    @DisplayName("ADMIN не может читать список сотрудников")
    void getStaff_adminForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/super/staff")
                        .header("X-User-Id", "8")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isForbidden());

        verify(service, never()).getStaffByRole(any(), any());
    }

    @Test
    @DisplayName("ADMIN не может читать журнал аудита")
    void getAuditLogs_adminForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/super/audit-logs")
                        .header("X-User-Id", "8")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isForbidden());

        verify(service, never()).getAuditLogs(any());
    }

    @Test
    @DisplayName("getStaff отклоняет неизвестную и нештатную роль как bad request")
    void getStaff_invalidRole_badRequest() throws Exception {
        mockMvc.perform(get("/api/v1/super/staff")
                        .param("role", "USER")
                        .header("X-User-Id", "7")
                        .header("X-User-Role", "SUPER_ADMIN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Допустимы только роли ADMIN или MODERATOR"));

        verify(service, never()).getStaffByRole(any(), any());
    }

    @Test
    @DisplayName("getStaff отклоняет отрицательный номер страницы")
    void getStaff_negativePage_badRequest() throws Exception {
        mockMvc.perform(get("/api/v1/super/staff")
                        .param("page", "-1")
                        .header("X-User-Id", "7")
                        .header("X-User-Role", "SUPER_ADMIN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Номер страницы не может быть отрицательным"));

        verify(service, never()).getStaffByRole(any(), any());
    }

    @Test
    @DisplayName("getAuditLogs ограничивает размер страницы")
    void getAuditLogs_oversizedPage_badRequest() throws Exception {
        mockMvc.perform(get("/api/v1/super/audit-logs")
                        .param("size", "101")
                        .header("X-User-Id", "7")
                        .header("X-User-Role", "SUPER_ADMIN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Размер страницы должен быть от 1 до 100"));

        verify(service, never()).getAuditLogs(any());
    }

    @Test
    @DisplayName("blockUser требует явный флаг blocked")
    void blockUser_missingBlocked_badRequest() throws Exception {
        mockMvc.perform(patch("/api/v1/super/users/9/block")
                        .header("X-User-Id", "7")
                        .header("X-User-Role", "SUPER_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.blocked").value("Статус блокировки обязателен"));

        verify(service, never()).blockUser(any(), any(), any(Boolean.class));
    }

    @Test
    @DisplayName("createAdmin возвращает точную ошибку для распространённого пароля")
    void createAdmin_commonPassword_badRequest() throws Exception {
        mockMvc.perform(post("/api/v1/super/admins")
                        .header("X-User-Id", "7")
                        .header("X-User-Role", "SUPER_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "new.admin@topdim.uz",
                                  "password": "Admin123!",
                                  "firstName": "New",
                                  "role": "ADMIN"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.password")
                        .value("Этот пароль слишком распространённый. Выберите более надёжный пароль"));

        verify(service, never()).createAdmin(any(), any());
    }
}
