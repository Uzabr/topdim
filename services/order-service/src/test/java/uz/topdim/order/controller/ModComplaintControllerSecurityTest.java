package uz.topdim.order.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import uz.topdim.order.config.SecurityConfig;
import uz.topdim.order.exception.GlobalExceptionHandler;
import uz.topdim.order.exception.ResourceNotFoundException;
import uz.topdim.order.security.RoleHeaderAuthenticationFilter;
import uz.topdim.order.service.ComplaintService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ModComplaintController.class)
@Import({SecurityConfig.class, RoleHeaderAuthenticationFilter.class, GlobalExceptionHandler.class})
class ModComplaintControllerSecurityTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private ComplaintService complaintService;

    @ParameterizedTest
    @ValueSource(strings = {"MODERATOR", "ADMIN", "SUPER_ADMIN"})
    void staffRoles_canListAndResolveComplaints(String role) throws Exception {
        when(complaintService.getPendingComplaints(any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/mod/complaints")
                        .header("X-User-Id", "42")
                        .header("X-User-Role", role))
                .andExpect(status().isOk());

        mockMvc.perform(resolveComplaint(role, "RESOLVE", "Проверено"))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PARTNER", "USER"})
    void nonStaffRoles_cannotListOrResolveComplaints(String role) throws Exception {
        mockMvc.perform(get("/api/v1/mod/complaints")
                        .header("X-User-Id", "42")
                        .header("X-User-Role", role))
                .andExpect(status().isForbidden());

        mockMvc.perform(resolveComplaint(role, "RESOLVE", "Проверено"))
                .andExpect(status().isForbidden());
    }

    @Test
    void blankResolution_isRejectedBeforeService() throws Exception {
        mockMvc.perform(resolveComplaint("MODERATOR", "RESOLVE", "   "))
                .andExpect(status().isBadRequest());

        verify(complaintService, never()).resolveComplaint(anyLong(), anyLong(), any());
    }

    @Test
    void missingComplaint_returnsNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("Обращение не найдено: 31"))
                .when(complaintService).resolveComplaint(anyLong(), anyLong(), any());

        mockMvc.perform(resolveComplaint("ADMIN", "RESOLVE", "Проверено"))
                .andExpect(status().isNotFound());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder resolveComplaint(
            String role,
            String decision,
            String resolution
    ) {
        return patch("/api/v1/mod/complaints/31/resolve")
                .header("X-User-Id", "42")
                .header("X-User-Role", role)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"" + decision + "\",\"resolution\":\"" + resolution + "\"}");
    }
}
