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
import uz.topdim.coupon.dto.CouponOfferResponse;
import uz.topdim.coupon.exception.GlobalExceptionHandler;
import uz.topdim.coupon.service.CouponOfferService;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * L4: BotWebhookController API key validation tests.
 * Verifies fail-closed behavior and constant-time comparison.
 */
@ExtendWith(MockitoExtension.class)
class BotWebhookApiKeyTest {

    @Mock private CouponOfferService couponOfferService;
    @InjectMocks private BotWebhookController botWebhookController;

    private MockMvc botMvc;

    @BeforeEach
    void setUp() {
        botMvc = MockMvcBuilders.standaloneSetup(botWebhookController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("L4: empty bot API key → fail-closed (401), not passthrough")
    void emptyBotApiKey_failsClosed() throws Exception {
        ReflectionTestUtils.setField(botWebhookController, "botApiKey", "");

        botMvc.perform(post("/api/v1/bot/coupons/1/approve"))
                .andExpect(status().isUnauthorized());

        verify(couponOfferService, never()).approveByMerchant(anyLong());
    }

    @Test
    @DisplayName("L4: null bot API key → fail-closed (401)")
    void nullBotApiKey_failsClosed() throws Exception {
        ReflectionTestUtils.setField(botWebhookController, "botApiKey", null);

        botMvc.perform(post("/api/v1/bot/coupons/1/approve"))
                .andExpect(status().isUnauthorized());

        verify(couponOfferService, never()).approveByMerchant(anyLong());
    }

    @Test
    @DisplayName("L4: valid API key → request passes through")
    void validApiKey_passes() throws Exception {
        String key = "c37cb277f78152b4ecc670efb331b8b0947fe689ac5f9eb82f1ca3da7a7057ab";
        ReflectionTestUtils.setField(botWebhookController, "botApiKey", key);
        when(couponOfferService.approveByMerchant(1L))
                .thenReturn(new CouponOfferResponse());

        botMvc.perform(post("/api/v1/bot/coupons/1/approve")
                        .header("X-Bot-Api-Key", key))
                .andExpect(status().isOk());

        verify(couponOfferService).approveByMerchant(1L);
    }

    @Test
    @DisplayName("L4: wrong API key → 401")
    void wrongApiKey_isRejected() throws Exception {
        ReflectionTestUtils.setField(botWebhookController, "botApiKey", "correct-key");

        botMvc.perform(post("/api/v1/bot/coupons/1/approve")
                        .header("X-Bot-Api-Key", "wrong-key"))
                .andExpect(status().isUnauthorized());

        verify(couponOfferService, never()).approveByMerchant(anyLong());
    }

    @Test
    @DisplayName("L4: missing API key header → 401")
    void missingApiKeyHeader_isRejected() throws Exception {
        ReflectionTestUtils.setField(botWebhookController, "botApiKey", "some-key");

        botMvc.perform(post("/api/v1/bot/coupons/1/approve"))
                .andExpect(status().isUnauthorized());

        verify(couponOfferService, never()).approveByMerchant(anyLong());
    }
}
