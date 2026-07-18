package uz.topdim.coupon.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import uz.topdim.coupon.dto.CouponOfferResponse;
import uz.topdim.coupon.dto.SituationResponse;
import uz.topdim.coupon.exception.GlobalExceptionHandler;
import uz.topdim.coupon.service.CouponOfferService;
import uz.topdim.coupon.service.SituationService;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SituationControllerTest {

    @Mock private SituationService situationService;
    @Mock private CouponOfferService couponOfferService;
    @InjectMocks private SituationController situationController;
    @InjectMocks private CouponController couponController;

    private MockMvc situationsMvc;
    private MockMvc couponsMvc;

    @BeforeEach
    void setUp() {
        situationsMvc = MockMvcBuilders.standaloneSetup(situationController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        couponsMvc = MockMvcBuilders.standaloneSetup(couponController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ==================== GET /api/v1/situations ====================

    @Test
    @DisplayName("GET /api/v1/situations: 200 с данными")
    void getSituations_withData_returns200() throws Exception {
        when(situationService.getActiveSituations()).thenReturn(List.of(
                SituationResponse.builder()
                        .key("kids")
                        .title("Отдохнуть с детьми")
                        .titleUz("Bolalar bilan dam olish")
                        .couponCount(64)
                        .featured(true)
                        .sortOrder(0)
                        .build(),
                SituationResponse.builder()
                        .key("beauty")
                        .title("Привести себя в порядок")
                        .couponCount(0)
                        .featured(false)
                        .sortOrder(1)
                        .build()
        ));

        situationsMvc.perform(get("/api/v1/situations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].key").value("kids"))
                .andExpect(jsonPath("$.data[0].title").value("Отдохнуть с детьми"))
                .andExpect(jsonPath("$.data[0].couponCount").value(64))
                .andExpect(jsonPath("$.data[0].featured").value(true))
                .andExpect(jsonPath("$.data[1].key").value("beauty"))
                .andExpect(jsonPath("$.data[1].couponCount").value(0));
    }

    @Test
    @DisplayName("GET /api/v1/situations: пустой список → 200, data = []")
    void getSituations_empty_returns200WithEmptyArray() throws Exception {
        when(situationService.getActiveSituations()).thenReturn(List.of());

        situationsMvc.perform(get("/api/v1/situations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    // ==================== GET /api/v1/coupons?situation=... ====================

    @Test
    @DisplayName("Каталог ?situation=kids: фильтрация работает")
    void getCatalog_withSituation_filtersCorrectly() throws Exception {
        CouponOfferResponse coupon = CouponOfferResponse.builder()
                .id(1L).title("Детский парк").status("ACTIVE").build();
        Page<CouponOfferResponse> page = new PageImpl<>(List.of(coupon), PageRequest.of(0, 20), 1);

        when(couponOfferService.getCatalog(isNull(), isNull(), eq("kids"), eq("popular"), eq(0), eq(20)))
                .thenReturn(page);

        couponsMvc.perform(get("/api/v1/coupons").param("situation", "kids"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].title").value("Детский парк"));
    }

    @Test
    @DisplayName("Каталог ?situation=nonexistent: пустая страница, не 500")
    void getCatalog_nonexistentSituation_returnsEmptyPage() throws Exception {
        Page<CouponOfferResponse> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);

        when(couponOfferService.getCatalog(isNull(), isNull(), eq("nonexistent"), eq("popular"), eq(0), eq(20)))
                .thenReturn(emptyPage);

        couponsMvc.perform(get("/api/v1/coupons").param("situation", "nonexistent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isEmpty());
    }
}
