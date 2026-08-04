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
import uz.topdim.identity.dto.AuthResponse;
import uz.topdim.identity.service.AuthService;
import uz.topdim.identity.service.EmailChangeService;
import uz.topdim.identity.service.EmailConfirmationService;
import uz.topdim.identity.service.OtpService;
import uz.topdim.identity.service.PasswordResetService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
    private EmailChangeService emailChangeService;

    @MockBean
    private OtpService otpService;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("M4: POST /login → Set-Cookie refreshToken HttpOnly+Secure+SameSite=Lax+Path=/api/v1/auth, refreshToken НЕ в JSON body")
    void login_setsHttpOnlyCookieAndOmitsRefreshTokenFromBody() throws Exception {
        AuthResponse mockResponse = AuthResponse.builder()
                .accessToken("access-jwt")
                .refreshToken("rt-secret-value")
                .tokenType("Bearer")
                .expiresIn(900)
                .user(AuthResponse.UserDto.builder()
                        .id(1L).email("user@topdim.uz").firstName("Ali").role("USER").build())
                .build();
        when(authService.login(any())).thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user@topdim.uz",
                                  "password": "SafePass123!"
                                }
                                """))
                .andExpect(status().isOk())
                // Cookie attributes
                .andExpect(header().exists("Set-Cookie"))
                .andExpect(header().string("Set-Cookie",
                        org.hamcrest.Matchers.containsString("refreshToken=rt-secret-value")))
                .andExpect(header().string("Set-Cookie",
                        org.hamcrest.Matchers.containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie",
                        org.hamcrest.Matchers.containsString("Secure")))
                .andExpect(header().string("Set-Cookie",
                        org.hamcrest.Matchers.containsString("SameSite=Lax")))
                .andExpect(header().string("Set-Cookie",
                        org.hamcrest.Matchers.containsString("Path=/api/v1/auth")))
                // JSON body: accessToken present, refreshToken absent
                .andExpect(jsonPath("$.data.accessToken").value("access-jwt"))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist());
    }

    @Test
    @DisplayName("T5: POST /guest → 410 Gone, гостевой вход отключён (не выполняет никакого входа)")
    void guest_isGone() throws Exception {
        mockMvc.perform(post("/api/v1/auth/guest")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(
                        "Гостевой вход отключён, используйте вход по номеру телефона"));

        verify(authService, never()).phoneAuth(any(), any());
    }

    @Test
    @DisplayName("T5: POST /auth/phone/request — публичный endpoint, всегда 202 (anti-enumeration)")
    void phoneOtpRequest_publicEndpoint_acceptsAnonymousRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/phone/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "phone": "+998901234567"
                                }
                                """))
                .andExpect(status().isAccepted());

        verify(otpService).requestOtp("+998901234567");
    }

    @Test
    @DisplayName("T5: POST /auth/phone/request — некорректный формат телефона отклоняется до сервиса")
    void phoneOtpRequest_invalidPhone_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/phone/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "phone": "not-a-phone"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verify(otpService, never()).requestOtp(any());
    }

    @Test
    @DisplayName("T5: POST /auth/phone/confirm — публичный endpoint, ставит refreshToken в httpOnly cookie, не отдаёт в body")
    void phoneOtpConfirm_setsHttpOnlyCookieAndOmitsRefreshTokenFromBody() throws Exception {
        AuthResponse mockResponse = AuthResponse.builder()
                .accessToken("access-jwt")
                .refreshToken("rt-secret-value")
                .tokenType("Bearer")
                .expiresIn(900)
                .user(AuthResponse.UserDto.builder()
                        .id(1L).phone("+998901234567").firstName("Ali").role("USER").build())
                .build();
        when(authService.phoneAuth("+998901234567", "111111")).thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/auth/phone/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "phone": "+998901234567",
                                  "code": "111111"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie",
                        org.hamcrest.Matchers.containsString("refreshToken=rt-secret-value")))
                .andExpect(header().string("Set-Cookie",
                        org.hamcrest.Matchers.containsString("HttpOnly")))
                .andExpect(jsonPath("$.data.accessToken").value("access-jwt"))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist());
    }

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
    @DisplayName("T6: POST /auth/google — публичный endpoint, ставит refreshToken в httpOnly cookie, не отдаёт в body")
    void googleAuth_setsHttpOnlyCookieAndOmitsRefreshTokenFromBody() throws Exception {
        AuthResponse mockResponse = AuthResponse.builder()
                .accessToken("access-jwt")
                .refreshToken("rt-secret-value")
                .tokenType("Bearer")
                .expiresIn(900)
                .user(AuthResponse.UserDto.builder()
                        .id(1L).email("g@x.uz").firstName("Ali").role("USER").build())
                .build();
        when(authService.googleAuth("valid-id-token")).thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "idToken": "valid-id-token"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie",
                        org.hamcrest.Matchers.containsString("refreshToken=rt-secret-value")))
                .andExpect(header().string("Set-Cookie",
                        org.hamcrest.Matchers.containsString("HttpOnly")))
                .andExpect(jsonPath("$.data.accessToken").value("access-jwt"))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist());
    }

    @Test
    @DisplayName("T6: POST /auth/google — пустой idToken отклоняется до сервиса")
    void googleAuth_blankIdToken_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "idToken": ""
                                }
                                """))
                .andExpect(status().isBadRequest());

        verify(authService, never()).googleAuth(any());
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

    // ==================== T7a: Email Change ====================

    @Test
    @DisplayName("email-change/request: без gateway headers endpoint недоступен")
    void requestEmailChange_withoutAuthentication_isRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/email-change/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "newEmail": "new@topdim.uz"
                                }
                                """))
                .andExpect(status().isForbidden());

        verify(emailChangeService, never()).requestEmailChange(any(), any());
    }

    @Test
    @DisplayName("email-change/request: с gateway headers запрос проходит")
    void requestEmailChange_withGatewayHeaders_callsService() throws Exception {
        mockMvc.perform(post("/api/v1/auth/email-change/request")
                        .header("X-User-Id", "7")
                        .header("X-User-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "newEmail": "new@topdim.uz"
                                }
                                """))
                .andExpect(status().isAccepted());

        verify(emailChangeService).requestEmailChange(7L, "new@topdim.uz");
    }

    @Test
    @DisplayName("email-change/request: невалидный email отклоняется до сервиса")
    void requestEmailChange_invalidEmail_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/email-change/request")
                        .header("X-User-Id", "7")
                        .header("X-User-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "newEmail": "not-an-email"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verify(emailChangeService, never()).requestEmailChange(any(), any());
    }

    @Test
    @DisplayName("email-change/confirm: публичный endpoint доступен без аутентификации")
    void confirmEmailChange_publicEndpoint_acceptsAnonymousRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/email-change/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "email-change-token"
                                }
                                """))
                .andExpect(status().isOk());

        verify(emailChangeService).confirmEmailChange("email-change-token");
    }
}
