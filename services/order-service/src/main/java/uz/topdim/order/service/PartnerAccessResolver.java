package uz.topdim.order.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.order.client.IdentityPartnerAccessClient;
import uz.topdim.order.client.PartnerAccessContext;

/**
 * Резолвер контекста доступа партнёра.
 * Обращается к identity-service для определения роли (OWNER/MANAGER/CASHIER),
 * merchantId и merchantLocationId.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PartnerAccessResolver {

    private final IdentityPartnerAccessClient identityClient;

    /**
     * Резолвит полный контекст доступа партнёра.
     * @throws IllegalStateException если контекст не получен
     */
    public PartnerAccessContext resolve(Long userId) {
        try {
            ApiResponse<PartnerAccessContext> response = identityClient.getPartnerAccessContext(userId);
            PartnerAccessContext ctx = response != null ? response.getData() : null;

            if (ctx == null || ctx.getMerchantId() == null) {
                throw new IllegalStateException("Контекст партнёра для пользователя не найден");
            }

            return ctx;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("Ошибка получения контекста партнёра для userId={}: {}", userId, e.getMessage());
            throw new IllegalStateException("Не удалось определить контекст партнёра: " + e.getMessage());
        }
    }

    /**
     * Резолвит контекст и проверяет право на просмотр дашборда.
     */
    public PartnerAccessContext resolveForDashboard(Long userId) {
        PartnerAccessContext ctx = resolve(userId);
        if (!ctx.isCanViewDashboard()) {
            throw new IllegalStateException("Доступ к дашборду запрещён для роли " + ctx.getRole());
        }
        return ctx;
    }

    /**
     * Резолвит контекст и проверяет право на погашение.
     */
    public PartnerAccessContext resolveForRedemption(Long userId) {
        PartnerAccessContext ctx = resolve(userId);
        if (!ctx.isCanRedeem()) {
            throw new IllegalStateException("Погашение запрещено для роли " + ctx.getRole());
        }
        return ctx;
    }
}
