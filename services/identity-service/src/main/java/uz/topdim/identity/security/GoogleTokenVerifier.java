package uz.topdim.identity.security;

import uz.topdim.identity.dto.GoogleIdentity;

/**
 * Проверяет Google ID-token и извлекает из него подтверждённую идентичность.
 * Реализация — {@link GoogleTokenVerifierImpl}.
 */
public interface GoogleTokenVerifier {

    /**
     * Проверяет подпись/срок действия ID-token.
     *
     * @throws uz.topdim.identity.exception.AuthException если токен невалиден
     *         или проверка не настроена.
     */
    GoogleIdentity verify(String idToken);
}
