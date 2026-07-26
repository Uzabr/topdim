package uz.topdim.identity.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uz.topdim.identity.dto.GoogleIdentity;
import uz.topdim.identity.exception.AuthException;

/**
 * СТАБ проверки Google ID-token.
 *
 * <p>Настоящая криптографическая проверка (подпись JWT по JWKS Google, issuer/audience/exp)
 * требует библиотеки {@code com.google.api-client:google-api-client} (или ручного JWKS-клиента).
 * Добавление новой зависимости не согласовано владельцем проекта — это отдельная задача
 * ПОСЛЕ согласования. До тех пор {@link #verify(String)} никогда не подтверждает токен:
 * при отсутствии {@code google.client-id} бросает «Google-вход не настроен», а при формально
 * заданном client-id (конфигурация выставлена заранее, до появления реализации) — явно
 * бросает отдельную ошибку, а не молча доверяет непроверенному токену.
 *
 * <p>Паттерн — как {@link TelegramLoginVerifier}: безусловный {@code @Component}
 * с {@code @Value(...:"")} по умолчанию, а НЕ {@code @ConditionalOnProperty}. Если бы бин
 * не создавался при отсутствующем свойстве, конструкторная инъекция {@code GoogleTokenVerifier}
 * в {@code AuthService} сломала бы старт приложения (bean not found) в любом окружении,
 * где {@code google.client-id} не задан — а сейчас он нигде не задан.
 */
@Slf4j
@Component
public class GoogleTokenVerifierImpl implements GoogleTokenVerifier {

    private final boolean configured;

    public GoogleTokenVerifierImpl(@Value("${google.client-id:}") String clientId) {
        this.configured = clientId != null && !clientId.isBlank();
        if (!configured) {
            log.warn("Google login disabled: google.client-id не задан");
        }
    }

    @Override
    public GoogleIdentity verify(String idToken) {
        if (!configured) {
            throw new AuthException("Google-вход не настроен");
        }
        // google.client-id задан, но реальной проверки JWKS/подписи ещё нет (см. class javadoc) —
        // намеренно НЕ доверяем токену молча. Реализация — отдельная задача после согласования
        // com.google.api-client (или ручного JWKS-клиента без новых зависимостей).
        throw new AuthException("Проверка Google ID-token ещё не реализована");
    }
}
