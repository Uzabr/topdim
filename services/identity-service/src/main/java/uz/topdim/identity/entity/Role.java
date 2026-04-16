package uz.topdim.identity.entity;

/**
 * Перечисление ролей пользователя.
 * Иерархия: GUEST → USER → PARTNER → MODERATOR → ADMIN → SUPER_ADMIN.
 * Каждая следующая роль наследует все права предыдущей.
 */
public enum Role {
    GUEST,
    USER,
    PARTNER,
    MODERATOR,
    ADMIN,
    SUPER_ADMIN
}
