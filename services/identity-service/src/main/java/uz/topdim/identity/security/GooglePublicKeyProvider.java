package uz.topdim.identity.security;

import java.security.PublicKey;

/**
 * Резолвит публичный RSA-ключ Google для проверки подписи ID-token по его {@code kid}
 * (идентификатору ключа из JOSE-заголовка). Реализация тянет и кэширует JWKS Google
 * ({@link GooglePublicKeyProviderImpl}); в тестах подменяется ин-мемори-двойником, чтобы
 * криптопроверку {@link GoogleTokenVerifierImpl} можно было гонять без сети.
 */
public interface GooglePublicKeyProvider {

    /**
     * @param kid идентификатор ключа из заголовка ID-token
     * @return публичный ключ для проверки подписи, либо {@code null}, если {@code kid}
     *         неизвестен (в т.ч. после ротации/недоступности JWKS) — тогда токен НЕ доверенный.
     */
    PublicKey resolve(String kid);
}
