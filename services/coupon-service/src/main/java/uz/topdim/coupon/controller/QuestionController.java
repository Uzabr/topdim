package uz.topdim.coupon.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.coupon.dto.CreateQuestionRequest;
import uz.topdim.coupon.dto.QuestionResponse;
import uz.topdim.coupon.service.QuestionService;

@RestController
@RequestMapping("/api/v1/questions")
@RequiredArgsConstructor
public class QuestionController {

    private final QuestionService questionService;

    @PostMapping
    public ResponseEntity<ApiResponse<Long>> createQuestion(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-User-Name", required = false, defaultValue = "Пользователь") String userName,
            @Valid @RequestBody CreateQuestionRequest request
    ) {
        Long questionId = questionService.createQuestion(userId, userName, request);
        return ResponseEntity.ok(ApiResponse.success("Вопрос отправлен на модерацию", questionId));
    }

    @GetMapping("/coupon/{offerId}")
    public ResponseEntity<ApiResponse<Page<QuestionResponse>>> getCouponQuestions(
            @PathVariable Long offerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                questionService.getPublishedQuestionsForCoupon(offerId, PageRequest.of(page, size))
        ));
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<Page<QuestionResponse>>> getMyQuestions(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                questionService.getMyQuestions(userId, PageRequest.of(page, size))
        ));
    }
}
