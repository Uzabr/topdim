package uz.topdim.coupon.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.coupon.dto.AnswerQuestionRequest;
import uz.topdim.coupon.dto.QuestionResponse;
import uz.topdim.coupon.dto.RejectQuestionRequest;
import uz.topdim.coupon.entity.QuestionStatus;
import uz.topdim.coupon.service.QuestionService;

@RestController
@RequestMapping("/api/v1/mod/questions")
@PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN', 'SUPER_ADMIN')")
@RequiredArgsConstructor
public class ModQuestionController {

    private final QuestionService questionService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<QuestionResponse>>> getQuestions(
            @RequestParam(defaultValue = "PENDING") QuestionStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                questionService.getQuestionsForModeration(status, PageRequest.of(page, size))
        ));
    }

    @PatchMapping("/{id}/answer")
    public ResponseEntity<ApiResponse<Void>> answerQuestion(
            @RequestHeader("X-User-Id") Long moderatorId,
            @PathVariable Long id,
            @Valid @RequestBody AnswerQuestionRequest request
    ) {
        questionService.answerQuestion(moderatorId, id, request.getAnswer());
        return ResponseEntity.ok(ApiResponse.success("Ответ опубликован", null));
    }

    @PatchMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<Void>> rejectQuestion(
            @PathVariable Long id,
            @Valid @RequestBody RejectQuestionRequest request
    ) {
        questionService.rejectQuestion(id, request.getRejectReason());
        return ResponseEntity.ok(ApiResponse.success("Вопрос отклонен", null));
    }
}
