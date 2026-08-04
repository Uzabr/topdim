package uz.topdim.identity.service;

/**
 * Абстракция отправки уведомлений (email, SMS).
 * В тестах — мокируется. В проде — интеграция с email/SMS провайдером.
 */
public interface NotificationSender {

    /**
     * Отправить код/ссылку сброса пароля.
     * @param target email или phone
     * @param token plain text токен (не hash)
     */
    void sendPasswordResetToken(String target, String token);

    /**
     * Отправить код подтверждения email.
     * @param email email адрес
     * @param token plain text токен
     */
    void sendEmailConfirmationToken(String email, String token);

    /**
     * Отправить код подтверждения телефона.
     * @param phone номер телефона
     * @param code plain text код
     */
    void sendPhoneConfirmationCode(String phone, String code);

    /**
     * Отправить токен подтверждения при смене email.
     * Письмо уходит на НОВЫЙ адрес (proof-of-ownership), не на старый.
     * @param newEmail новый email адрес, которым пользователь ещё не владеет доказанно
     * @param token plain text токен
     */
    void sendEmailChangeToken(String newEmail, String token);
}
