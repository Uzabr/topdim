package uz.topdim.identity.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = StrongPasswordValidator.class)
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface StrongPassword {
    String message() default "Пароль должен содержать минимум 8 символов, заглавную букву, строчную букву, цифру и спецсимвол";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
