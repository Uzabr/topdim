package uz.topdim.coupon.controller;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import uz.topdim.coupon.config.SecurityConfig;
import uz.topdim.coupon.dto.merchantprofile.MerchantProfileChangeResponse;
import uz.topdim.coupon.dto.merchantprofile.MerchantProfileChangeSummary;
import uz.topdim.coupon.entity.MerchantProfileChangeStatus;
import uz.topdim.coupon.exception.GlobalExceptionHandler;
import uz.topdim.coupon.exception.ResourceNotFoundException;
import uz.topdim.coupon.security.RoleHeaderAuthenticationFilter;
import uz.topdim.coupon.service.MerchantProfileDraftService;
import uz.topdim.coupon.service.PartnerAccessResolver;
import uz.topdim.coupon.service.ResolvedPartnerAccess;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PartnerMerchantProfileChangeController.class)
@Import({SecurityConfig.class, RoleHeaderAuthenticationFilter.class, GlobalExceptionHandler.class})
class PartnerMerchantProfileChangeControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private MerchantProfileDraftService draftService;
    @MockBean private PartnerAccessResolver accessResolver;

    private final ResolvedPartnerAccess ownerAccess =
            new ResolvedPartnerAccess(7L, "OWNER", null);

    @Test
    void createDraftReturnsCreatedSnapshot() throws Exception {
        when(accessResolver.resolveOwnerOrManager(41L)).thenReturn(ownerAccess);
        when(draftService.createDraft(41L, ownerAccess)).thenReturn(response(100L));

        mockMvc.perform(partner(post("/api/v1/partner/merchant/change-requests")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.baseProfileVersion").value(4));
    }

    @Test
    void listUsesOwnedStatusFilterAndBoundedPage() throws Exception {
        when(accessResolver.resolveOwnerOrManager(41L)).thenReturn(ownerAccess);
        MerchantProfileChangeSummary summary = new MerchantProfileChangeSummary(
                100L, 7L, "Draft", MerchantProfileChangeStatus.DRAFT, 4L,
                41L, null, "OWNER", null, null, null, null, null, null
        );
        when(draftService.list(eq(MerchantProfileChangeStatus.DRAFT), any(), eq(ownerAccess)))
                .thenReturn(new PageImpl<>(List.of(summary), PageRequest.of(2, 5), 11));

        mockMvc.perform(partner(get("/api/v1/partner/merchant/change-requests")
                        .param("status", "DRAFT")
                        .param("page", "2")
                        .param("size", "5")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(100))
                .andExpect(jsonPath("$.data.totalElements").value(11));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(draftService).list(
                eq(MerchantProfileChangeStatus.DRAFT), pageable.capture(), eq(ownerAccess));
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    void foreignRequestIsReturnedAsNotFound() throws Exception {
        when(accessResolver.resolveOwnerOrManager(41L)).thenReturn(ownerAccess);
        when(draftService.get(999L, ownerAccess))
                .thenThrow(new ResourceNotFoundException("Заявка не найдена"));

        mockMvc.perform(partner(get("/api/v1/partner/merchant/change-requests/999")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Заявка не найдена"));
    }

    @Test
    void invalidUpdatePayloadIsRejectedBeforeService() throws Exception {
        when(accessResolver.resolveOwnerOrManager(41L)).thenReturn(ownerAccess);

        mockMvc.perform(partner(put("/api/v1/partner/merchant/change-requests/100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "   ",
                                  "website": "ftp://invalid.example",
                                  "locations": []
                                }
                                """)))
                .andExpect(status().isBadRequest());

        verify(draftService, never()).update(any(), any(), any());
    }

    @Test
    void validUpdateReturnsUpdatedSnapshot() throws Exception {
        when(accessResolver.resolveOwnerOrManager(41L)).thenReturn(ownerAccess);
        when(draftService.update(eq(100L), any(), eq(ownerAccess))).thenReturn(response(100L));

        mockMvc.perform(partner(put("/api/v1/partner/merchant/change-requests/100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Updated company",
                                  "email": "company@sizbiz.uz",
                                  "website": "https://sizbiz.uz/company",
                                  "locations": []
                                }
                                """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(100));
    }

    @Test
    void deleteDraftReturnsNoContentAndConflictIsPreserved() throws Exception {
        when(accessResolver.resolveOwnerOrManager(41L)).thenReturn(ownerAccess);

        mockMvc.perform(partner(delete("/api/v1/partner/merchant/change-requests/100")))
                .andExpect(status().isNoContent());
        verify(draftService).deleteDraft(100L, ownerAccess);

        org.mockito.Mockito.doThrow(new IllegalStateException("Удалить можно только черновик заявки"))
                .when(draftService).deleteDraft(101L, ownerAccess);
        mockMvc.perform(partner(delete("/api/v1/partner/merchant/change-requests/101")))
                .andExpect(status().isConflict());
    }

    @Test
    void cashierIsForbiddenBeforeDraftService() throws Exception {
        when(accessResolver.resolveOwnerOrManager(41L))
                .thenThrow(new AccessDeniedException("CASHIER cannot manage company"));

        mockMvc.perform(partner(get("/api/v1/partner/merchant/change-requests")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(draftService);
    }

    @Test
    void submitUsesAuthenticatedActorAndResolvedMerchant() throws Exception {
        when(accessResolver.resolveOwnerOrManager(41L)).thenReturn(ownerAccess);
        when(draftService.submit(100L, 41L, ownerAccess)).thenReturn(response(100L));

        mockMvc.perform(partner(post(
                        "/api/v1/partner/merchant/change-requests/100/submit")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(100));

        verify(draftService).submit(100L, 41L, ownerAccess);
    }

    @Test
    void withdrawRejectsBlankReasonBeforeService() throws Exception {
        when(accessResolver.resolveOwnerOrManager(41L)).thenReturn(ownerAccess);

        mockMvc.perform(partner(post(
                        "/api/v1/partner/merchant/change-requests/100/withdraw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"   "}
                                """)))
                .andExpect(status().isBadRequest());

        verify(draftService, never()).withdraw(any(), any(), any(), any());
    }

    @Test
    void withdrawReturnsWithdrawnSnapshot() throws Exception {
        when(accessResolver.resolveOwnerOrManager(41L)).thenReturn(ownerAccess);
        when(draftService.withdraw(100L, 41L, "Company changed", ownerAccess))
                .thenReturn(response(100L));

        mockMvc.perform(partner(post(
                        "/api/v1/partner/merchant/change-requests/100/withdraw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Company changed"}
                                """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(100));
    }

    @Test
    void copyCreatesNewDraft() throws Exception {
        when(accessResolver.resolveOwnerOrManager(41L)).thenReturn(ownerAccess);
        when(draftService.copy(100L, 41L, ownerAccess)).thenReturn(response(101L));

        mockMvc.perform(partner(post(
                        "/api/v1/partner/merchant/change-requests/100/copy")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(101));
    }

    private MockHttpServletRequestBuilder partner(MockHttpServletRequestBuilder request) {
        return request
                .header("X-User-Id", "41")
                .header("X-User-Role", "PARTNER");
    }

    private MerchantProfileChangeResponse response(Long id) {
        return new MerchantProfileChangeResponse(
                id,
                7L,
                41L,
                null,
                "OWNER",
                4L,
                MerchantProfileChangeStatus.DRAFT,
                null,
                null,
                "Draft company",
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                0L
        );
    }
}
