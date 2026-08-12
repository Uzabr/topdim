package uz.topdim.coupon.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import uz.topdim.coupon.config.SecurityConfig;
import uz.topdim.coupon.dto.MerchantLocationResponse;
import uz.topdim.coupon.dto.MerchantResponse;
import uz.topdim.coupon.exception.GlobalExceptionHandler;
import uz.topdim.coupon.exception.PartnerAccessUnavailableException;
import uz.topdim.coupon.security.RoleHeaderAuthenticationFilter;
import uz.topdim.coupon.service.MerchantService;
import uz.topdim.coupon.service.PartnerAccessResolver;
import uz.topdim.coupon.service.ResolvedPartnerAccess;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PartnerMerchantController.class)
@Import({SecurityConfig.class, RoleHeaderAuthenticationFilter.class, GlobalExceptionHandler.class})
class PartnerMerchantControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private MerchantService merchantService;
    @MockBean private PartnerAccessResolver partnerAccessResolver;

    @Test
    void managerReadsPublishedMerchantThroughResolvedMerchantId() throws Exception {
        when(partnerAccessResolver.resolveOwnerOrManager(44L))
                .thenReturn(new ResolvedPartnerAccess(7L, "MANAGER", 12L));
        when(merchantService.getPartnerMerchant(7L)).thenReturn(MerchantResponse.builder()
                .id(7L)
                .name("Published merchant")
                .profileVersion(3L)
                .active(true)
                .build());

        mockMvc.perform(partner(get("/api/v1/partner/merchant"), 44L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(7))
                .andExpect(jsonPath("$.data.profileVersion").value(3));

        verify(merchantService).getPartnerMerchant(7L);
    }

    @Test
    void managerReadsLocationsThroughResolvedMerchantId() throws Exception {
        when(partnerAccessResolver.resolveOwnerOrManager(44L))
                .thenReturn(new ResolvedPartnerAccess(7L, "MANAGER", 12L));
        when(merchantService.getPartnerLocations(7L)).thenReturn(List.of(
                MerchantLocationResponse.builder().id(70L).title("Main branch").active(true).build()
        ));

        mockMvc.perform(partner(get("/api/v1/partner/merchant/locations"), 44L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(70));

        verify(merchantService).getPartnerLocations(7L);
    }

    @Test
    void cashierIsForbiddenBeforeMerchantLookup() throws Exception {
        when(partnerAccessResolver.resolveOwnerOrManager(45L))
                .thenThrow(new AccessDeniedException("CASHIER cannot manage company"));

        mockMvc.perform(partner(get("/api/v1/partner/merchant"), 45L))
                .andExpect(status().isForbidden());

        verifyNoInteractions(merchantService);
    }

    @Test
    void identityFailureReturnsServiceUnavailable() throws Exception {
        when(partnerAccessResolver.resolveOwnerOrManager(46L))
                .thenThrow(new PartnerAccessUnavailableException("Контекст партнёра временно недоступен"));

        mockMvc.perform(partner(get("/api/v1/partner/merchant"), 46L))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Контекст партнёра временно недоступен"));

        verifyNoInteractions(merchantService);
    }

    private MockHttpServletRequestBuilder partner(MockHttpServletRequestBuilder request, Long userId) {
        return request
                .header("X-User-Id", userId.toString())
                .header("X-User-Role", "PARTNER");
    }
}
