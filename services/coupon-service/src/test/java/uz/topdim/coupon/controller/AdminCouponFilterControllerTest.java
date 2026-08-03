package uz.topdim.coupon.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.test.web.servlet.MockMvc;
import uz.topdim.coupon.config.SecurityConfig;
import uz.topdim.coupon.dto.AdminCouponFilter;
import uz.topdim.coupon.dto.CouponAssigneeResponse;
import uz.topdim.coupon.entity.CouponStatus;
import uz.topdim.coupon.exception.GlobalExceptionHandler;
import uz.topdim.coupon.security.RoleHeaderAuthenticationFilter;
import uz.topdim.coupon.service.CouponOfferService;
import uz.topdim.coupon.service.MerchantService;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminCouponController.class)
@Import({SecurityConfig.class, RoleHeaderAuthenticationFilter.class, GlobalExceptionHandler.class})
class AdminCouponFilterControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private CouponOfferService couponOfferService;
    @MockBean private MerchantService merchantService;

    @Test
    @DisplayName("GET admin coupons: одиночный status сохраняет совместимость")
    void getAllCoupons_singleStatus_buildsOneStatusFilter() throws Exception {
        when(couponOfferService.getAllForAdmin(any(AdminCouponFilter.class), anyInt(), anyInt()))
                .thenReturn(Page.empty());

        mockMvc.perform(staffGet("/api/v1/admin/coupons", "MODERATOR")
                        .param("status", "LEAD")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk());

        ArgumentCaptor<AdminCouponFilter> filterCaptor =
                ArgumentCaptor.forClass(AdminCouponFilter.class);
        verify(couponOfferService).getAllForAdmin(filterCaptor.capture(), eq(0), eq(20));
        assertThat(filterCaptor.getValue().statuses()).containsExactly(CouponStatus.LEAD);
    }

    @Test
    @DisplayName("GET admin coupons: statuses передаёт объединённый фильтр")
    void getAllCoupons_multipleStatuses_buildsStatusSet() throws Exception {
        when(couponOfferService.getAllForAdmin(any(AdminCouponFilter.class), anyInt(), anyInt()))
                .thenReturn(Page.empty());

        mockMvc.perform(staffGet("/api/v1/admin/coupons", "ADMIN")
                        .param("statuses", "ACTIVE,PAUSED")
                        .param("search", "  pizza  ")
                        .param("merchantId", "12")
                        .param("assignedModeratorId", "7"))
                .andExpect(status().isOk());

        ArgumentCaptor<AdminCouponFilter> filterCaptor =
                ArgumentCaptor.forClass(AdminCouponFilter.class);
        verify(couponOfferService).getAllForAdmin(filterCaptor.capture(), eq(0), eq(20));
        AdminCouponFilter filter = filterCaptor.getValue();
        assertThat(filter.statuses()).containsExactlyInAnyOrder(
                CouponStatus.ACTIVE,
                CouponStatus.PAUSED
        );
        assertThat(filter.search()).isEqualTo("pizza");
        assertThat(filter.merchantId()).isEqualTo(12L);
        assertThat(filter.assignedModeratorId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("GET admin coupons: status вместе с statuses возвращает 400")
    void getAllCoupons_ambiguousStatuses_returnsBadRequest() throws Exception {
        mockMvc.perform(staffGet("/api/v1/admin/coupons", "ADMIN")
                        .param("status", "LEAD")
                        .param("statuses", "ACTIVE,PAUSED"))
                .andExpect(status().isBadRequest());

        verify(couponOfferService, never())
                .getAllForAdmin(any(AdminCouponFilter.class), anyInt(), anyInt());
    }

    @ParameterizedTest(name = "page={0} возвращает 400")
    @ValueSource(strings = {"-1"})
    void getAllCoupons_invalidPage_returnsBadRequest(String page) throws Exception {
        mockMvc.perform(staffGet("/api/v1/admin/coupons", "ADMIN")
                        .param("page", page))
                .andExpect(status().isBadRequest());

        verify(couponOfferService, never())
                .getAllForAdmin(any(AdminCouponFilter.class), anyInt(), anyInt());
    }

    @ParameterizedTest(name = "size={0} возвращает 400")
    @ValueSource(strings = {"0", "101"})
    void getAllCoupons_invalidSize_returnsBadRequest(String size) throws Exception {
        mockMvc.perform(staffGet("/api/v1/admin/coupons", "ADMIN")
                        .param("size", size))
                .andExpect(status().isBadRequest());

        verify(couponOfferService, never())
                .getAllForAdmin(any(AdminCouponFilter.class), anyInt(), anyInt());
    }

    @ParameterizedTest(name = "{0}=0 возвращает 400")
    @ValueSource(strings = {"merchantId", "assignedModeratorId"})
    void getAllCoupons_zeroFilterId_returnsBadRequest(String parameter) throws Exception {
        mockMvc.perform(staffGet("/api/v1/admin/coupons", "ADMIN")
                        .param(parameter, "0"))
                .andExpect(status().isBadRequest());

        verify(couponOfferService, never())
                .getAllForAdmin(any(AdminCouponFilter.class), anyInt(), anyInt());
    }

    @ParameterizedTest(name = "{0}=-1 возвращает 400")
    @ValueSource(strings = {"merchantId", "assignedModeratorId"})
    void getAllCoupons_negativeFilterId_returnsBadRequest(String parameter) throws Exception {
        mockMvc.perform(staffGet("/api/v1/admin/coupons", "ADMIN")
                        .param(parameter, "-1"))
                .andExpect(status().isBadRequest());

        verify(couponOfferService, never())
                .getAllForAdmin(any(AdminCouponFilter.class), anyInt(), anyInt());
    }

    @ParameterizedTest(name = "{0} читает справочник ответственных")
    @ValueSource(strings = {"MODERATOR", "ADMIN", "SUPER_ADMIN"})
    void getAssignees_staffRoles_returnsMinimalDirectory(String role) throws Exception {
        when(couponOfferService.getCouponAssignees())
                .thenReturn(List.of(new CouponAssigneeResponse(7L, "mod@sizbiz.uz")));

        mockMvc.perform(staffGet("/api/v1/admin/coupons/assignees", role))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(7))
                .andExpect(jsonPath("$.data[0].name").value("mod@sizbiz.uz"))
                .andExpect(jsonPath("$.data[0].email").doesNotExist());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder staffGet(
            String path,
            String role
    ) {
        return get(path)
                .header("X-User-Id", "42")
                .header("X-User-Role", role);
    }
}
