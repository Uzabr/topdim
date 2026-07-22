package uz.topdim.identity.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Payload Telegram Login Widget (поля приходят в snake_case).
 *
 * <p>{@code hash} = hex(HMAC_SHA256(data_check_string, SHA256(bot_token))) —
 * проверяется на сервере ({@code TelegramLoginVerifier}). {@code auth_date} —
 * unix-время (сек) авторизации, проверяется на свежесть (анти-replay).
 *
 * @see <a href="https://core.telegram.org/widgets/login#checking-authorization">Telegram: checking authorization</a>
 */
@Data
public class TelegramAuthRequest {

    /** Telegram user id — используется как ключ привязки аккаунта. */
    @NotNull
    private Long id;

    @JsonProperty("first_name")
    private String firstName;

    @JsonProperty("last_name")
    private String lastName;

    private String username;

    @JsonProperty("photo_url")
    private String photoUrl;

    @NotNull
    @JsonProperty("auth_date")
    private Long authDate;

    @NotBlank
    private String hash;
}
