# Account Identity (подсистема #1) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Реализовать backend модели идентичности topdim: единый аккаунт с объединением входов по доказанному владению, телефон-OTP как вход (замена небезопасного `auth/guest`), Google-вход, уникальность идентификаторов, вход=восстановление, вычисляемый L1.

**Architecture:** Централизуем разрешение аккаунта в `AccountResolutionService` (find-by-identifier → вход/recovery / link / collision). Внешние зависимости абстрагируем интерфейсами (`SmsSender` для OTP, `GoogleTokenVerifier`) со стабами — реальные Eskiz/Google подключаются позже (#3) без изменения логики. L1 — вычисляемое (`TrustService`).

**Tech Stack:** Java 21, Spring Boot, Spring Security, Spring Data JPA (PostgreSQL, Flyway), Spring Data Redis (OTP), JUnit 5 + Mockito + AssertJ.

## Global Constraints
- Backend-only: `services/identity-service`, `infrastructure/api-gateway`. НЕ трогать `frontend/*`.
- Формат ответа: `uz.topdim.common.dto.ApiResponse`. Ошибки: `AuthException`→401, `IllegalStateException`→409 (см. `GlobalExceptionHandler`).
- Текущий пользователь в контроллерах: `@RequestHeader("X-User-Id") Long userId`.
- Следующая Flyway-миграция identity: **V14** (сверить `origin/main` перед созданием).
- Существующее переиспользовать: `PasswordResetService.sha256(...)` (public static), `AuthService.buildAuthResponse`/`createRefreshToken`, `TelegramLoginVerifier` (образец абстракции), `AuthResponse` DTO, `UserRepository`.
- Каждый PR — сфокусированный срез (1-2 задачи), ревью Claude перед мержем.
- Тесты гонять: `./gradlew :services:identity-service:test` (и `:infrastructure:api-gateway:test` для gateway-задач).

## File Structure
- `entity/User.java` — +`googleSub`, +`paidAt` (факт оплаты; L1-вычисление).
- `db/migration/V14__account_identifiers.sql` — `google_sub` UNIQUE, `paid_at`.
- `repository/UserRepository.java` — +`findByGoogleSub`.
- `service/TrustService.java` — `computeTrustLevel(user)` из `phone_verified || paidAt`.
- `service/AccountResolutionService.java` — вход/recovery/link/collision + уникальность.
- `service/OtpService.java` — Redis: генерация/проверка OTP, rate-limit.
- `security/SmsSender.java` (интерфейс) + `LoggingSmsSender.java` (стаб) — реальный Eskiz в #3.
- `security/GoogleTokenVerifier.java` (интерфейс) + `GoogleTokenVerifierImpl`/стаб — проверка Google ID-token.
- `dto/PhoneOtpRequest.java`, `dto/PhoneOtpConfirmRequest.java`, `dto/GoogleAuthRequest.java`.
- `controller/AuthController.java` — +`/auth/phone/request`, `/auth/phone/confirm`, `/auth/google`; депрекация `/auth/guest`.
- `controller/InternalUserController.java` — +`POST /internal/users/{id}/mark-paid` (сигнал payment→identity).
- `services/payment-service/.../PaymentEventListener` или сервис — вызов identity `mark-paid` на `PaymentCompleted` (последняя задача).
- `infrastructure/api-gateway`: `JwtAuthenticationFilter` OPEN_ENDPOINTS + `application.yml` routes/rate-limit для новых публичных путей.

---

### Task 1: Модель данных — `google_sub` + `paid_at`

**Files:**
- Create: `services/identity-service/src/main/resources/db/migration/V14__account_identifiers.sql`
- Modify: `services/identity-service/src/main/java/uz/topdim/identity/entity/User.java`
- Test: `services/identity-service/src/test/java/uz/topdim/identity/repository/UserRepositoryTest.java` (создать, @DataJpaTest)

**Interfaces — Produces:** `User.getGoogleSub()/setGoogleSub(String)`, `User.getPaidAt()/setPaidAt(LocalDateTime)`; `UserRepository.findByGoogleSub(String)`.

- [ ] **Step 1: Миграция V14**
```sql
-- Идентификатор Google-аккаунта + факт первой оплаты (для вычисляемого L1).
ALTER TABLE users
    ADD COLUMN google_sub VARCHAR(64),
    ADD COLUMN paid_at    TIMESTAMP;
ALTER TABLE users ADD CONSTRAINT uq_users_google_sub UNIQUE (google_sub);
CREATE INDEX idx_users_google_sub ON users (google_sub);
```

- [ ] **Step 2: Поля в `User.java`** (добавить рядом с telegram-полями)
```java
    @Column(name = "google_sub", unique = true)
    private String googleSub;

    /** Время первой успешной оплаты. Не null → «была оплата» (навсегда), вклад в L1. */
    @Column(name = "paid_at")
    private LocalDateTime paidAt;
```

- [ ] **Step 3: Репозиторий-метод** в `UserRepository.java`
```java
    Optional<User> findByGoogleSub(String googleSub);
```

- [ ] **Step 4: Failing test** `UserRepositoryTest`
```java
@DataJpaTest
class UserRepositoryTest {
    @Autowired UserRepository repo;

    @Test
    void findByGoogleSub_returnsUser() {
        User u = User.builder().email("g@topdim.uz").password("x").firstName("G")
                .role(Role.USER).enabled(true).googleSub("google-123").build();
        repo.save(u);
        assertThat(repo.findByGoogleSub("google-123")).isPresent();
        assertThat(repo.findByGoogleSub("nope")).isEmpty();
    }
}
```

- [ ] **Step 5: Run → fail, then pass**
Run: `./gradlew :services:identity-service:test --tests "*UserRepositoryTest"`
Expected: сначала FAIL (нет метода/колонки) → после Step 1-3 PASS.

- [ ] **Step 6: Commit**
```bash
git add services/identity-service/src/main/resources/db/migration/V14__account_identifiers.sql \
        services/identity-service/src/main/java/uz/topdim/identity/entity/User.java \
        services/identity-service/src/main/java/uz/topdim/identity/repository/UserRepository.java \
        services/identity-service/src/test/java/uz/topdim/identity/repository/UserRepositoryTest.java
git commit -m "feat(identity): google_sub + paid_at на users (миграция V14)"
```

---

### Task 2: `TrustService` — вычисляемый L1

**Files:**
- Create: `services/identity-service/src/main/java/uz/topdim/identity/service/TrustService.java`
- Test: `services/identity-service/src/test/java/uz/topdim/identity/service/TrustServiceTest.java`

**Interfaces — Consumes:** `User.isPhoneVerified()`, `User.getPaidAt()`. **Produces:** `TrustLevel TrustService.computeTrustLevel(User)`.

- [ ] **Step 1: Failing test**
```java
class TrustServiceTest {
    private final TrustService svc = new TrustService();
    private User u(boolean phoneVerified, boolean paid) {
        return User.builder().phoneVerified(phoneVerified)
                .paidAt(paid ? java.time.LocalDateTime.now() : null).build();
    }
    @Test void l0_whenNoProof() { assertThat(svc.computeTrustLevel(u(false,false))).isEqualTo(TrustLevel.L0); }
    @Test void l1_whenPhoneVerified() { assertThat(svc.computeTrustLevel(u(true,false))).isEqualTo(TrustLevel.L1); }
    @Test void l1_whenPaid() { assertThat(svc.computeTrustLevel(u(false,true))).isEqualTo(TrustLevel.L1); }
}
```

- [ ] **Step 2: Run → fail** (`TrustService` не существует).

- [ ] **Step 3: Реализация**
```java
package uz.topdim.identity.service;

import org.springframework.stereotype.Service;
import uz.topdim.identity.entity.TrustLevel;
import uz.topdim.identity.entity.User;

/** L1 — вычисляемое состояние: телефон подтверждён ИЛИ была оплата. Не полагаемся на хранимый trust_level. */
@Service
public class TrustService {
    public TrustLevel computeTrustLevel(User user) {
        boolean verifiedPhone = user.isPhoneVerified();
        boolean everPaid = user.getPaidAt() != null;
        return (verifiedPhone || everPaid) ? TrustLevel.L1 : TrustLevel.L0;
    }
}
```

- [ ] **Step 4: Run → pass.** `./gradlew :services:identity-service:test --tests "*TrustServiceTest"`

- [ ] **Step 5: Использовать в ответе** — в `AuthService.buildAuthResponse` и профиле проставлять `trustLevel = trustService.computeTrustLevel(user)` (внедрить `TrustService`), НЕ читать хранимую колонку. (Колонку `trust_level` оставляем как deprecated — не источник правды.)

- [ ] **Step 6: Commit**
```bash
git add services/identity-service/src/main/java/uz/topdim/identity/service/TrustService.java \
        services/identity-service/src/test/java/uz/topdim/identity/service/TrustServiceTest.java \
        services/identity-service/src/main/java/uz/topdim/identity/service/AuthService.java
git commit -m "feat(identity): вычисляемый L1 (TrustService: phone_verified||paid)"
```

---

### Task 3: `AccountResolutionService` — find-or-create / link / collision

**Files:**
- Create: `services/identity-service/src/main/java/uz/topdim/identity/service/AccountResolutionService.java`
- Test: `services/identity-service/src/test/java/uz/topdim/identity/service/AccountResolutionServiceTest.java`

**Interfaces — Consumes:** `UserRepository` (findByPhone/findByEmailIgnoreCase/findByGoogleSub/save), `PasswordEncoder`. **Produces:**
- `User resolveByPhone(String phoneE164)` — вход/recovery/создание.
- `User linkPhone(User current, String phoneE164)` — привязка, коллизия→`IllegalStateException("Номер уже занят")`.
- `User resolveByGoogle(String sub, String email, boolean googleEmailVerified)` — вход/link/создание по правилам спека.

- [ ] **Step 1: Failing tests** (Mockito)
```java
@ExtendWith(MockitoExtension.class)
class AccountResolutionServiceTest {
    @Mock UserRepository users; @Mock PasswordEncoder encoder;
    @InjectMocks AccountResolutionService svc;

    @Test void resolveByPhone_existing_returnsSame() {
        User a = User.builder().id(1L).phone("+998901112233").phoneVerified(true).build();
        when(users.findByPhone("+998901112233")).thenReturn(Optional.of(a));
        assertThat(svc.resolveByPhone("+998901112233").getId()).isEqualTo(1L);
        verify(users, never()).save(any());
    }
    @Test void resolveByPhone_new_createsVerified() {
        when(users.findByPhone("+998901112233")).thenReturn(Optional.empty());
        when(encoder.encode(anyString())).thenReturn("h");
        when(users.save(any(User.class))).thenAnswer(i -> { User u=i.getArgument(0); u.setId(9L); return u; });
        User r = svc.resolveByPhone("+998901112233");
        assertThat(r.isPhoneVerified()).isTrue();
        assertThat(r.getRole()).isEqualTo(Role.USER);
    }
    @Test void linkPhone_takenByOther_throws() {
        User current = User.builder().id(1L).build();
        when(users.findByPhone("+998901112233")).thenReturn(Optional.of(User.builder().id(2L).build()));
        assertThatThrownBy(() -> svc.linkPhone(current, "+998901112233"))
            .isInstanceOf(IllegalStateException.class);
    }
    @Test void resolveByGoogle_matchVerifiedEmail_linksSub() {
        User a = User.builder().id(1L).email("g@topdim.uz").emailVerified(true).build();
        when(users.findByGoogleSub("s")).thenReturn(Optional.empty());
        when(users.findByEmailIgnoreCase("g@topdim.uz")).thenReturn(Optional.of(a));
        when(users.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        User r = svc.resolveByGoogle("s", "g@topdim.uz", true);
        assertThat(r.getId()).isEqualTo(1L);
        assertThat(r.getGoogleSub()).isEqualTo("s");
    }
}
```

- [ ] **Step 2: Run → fail.**

- [ ] **Step 3: Реализация** (правила из спека §«Алгоритм разрешения входа»)
```java
package uz.topdim.identity.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.topdim.identity.entity.Role;
import uz.topdim.identity.entity.User;
import uz.topdim.identity.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountResolutionService {
    private final UserRepository users;
    private final PasswordEncoder encoder;

    /** Вход/восстановление/создание по номеру (после успешного OTP). */
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

    /** Привязка номера к текущему аккаунту; занят другим → отказ (без слияния). */
    @Transactional
    public User linkPhone(User current, String phoneE164) {
        users.findByPhone(phoneE164).ifPresent(owner -> {
            if (!owner.getId().equals(current.getId()))
                throw new IllegalStateException("Номер уже занят");
        });
        current.setPhone(phoneE164);
        current.setPhoneVerified(true);
        return users.save(current);
    }

    /** Google-вход: по sub → по verified email → новый; неподтверждённый чужой email — освобождаем. */
    @Transactional
    public User resolveByGoogle(String sub, String email, boolean googleEmailVerified) {
        var bySub = users.findByGoogleSub(sub);
        if (bySub.isPresent()) return bySub.get();

        User match = users.findByEmailIgnoreCase(email).orElse(null);
        if (match != null) {
            if (match.isEmailVerified()) {           // тот же человек
                match.setGoogleSub(sub);
                return users.save(match);
            }
            // email у чужого аккаунта не доказан → освобождаем его, Google-владелец забирает email
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
```

- [ ] **Step 4: Run → pass.** `./gradlew :services:identity-service:test --tests "*AccountResolutionServiceTest"`

- [ ] **Step 5: Commit**
```bash
git add services/identity-service/src/main/java/uz/topdim/identity/service/AccountResolutionService.java \
        services/identity-service/src/test/java/uz/topdim/identity/service/AccountResolutionServiceTest.java
git commit -m "feat(identity): AccountResolutionService (вход/recovery/link/collision)"
```

---

### Task 4: OTP — `SmsSender` (стаб) + `OtpService` (Redis)

**Files:**
- Create: `security/SmsSender.java`, `security/LoggingSmsSender.java`, `service/OtpService.java`
- Test: `service/OtpServiceTest.java`

**Interfaces — Produces:** `void SmsSender.sendOtp(String phone, String code)`; `void OtpService.requestOtp(String phone)`; `boolean OtpService.verifyOtp(String phone, String code)`.

> ⚠️ Реальная отправка (Eskiz) — подсистема #3. Здесь `LoggingSmsSender` логирует код (stub). OtpService хранит **хэш** кода в Redis.

- [ ] **Step 1: Интерфейс + стаб**
```java
// security/SmsSender.java
package uz.topdim.identity.security;
public interface SmsSender { void sendOtp(String phone, String code); }
```
```java
// security/LoggingSmsSender.java
package uz.topdim.identity.security;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
@Slf4j @Component
public class LoggingSmsSender implements SmsSender {
    @Override public void sendOtp(String phone, String code) {
        log.info("[SMS STUB] OTP для {} = {}", phone, code); // Eskiz заменит в #3
    }
}
```

- [ ] **Step 2: Failing test** `OtpServiceTest` (с фейковым StringRedisTemplate через Mockito или встроенный map — используем Mockito на `StringRedisTemplate.opsForValue()`)
```java
@ExtendWith(MockitoExtension.class)
class OtpServiceTest {
    @Mock StringRedisTemplate redis; @Mock org.springframework.data.redis.core.ValueOperations<String,String> ops;
    @Mock SmsSender sms;
    OtpService svc;
    @BeforeEach void init(){ svc = new OtpService(redis, sms); when(redis.opsForValue()).thenReturn(ops); }

    @Test void request_generatesAndSends() {
        svc.requestOtp("+998901112233");
        verify(sms).sendOtp(eq("+998901112233"), anyString());
        verify(ops).set(startsWith("otp:"), anyString(), any());
    }
    @Test void verify_wrongCode_false() {
        when(ops.get("otp:+998901112233")).thenReturn(PasswordResetService.sha256("111111"));
        assertThat(svc.verifyOtp("+998901112233","000000")).isFalse();
    }
    @Test void verify_correct_true_andClears() {
        when(ops.get("otp:+998901112233")).thenReturn(PasswordResetService.sha256("111111"));
        assertThat(svc.verifyOtp("+998901112233","111111")).isTrue();
        verify(redis).delete("otp:+998901112233");
    }
}
```

- [ ] **Step 3: Реализация `OtpService`**
```java
package uz.topdim.identity.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import uz.topdim.identity.security.SmsSender;

import java.security.SecureRandom;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class OtpService {
    private final StringRedisTemplate redis;
    private final SmsSender smsSender;
    private static final SecureRandom RND = new SecureRandom();
    private static final Duration TTL = Duration.ofMinutes(5);

    public void requestOtp(String phone) {
        String code = String.format("%06d", RND.nextInt(1_000_000));
        redis.opsForValue().set("otp:" + phone, PasswordResetService.sha256(code), TTL);
        smsSender.sendOtp(phone, code);
    }

    public boolean verifyOtp(String phone, String code) {
        String stored = redis.opsForValue().get("otp:" + phone);
        if (stored == null) return false;
        boolean ok = stored.equals(PasswordResetService.sha256(code));
        if (ok) redis.delete("otp:" + phone);
        return ok;
    }
}
```
> Примечание: rate-limit на запрос OTP реализуется на gateway (Task 8, RequestRateLimiter на `/auth/phone/request`) — как у остальных auth-роутов. Дополнительный per-phone счётчик попыток можно добавить позже, вне MVP.

- [ ] **Step 4: Run → pass.** `./gradlew :services:identity-service:test --tests "*OtpServiceTest"`

- [ ] **Step 5: Commit**
```bash
git add services/identity-service/src/main/java/uz/topdim/identity/security/SmsSender.java \
        services/identity-service/src/main/java/uz/topdim/identity/security/LoggingSmsSender.java \
        services/identity-service/src/main/java/uz/topdim/identity/service/OtpService.java \
        services/identity-service/src/test/java/uz/topdim/identity/service/OtpServiceTest.java
git commit -m "feat(identity): OTP-сервис (Redis) + SmsSender-стаб (Eskiz в #3)"
```

---

### Task 5: Телефон-OTP как ВХОД + депрекация `auth/guest`

**Files:**
- Create: `dto/PhoneOtpRequest.java` (`@NotBlank @Pattern("^\\+998\\d{9}$") String phone`), `dto/PhoneOtpConfirmRequest.java` (`phone` + `@NotBlank String code`)
- Modify: `controller/AuthController.java` (+`/auth/phone/request`, `/auth/phone/confirm`; убрать `/auth/guest` — 410 Gone или удалить), `service/AuthService.java` (метод `phoneAuth`), `config/SecurityConfig.java` (открыть новые пути, убрать guest)
- Test: `service/AuthServiceTest.java` (+ кейсы phoneAuth), `controller/AuthControllerSecurityTest.java` (guest→410)

**Interfaces — Consumes:** `OtpService`, `AccountResolutionService`, `buildAuthResponse`. **Produces:** `AuthResponse AuthService.phoneAuth(String phone, String code)`.

- [ ] **Step 1: Failing test** (AuthServiceTest — добавить мок `OtpService`, `AccountResolutionService`)
```java
@Test
void phoneAuth_validOtp_returnsTokens() {
    when(otpService.verifyOtp("+998901112233","111111")).thenReturn(true);
    User u = User.builder().id(7L).phone("+998901112233").phoneVerified(true)
            .role(Role.USER).enabled(true).email("phone_x@topdim.uz").build();
    when(accountResolutionService.resolveByPhone("+998901112233")).thenReturn(u);
    when(jwtService.generateAccessToken(any())).thenReturn("access");
    when(jwtService.getAccessTokenExpiration()).thenReturn(900_000L);
    when(jwtService.getRefreshTokenExpiration()).thenReturn(604_800_000L);
    when(refreshTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    AuthResponse r = authService.phoneAuth("+998901112233","111111");
    assertThat(r.getAccessToken()).isEqualTo("access");
}
@Test
void phoneAuth_badOtp_throws() {
    when(otpService.verifyOtp(anyString(), anyString())).thenReturn(false);
    assertThatThrownBy(() -> authService.phoneAuth("+998901112233","000000"))
        .isInstanceOf(AuthException.class);
}
```

- [ ] **Step 2: Run → fail.**

- [ ] **Step 3: `AuthService.phoneAuth`** (+внедрить `otpService`, `accountResolutionService`)
```java
@Transactional
public AuthResponse phoneAuth(String phone, String code) {
    if (!otpService.verifyOtp(phone, code)) {
        throw new AuthException("Неверный или просроченный код");
    }
    User user = accountResolutionService.resolveByPhone(phone);
    if (!user.isEnabled() || user.isDeleted()) throw new AuthException("Аккаунт недоступен");
    refreshTokenRepository.revokeAllByUser(user);   // консистентно с login
    return buildAuthResponse(user);
}
```

- [ ] **Step 4: Контроллер** — добавить эндпоинты, удалить guest
```java
    @PostMapping("/phone/request")
    public ResponseEntity<ApiResponse<Void>> phoneOtpRequest(@Valid @RequestBody PhoneOtpRequest req) {
        otpService.requestOtp(req.getPhone());          // всегда 202-подобно, не раскрываем существование
        return ResponseEntity.accepted().body(ApiResponse.success("Код отправлен", null));
    }

    @PostMapping("/phone/confirm")
    public ResponseEntity<ApiResponse<AuthResponse>> phoneOtpConfirm(
            @Valid @RequestBody PhoneOtpConfirmRequest req, HttpServletResponse response) {
        AuthResponse auth = authService.phoneAuth(req.getPhone(), req.getCode());
        addRefreshTokenCookie(response, auth.getRefreshToken());
        auth.setRefreshToken(null);
        return ResponseEntity.ok(ApiResponse.success("Вход выполнен", auth));
    }
```
Удалить метод `guestAuth` из контроллера (или вернуть `410 GONE`); удалить `AuthService.guestAuth`; убрать `/api/v1/auth/guest` из `SecurityConfig` permitAll и из gateway OPEN_ENDPOINTS/route (Task 8). Добавить в `SecurityConfig` permitAll: `/api/v1/auth/phone/request`, `/api/v1/auth/phone/confirm`.

- [ ] **Step 5: Run → pass** (identity тесты). Обновить/удалить существующие guest-тесты в `AuthServiceTest`.

- [ ] **Step 6: Commit**
```bash
git add -A services/identity-service
git commit -m "feat(identity): телефон-OTP вход; депрекация небезопасного auth/guest"
```

---

### Task 6: Google-вход — `GoogleTokenVerifier` + `/auth/google`

**Files:**
- Create: `security/GoogleTokenVerifier.java` (интерфейс) + `security/GoogleTokenVerifierImpl.java`, `dto/GoogleAuthRequest.java` (`@NotBlank String idToken`), `dto/GoogleIdentity.java` (record: `sub`, `email`, `emailVerified`)
- Modify: `service/AuthService.java` (+`googleAuth`), `controller/AuthController.java` (+`/auth/google`), `config/SecurityConfig.java` (permitAll)
- Test: `service/AuthServiceTest.java` (+googleAuth)

> ⚠️ Проверка Google ID-token требует библиотеки `com.google.api-client:google-api-client` (или ручной JWKS). **Новая зависимость → согласовать по регламенту.** Интерфейс `GoogleTokenVerifier` позволяет тестировать логику через мок сейчас; `GoogleTokenVerifierImpl` (с libs + `google.client-id` в конфиге) — отдельный шаг, включается когда добавят зависимость.

**Interfaces — Produces:** `GoogleIdentity GoogleTokenVerifier.verify(String idToken)` (бросает `AuthException` при невалидности); `AuthResponse AuthService.googleAuth(String idToken)`.

- [ ] **Step 1: DTO/record + интерфейс**
```java
// dto/GoogleIdentity.java
package uz.topdim.identity.dto;
public record GoogleIdentity(String sub, String email, boolean emailVerified) {}
```
```java
// security/GoogleTokenVerifier.java
package uz.topdim.identity.security;
import uz.topdim.identity.dto.GoogleIdentity;
public interface GoogleTokenVerifier { GoogleIdentity verify(String idToken); }
```

- [ ] **Step 2: Failing test** (AuthServiceTest, мок `GoogleTokenVerifier` + `AccountResolutionService`)
```java
@Test
void googleAuth_validToken_returnsTokens() {
    when(googleTokenVerifier.verify("tok")).thenReturn(new GoogleIdentity("sub1","g@x.uz",true));
    User u = User.builder().id(5L).email("g@x.uz").emailVerified(true).googleSub("sub1")
            .role(Role.USER).enabled(true).build();
    when(accountResolutionService.resolveByGoogle("sub1","g@x.uz",true)).thenReturn(u);
    when(jwtService.generateAccessToken(any())).thenReturn("access");
    when(jwtService.getAccessTokenExpiration()).thenReturn(900_000L);
    when(jwtService.getRefreshTokenExpiration()).thenReturn(604_800_000L);
    when(refreshTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    assertThat(authService.googleAuth("tok").getAccessToken()).isEqualTo("access");
}
```

- [ ] **Step 3: `AuthService.googleAuth`**
```java
@Transactional
public AuthResponse googleAuth(String idToken) {
    GoogleIdentity g = googleTokenVerifier.verify(idToken);   // бросит AuthException если невалиден
    User user = accountResolutionService.resolveByGoogle(g.sub(), g.email(), g.emailVerified());
    if (!user.isEnabled() || user.isDeleted()) throw new AuthException("Аккаунт недоступен");
    return buildAuthResponse(user);
}
```

- [ ] **Step 4: Контроллер** `/auth/google` (по образцу `/auth/telegram`: cookie для refresh) + `SecurityConfig` permitAll `/api/v1/auth/google`.

- [ ] **Step 5: Run → pass** (логика через мок). `GoogleTokenVerifierImpl` (реальная проверка) — оставить `@ConditionalOnProperty("google.client-id")`, стаб бросающий «не настроено» при отсутствии — как `TelegramLoginVerifier`.

- [ ] **Step 6: Commit**
```bash
git add -A services/identity-service
git commit -m "feat(identity): Google-вход (/auth/google) через AccountResolutionService"
```

---

### Task 7: Сигнал оплаты payment→identity (`paid_at` → L1)

**Files:**
- Create: `controller/InternalUserController.java` (или расширить существующий internal-контроллер): `POST /api/v1/internal/users/{userId}/mark-paid`
- Modify: `service/UserService.java` (метод `markPaid(userId)` — идемпотентно ставит `paidAt` если null)
- Modify (payment-service): `PaymentEventListener`/сервис — на `PaymentCompletedEvent` вызвать identity `mark-paid` (Feign-клиент с `X-Gateway-Auth` через существующий `FeignInternalAuthConfig`)
- Test: `service/UserServiceTest.java` (+markPaid идемпотентность)

**Interfaces — Produces:** `void UserService.markPaid(Long userId)` (idempotent); internal endpoint `POST /internal/users/{id}/mark-paid`.

- [ ] **Step 1: Failing test** (UserServiceTest)
```java
@Test void markPaid_setsPaidAtOnce() {
    User u = User.builder().id(3L).build();
    when(userRepository.findById(3L)).thenReturn(Optional.of(u));
    userService.markPaid(3L);
    assertThat(u.getPaidAt()).isNotNull();
    var first = u.getPaidAt();
    userService.markPaid(3L);              // идемпотентно — не перезатирает
    assertThat(u.getPaidAt()).isEqualTo(first);
}
```

- [ ] **Step 2-4: Реализация + endpoint + run→pass**
```java
// UserService
@Transactional
public void markPaid(Long userId) {
    userRepository.findById(userId).ifPresent(u -> {
        if (u.getPaidAt() == null) { u.setPaidAt(java.time.LocalDateTime.now()); userRepository.save(u); }
    });
}
```
```java
// InternalUserController (permitAll на /internal/**, защищён X-Gateway-Auth фильтром)
@PostMapping("/api/v1/internal/users/{userId}/mark-paid")
public ResponseEntity<ApiResponse<Void>> markPaid(@PathVariable Long userId) {
    userService.markPaid(userId);
    return ResponseEntity.ok(ApiResponse.success(null));
}
```

- [ ] **Step 5: payment-service вызыватель** — в обработчике `PaymentCompletedEvent` дёрнуть Feign `IdentityClient.markPaid(userId)` (`@FeignClient(name="identity-service", path="/api/v1")`, использует существующий `FeignInternalAuthConfig` → шлёт `X-Gateway-Auth`). Тест: verify вызов при событии.

- [ ] **Step 6: Commit** (два коммита — identity, затем payment)
```bash
git commit -am "feat(identity): mark-paid internal endpoint (payment→L1)"
```

---

### Task 8: Gateway — маршруты и rate-limit новых auth-путей

**Files:**
- Modify: `infrastructure/api-gateway/.../filter/JwtAuthenticationFilter.java` (OPEN_ENDPOINTS: +`/api/v1/auth/phone/request`, `/api/v1/auth/phone/confirm`, `/api/v1/auth/google`; убрать `/api/v1/auth/guest`)
- Modify: `infrastructure/api-gateway/src/main/resources/application.yml` (routes+RequestRateLimiter для новых путей; убрать `auth-guest`)
- Test: `infrastructure/api-gateway/.../JwtAuthenticationFilterTest.java` (новые пути открыты; guest больше не открыт)

- [ ] **Step 1: Failing test** — по образцу существующих (`situations`/`telegram`): `/api/v1/auth/phone/confirm` без токена → пропускается (open); `/api/v1/auth/guest` → НЕ open.
- [ ] **Step 2: OPEN_ENDPOINTS** — добавить 3 пути, удалить guest.
- [ ] **Step 3: application.yml** — блоки rate-limit (phone/request строго 1 r/s burst 2; phone/confirm 2 r/s burst 3; google 2 r/s burst 3), выше catch-all; удалить `auth-guest`.
- [ ] **Step 4: Run → pass** `./gradlew :infrastructure:api-gateway:test`
- [ ] **Step 5: Commit**
```bash
git add -A infrastructure/api-gateway
git commit -m "feat(gateway): маршруты+rate-limit для phone/google входа; убрать guest"
```

---

## Self-Review (выполнено автором плана)
- **Покрытие спека:** модель данных (T1) · L1 вычисляемый (T2) · алгоритм разрешения/recovery/collision/uniqueness (T3) · телефон-OTP вход + депрекация guest (T4-T5) · Google-вход с правилами verified-email (T6) · has_paid→L1 сигнал (T7) · публичные маршруты+лимиты (T8). Верификация из спека покрыта тестами T3/T5/T6 + ручными сценариями.
- **Плейсхолдеры:** внешние интеграции (Eskiz, Google-verify, google-api-client dep) явно абстрагированы интерфейсами со стабами — это не TODO, а границы подсистемы (#3 + согласование зависимости). Логика тестируема сейчас через моки.
- **Типы согласованы:** `resolveByPhone/linkPhone/resolveByGoogle`, `computeTrustLevel`, `requestOtp/verifyOtp`, `phoneAuth/googleAuth`, `markPaid`, `GoogleIdentity(sub,email,emailVerified)` — используются одинаково в задачах-потребителях.

## Открытый пункт (требует согласования по регламенту)
- **T6:** зависимость `google-api-client` (или ручной JWKS) для реальной проверки Google-token. Логика/тесты не блокируются (мок), но live-Google требует добавить lib + `google.client-id` — согласовать перед реализацией `GoogleTokenVerifierImpl`.
