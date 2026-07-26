package uz.topdim.identity.dto;

/**
 * Данные, извлечённые из проверенного Google ID-token.
 * {@code sub} — стабильный идентификатор аккаунта Google (ключ привязки);
 * {@code emailVerified} — подтверждён ли email на стороне Google.
 */
public record GoogleIdentity(String sub, String email, boolean emailVerified) {}
