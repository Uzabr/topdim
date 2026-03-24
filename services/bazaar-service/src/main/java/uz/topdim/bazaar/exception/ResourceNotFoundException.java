package uz.topdim.bazaar.exception;

/**
 * Исключение — ресурс не найден.
 * HTTP 404 при отсутствии купона, категории или партнёра.
 */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
