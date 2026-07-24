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
import uz.topdim.identity.service.UserService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UserController.class)
@AutoConfigureMockMvc(addFilters = true)
@Import({SecurityConfig.class, RoleHeaderAuthenticationFilter.class, GlobalExceptionHandler.class})
class UserControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("updateProfile: буквенный телефон отклоняется до сервиса")
    void updateProfile_alphabeticPhone_returnsBadRequest() throws Exception {
        mockMvc.perform(updateProfile("+998abcdefghi"))
                .andExpect(status().isBadRequest());

        verify(userService, never()).updateProfile(any(), any());
    }

    @Test
    @DisplayName("updateProfile: короткий телефон отклоняется до сервиса")
    void updateProfile_shortPhone_returnsBadRequest() throws Exception {
        mockMvc.perform(updateProfile("+99890123"))
                .andExpect(status().isBadRequest());

        verify(userService, never()).updateProfile(any(), any());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder updateProfile(String phone) {
        return put("/api/v1/users/me")
                .header("X-User-Id", "1")
                .header("X-User-Role", "USER")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"" + phone + "\"}");
    }
}
