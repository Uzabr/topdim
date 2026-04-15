package uz.topdim.coupon.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import uz.topdim.coupon.dto.CreatePromoCodeRequest;
import uz.topdim.coupon.dto.PromoCodeResponse;
import uz.topdim.coupon.entity.PromoCode;
import uz.topdim.coupon.repository.PromoCodeRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromoCodeServiceTest {

    @Mock private PromoCodeRepository promoCodeRepository;

    @InjectMocks
    private PromoCodeService promoCodeService;

    private CreatePromoCodeRequest createRequest() {
        CreatePromoCodeRequest request = new CreatePromoCodeRequest();
        request.setCode("SPRING10");
        request.setDiscountAmount(BigDecimal.TEN);
        request.setPercentage(true);
        request.setUsageLimit(100);
        request.setExpiresAt(LocalDateTime.now().plusDays(30));
        return request;
    }

    @Test
    @DisplayName("createPromoCode: сохраняет новый активный промокод с usedCount=0")
    void createPromoCode_success_savesActivePromoCode() {
        CreatePromoCodeRequest request = createRequest();

        when(promoCodeRepository.findByCode("SPRING10")).thenReturn(Optional.empty());
        when(promoCodeRepository.save(any(PromoCode.class))).thenAnswer(invocation -> {
            PromoCode promoCode = invocation.getArgument(0);
            promoCode.setId(17L);
            return promoCode;
        });

        Long id = promoCodeService.createPromoCode(request);

        ArgumentCaptor<PromoCode> promoCodeCaptor = ArgumentCaptor.forClass(PromoCode.class);
        verify(promoCodeRepository).save(promoCodeCaptor.capture());

        PromoCode saved = promoCodeCaptor.getValue();
        assertThat(id).isEqualTo(17L);
        assertThat(saved.getCode()).isEqualTo("SPRING10");
        assertThat(saved.getUsedCount()).isZero();
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.isPercentage()).isTrue();
    }

    @Test
    @DisplayName("createPromoCode: дубликат кода отклоняется до сохранения")
    void createPromoCode_duplicateCode_throws() {
        CreatePromoCodeRequest request = createRequest();

        when(promoCodeRepository.findByCode("SPRING10"))
                .thenReturn(Optional.of(PromoCode.builder().id(1L).code("SPRING10").build()));

        assertThatThrownBy(() -> promoCodeService.createPromoCode(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("уже существует");

        verify(promoCodeRepository, never()).save(any(PromoCode.class));
    }

    @Test
    @DisplayName("getAllPromoCodes: возвращает страницу с корректным маппингом")
    void getAllPromoCodes_mapsPage() {
        PromoCode promoCode = PromoCode.builder()
                .id(5L)
                .code("WELCOME")
                .discountAmount(BigDecimal.valueOf(5000))
                .percentage(false)
                .usageLimit(20)
                .usedCount(3)
                .expiresAt(LocalDateTime.now().plusDays(5))
                .active(true)
                .build();

        when(promoCodeRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(java.util.List.of(promoCode)));

        Page<PromoCodeResponse> result = promoCodeService.getAllPromoCodes(PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getCode()).isEqualTo("WELCOME");
        assertThat(result.getContent().get(0).getUsedCount()).isEqualTo(3);
        assertThat(result.getContent().get(0).isPercentage()).isFalse();
    }
}
