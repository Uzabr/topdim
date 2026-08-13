package uz.topdim.identity.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import uz.topdim.identity.config.SecurityConfig;
import uz.topdim.identity.entity.Role;
import uz.topdim.identity.entity.User;
import uz.topdim.identity.exception.GlobalExceptionHandler;
import uz.topdim.identity.repository.UserRepository;
import uz.topdim.identity.security.CustomUserDetailsService;
import uz.topdim.identity.security.RoleHeaderAuthenticationFilter;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = InternalNotificationTargetController.class)
@AutoConfigureMockMvc(addFilters = true)
@Import({SecurityConfig.class, RoleHeaderAuthenticationFilter.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "internal.auth-secret=service-secret")
class InternalNotificationTargetControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private UserRepository userRepository;
    @MockBean private CustomUserDetailsService customUserDetailsService;

    @Test
    void linkedTelegramAndVerifiedEmailAreReturnedToAuthorizedInternalClient() throws Exception {
        when(userRepository.findById(41L)).thenReturn(Optional.of(User.builder()
                .id(41L)
                .email("manager@example.uz")
                .emailVerified(true)
                .password("hash")
                .firstName("Manager")
                .role(Role.PARTNER)
                .enabled(true)
                .telegramChatId(998877L)
                .telegramLinkedAt(LocalDateTime.of(2026, 8, 13, 10, 0))
                .build()));

        mockMvc.perform(get("/api/v1/internal/notification-targets/41")
                        .header("X-Gateway-Auth", "service-secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(41))
                .andExpect(jsonPath("$.data.email").value("manager@example.uz"))
                .andExpect(jsonPath("$.data.emailVerified").value(true))
                .andExpect(jsonPath("$.data.telegramChatId").value(998877))
                .andExpect(jsonPath("$.data.telegramLinked").value(true));
    }

    @Test
    void unlinkedTelegramAndUnverifiedEmailAreMarkedUnavailable() throws Exception {
        when(userRepository.findById(42L)).thenReturn(Optional.of(User.builder()
                .id(42L)
                .email("owner@example.uz")
                .emailVerified(false)
                .password("hash")
                .firstName("Owner")
                .role(Role.PARTNER)
                .enabled(true)
                .telegramChatId(112233L)
                .telegramLinkedAt(null)
                .build()));

        mockMvc.perform(get("/api/v1/internal/notification-targets/42")
                        .header("X-Gateway-Auth", "service-secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.emailVerified").value(false))
                .andExpect(jsonPath("$.data.telegramLinked").value(false));
    }

    @Test
    void disabledUserContactDataIsNotExposedForExternalDelivery() throws Exception {
        when(userRepository.findById(43L)).thenReturn(Optional.of(User.builder()
                .id(43L)
                .email("blocked@example.uz")
                .emailVerified(true)
                .password("hash")
                .firstName("Blocked")
                .role(Role.PARTNER)
                .enabled(false)
                .telegramChatId(334455L)
                .telegramLinkedAt(LocalDateTime.of(2026, 8, 13, 10, 0))
                .build()));

        mockMvc.perform(get("/api/v1/internal/notification-targets/43")
                        .header("X-Gateway-Auth", "service-secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(43))
                .andExpect(jsonPath("$.data.email").doesNotExist())
                .andExpect(jsonPath("$.data.emailVerified").value(false))
                .andExpect(jsonPath("$.data.telegramChatId").doesNotExist())
                .andExpect(jsonPath("$.data.telegramLinked").value(false));
    }

    @Test
    void missingOrWrongInternalSecretIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/internal/notification-targets/41"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/internal/notification-targets/41")
                        .header("X-Gateway-Auth", "wrong-secret"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownUserReturnsNotFoundWithoutContactData() throws Exception {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/internal/notification-targets/999")
                        .header("X-Gateway-Auth", "service-secret"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}
