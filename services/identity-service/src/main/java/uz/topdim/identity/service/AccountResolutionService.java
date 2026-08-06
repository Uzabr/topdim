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

    /**
     * Вход/восстановление/создание по номеру (после успешного OTP — телефон уже доказан
     * вызывающим). Если номер занят аккаунтом, где он НЕ подтверждён (кто-то указал чужой
     * номер при регистрации, но не доказал владение им) — номер там лишь заявлен, не доказан:
     * освобождаем его у сквоттера, а доказанный владелец получает его на новом аккаунте.
     * Иначе жертва OTP попала бы в чужой (сквоттера) аккаунт — account takeover.
     */
    @Transactional
    public User resolveByPhone(String phoneE164) {
        User match = users.findByPhone(phoneE164).orElse(null);
        if (match != null) {
            if (match.isPhoneVerified()) {
                // тот же человек — номер доказан на этом аккаунте
                return match;
            }
            // номер у чужого (непроверенного) аккаунта → освобождаем его.
            // saveAndFlush(!) — обязателен: Hibernate по умолчанию выполняет ВСЕ INSERT
            // в очереди действий раньше ВСЕХ UPDATE независимо от порядка вызовов save() в коде
            // (а для нового User здесь IDENTITY-генератор ID и вовсе форсирует немедленный INSERT).
            // Без явного flush UPDATE (освобождение phone у match) не долетит до БД раньше INSERT
            // нового пользователя с тем же phone → нарушение unique(phone) в реальной БД
            // (см. AccountResolutionServiceIntegrationTest — RED без этой строки, симметрично resolveByGoogle).
            match.setPhone(null);
            match.setPhoneVerified(false);
            users.saveAndFlush(match);
        }

        User u = User.builder()
                .email("phone_" + phoneE164 + "@topdim.uz")
                .password(encoder.encode(UUID.randomUUID().toString()))
                .firstName("Пользователь")
                .phone(phoneE164).phoneVerified(true)
                .role(Role.USER).enabled(true)
                .emailVerified(false)
                .build();
        return users.save(u);
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
    public User resolveByGoogle(String sub, String email, boolean googleEmailVerified,
                                String firstName, String lastName) {
        var bySub = users.findByGoogleSub(sub);
        if (bySub.isPresent()) {
            User existing = bySub.get();
            // Бэкфилл имени из Google для аккаунтов, созданных до появления этой логики
            // (или оставшихся с плейсхолдером «Пользователь»): реальные имя/фамилия — один раз.
            boolean changed = false;
            String storedFirst = existing.getFirstName();
            if ((storedFirst == null || storedFirst.isBlank() || "Пользователь".equals(storedFirst))
                    && firstName != null && !firstName.isBlank()) {
                existing.setFirstName(firstName);
                changed = true;
            }
            if ((existing.getLastName() == null || existing.getLastName().isBlank())
                    && lastName != null && !lastName.isBlank()) {
                existing.setLastName(lastName);
                changed = true;
            }
            return changed ? users.save(existing) : existing;
        }

        User match = users.findByEmailIgnoreCase(email).orElse(null);
        if (match != null) {
            if (match.isEmailVerified()) {
                // тот же человек — email доказан на этом аккаунте
                match.setGoogleSub(sub);
                return users.save(match);
            }
            // email у чужого аккаунта не доказан → освобождаем его.
            // saveAndFlush(!) — обязателен: Hibernate по умолчанию выполняет ВСЕ INSERT
            // в очереди действий раньше ВСЕХ UPDATE независимо от порядка вызовов save() в коде
            // (а для нового User здесь IDENTITY-генератор ID и вовсе форсирует немедленный INSERT).
            // Без явного flush UPDATE (освобождение email у match) не долетит до БД раньше INSERT
            // нового пользователя с тем же email → нарушение unique(email) в реальной БД
            // (см. интеграционный тест AccountResolutionServiceIntegrationTest — RED без этой строки).
            match.setEmail("released_" + match.getId() + "@topdim.uz");
            match.setEmailVerified(false);
            users.saveAndFlush(match);
        }

        User u = User.builder()
                // verified берём из провайдера (googleEmailVerified), а не хардкодим true:
                // консервативно-безопаснее и эквивалентно на практике (Google почти всегда verified).
                .email(email).emailVerified(googleEmailVerified)
                .password(encoder.encode(UUID.randomUUID().toString()))
                .firstName(firstName != null && !firstName.isBlank() ? firstName : "Пользователь")
                .lastName(lastName != null && !lastName.isBlank() ? lastName : null)
                .googleSub(sub)
                .role(Role.USER).enabled(true)
                .build();
        return users.save(u);
    }
}
