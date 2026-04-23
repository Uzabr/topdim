package uz.topdim.coupon.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CouponMerchantContractCleanupTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("CreateCouponOfferRequest: требует canonical offerDescription")
    void createCouponOfferRequest_requiresCanonicalOfferDescription() {
        CreateCouponOfferRequest request = new CreateCouponOfferRequest();
        request.setTitle("Тестовый купон");
        request.setOfferDescription(" ");
        request.setMerchantId(1L);
        request.setCategoryId(1L);
        request.setFromPrice(BigDecimal.valueOf(100_000));
        request.setCoverImageUrl("/cover.jpg");
        request.setBuyUntil(LocalDateTime.now().plusDays(30));
        request.setUseUntil(LocalDateTime.now().plusDays(60));

        var violations = validator.validate(request);

        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .contains("offerDescription");
    }

    @Test
    @DisplayName("CreateCouponOfferRequest: больше не содержит legacy text fields")
    void createCouponOfferRequest_noLongerContainsLegacyTextFields() {
        assertMissingFields(CreateCouponOfferRequest.class,
                "shortDescription", "fullDescription", "terms", "usageRules", "howToUse");
    }

    @Test
    @DisplayName("CouponOfferResponse: больше не содержит legacy text fields")
    void couponOfferResponse_noLongerContainsLegacyTextFields() {
        assertMissingFields(CouponOfferResponse.class,
                "shortDescription", "fullDescription", "terms", "usageRules", "howToUse");
    }

    @Test
    @DisplayName("CreateMerchantRequest: принимает только normalized locations для contact data")
    void createMerchantRequest_noLongerContainsLegacyContactFields() {
        assertMissingFields(CreateMerchantRequest.class, "address", "phone", "workingHours");

        Set<String> fieldNames = Set.of(CreateMerchantRequest.class.getDeclaredFields())
                .stream()
                .map(Field::getName)
                .collect(java.util.stream.Collectors.toSet());

        assertThat(fieldNames).contains("locations");
    }

    @Test
    @DisplayName("MerchantResponse: больше не содержит legacy contact fields")
    void merchantResponse_noLongerContainsLegacyContactFields() {
        assertMissingFields(MerchantResponse.class, "address", "phone", "workingHours");
    }

    private static void assertMissingFields(Class<?> type, String... fieldNames) {
        for (String fieldName : fieldNames) {
            assertThatThrownBy(() -> type.getDeclaredField(fieldName))
                    .isInstanceOf(NoSuchFieldException.class);
        }
    }
}
