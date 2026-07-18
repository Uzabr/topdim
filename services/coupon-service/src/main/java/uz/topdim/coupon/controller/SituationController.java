package uz.topdim.coupon.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.coupon.dto.SituationResponse;
import uz.topdim.coupon.service.SituationService;

import java.util.List;

/**
 * Публичный контроллер ситуаций (подборок купонов).
 * GET /api/v1/situations — кэшируемый список для главной страницы.
 */
@RestController
@RequestMapping("/api/v1/situations")
@RequiredArgsConstructor
public class SituationController {

    private final SituationService situationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<SituationResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(situationService.getActiveSituations()));
    }
}
