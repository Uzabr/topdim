package uz.topdim.identity.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import uz.topdim.identity.config.SecurityConfig;
import uz.topdim.identity.exception.GlobalExceptionHandler;
import uz.topdim.identity.security.CustomUserDetailsService;
import uz.topdim.identity.security.RoleHeaderAuthenticationFilter;
import uz.topdim.identity.service.AuthService;
import uz.topdim.identity.service.EmailConfirmationService;
import uz.topdim.identity.service.PasswordResetService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = true)
@Import({SecurityConfig.class, RoleHeaderAuthenticationFilter.class, GlobalExceptionHandler.class})
class AuthControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @MockBean
    private PasswordResetService passwordResetService;

    @MockBean
    private EmailConfirmationService emailConfirmationService;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("change-password: без аутентификации endpoint недоступен")
    void changePassword_withoutAuthentication_isRejected() throws Exception {
        mockMvc.perform(put("/api/v1/auth/change-password")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "OldPass123!",
                                  "newPassword": "NewPass123!",
                                  "confirmPassword": "NewPass123!"
                                }
                                """))
                .andExpect(status().isForbidden());

        verify(authService, never()).changePassword(eq(1L), any());
    }

    @Test
    @DisplayName("change-password: с gateway headers запрос проходит")
    void changePassword_withGatewayHeaders_callsService() throws Exception {
        mockMvc.perform(put("/api/v1/auth/change-password")
                        .header("X-User-Id", "1")
                        .header("X-User-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "OldPass123!",
                                  "newPassword": "NewPass123!",
                                  "confirmPassword": "NewPass123!"
                                }
                                """))
                .andExpect(status().isOk());

        verify(authService).changePassword(eq(1L), any());
    }

    @Test
    @DisplayName("M4: logout без cookie — вызывает service с null refreshToken")
    void logout_withoutCookie_callsServiceWithNullRefreshToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(authService).logout(any(), eq(null));
    }

    @Test
    @DisplayName("M4: logout с refreshToken cookie — вызывает service с cookie значением")
    void logout_withCookie_passesRefreshTokenFromCookie() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(new jakarta.servlet.http.Cookie("refreshToken", "rt-cookie-value"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(authService).logout(any(), eq("rt-cookie-value"));
    }

    @Test
    @DisplayName("M4: refresh без cookie — возвращает 401")
    void refresh_withoutCookie_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());

        verify(authService, never()).refreshToken(any());
    }

    @Test
    @DisplayName("password-reset/request: публичный endpoint принимает корректный email без аутентификации")
    void passwordResetRequest_publicEndpoint_acceptsAnonymousRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user@topdim.uz"
                                }
                                """))
                .andExpect(status().isAccepted());

        verify(passwordResetService).requestReset("user@topdim.uz");
    }

    @Test
    @DisplayName("password-reset/request: невалидный email отклоняется до сервиса")
    void passwordResetRequest_invalidEmail_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "not-an-email"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verify(passwordResetService, never()).requestReset(any());
    }

    @Test
    @DisplayName("password-reset/confirm: публичный endpoint доступен без аутентификации")
    void passwordResetConfirm_publicEndpoint_acceptsAnonymousRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "reset-token",
                                  "newPassword": "NewPass123!",
                                  "confirmPassword": "NewPass123!"
                                }
                                """))
                .andExpect(status().isOk());

        verify(passwordResetService).confirmReset("reset-token", "NewPass123!");
    }

    @Test
    @DisplayName("confirm/email: публичный endpoint доступен без аутентификации")
    void confirmEmail_publicEndpoint_acceptsAnonymousRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/confirm/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "email-token"
                                }
                                """))
                .andExpect(status().isOk());

        verify(emailConfirmationService).confirmEmail("email-token");
    }

    @Test
    @DisplayName("confirm/request: без gateway headers endpoint недоступен")
    void requestEmailConfirmation_withoutAuthentication_isRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/confirm/request"))
                .andExpect(status().isForbidden());

        verify(emailConfirmationService, never()).requestEmailConfirmation(any());
    }

    @Test
    @DisplayName("confirm/request: с gateway headers запрос проходит")
    void requestEmailConfirmation_withGatewayHeaders_callsService() throws Exception {
        mockMvc.perform(post("/api/v1/auth/confirm/request")
                        .header("X-User-Id", "7")
                        .header("X-User-Role", "USER"))
                .andExpect(status().isAccepted());

        verify(emailConfirmationService).requestEmailConfirmation(7L);
    }
}
