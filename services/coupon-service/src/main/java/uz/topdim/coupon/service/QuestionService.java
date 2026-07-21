package uz.topdim.coupon.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.topdim.coupon.dto.CreateQuestionRequest;
import uz.topdim.coupon.dto.QuestionResponse;
import uz.topdim.coupon.entity.CouponOffer;
import uz.topdim.coupon.entity.CouponQuestion;
import uz.topdim.coupon.entity.QuestionStatus;
import uz.topdim.coupon.exception.ResourceNotFoundException;
import uz.topdim.coupon.mapper.QuestionMapper;
import uz.topdim.coupon.repository.CouponOfferRepository;
import uz.topdim.coupon.repository.CouponQuestionRepository;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class QuestionService {

    private final CouponQuestionRepository questionRepository;
    private final CouponOfferRepository couponOfferRepository;
    private final QuestionMapper questionMapper;

    @Transactional
    public Long createQuestion(Long userId, String userName, CreateQuestionRequest request) {
        CouponOffer couponOffer = couponOfferRepository.findById(request.getCouponOfferId())
                .orElseThrow(() -> new ResourceNotFoundException("Купон не найден"));

        CouponQuestion question = CouponQuestion.builder()
                .couponOffer(couponOffer)
                .userId(userId)
                .userName(userName)
                .question(request.getQuestion().trim())
                .status(QuestionStatus.PENDING)
                .build();

        return questionRepository.save(question).getId();
    }

    @Transactional(readOnly = true)
    public Page<QuestionResponse> getPublishedQuestionsForCoupon(Long couponOfferId, Pageable pageable) {
        return questionRepository.findVisibleByCouponOfferIdAndStatus(
                        couponOfferId, QuestionStatus.PUBLISHED, pageable)
                .map(questionMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<QuestionResponse> getMyQuestions(Long userId, Pageable pageable) {
        return questionRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(questionMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<QuestionResponse> getQuestionsForModeration(QuestionStatus status, Pageable pageable) {
        return questionRepository.findByStatusOrderByCreatedAtDesc(status, pageable)
                .map(questionMapper::toResponse);
    }

    @Transactional
    public void answerQuestion(Long moderatorId, Long questionId, String answer) {
        CouponQuestion question = findQuestion(questionId);
        ensurePending(question);

        question.setAnswer(answer.trim());
        question.setAnsweredByUserId(moderatorId);
        question.setAnsweredAt(LocalDateTime.now());
        question.setStatus(QuestionStatus.PUBLISHED);
        question.setRejectReason(null);
        questionRepository.save(question);
    }

    @Transactional
    public void rejectQuestion(Long questionId, String rejectReason) {
        CouponQuestion question = findQuestion(questionId);
        ensurePending(question);

        question.setStatus(QuestionStatus.REJECTED);
        if (rejectReason == null || rejectReason.trim().isEmpty()) {
            question.setRejectReason(null);
        } else {
            question.setRejectReason(rejectReason.trim());
        }
        questionRepository.save(question);
    }

    private CouponQuestion findQuestion(Long questionId) {
        return questionRepository.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("Вопрос не найден"));
    }

    private void ensurePending(CouponQuestion question) {
        if (question.getStatus() != QuestionStatus.PENDING) {
            throw new IllegalStateException("Вопрос уже обработан");
        }
    }
}
