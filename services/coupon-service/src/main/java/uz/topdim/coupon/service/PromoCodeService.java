package uz.topdim.coupon.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.topdim.coupon.dto.CreatePromoCodeRequest;
import uz.topdim.coupon.dto.PromoCodeResponse;
import uz.topdim.coupon.entity.PromoCode;
import uz.topdim.coupon.repository.PromoCodeRepository;

@Service
@RequiredArgsConstructor
public class PromoCodeService {

    private final PromoCodeRepository promoCodeRepository;

    @Transactional
    public Long createPromoCode(CreatePromoCodeRequest request) {
        if (promoCodeRepository.findByCode(request.getCode()).isPresent()) {
            throw new RuntimeException("Промокод с таким кодом уже существует");
        }
        
        PromoCode promoCode = PromoCode.builder()
                .code(request.getCode())
                .discountAmount(request.getDiscountAmount())
                .percentage(request.isPercentage())
                .usageLimit(request.getUsageLimit())
                .usedCount(0)
                .expiresAt(request.getExpiresAt())
                .active(true)
                .build();
        
        return promoCodeRepository.save(promoCode).getId();
    }

    @Transactional(readOnly = true)
    public Page<PromoCodeResponse> getAllPromoCodes(Pageable pageable) {
        return promoCodeRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(this::mapToResponse);
    }

    private PromoCodeResponse mapToResponse(PromoCode code) {
        return PromoCodeResponse.builder()
                .id(code.getId())
                .code(code.getCode())
                .discountAmount(code.getDiscountAmount())
                .percentage(code.isPercentage())
                .usageLimit(code.getUsageLimit())
                .usedCount(code.getUsedCount())
                .expiresAt(code.getExpiresAt())
                .active(code.isActive())
                .createdAt(code.getCreatedAt())
                .build();
    }
}
