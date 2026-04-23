package uz.topdim.coupon.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import uz.topdim.coupon.exception.GlobalExceptionHandler;
import uz.topdim.coupon.service.CouponOfferService;
import uz.topdim.coupon.service.MerchantService;
import uz.topdim.coupon.service.PartnerCouponService;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-level validation tests (standalone MockMvc — no Spring context needed).
 *
 * Verifies that Jakarta Bean Validation (@NotBlank, @NotNull, @Positive)
 * on CreateCouponOfferRequest is actually enforced by the Spring MVC pipeline
 * and returns HTTP 400.
 *
 * Service-unit tests do NOT exercise this path because they bypass the
 * request→deserialization→validation→controller chain.
 */
@ExtendWith(MockitoExtension.class)
class CouponControllerValidationTest {

    @Mock private CouponOfferService couponOfferService;
    @Mock private MerchantService merchantService;
    @Mock private PartnerCouponService partnerCouponService;

    @InjectMocks private AdminCouponController adminCouponController;
    @InjectMocks private PartnerCouponController partnerCouponController;

    private MockMvc adminMvc;
    private MockMvc partnerMvc;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        // Standalone setup with GlobalExceptionHandler to map
        // MethodArgumentNotValidException → 400
        adminMvc = MockMvcBuilders.standaloneSetup(adminCouponController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        partnerMvc = MockMvcBuilders.standaloneSetup(partnerCouponController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    /**
     * Builds a valid coupon payload as a baseline.
     * Individual tests null-out or blank specific fields to trigger validation.
     */
    private Map<String, Object> validCouponPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", "Тестовый купон");
        payload.put("offerDescription", "Полное описание оффера");
        payload.put("merchantId", 1);
        payload.put("categoryId", 1);
        payload.put("fromPrice", 50000);
        payload.put("coverImageUrl", "/cover.jpg");
        payload.put("buyUntil", "2027-06-01T00:00:00");
        payload.put("useUntil", "2027-07-01T00:00:00");
        return payload;
    }

    // ==================== AdminCouponController: POST ====================

    @Nested
    @DisplayName("POST /api/v1/admin/coupons — validation")
    class AdminCreateValidation {

        @Test
        @DisplayName("Пустой offerDescription → 400")
        void blankOfferDescription_returns400() throws Exception {
            Map<String, Object> payload = validCouponPayload();
            payload.put("offerDescription", "");

            adminMvc.perform(post("/api/v1/admin/coupons")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(payload)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("null offerDescription → 400")
        void nullOfferDescription_returns400() throws Exception {
            Map<String, Object> payload = validCouponPayload();
            payload.remove("offerDescription");

            adminMvc.perform(post("/api/v1/admin/coupons")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(payload)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Пробельный offerDescription → 400")
        void whitespaceOnlyOfferDescription_returns400() throws Exception {
            Map<String, Object> payload = validCouponPayload();
            payload.put("offerDescription", "   ");

            adminMvc.perform(post("/api/v1/admin/coupons")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(payload)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Пустой title → 400")
        void blankTitle_returns400() throws Exception {
            Map<String, Object> payload = validCouponPayload();
            payload.put("title", "");

            adminMvc.perform(post("/api/v1/admin/coupons")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(payload)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("null merchantId → 400")
        void nullMerchantId_returns400() throws Exception {
            Map<String, Object> payload = validCouponPayload();
            payload.remove("merchantId");

            adminMvc.perform(post("/api/v1/admin/coupons")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(payload)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("null fromPrice → 400")
        void nullFromPrice_returns400() throws Exception {
            Map<String, Object> payload = validCouponPayload();
            payload.remove("fromPrice");

            adminMvc.perform(post("/api/v1/admin/coupons")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(payload)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("null coverImageUrl → 400")
        void nullCoverImageUrl_returns400() throws Exception {
            Map<String, Object> payload = validCouponPayload();
            payload.remove("coverImageUrl");

            adminMvc.perform(post("/api/v1/admin/coupons")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(payload)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ==================== AdminCouponController: PUT ====================

    @Nested
    @DisplayName("PUT /api/v1/admin/coupons/{id} — validation")
    class AdminUpdateValidation {

        @Test
        @DisplayName("Пустой offerDescription при обновлении → 400")
        void blankOfferDescription_returns400() throws Exception {
            Map<String, Object> payload = validCouponPayload();
            payload.put("offerDescription", "");

            adminMvc.perform(put("/api/v1/admin/coupons/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-User-Id", "1")
                            .header("X-User-Role", "ADMIN")
                            .content(objectMapper.writeValueAsString(payload)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("null offerDescription при обновлении → 400")
        void nullOfferDescription_returns400() throws Exception {
            Map<String, Object> payload = validCouponPayload();
            payload.remove("offerDescription");

            adminMvc.perform(put("/api/v1/admin/coupons/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-User-Id", "1")
                            .header("X-User-Role", "ADMIN")
                            .content(objectMapper.writeValueAsString(payload)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ==================== PartnerCouponController: POST ====================

    @Nested
    @DisplayName("POST /api/v1/partner/coupons — validation")
    class PartnerCreateValidation {

        @Test
        @DisplayName("Пустой offerDescription → 400")
        void blankOfferDescription_returns400() throws Exception {
            Map<String, Object> payload = validCouponPayload();
            payload.put("offerDescription", "");

            partnerMvc.perform(post("/api/v1/partner/coupons")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-User-Id", "10")
                            .content(objectMapper.writeValueAsString(payload)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("null offerDescription → 400")
        void nullOfferDescription_returns400() throws Exception {
            Map<String, Object> payload = validCouponPayload();
            payload.remove("offerDescription");

            partnerMvc.perform(post("/api/v1/partner/coupons")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-User-Id", "10")
                            .content(objectMapper.writeValueAsString(payload)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Пробельный offerDescription → 400")
        void whitespaceOnlyOfferDescription_returns400() throws Exception {
            Map<String, Object> payload = validCouponPayload();
            payload.put("offerDescription", "   ");

            partnerMvc.perform(post("/api/v1/partner/coupons")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-User-Id", "10")
                            .content(objectMapper.writeValueAsString(payload)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Пустой title → 400")
        void blankTitle_returns400() throws Exception {
            Map<String, Object> payload = validCouponPayload();
            payload.put("title", "");

            partnerMvc.perform(post("/api/v1/partner/coupons")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-User-Id", "10")
                            .content(objectMapper.writeValueAsString(payload)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ==================== PartnerCouponController: PUT ====================

    @Nested
    @DisplayName("PUT /api/v1/partner/coupons/{id} — validation")
    class PartnerUpdateValidation {

        @Test
        @DisplayName("Пустой offerDescription при обновлении → 400")
        void blankOfferDescription_returns400() throws Exception {
            Map<String, Object> payload = validCouponPayload();
            payload.put("offerDescription", "");

            partnerMvc.perform(put("/api/v1/partner/coupons/100")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-User-Id", "10")
                            .content(objectMapper.writeValueAsString(payload)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("null offerDescription при обновлении → 400")
        void nullOfferDescription_returns400() throws Exception {
            Map<String, Object> payload = validCouponPayload();
            payload.remove("offerDescription");

            partnerMvc.perform(put("/api/v1/partner/coupons/100")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-User-Id", "10")
                            .content(objectMapper.writeValueAsString(payload)))
                    .andExpect(status().isBadRequest());
        }
    }
}
