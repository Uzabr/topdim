package uz.topdim.auth.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Аннотация для валидации надёжности пароля.
 * Проверяет regex (8+ символов, заглавная, цифра, спецсимвол)
 * и blocklist распространённых паролей.
 */
@Documented
@Constraint(validatedBy = StrongPasswordValidator.class)
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface StrongPassword {
    String message() default "Пароль должен содержать минимум 8 символов, "
            + "включая заглавную букву, строчную букву, цифру и спецсимвол (@$!%*?&)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
