package uz.topdim.identity.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Валидатор надёжности пароля (OWASP best practices).
 *
 * <p>Проверки:
 * <ol>
 *   <li>Regex: минимум 8 символов, 1 заглавная, 1 строчная, 1 цифра, 1 спецсимвол</li>
 *   <li>Максимум 128 символов (BCrypt обрезает на 72 байта, но пользователь не должен это знать)</li>
 *   <li>Blocklist: 100 самых распространённых паролей (OWASP)</li>
 *   <li>Пароль НЕ обрезается (no trim) — принимается as-is</li>
 * </ol>
 */
public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

    /**
     * Regex: минимум 8 символов, 1 заглавная, 1 строчная, 1 цифра, 1 спецсимвол.
     * Максимум 128 символов.
     */
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&#^()\\-_=+])[A-Za-z\\d@$!%*?&#^()\\-_=+]{8,128}$"
    );

    /**
     * Blocklist распространённых паролей (OWASP).
     * Топ-100 самых частых паролей из утечек.
     */
    private static final Set<String> BLOCKED_PASSWORDS = Set.of(
            "password", "password1", "password123", "Password1", "Password1!",
            "123456", "12345678", "123456789", "1234567890", "12345678!",
            "qwerty", "qwerty123", "Qwerty123!", "qwertyuiop",
            "abc123", "abcdef", "abcd1234",
            "admin", "admin123", "Admin123!", "administrator",
            "letmein", "welcome", "welcome1", "Welcome1!",
            "monkey", "dragon", "master", "login",
            "princess", "football", "shadow", "sunshine",
            "trustno1", "iloveyou", "batman", "access",
            "hello", "charlie", "donald", "password1!",
            "Pass@123", "P@ssw0rd", "P@ssword1", "Passw0rd!",
            "Test1234!", "Test@123", "Qwer1234!",
            "superman", "michael", "ashley", "jessica",
            "changeme", "default", "guest", "root",
            "toor", "pass", "test", "temp",
            "123qwe", "qwe123", "1q2w3e4r",
            "1q2w3e4r5t", "zaq1@WSX", "!QAZ2wsx",
            "topdim", "topdim123", "Topdim123!", "topdim_secret",
            "secret", "Secret123!", "secret123",
            "user", "user123", "User1234!",
            "summer", "winter", "spring", "autumn",
            "january", "february", "march",
            "monday", "friday", "sunday",
            "baseball", "soccer", "hockey",
            "111111", "123123", "654321", "666666",
            "777777", "888888", "999999", "000000",
            "aa123456", "password!", "passwd",
            "nothing", "whatever", "computer",
            "internet", "security", "asshole",
            "fuckyou", "starwars", "mustang",
            "killer", "pepper", "george",
            "zxcvbnm", "asdfghjk", "1234qwer"
    );

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null) {
            return false; // @NotBlank поймает null отдельно
        }

        // Проверка blocklist (case-insensitive)
        if (isBlocked(password)) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                    "Этот пароль слишком распространённый. Выберите более надёжный пароль"
            ).addConstraintViolation();
            return false;
        }

        // Проверка regex
        if (!PASSWORD_PATTERN.matcher(password).matches()) {
            return false; // Используется дефолтное сообщение из аннотации
        }

        return true;
    }

    public static boolean isStrong(String password) {
        return password != null && !isBlocked(password) && PASSWORD_PATTERN.matcher(password).matches();
    }

    private static boolean isBlocked(String password) {
        return BLOCKED_PASSWORDS.stream().anyMatch(blocked -> blocked.equalsIgnoreCase(password));
    }
}
