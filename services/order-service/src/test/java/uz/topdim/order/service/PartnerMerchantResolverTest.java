package uz.topdim.order.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.order.client.CouponClient;
import uz.topdim.order.client.MerchantContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PartnerMerchantResolverTest {

    @Mock private CouponClient couponClient;
    @InjectMocks private PartnerMerchantResolver resolver;

    @Test
    @DisplayName("resolveMerchantId: active merchant returns merchantId")
    void resolveMerchantId_activeMerchant_returnsId() {
        MerchantContext context = MerchantContext.builder()
                .merchantId(77L)
                .userId(10L)
                .name("SPA Oasis")
                .active(true)
                .build();
        when(couponClient.getMerchantContextByUserId(10L)).thenReturn(ApiResponse.success(context));

        Long merchantId = resolver.resolveMerchantId(10L);

        assertThat(merchantId).isEqualTo(77L);
    }

    @Test
    @DisplayName("resolveMerchantId: inactive merchant is rejected")
    void resolveMerchantId_inactiveMerchant_throws() {
        MerchantContext context = MerchantContext.builder()
                .merchantId(77L)
                .userId(10L)
                .name("SPA Oasis")
                .active(false)
                .build();
        when(couponClient.getMerchantContextByUserId(10L)).thenReturn(ApiResponse.success(context));

        assertThatThrownBy(() -> resolver.resolveMerchantId(10L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Мерчант не активен");
    }

    @Test
    @DisplayName("resolveMerchantId: null response data throws")
    void resolveMerchantId_nullData_throws() {
        when(couponClient.getMerchantContextByUserId(999L)).thenReturn(ApiResponse.success(null));

        assertThatThrownBy(() -> resolver.resolveMerchantId(999L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("не найден");
    }
}
