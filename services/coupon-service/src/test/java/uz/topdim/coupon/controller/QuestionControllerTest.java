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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QuestionControllerTest {

    private final QuestionService questionService = mock(QuestionService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new QuestionController(questionService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(createValidator())
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/questions: валидный вопрос → 200 и id")
    void createQuestion_validRequest_returnsQuestionId() throws Exception {
        when(questionService.createQuestion(eq(42L), eq("Иван"), any())).thenReturn(77L);

        mockMvc.perform(post("/api/v1/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", 42)
                        .header("X-User-Name", "Иван")
                        .content("""
                                {
                                  "couponOfferId": 1,
                                  "question": "Можно ли использовать в выходные?"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(77));
    }

    @Test
    @DisplayName("POST /api/v1/questions: короткий вопрос → 400 и сервис не вызывается")
    void createQuestion_shortQuestion_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", 42)
                        .content("""
                                {
                                  "couponOfferId": 1,
                                  "question": "Ок?"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Ошибка валидации"));

        verifyNoInteractions(questionService);
    }

    @Test
    @DisplayName("GET /api/v1/questions/coupon/{offerId}: отдаёт опубликованные Q&A из service")
    void getCouponQuestions_returnsPublishedQuestions() throws Exception {
        QuestionResponse response = QuestionResponse.builder()
                .id(10L)
                .couponOfferId(1L)
                .userName("Анна")
                .question("Есть парковка?")
                .answer("Да, парковка есть.")
                .status(QuestionStatus.PUBLISHED)
                .build();
        when(questionService.getPublishedQuestionsForCoupon(eq(1L), eq(PageRequest.of(0, 10))))
                .thenReturn(new PageImpl<>(List.of(response), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/v1/questions/coupon/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.content[0].answer").value("Да, парковка есть."));
    }

    private Validator createValidator() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        return validator;
    }
}
