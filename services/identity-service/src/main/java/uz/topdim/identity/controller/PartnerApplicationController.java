package uz.topdim.identity.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.topdim.identity.dto.PartnerApplicationRequest;
import uz.topdim.identity.dto.PartnerApplicationResponse;
import uz.topdim.identity.service.PartnerApplicationService;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/partners/applications")
@RequiredArgsConstructor
public class PartnerApplicationController {

    private final PartnerApplicationService service;

    @PostMapping
    public ResponseEntity<Map<String, Object>> submit(@Valid @RequestBody PartnerApplicationRequest request) {
        PartnerApplicationResponse response = service.submit(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "success", true,
                "message", "Заявка успешно отправлена. Мы свяжемся с вами в ближайшее время.",
                "data", response));
    }
}
