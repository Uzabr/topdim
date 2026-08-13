package uz.topdim.coupon.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import uz.topdim.coupon.config.SecurityConfig;
import uz.topdim.coupon.dto.merchantprofile.AdminMerchantProfileChangeFilter;
import uz.topdim.coupon.entity.MerchantProfileChangeStatus;
import uz.topdim.coupon.exception.GlobalExceptionHandler;
import uz.topdim.coupon.security.RoleHeaderAuthenticationFilter;
import uz.topdim.coupon.service.MerchantProfileModerationService;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminMerchantProfileChangeController.class)
@Import({SecurityConfig.class, RoleHeaderAuthenticationFilter.class, GlobalExceptionHandler.class})
class AdminMerchantProfileChangeControllerSecurityTest {

    private static final String BASE_PATH = "/api/v1/admin/merchant-change-requests";

    @Autowired private MockMvc mockMvc;
    @MockBean private MerchantProfileModerationService moderationService;

    @ParameterizedTest
    @ValueSource(strings = {"MODERATOR", "ADMIN", "SUPER_ADMIN"})
    void staffRolesCanListGetAndTakeSubmittedRequests(String role) throws Exception {
        when(moderationService.list(any(), any())).thenReturn(Page.empty());

        mockMvc.perform(withStaff(get(BASE_PATH), role)).andExpect(status().isOk());
        mockMvc.perform(withStaff(get(BASE_PATH + "/10"), role)).andExpect(status().isOk());
        mockMvc.perform(withStaff(post(BASE_PATH + "/10/take-to-work"), role))
                .andExpect(status().isOk());
        mockMvc.perform(withStaff(post(BASE_PATH + "/10/approve"), role))
                .andExpect(status().isOk());
        mockMvc.perform(withStaff(post(BASE_PATH + "/10/request-revision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"Уточните адрес\"}"), role))
                .andExpect(status().isOk());
        mockMvc.perform(withStaff(post(BASE_PATH + "/10/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"Нарушение правил\"}"), role))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"USER", "PARTNER"})
    void nonStaffCannotAccessModerationQueue(String role) throws Exception {
        mockMvc.perform(withStaff(get(BASE_PATH), role)).andExpect(status().isForbidden());
        verify(moderationService, never()).list(any(), any());
    }

    @Test
    void moderatorCannotReleaseOrReassign() throws Exception {
        mockMvc.perform(withStaff(post(BASE_PATH + "/10/release"), "MODERATOR"))
                .andExpect(status().isForbidden());
        mockMvc.perform(withStaff(post(BASE_PATH + "/10/reassign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeUserId\":88}"), "MODERATOR"))
                .andExpect(status().isForbidden());

        verify(moderationService, never()).release(any(), any(), any());
        verify(moderationService, never()).reassign(any(), any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "SUPER_ADMIN"})
    void adminRolesCanReleaseAndReassign(String role) throws Exception {
        mockMvc.perform(withStaff(post(BASE_PATH + "/10/release"), role))
                .andExpect(status().isOk());
        mockMvc.perform(withStaff(post(BASE_PATH + "/10/reassign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeUserId\":88}"), role))
                .andExpect(status().isOk());

        verify(moderationService).release(10L, 42L, role);
        verify(moderationService).reassign(10L, 88L, 42L, role);
    }

    @Test
    void listNormalizesAndPassesAllFiltersAndPaging() throws Exception {
        when(moderationService.list(any(), any())).thenReturn(Page.empty());

        mockMvc.perform(withStaff(get(BASE_PATH)
                        .param("status", "IN_REVIEW")
                        .param("search", "  market  ")
                        .param("assigneeUserId", "77")
                        .param("submittedFrom", "2026-08-01T00:00:00")
                        .param("submittedTo", "2026-08-13T23:59:59")
                        .param("page", "2")
                        .param("size", "15"), "ADMIN"))
                .andExpect(status().isOk());

        ArgumentCaptor<AdminMerchantProfileChangeFilter> filterCaptor =
                ArgumentCaptor.forClass(AdminMerchantProfileChangeFilter.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(moderationService).list(filterCaptor.capture(), pageableCaptor.capture());
        assertThat(filterCaptor.getValue()).isEqualTo(new AdminMerchantProfileChangeFilter(
                MerchantProfileChangeStatus.IN_REVIEW,
                "market",
                77L,
                LocalDateTime.of(2026, 8, 1, 0, 0),
                LocalDateTime.of(2026, 8, 13, 23, 59, 59)));
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(15);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "status=DRAFT",
            "page=-1",
            "size=0",
            "size=101",
            "assigneeUserId=0",
            "submittedFrom=2026-08-14T00:00:00&submittedTo=2026-08-13T00:00:00"
    })
    void invalidQueueFiltersReturnBadRequest(String query) throws Exception {
        mockMvc.perform(withStaff(get(BASE_PATH + "?" + query), "ADMIN"))
                .andExpect(status().isBadRequest());
        verify(moderationService, never()).list(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"assigneeUserId\":0}"})
    void invalidReassignBodyReturnsBadRequest(String body) throws Exception {
        mockMvc.perform(withStaff(post(BASE_PATH + "/10/reassign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body), "ADMIN"))
                .andExpect(status().isBadRequest());
        verify(moderationService, never()).reassign(any(), any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"comment\":\"   \"}"})
    void revisionAndRejectRequireNonBlankComment(String body) throws Exception {
        for (String action : new String[]{"request-revision", "reject"}) {
            mockMvc.perform(withStaff(post(BASE_PATH + "/10/" + action)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body), "MODERATOR"))
                    .andExpect(status().isBadRequest());
        }
        verify(moderationService, never()).requestRevision(any(), any(), any(), any());
        verify(moderationService, never()).reject(any(), any(), any(), any());
    }

    @Test
    void losingConcurrentTakeReturnsConflict() throws Exception {
        doThrow(new IllegalStateException("Заявка уже взята в работу"))
                .when(moderationService).takeToWork(10L, 42L, "MODERATOR");

        mockMvc.perform(withStaff(post(BASE_PATH + "/10/take-to-work"), "MODERATOR"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Заявка уже взята в работу"));
    }

    private MockHttpServletRequestBuilder withStaff(
            MockHttpServletRequestBuilder request,
            String role
    ) {
        return request
                .header("X-User-Id", "42")
                .header("X-User-Role", role);
    }
}
