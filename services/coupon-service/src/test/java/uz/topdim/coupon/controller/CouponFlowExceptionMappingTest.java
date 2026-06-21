package uz.topdim.coupon.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import uz.topdim.coupon.exception.GlobalExceptionHandler;
import uz.topdim.coupon.service.CouponOfferService;
import uz.topdim.coupon.service.MerchantService;
import uz.topdim.coupon.service.ModCouponService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests that business-rule exceptions (IllegalStateException, IllegalArgumentException)
 * are mapped to actionable HTTP responses (409, 400) instead of generic 500.
 *
 * Uses standalone MockMvc — no Spring context needed.
 */
@ExtendWith(MockitoExtension.class)
class CouponFlowExceptionMappingTest {

    @Mock private ModCouponService modCouponService;
    @Mock private MerchantService merchantService;
    @Mock private CouponOfferService couponOfferService;

    @InjectMocks private ModCouponController modCouponController;
    @InjectMocks private AdminCouponController adminCouponController;
    @InjectMocks private BotWebhookController botWebhookController;

    private MockMvc modMvc;
    private MockMvc adminMvc;
    private MockMvc botMvc;

    @BeforeEach
    void setUp() {
        modMvc = MockMvcBuilders.standaloneSetup(modCouponController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        adminMvc = MockMvcBuilders.standaloneSetup(adminCouponController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        // Set a valid bot API key (L4: empty key is now fail-closed)
        ReflectionTestUtils.setField(botWebhookController, "botApiKey", "test-bot-key");
        botMvc = MockMvcBuilders.standaloneSetup(botWebhookController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("Mod approve: publication guard failure returns 409 with business message")
    void modApprove_publicationGuardFailure_returnsConflict() throws Exception {
        doThrow(new IllegalStateException("Нельзя публиковать купон без active primary location у мерчанта"))
                .when(modCouponService).reviewCoupon(7L, 12L, "APPROVE", null);

        modMvc.perform(patch("/api/v1/mod/coupons/12/review")
                        .header("X-User-Id", 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"APPROVE"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message")
                        .value("Нельзя публиковать купон без active primary location у мерчанта"));
    }

    @Test
    @DisplayName("Admin merchant update: invalid location payload returns 400 with business message")
    void adminMerchantUpdate_invalidLocationPayload_returnsBadRequest() throws Exception {
        when(merchantService.updateMerchant(eq(5L), any()))
                .thenThrow(new IllegalArgumentException("У мерчанта может быть только одна primary location"));

        adminMvc.perform(put("/api/v1/admin/merchants/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"SPA Oasis","locations":[{"address":"ул. 1","primary":true},{"address":"ул. 2","primary":true}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message")
                        .value("У мерчанта может быть только одна primary location"));
    }

    @Test
    @DisplayName("Bot approve: publication guard failure returns 409 with business message")
    void botApprove_publicationGuardFailure_returnsConflict() throws Exception {
        when(couponOfferService.approveByMerchant(12L))
                .thenThrow(new IllegalStateException("Нельзя публиковать купон без адреса в primary location мерчанта"));

        botMvc.perform(post("/api/v1/bot/coupons/12/approve")
                        .header("X-Bot-Api-Key", "test-bot-key"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message")
                        .value("Нельзя публиковать купон без адреса в primary location мерчанта"));
    }
}
