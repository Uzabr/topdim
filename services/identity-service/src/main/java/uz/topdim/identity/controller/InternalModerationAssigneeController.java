package uz.topdim.identity.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.identity.dto.ModerationAssigneeResponse;
import uz.topdim.identity.dto.ModerationAssigneeOptionResponse;
import uz.topdim.identity.service.ModerationAssigneeService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/internal/moderation-assignees")
@RequiredArgsConstructor
public class InternalModerationAssigneeController {

    private final ModerationAssigneeService moderationAssigneeService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ModerationAssigneeOptionResponse>>> listAssignees() {
        return ResponseEntity.ok(ApiResponse.success(moderationAssigneeService.listEligible()));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<ModerationAssigneeResponse>> getAssignee(
            @PathVariable Long userId
    ) {
        return ResponseEntity.ok(ApiResponse.success(moderationAssigneeService.resolve(userId)));
    }
}
