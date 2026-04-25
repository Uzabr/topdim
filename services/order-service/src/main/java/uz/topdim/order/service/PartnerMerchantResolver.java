package uz.topdim.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.order.client.CouponClient;
import uz.topdim.order.client.MerchantContext;

@Service
@RequiredArgsConstructor
public class PartnerMerchantResolver {
    private final CouponClient couponClient;

    public Long resolveMerchantId(Long userId) {
        ApiResponse<MerchantContext> response = couponClient.getMerchantContextByUserId(userId);
        MerchantContext context = response != null ? response.getData() : null;

        if (context == null || context.getMerchantId() == null) {
            throw new IllegalStateException("Мерчант для пользователя не найден");
        }
        if (!context.isActive()) {
            throw new IllegalStateException("Мерчант не активен");
        }

        return context.getMerchantId();
    }
}
