package uz.topdim.coupon.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import uz.topdim.coupon.config.SecurityConfig;
import uz.topdim.coupon.security.RoleHeaderAuthenticationFilter;
import uz.topdim.coupon.service.QuestionService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ModQuestionController.class)
@Import({SecurityConfig.class, RoleHeaderAuthenticationFilter.class})
class ModQuestionControllerSecurityTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private QuestionService questionService;

    @Test
    @DisplayName("GET /api/v1/mod/questions: USER без прав модерации получает 403")
    void getQuestions_userRole_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/mod/questions")
                        .header("X-User-Id", "42")
                        .header("X-User-Role", "USER"))
                .andExpect(status().isForbidden());
    }
}
