package uz.topdim.identity.dto;

/**
 * Данные, извлечённые из проверенного Google ID-token.
 * {@code sub} — стабильный идентификатор аккаунта Google (ключ привязки);
 * {@code emailVerified} — подтверждён ли email на стороне Google;
 * {@code firstName}/{@code lastName} — имя/фамилия из claims {@code given_name}/{@code family_name}
 * (могут быть null, если Google их не прислал).
 */
public record GoogleIdentity(String sub, String email, boolean emailVerified,
                             String firstName, String lastName) {}
