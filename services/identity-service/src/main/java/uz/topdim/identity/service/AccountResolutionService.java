package uz.topdim.identity.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.topdim.identity.entity.Role;
import uz.topdim.identity.entity.User;
import uz.topdim.identity.repository.UserRepository;

import java.util.UUID;

/**
 * Централизованное разрешение аккаунта (подсистема #1 «Идентичность и аккаунт»).
 * Реализует «Алгоритм разрешения входа» из спеки: найти по идентификатору → вход/восстановление;
 * иначе, если метод доказал владение — привязать к совпавшему по другой колонке аккаунту;
 * иначе — создать новый. Привязка из авторизованной сессии на занятый идентификатор — отказ без слияния.
 */
@Service
@RequiredArgsConstructor
public class AccountResolutionService {

    private final UserRepository users;
    private final PasswordEncoder encoder;

    /** Вход/восстановление/создание по номеру (после успешного OTP — телефон уже доказан). */
    @Transactional
    public User resolveByPhone(String phoneE164) {
        return users.findByPhone(phoneE164).orElseGet(() -> {
            User u = User.builder()
                    .email("phone_" + phoneE164 + "@topdim.uz")
                    .password(encoder.encode(UUID.randomUUID().toString()))
                    .firstName("Пользователь")
                    .phone(phoneE164).phoneVerified(true)
                    .role(Role.USER).enabled(true)
                    .emailVerified(false)
                    .build();
            return users.save(u);
        });
    }

    /** Привязка номера к текущему (залогиненному) аккаунту; занят другим → отказ (без слияния). */
    @Transactional
    public User linkPhone(User current, String phoneE164) {
        users.findByPhone(phoneE164).ifPresent(owner -> {
            if (!owner.getId().equals(current.getId())) {
                throw new IllegalStateException("Номер уже занят");
            }
        });
        current.setPhone(phoneE164);
        current.setPhoneVerified(true);
        return users.save(current);
    }

    /**
     * Google-вход: по {@code google_sub} → по подтверждённому email (привязать sub) → новый.
     * Если email совпал с аккаунтом, где он НЕ подтверждён — email там лишь заявлен, не доказан:
     * освобождаем его у старого аккаунта, а доказанный Google-владелец получает его на новом аккаунте.
     */
    @Transactional
    public User resolveByGoogle(String sub, String email, boolean googleEmailVerified) {
        var bySub = users.findByGoogleSub(sub);
        if (bySub.isPresent()) {
            return bySub.get();
        }

        User match = users.findByEmailIgnoreCase(email).orElse(null);
        if (match != null) {
            if (match.isEmailVerified()) {
                // тот же человек — email доказан на этом аккаунте
                match.setGoogleSub(sub);
                return users.save(match);
            }
            // email у чужого аккаунта не доказан → освобождаем его
            match.setEmail("released_" + match.getId() + "@topdim.uz");
            match.setEmailVerified(false);
            users.save(match);
        }

        User u = User.builder()
                .email(email).emailVerified(googleEmailVerified)
                .password(encoder.encode(UUID.randomUUID().toString()))
                .firstName("Пользователь").googleSub(sub)
                .role(Role.USER).enabled(true)
                .build();
        return users.save(u);
    }
}
