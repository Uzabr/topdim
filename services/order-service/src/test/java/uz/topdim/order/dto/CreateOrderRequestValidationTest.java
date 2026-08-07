package uz.topdim.order.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CreateOrderRequestValidationTest {
    private final Validator v = Validation.buildDefaultValidatorFactory().getValidator();

    private CreateOrderRequest req(String email, String phone) {
        CreateOrderRequest r = new CreateOrderRequest();
        r.setEmail(email); r.setPhone(phone);
        return r;
    }

    @Test void phoneRequired_nullPhone_invalid() {
        assertThat(v.validate(req("i@x.uz", null))).isNotEmpty();
    }
    @Test void phoneBadFormat_invalid() {
        assertThat(v.validate(req(null, "12345"))).isNotEmpty();
    }
    @Test void emailOptional_nullEmailValidPhone_valid() {
        assertThat(v.validate(req(null, "+998901234567"))).isEmpty();
    }
    @Test void emailBadFormatWhenPresent_invalid() {
        assertThat(v.validate(req("not-an-email", "+998901234567"))).isNotEmpty();
    }
}
