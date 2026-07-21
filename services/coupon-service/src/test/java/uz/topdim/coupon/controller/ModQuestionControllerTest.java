package uz.topdim.coupon.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.Validator;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import uz.topdim.coupon.dto.QuestionResponse;
import uz.topdim.coupon.entity.QuestionStatus;
import uz.topdim.coupon.exception.GlobalExceptionHandler;
import uz.topdim.coupon.service.QuestionService;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ModQuestionControllerTest {

    private final QuestionService questionService = mock(QuestionService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ModQuestionController(questionService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(createValidator())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/mod/questions: по умолчанию возвращает PENDING queue")
    void getQuestions_defaultStatus_returnsPendingQueue() throws Exception {
        QuestionResponse response = QuestionResponse.builder()
                .id(10L)
                .couponOfferId(1L)
                .question("Есть парковка?")
                .status(QuestionStatus.PENDING)
                .build();
        when(questionService.getQuestionsForModeration(eq(QuestionStatus.PENDING), eq(PageRequest.of(0, 10))))
                .thenReturn(new PageImpl<>(List.of(response), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/v1/mod/questions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].status").value("PENDING"));
    }

    @Test
    @DisplayName("PATCH answer: валидный ответ публикуется")
    void answerQuestion_validRequest_returnsOk() throws Exception {
        mockMvc.perform(patch("/api/v1/mod/questions/10/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", 7)
                        .content("""
                                {
                                  "answer": "Да, можно использовать каждый день."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(questionService).answerQuestion(7L, 10L, "Да, можно использовать каждый день.");
    }

    @Test
    @DisplayName("PATCH answer: пустой ответ → 400 и сервис не вызывается")
    void answerQuestion_blankAnswer_returnsBadRequest() throws Exception {
        mockMvc.perform(patch("/api/v1/mod/questions/10/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", 7)
                        .content("""
                                {
                                  "answer": "   "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        verifyNoInteractions(questionService);
    }

    @Test
    @DisplayName("PATCH reject: причина опциональна, отклонение сохраняется")
    void rejectQuestion_emptyBody_returnsOk() throws Exception {
        mockMvc.perform(patch("/api/v1/mod/questions/10/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(questionService).rejectQuestion(10L, null);
    }

    private Validator createValidator() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        return validator;
    }
}
