package uz.topdim.coupon.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.coupon.client.IdentityPartnerAccessClient;
import uz.topdim.coupon.client.PartnerAccessContext;
import uz.topdim.coupon.exception.PartnerAccessUnavailableException;

import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class PartnerAccessResolver {

    private static final Set<String> COMPANY_PROFILE_ROLES = Set.of("OWNER", "MANAGER");
    private static final String UNAVAILABLE_MESSAGE = "Контекст партнёра временно недоступен";

    private final IdentityPartnerAccessClient identityClient;

    public ResolvedPartnerAccess resolveOwnerOrManager(Long userId) {
        PartnerAccessContext context = loadContext(userId);
        String role = normalizeRole(context.getRole());
        if (!COMPANY_PROFILE_ROLES.contains(role)) {
            throw new AccessDeniedException("У роли нет доступа к профилю компании");
        }
        return new ResolvedPartnerAccess(context.getMerchantId(), role, context.getStaffId());
    }

    private PartnerAccessContext loadContext(Long userId) {
        try {
            ApiResponse<PartnerAccessContext> response = identityClient.getPartnerAccessContext(userId);
            PartnerAccessContext context = response != null && response.isSuccess()
                    ? response.getData()
                    : null;
            if (context == null || context.getMerchantId() == null || context.getMerchantId() <= 0) {
                throw new PartnerAccessUnavailableException(UNAVAILABLE_MESSAGE);
            }
            return context;
        } catch (PartnerAccessUnavailableException exception) {
            throw exception;
        } catch (Exception exception) {
            log.warn("Identity partner access unavailable for userId={}: {}",
                    userId, exception.getClass().getSimpleName());
            throw new PartnerAccessUnavailableException(UNAVAILABLE_MESSAGE);
        }
    }

    private String normalizeRole(String rawRole) {
        if (rawRole == null || rawRole.isBlank()) {
            throw new PartnerAccessUnavailableException(UNAVAILABLE_MESSAGE);
        }
        return rawRole.trim().toUpperCase(Locale.ROOT);
    }
}
