package uz.topdim.coupon.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import uz.topdim.coupon.dto.CreateReviewRequest;
import uz.topdim.coupon.service.ReviewService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ReviewControllerValidationTest {

    @Mock
    private ReviewService reviewService;

    @InjectMocks
    private ReviewController reviewController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(reviewController).build();
    }

    @Test
    @DisplayName("POST /reviews принимает рейтинг без необязательного комментария")
    void createReview_ratingOnly_passesNullCommentToService() throws Exception {
        when(reviewService.createReview(eq(42L), eq("Иван"), any(CreateReviewRequest.class)))
                .thenReturn(91L);

        mockMvc.perform(post("/api/v1/reviews")
                        .header("X-User-Id", 42L)
                        .header("X-User-Name", "Иван")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "couponOfferId": 7,
                                  "rating": 5
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(91));

        ArgumentCaptor<CreateReviewRequest> request =
                ArgumentCaptor.forClass(CreateReviewRequest.class);
        verify(reviewService).createReview(eq(42L), eq("Иван"), request.capture());
        assertThat(request.getValue().getComment()).isNull();
    }
}
