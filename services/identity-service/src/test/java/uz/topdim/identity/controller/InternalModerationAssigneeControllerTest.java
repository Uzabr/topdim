package uz.topdim.identity.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import uz.topdim.identity.config.SecurityConfig;
import uz.topdim.identity.dto.ModerationAssigneeResponse;
import uz.topdim.identity.dto.ModerationAssigneeOptionResponse;
import uz.topdim.identity.exception.GlobalExceptionHandler;
import uz.topdim.identity.security.CustomUserDetailsService;
import uz.topdim.identity.security.RoleHeaderAuthenticationFilter;
import uz.topdim.identity.service.ModerationAssigneeService;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = InternalModerationAssigneeController.class)
@AutoConfigureMockMvc(addFilters = true)
@Import({SecurityConfig.class, RoleHeaderAuthenticationFilter.class, GlobalExceptionHandler.class})
class InternalModerationAssigneeControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private ModerationAssigneeService moderationAssigneeService;
    @MockBean private CustomUserDetailsService customUserDetailsService;

    @Test
    void returnsEligibilityContextToInternalClient() throws Exception {
        when(moderationAssigneeService.resolve(88L))
                .thenReturn(new ModerationAssigneeResponse(88L, "MODERATOR", true));

        mockMvc.perform(get("/api/v1/internal/moderation-assignees/88"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(88))
                .andExpect(jsonPath("$.data.role").value("MODERATOR"))
                .andExpect(jsonPath("$.data.eligible").value(true));
    }

    @Test
    void returnsEligibleAssigneeOptionsToInternalClient() throws Exception {
        when(moderationAssigneeService.listEligible()).thenReturn(List.of(
                new ModerationAssigneeOptionResponse(
                        88L, "Ali Valiyev", "ali@topdim.uz", "MODERATOR")));

        mockMvc.perform(get("/api/v1/internal/moderation-assignees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].userId").value(88))
                .andExpect(jsonPath("$.data[0].name").value("Ali Valiyev"))
                .andExpect(jsonPath("$.data[0].role").value("MODERATOR"));
    }
}
