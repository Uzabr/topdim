package uz.topdim.identity.entity;

/**
 * Типы одноразовых auth action tokens.
 * Расширяемо: для нового флоу достаточно добавить значение.
 */
public enum AuthActionType {
    PASSWORD_RESET,
    EMAIL_CONFIRM,
    PHONE_CONFIRM
}
