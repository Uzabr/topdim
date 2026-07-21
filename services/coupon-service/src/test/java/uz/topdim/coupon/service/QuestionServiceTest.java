package uz.topdim.coupon.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import uz.topdim.coupon.dto.CreateQuestionRequest;
import uz.topdim.coupon.dto.QuestionResponse;
import uz.topdim.coupon.entity.CouponOffer;
import uz.topdim.coupon.entity.CouponQuestion;
import uz.topdim.coupon.entity.CouponStatus;
import uz.topdim.coupon.entity.QuestionStatus;
import uz.topdim.coupon.exception.ResourceNotFoundException;
import uz.topdim.coupon.mapper.QuestionMapper;
import uz.topdim.coupon.repository.CouponOfferRepository;
import uz.topdim.coupon.repository.CouponQuestionRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionServiceTest {

    @Mock private CouponQuestionRepository questionRepository;
    @Mock private CouponOfferRepository couponOfferRepository;
    @Mock private QuestionMapper questionMapper;

    @InjectMocks
    private QuestionService questionService;

    @Test
    @DisplayName("createQuestion: существующий оффер → новый вопрос PENDING")
    void createQuestion_existingOffer_createsPendingQuestion() {
        CouponOffer coupon = coupon();
        CreateQuestionRequest request = request(1L, "  Можно ли использовать вечером?  ");

        when(couponOfferRepository.findById(1L)).thenReturn(Optional.of(coupon));
        when(questionRepository.save(any(CouponQuestion.class))).thenAnswer(invocation -> {
            CouponQuestion q = invocation.getArgument(0);
            q.setId(77L);
            return q;
        });

        Long result = questionService.createQuestion(42L, "Иван", request);

        assertThat(result).isEqualTo(77L);
        ArgumentCaptor<CouponQuestion> captor = ArgumentCaptor.forClass(CouponQuestion.class);
        verify(questionRepository).save(captor.capture());
        CouponQuestion saved = captor.getValue();
        assertThat(saved.getCouponOffer()).isEqualTo(coupon);
        assertThat(saved.getUserId()).isEqualTo(42L);
        assertThat(saved.getUserName()).isEqualTo("Иван");
        assertThat(saved.getQuestion()).isEqualTo("Можно ли использовать вечером?");
        assertThat(saved.getStatus()).isEqualTo(QuestionStatus.PENDING);
    }

    @Test
    @DisplayName("createQuestion: оффер не найден → 404 бизнес-ошибка")
    void createQuestion_offerNotFound_throwsNotFound() {
        when(couponOfferRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> questionService.createQuestion(42L, "Иван", request(999L, "Есть ли парковка?")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Купон не найден");
    }

    @Test
    @DisplayName("answerQuestion: PENDING → PUBLISHED, проставляет модератора и answer")
    void answerQuestion_pending_publishesWithModeratorFields() {
        CouponQuestion question = CouponQuestion.builder()
                .id(10L)
                .couponOffer(coupon())
                .userId(42L)
                .question("Есть ли парковка?")
                .status(QuestionStatus.PENDING)
                .build();
        when(questionRepository.findById(10L)).thenReturn(Optional.of(question));

        questionService.answerQuestion(7L, 10L, "  Да, парковка есть.  ");

        assertThat(question.getStatus()).isEqualTo(QuestionStatus.PUBLISHED);
        assertThat(question.getAnswer()).isEqualTo("Да, парковка есть.");
        assertThat(question.getAnsweredByUserId()).isEqualTo(7L);
        assertThat(question.getAnsweredAt()).isNotNull();
        verify(questionRepository).save(question);
    }

    @Test
    @DisplayName("answerQuestion: уже обработанный вопрос → conflict")
    void answerQuestion_processedQuestion_throwsConflict() {
        CouponQuestion question = CouponQuestion.builder()
                .id(10L)
                .status(QuestionStatus.PUBLISHED)
                .build();
        when(questionRepository.findById(10L)).thenReturn(Optional.of(question));

        assertThatThrownBy(() -> questionService.answerQuestion(7L, 10L, "Повторный ответ"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("уже обработан");
    }

    @Test
    @DisplayName("rejectQuestion: PENDING → REJECTED, сохраняет причину")
    void rejectQuestion_pending_rejectsWithReason() {
        CouponQuestion question = CouponQuestion.builder()
                .id(10L)
                .status(QuestionStatus.PENDING)
                .build();
        when(questionRepository.findById(10L)).thenReturn(Optional.of(question));

        questionService.rejectQuestion(10L, "  Дублирующий вопрос  ");

        assertThat(question.getStatus()).isEqualTo(QuestionStatus.REJECTED);
        assertThat(question.getRejectReason()).isEqualTo("Дублирующий вопрос");
        verify(questionRepository).save(question);
    }

    @Test
    @DisplayName("getPublishedQuestionsForCoupon: использует visible-query и маппит DTO")
    void getPublishedQuestionsForCoupon_returnsVisibleMappedPage() {
        CouponQuestion question = CouponQuestion.builder()
                .id(10L)
                .couponOffer(coupon())
                .question("Есть парковка?")
                .answer("Да.")
                .status(QuestionStatus.PUBLISHED)
                .build();
        QuestionResponse response = QuestionResponse.builder()
                .id(10L)
                .couponOfferId(1L)
                .question("Есть парковка?")
                .answer("Да.")
                .status(QuestionStatus.PUBLISHED)
                .build();

        when(questionRepository.findVisibleByCouponOfferIdAndStatus(
                eq(1L), eq(QuestionStatus.PUBLISHED), any())).thenReturn(new PageImpl<>(List.of(question)));
        when(questionMapper.toResponse(question)).thenReturn(response);

        var result = questionService.getPublishedQuestionsForCoupon(1L, PageRequest.of(0, 10));

        assertThat(result.getContent()).containsExactly(response);
    }

    private CouponOffer coupon() {
        return CouponOffer.builder()
                .id(1L)
                .title("Тестовый купон")
                .status(CouponStatus.ACTIVE)
                .build();
    }

    private CreateQuestionRequest request(Long couponOfferId, String question) {
        CreateQuestionRequest request = new CreateQuestionRequest();
        request.setCouponOfferId(couponOfferId);
        request.setQuestion(question);
        return request;
    }
}
