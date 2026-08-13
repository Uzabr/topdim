package uz.topdim.coupon.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.coupon.client.IdentityPartnerAccessClient;
import uz.topdim.coupon.client.PartnerAccessContext;
import uz.topdim.coupon.exception.PartnerAccessUnavailableException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PartnerAccessResolverTest {

    @Mock private IdentityPartnerAccessClient identityClient;
    @InjectMocks private PartnerAccessResolver resolver;

    @Test
    void managerResolvesCompanyWideMerchantAccess() {
        when(identityClient.getPartnerAccessContext(44L))
                .thenReturn(ApiResponse.success(context("MANAGER", 7L, 12L)));

        ResolvedPartnerAccess result = resolver.resolveOwnerOrManager(44L);

        assertThat(result.merchantId()).isEqualTo(7L);
        assertThat(result.role()).isEqualTo("MANAGER");
        assertThat(result.staffId()).isEqualTo(12L);
    }

    @Test
    void ownerResolvesMerchantWithoutStaffId() {
        when(identityClient.getPartnerAccessContext(43L))
                .thenReturn(ApiResponse.success(context("OWNER", 7L, null)));

        ResolvedPartnerAccess result = resolver.resolveOwnerOrManager(43L);

        assertThat(result.merchantId()).isEqualTo(7L);
        assertThat(result.role()).isEqualTo("OWNER");
        assertThat(result.staffId()).isNull();
    }

    @Test
    void cashierAndUnknownRoleAreForbidden() {
        when(identityClient.getPartnerAccessContext(45L))
                .thenReturn(ApiResponse.success(context("CASHIER", 7L, 13L)));
        when(identityClient.getPartnerAccessContext(46L))
                .thenReturn(ApiResponse.success(context("SUPPORT", 7L, 14L)));

        assertThatThrownBy(() -> resolver.resolveOwnerOrManager(45L))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> resolver.resolveOwnerOrManager(46L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void dependencyFailureAndMalformedResponseAreUnavailable() {
        when(identityClient.getPartnerAccessContext(47L))
                .thenThrow(new RuntimeException("identity is down"));
        when(identityClient.getPartnerAccessContext(48L))
                .thenReturn(ApiResponse.success(null));
        when(identityClient.getPartnerAccessContext(49L))
                .thenReturn(ApiResponse.success(context("MANAGER", null, 15L)));
        when(identityClient.getPartnerAccessContext(50L))
                .thenReturn(ApiResponse.<PartnerAccessContext>builder()
                        .success(false)
                        .message("identity rejected request")
                        .data(context("MANAGER", 7L, 16L))
                        .build());
        when(identityClient.getPartnerAccessContext(51L))
                .thenReturn(ApiResponse.success(context(null, 7L, 17L)));

        assertThatThrownBy(() -> resolver.resolveOwnerOrManager(47L))
                .isInstanceOf(PartnerAccessUnavailableException.class);
        assertThatThrownBy(() -> resolver.resolveOwnerOrManager(48L))
                .isInstanceOf(PartnerAccessUnavailableException.class);
        assertThatThrownBy(() -> resolver.resolveOwnerOrManager(49L))
                .isInstanceOf(PartnerAccessUnavailableException.class);
        assertThatThrownBy(() -> resolver.resolveOwnerOrManager(50L))
                .isInstanceOf(PartnerAccessUnavailableException.class);
        assertThatThrownBy(() -> resolver.resolveOwnerOrManager(51L))
                .isInstanceOf(PartnerAccessUnavailableException.class);
    }

    private PartnerAccessContext context(String role, Long merchantId, Long staffId) {
        return new PartnerAccessContext(
                role,
                merchantId,
                null,
                staffId,
                "Partner staff",
                true,
                true
        );
    }
}
