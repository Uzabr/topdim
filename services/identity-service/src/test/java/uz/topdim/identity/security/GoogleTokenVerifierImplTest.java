package uz.topdim.identity.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.topdim.identity.exception.AuthException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Стаб-верификатор (T6): реальная JWKS-проверка — отдельная задача (см. class javadoc
 * {@link GoogleTokenVerifierImpl}). Здесь проверяем только контракт "не настроено"/"не реализовано",
 * а не крипто-логику (её ещё нет — намеренно, чтобы не добавлять google-api-client без согласования).
 */
class GoogleTokenVerifierImplTest {

    @Test
    @DisplayName("google.client-id не задан → «Google-вход не настроен»")
    void notConfigured_throws() {
        GoogleTokenVerifierImpl verifier = new GoogleTokenVerifierImpl("");
        assertThatThrownBy(() -> verifier.verify("any-token"))
                .isInstanceOf(AuthException.class)
                .hasMessage("Google-вход не настроен");
    }

    @Test
    @DisplayName("google.client-id задан, но крипто-проверка ещё не реализована → явная ошибка, токен не принимается молча")
    void configuredButNotImplemented_stillRejects() {
        GoogleTokenVerifierImpl verifier = new GoogleTokenVerifierImpl("some-client-id.apps.googleusercontent.com");
        assertThatThrownBy(() -> verifier.verify("any-token"))
                .isInstanceOf(AuthException.class);
    }
}
