package uz.topdim.user.exception;

/**
 * Исключение — пользователь не найден.
 * HTTP 404 при отсутствии пользователя.
 */
public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(String message) {
        super(message);
    }
}
