package uz.topdim.coupon.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.Validator;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import uz.topdim.coupon.exception.GlobalExceptionHandler;
import uz.topdim.coupon.service.CouponOfferService;
import uz.topdim.coupon.service.MerchantService;
import uz.topdim.coupon.service.PartnerCouponService;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CouponOfferControllerValidationTest {

    private static final String INVALID_REQUEST_JSON = """
            {
              "title": "Весенний оффер",
              "offerDescription": "   ",
              "merchantId": 7,
              "categoryId": 3,
              "fromPrice": 99000,
              "coverImageUrl": "https://cdn.topdim.uz/coupon.jpg",
              "buyUntil": "2026-05-01T10:00:00",
              "useUntil": "2026-05-15T10:00:00",
              "giftAvailable": false,
              "options": [],
              "images": []
            }
            """;

    private final CouponOfferService couponOfferService = mock(CouponOfferService.class);
    private final MerchantService merchantService = mock(MerchantService.class);
    private final PartnerCouponService partnerCouponService = mock(PartnerCouponService.class);

    private MockMvc adminMockMvc;
    private MockMvc partnerMockMvc;

    @BeforeEach
    void setUp() {
        Validator validator = createValidator();
        adminMockMvc = MockMvcBuilders
                .standaloneSetup(new AdminCouponController(couponOfferService, merchantService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
        partnerMockMvc = MockMvcBuilders
                .standaloneSetup(new PartnerCouponController(partnerCouponService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    @DisplayName("Admin createCoupon: пустой canonical offerDescription возвращает 400 и не вызывает сервис")
    void adminCreateCoupon_blankOfferDescription_returnsBadRequest() throws Exception {
        adminMockMvc.perform(post("/api/v1/admin/coupons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(INVALID_REQUEST_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Ошибка валидации"));

        verifyNoInteractions(couponOfferService);
    }

    @Test
    @DisplayName("Partner createCoupon: пустой canonical offerDescription возвращает 400 и не вызывает сервис")
    void partnerCreateCoupon_blankOfferDescription_returnsBadRequest() throws Exception {
        partnerMockMvc.perform(post("/api/v1/partner/coupons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", 77)
                        .content(INVALID_REQUEST_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Ошибка валидации"));

        verifyNoInteractions(partnerCouponService);
    }

    private Validator createValidator() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        return validator;
    }
}
