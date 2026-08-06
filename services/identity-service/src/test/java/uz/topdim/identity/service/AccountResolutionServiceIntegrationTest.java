package uz.topdim.identity.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import uz.topdim.identity.entity.Role;
import uz.topdim.identity.entity.User;
import uz.topdim.identity.repository.AbstractIntegrationTest;
import uz.topdim.identity.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Интеграционный guard-тест на реальной (H2 MODE=PostgreSQL) БД.
 * Юнит-тесты с моками (AccountResolutionServiceTest) НЕ могут поймать баг flush-ordering:
 * с mock-репозиторием порядок вызовов save() ничего не говорит о порядке SQL-операций в реальной БД.
 * users.email — NOT NULL UNIQUE (V1__create_users_table.sql). В ветке resolveByGoogle
 * "email совпал, но не подтверждён" сервис сначала освобождает email у старого аккаунта,
 * затем создаёт новый аккаунт с этим же email — если освобождение (UPDATE) не долетит до БД
 * раньше вставки нового пользователя (INSERT), сработает unique-constraint.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AccountResolutionServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository users;

    private AccountResolutionService svc;

    @BeforeEach
    void setUp() {
        // PasswordEncoder не поднимается в @DataJpaTest-срезе контекста — инстанцируем сервис вручную,
        // без новых зависимостей (BCryptPasswordEncoder уже на classpath: spring-boot-starter-security).
        svc = new AccountResolutionService(users, new BCryptPasswordEncoder());
    }

    @Test
    @DisplayName("resolveByGoogle: реальная БД — освобождение чужого неподтверждённого email + создание нового аккаунта не должны нарушать unique(email) (RED без saveAndFlush, GREEN с ним)")
    void resolveByGoogle_releaseUnverifiedEmailAndCreateNew_noUniqueConstraintViolation() {
        User old = User.builder()
                .email("g@example.com")
                .password("x")
                .firstName("Old")
                .role(Role.USER)
                .enabled(true)
                .emailVerified(false)
                .build();
        old = users.saveAndFlush(old);
        Long oldId = old.getId();

        User result = svc.resolveByGoogle("google-sub-123", "g@example.com", true, "Иван", "Петров");

        // (б) новый аккаунт с доказанным email/sub + имя из Google
        assertThat(result.getId()).isNotEqualTo(oldId);
        assertThat(result.getGoogleSub()).isEqualTo("google-sub-123");
        assertThat(result.getEmail()).isEqualTo("g@example.com");
        assertThat(result.isEmailVerified()).isTrue();
        assertThat(result.getFirstName()).isEqualTo("Иван");
        assertThat(result.getLastName()).isEqualTo("Петров");

        // (в) старый аккаунт — email освобождён, сам не входит
        User reloadedOld = users.findById(oldId).orElseThrow();
        assertThat(reloadedOld.getEmail()).isEqualTo("released_" + oldId + "@topdim.uz");
        assertThat(reloadedOld.isEmailVerified()).isFalse();
    }

    @Test
    @DisplayName("resolveByPhone: реальная БД — освобождение номера у неподтверждённого сквоттера + создание нового аккаунта не должны нарушать unique(phone) (RED без saveAndFlush, GREEN с ним)")
    void resolveByPhone_releaseUnverifiedSquatterAndCreateNew_noUniqueConstraintViolation() {
        User squatter = User.builder()
                .email("squatter@example.com")
                .password("x")
                .firstName("Squatter")
                .role(Role.USER)
                .enabled(true)
                .phone("+998901112233")
                .phoneVerified(false)
                .build();
        squatter = users.saveAndFlush(squatter);
        Long squatterId = squatter.getId();

        User result = svc.resolveByPhone("+998901112233");

        // (б) новый аккаунт с доказанным (через OTP) номером
        assertThat(result.getId()).isNotEqualTo(squatterId);
        assertThat(result.getPhone()).isEqualTo("+998901112233");
        assertThat(result.isPhoneVerified()).isTrue();

        // (в) старый аккаунт (сквоттер) — номер освобождён, сам не входит в чужой аккаунт
        User reloadedSquatter = users.findById(squatterId).orElseThrow();
        assertThat(reloadedSquatter.getPhone()).isNull();
        assertThat(reloadedSquatter.isPhoneVerified()).isFalse();
    }
}
