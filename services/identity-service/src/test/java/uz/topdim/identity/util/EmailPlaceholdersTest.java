package uz.topdim.identity.util;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class EmailPlaceholdersTest {
    @Test void phonePlaceholderUnverified_isPlaceholder() {
        assertThat(EmailPlaceholders.isPlaceholder("phone_+998901234567@topdim.uz", false)).isTrue();
    }
    @Test void tgPlaceholderUnverified_isPlaceholder() {
        assertThat(EmailPlaceholders.isPlaceholder("tg_12345@topdim.uz", false)).isTrue();
    }
    @Test void releasedPlaceholderUnverified_isPlaceholder() {
        assertThat(EmailPlaceholders.isPlaceholder("released_42@topdim.uz", false)).isTrue();
    }
    @Test void realVerifiedEmail_isNotPlaceholder() {
        assertThat(EmailPlaceholders.isPlaceholder("ivan@gmail.com", true)).isFalse();
    }
    @Test void placeholderPatternButVerified_isNotPlaceholder() {
        // после email-change адрес мог бы совпасть с паттерном, но он подтверждён → не затычка
        assertThat(EmailPlaceholders.isPlaceholder("phone_x@topdim.uz", true)).isFalse();
    }
    @Test void nullEmail_isNotPlaceholder() {
        assertThat(EmailPlaceholders.isPlaceholder(null, false)).isFalse();
    }
}
