package uz.topdim.auth.exception;

/**
 * Исключение аутентификации.
 * Выбрасывается при неверных credentials, дубликатах email/phone.
 */
public class AuthException extends RuntimeException {
    public AuthException(String message) {
        super(message);
    }
}
