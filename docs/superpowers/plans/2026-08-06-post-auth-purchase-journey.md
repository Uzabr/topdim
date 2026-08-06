# Post-Auth Purchase Journey (Этап 1) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Довести пост-авторизационный путь покупки до вменяемого состояния: корзинная модель с видимой гостевой корзиной, возврат к намерению после входа, обязательный подтверждённый телефон на оформлении (email — не обязателен), скрытие синтетического email, точка «Добавить email» как канал.

**Architecture:** Небольшие backend-правки (identity: производный флаг `emailPlaceholder` в профиль/auth-DTO; order: email становится необязательным, телефон обязателен) + frontend-правки web-app (корзина/checkout/логин/профиль/успех). Backend-задачи идут первыми — фронт их потребляет.

**Tech Stack:** Java 21 / Spring Boot (identity-service, order-service), JUnit5 + Mockito + H2; React 19 + TypeScript + Zustand + react-router + vitest/RTL (frontend/web-app).

## Global Constraints

- Спек: `docs/superpowers/specs/2026-08-06-post-auth-purchase-journey-design.md`. Реализуем **только Этап 1**.
- **Вне скоупа (Этап 2):** реальная доставка купона в Telegram/email; «Подключить Telegram» (net-new: нет UI/поля/эндпоинта — переносится в Этап 2 к доставке); перки по trustLevel.
- Синтетический email = `!emailVerified && email ~ ^(tg_|phone_|released_).*@topdim\.uz$`. Сохранённый `email` НЕ трогаем (JWT-claim `JwtService.java:54`, уникальность, login-by-email целы) — прячем только в презентации.
- Телефон-формат везде `^\+998\d{9}$` (как в `PhoneOtpConfirmRequest`).
- Локали **ru + uz** для каждой новой строки.
- Ветки от свежего `main`; PR + squash; backend-тесты H2+Mockito, фронт — vitest/RTL. Каждая задача — один focused PR.
- Терминология после #88: «купон» (не «товар»/«акция»), бренд «sizbiz».

---

## File Structure

**Backend (identity-service):**
- Create `services/identity-service/src/main/java/uz/topdim/identity/util/EmailPlaceholders.java` — единственная ответственность: детект синтетического email.
- Modify `dto/UserProfileResponse.java`, `dto/AuthResponse.java` (UserDto), `service/UserService.java` (mapToProfile), `service/AuthService.java` (buildAuthResponse) — прокинуть флаг.

**Backend (order-service):**
- Modify `dto/CreateOrderRequest.java` — email опционален, телефон обязателен+формат.

**Frontend (web-app):**
- Modify `api/auth.ts` (UserDto тип +emailPlaceholder), `store/authStore.ts` (без навигации, как есть),
  `components/auth/LoginCard.tsx` + `pages/LoginPage.tsx` + `pages/RegisterPage.tsx` + `components/auth/LoginModal.tsx` (returnTo),
  `pages/CartDesktop.tsx` + `pages/CartMobile.tsx` (гость видит корзину),
  `pages/CouponDetailDesktop.tsx` + `pages/CouponMobile.tsx` (Buy = в корзину, без прыжка на checkout),
  `pages/CheckoutDesktop.tsx` + `pages/CheckoutMobile.tsx` (phone-required inline-OTP, email не требуется),
  `pages/ProfileDesktop.tsx` + `pages/ProfileMobile.tsx` + `components/profile/ProfileSettingsSection.tsx` (скрыть placeholder-email + «Добавить email» как канал),
  `pages/PaymentDesktop.tsx` + `pages/PaymentMobile.tsx` (пост-покупочный призыв добавить email),
  `locales/ru.json` + `locales/uz.json` (ключи по задачам).

---

## Task 1: identity — флаг `emailPlaceholder` в профиль/auth

**Files:**
- Create: `services/identity-service/src/main/java/uz/topdim/identity/util/EmailPlaceholders.java`
- Modify: `dto/UserProfileResponse.java` (после `boolean emailVerified`), `dto/AuthResponse.java` (UserDto, строки 30-39), `service/UserService.java:177-193` (mapToProfile), `service/AuthService.java:356-376` (buildAuthResponse)
- Test: `src/test/java/uz/topdim/identity/util/EmailPlaceholdersTest.java`

**Interfaces:**
- Produces: `EmailPlaceholders.isPlaceholder(String email, boolean emailVerified) -> boolean`; поле `emailPlaceholder` (boolean) в `UserProfileResponse` и `AuthResponse.UserDto`.
- Consumes: ничего.

- [ ] **Step 1: Failing test** `EmailPlaceholdersTest.java`

```java
package uz.topdim.identity.util;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class EmailPlaceholdersTest {
    @Test void phonePlaceholderUnverified_isPlaceholder() {
        assertThat(EmailPlaceholders.isPlaceholder("phone_+998901234567@topdim.uz", false)).isTrue();
    }
    @Test void tgPlaceholderUnverified_isPlaceholder() {
        assertThat(EmailPlaceholders.isPlaceholder("tg_12345@topdim.uz", false)).isTrue();
    }
    @Test void releasedPlaceholderUnverified_isPlaceholder() {
        assertThat(EmailPlaceholders.isPlaceholder("released_42@topdim.uz", false)).isTrue();
    }
    @Test void realVerifiedEmail_isNotPlaceholder() {
        assertThat(EmailPlaceholders.isPlaceholder("ivan@gmail.com", true)).isFalse();
    }
    @Test void placeholderPatternButVerified_isNotPlaceholder() {
        // после email-change адрес мог бы совпасть с паттерном, но он подтверждён → не затычка
        assertThat(EmailPlaceholders.isPlaceholder("phone_x@topdim.uz", true)).isFalse();
    }
    @Test void nullEmail_isNotPlaceholder() {
        assertThat(EmailPlaceholders.isPlaceholder(null, false)).isFalse();
    }
}
```

- [ ] **Step 2: Run, verify FAIL** — `./gradlew :services:identity-service:test --tests "*EmailPlaceholdersTest*"` → FAIL (класс не существует).

- [ ] **Step 3: Implement `EmailPlaceholders.java`**

```java
package uz.topdim.identity.util;

import java.util.regex.Pattern;

/** Детект синтетического («затычка») email, который система генерит для аккаунтов без реального
 *  адреса (phone_/tg_/released_ @topdim.uz). Такой email нельзя показывать как контакт. */
public final class EmailPlaceholders {
    private static final Pattern PLACEHOLDER =
            Pattern.compile("^(tg_|phone_|released_).*@topdim\\.uz$");
    private EmailPlaceholders() {}

    public static boolean isPlaceholder(String email, boolean emailVerified) {
        return email != null && !emailVerified && PLACEHOLDER.matcher(email).matches();
    }
}
```

- [ ] **Step 4: Add field to DTOs.** В `UserProfileResponse.java` добавить поле `private boolean emailPlaceholder;` (рядом с `emailVerified`). В `AuthResponse.java` UserDto (30-39) добавить `private boolean emailPlaceholder;`.

- [ ] **Step 5: Populate.** `UserService.mapToProfile` (строка ~180, где `.email(user.getEmail())`) добавить `.emailPlaceholder(EmailPlaceholders.isPlaceholder(user.getEmail(), user.isEmailVerified()))`. То же в `AuthService.buildAuthResponse` UserDto builder (строка ~367).

- [ ] **Step 6: Run tests** — `./gradlew :services:identity-service:test --tests "*EmailPlaceholdersTest*"` → PASS; затем полный `:services:identity-service:test` → зелёный + jacoco.

- [ ] **Step 7: Commit**
```bash
git add services/identity-service/src/main/java/uz/topdim/identity/util/EmailPlaceholders.java services/identity-service/src/test/java/uz/topdim/identity/util/EmailPlaceholdersTest.java services/identity-service/src/main/java/uz/topdim/identity/dto/UserProfileResponse.java services/identity-service/src/main/java/uz/topdim/identity/dto/AuthResponse.java services/identity-service/src/main/java/uz/topdim/identity/service/UserService.java services/identity-service/src/main/java/uz/topdim/identity/service/AuthService.java
git commit -m "feat(identity): флаг emailPlaceholder в профиль/auth (скрыть синтетический email на фронте)"
```

**Deploy:** identity-service.

---

## Task 2: order-service — email на оформлении не обязателен, телефон обязателен

**Files:**
- Modify: `services/order-service/src/main/java/uz/topdim/order/dto/CreateOrderRequest.java:15-23`
- Test: `src/test/java/uz/topdim/order/dto/CreateOrderRequestValidationTest.java` (new; используйте `jakarta.validation.Validator` через `Validation.buildDefaultValidatorFactory()`)

**Interfaces:**
- Consumes: ничего. Produces: контракт `/api/v1/orders` — `email` опционален, `phone` обязателен (формат `^\+998\d{9}$`). Service (`OrderService.createOrder`) правки НЕ требует (уже null-tolerant, passthrough).

- [ ] **Step 1: Failing test**

```java
package uz.topdim.order.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CreateOrderRequestValidationTest {
    private final Validator v = Validation.buildDefaultValidatorFactory().getValidator();

    private CreateOrderRequest req(String email, String phone) {
        CreateOrderRequest r = new CreateOrderRequest();
        r.setEmail(email); r.setPhone(phone);
        return r;
    }

    @Test void phoneRequired_nullPhone_invalid() {
        assertThat(v.validate(req("i@x.uz", null))).isNotEmpty();
    }
    @Test void phoneBadFormat_invalid() {
        assertThat(v.validate(req(null, "12345"))).isNotEmpty();
    }
    @Test void emailOptional_nullEmailValidPhone_valid() {
        assertThat(v.validate(req(null, "+998901234567"))).isEmpty();
    }
    @Test void emailBadFormatWhenPresent_invalid() {
        assertThat(v.validate(req("not-an-email", "+998901234567"))).isNotEmpty();
    }
}
```

- [ ] **Step 2: Run, verify FAIL** — `./gradlew :services:order-service:test --tests "*CreateOrderRequestValidationTest*"` → FAIL (сейчас email `@NotBlank`, phone без `@Pattern`).

- [ ] **Step 3: Implement.** `CreateOrderRequest.java`: убрать `@NotBlank` у `email` (оставить `@Email` — он пропускает null); у `phone` оставить `@NotBlank` и добавить `@Pattern(regexp = "^\\+998\\d{9}$", message = "Некорректный телефон")`. Импорт `jakarta.validation.constraints.Pattern`.

- [ ] **Step 4: Run** — тот же `--tests` → PASS; затем `:services:order-service:test` полный → зелёный.

- [ ] **Step 5: Commit**
```bash
git add services/order-service/src/main/java/uz/topdim/order/dto/CreateOrderRequest.java services/order-service/src/test/java/uz/topdim/order/dto/CreateOrderRequestValidationTest.java
git commit -m "feat(order): email на оформлении не обязателен, телефон обязателен (+формат)"
```

**Deploy:** order-service. Прим.: `AdminOrderResponse` покажет null email у таких заказов — ок (админ-инфо).

---

## Task 3: web-app — вход возвращает к намерению (intended-destination)

**Files:**
- Modify: `pages/LoginPage.tsx:18`, `pages/RegisterPage.tsx:19`, `components/auth/LoginModal.tsx` (проп), `pages/CheckoutDesktop.tsx:49` + `pages/CheckoutMobile.tsx:65` (гость-гейт передаёт `from`).
- `authStore.ts`/`LoginCard.tsx` навигацию НЕ меняем (навигация только через `onSuccess`).
- Test: `pages/LoginPage.test.tsx` (RTL).

**Interfaces:**
- Механизм: react-router `location.state.from`. Checkout-гейт: `navigate(lp('/login'), { state: { from: lp('/checkout') } })`. `LoginPage`: `const from = (location.state as {from?: string})?.from ?? lp('/'); <LoginCard onSuccess={() => navigate(from)} />`.

- [ ] **Step 1: Failing RTL test** — `LoginPage.test.tsx`: рендер `LoginPage` внутри `MemoryRouter` с `initialEntries=[{ pathname: '/ru/login', state: { from: '/ru/checkout' } }]`, мок `LoginCard` (дёргает `onSuccess`), проверка: после `onSuccess` навигация на `/ru/checkout` (например через тест-локейшн-споттер). Без `state` → на `/ru/`.

- [ ] **Step 2: Run, verify FAIL** — `cd frontend/web-app && npx vitest run src/pages/LoginPage.test.tsx`.

- [ ] **Step 3: Implement.** `LoginPage.tsx`: прочитать `useLocation().state?.from`, дефолт `lp('/')`, передать в `onSuccess`. То же в `RegisterPage.tsx`. `CheckoutDesktop.tsx:49`/`CheckoutMobile.tsx:65`: заменить `navigate(lp('/login'))` → `navigate(lp('/login'), { state: { from: lp('/checkout') } })`. `LoginModal.tsx`: пробросить опциональный `onSuccess` (уже есть) — колл-сайты не трогаем (модалка остаётся «остаться на месте»).

- [ ] **Step 4: Run** — `npx vitest run src/pages/LoginPage.test.tsx` → PASS; `npx tsc --noEmit` чисто; `npx eslint src/pages/LoginPage.tsx`.

- [ ] **Step 5: Commit**
```bash
git add frontend/web-app/src/pages/LoginPage.tsx frontend/web-app/src/pages/RegisterPage.tsx frontend/web-app/src/pages/LoginPage.test.tsx frontend/web-app/src/pages/CheckoutDesktop.tsx frontend/web-app/src/pages/CheckoutMobile.tsx frontend/web-app/src/components/auth/LoginModal.tsx
git commit -m "feat(web-app): вход возвращает на прежнюю страницу (intended-destination) вместо главной"
```

**Deploy:** web-app.

---

## Task 4: web-app — гость видит свою корзину на `/cart`

**Files:**
- Modify: `pages/CartDesktop.tsx:25-37`, `pages/CartMobile.tsx:58-65`.
- Test: `pages/CartDesktop.test.tsx` (RTL).

**Interfaces:**
- Consumes: `cartStore` (гостевые `items` из localStorage уже есть). Гость видит items + сумму; кнопка «Оформить» для гостя → `navigate(lp('/login'), { state: { from: lp('/checkout') } })` (из Task 3).

- [ ] **Step 1: Failing RTL test** — `CartDesktop.test.tsx`: гость (`isAuthenticated=false`) + непустой guest-cart в store → на `/cart` **видны позиции** (не `GuestAuthPrompt`); кнопка «Оформить» присутствует.

- [ ] **Step 2: Run, verify FAIL** — сейчас гость видит `GuestAuthPrompt`, теста нет.

- [ ] **Step 3: Implement.** Убрать ранний `if (!isAuthenticated) return <GuestAuthPrompt.../>` в `CartDesktop.tsx:25-37` и `CartMobile.tsx:58-65`; рендерить список из store всегда. Кнопку «Оформить» вести: если `!isAuthenticated` → `navigate(lp('/login'), { state: { from: lp('/checkout') } })`, иначе `navigate(lp('/checkout'))`. Пустая корзина — прежний empty-state.

- [ ] **Step 4: Run** — `npx vitest run src/pages/CartDesktop.test.tsx` → PASS; `tsc`/`eslint` чисто.

- [ ] **Step 5: Commit**
```bash
git add frontend/web-app/src/pages/CartDesktop.tsx frontend/web-app/src/pages/CartMobile.tsx frontend/web-app/src/pages/CartDesktop.test.tsx
git commit -m "feat(web-app): гостевая корзина видна на /cart (вход только на «Оформить»)"
```

**Deploy:** web-app.

---

## Task 5: web-app — «Купить» кладёт в корзину и оставляет выбирать (без прыжка на checkout)

**Files:**
- Modify: `pages/CouponDetailDesktop.tsx:116-119` (`handleBuy`), `pages/CouponMobile.tsx:347-357` (Buy CTA).
- Test: `pages/CouponMobile.test.tsx` или `CouponDetailDesktop.test.tsx` (RTL).

**Interfaces:**
- После правки: primary CTA купона = `putInCart()` без `navigate('/checkout')`. `addToCart` уже открывает дровер (`cartStore.ts:222 isOpen:true`) — юзер видит добавление и **продолжает выбирать**. Оформление — из дровера/`/cart`.

- [ ] **Step 1: Failing RTL test** — клик по «Купить» → `addToCart` вызван, `navigate` НЕ вызван с `/checkout` (остаёмся на странице, дровер открыт).

- [ ] **Step 2: Run, verify FAIL** — сейчас `handleBuy` навигирует на `/checkout`.

- [ ] **Step 3: Implement.** `CouponDetailDesktop.tsx handleBuy` (116-119): убрать `navigate(lp('/checkout'))`, оставить `putInCart()`. `CouponMobile.tsx` Buy (347-357): то же — только `putInCart()`. (Кнопка «В корзину» / cart-иконка не меняются.)

- [ ] **Step 4: Run** — vitest соответствующего файла → PASS; `tsc`/`eslint` чисто.

- [ ] **Step 5: Commit**
```bash
git add frontend/web-app/src/pages/CouponDetailDesktop.tsx frontend/web-app/src/pages/CouponMobile.tsx <test-file>
git commit -m "feat(web-app): «Купить» кладёт купон в корзину и оставляет выбирать (корзинная модель)"
```

**Deploy:** web-app.

---

## Task 6: web-app — телефон обязателен на checkout (inline-OTP), email не требуется

**Files:**
- Modify: `pages/CheckoutDesktop.tsx:70-132`, `pages/CheckoutMobile.tsx:88-132`.
- Reuse: OTP-паттерн из `ProfileSettingsSection.tsx:512-579` (`requestPhoneOtp`→код→`linkPhone`) и `api/auth.ts` (`requestPhoneOtp`, `linkPhone`).
- Test: `pages/CheckoutDesktop.test.tsx` (RTL).

**Interfaces:**
- Consumes: Task 2 (бэк принимает пустой email). `authApi.requestPhoneOtp(phone)`, `authApi.linkPhone({phone, code})` (существуют). После успешного `linkPhone` → `refreshProfile()`.
- Гейт: `const needsPhone = !userPhone` (email убрать из условия). Нет телефона → inline-шаг (IMaskInput `+998XXXXXXXXX` → 6-значный код → `linkPhone` → `refreshProfile` → продолжить). Есть телефон → сразу «Оплатить». `createOrder(email, phone)`: передавать `user.emailPlaceholder ? '' : (user.email ?? '')` и `user.phone`.

- [ ] **Step 1: Failing RTL tests** — (а) юзер с телефоном, без реального email (`emailPlaceholder=true`) → **нет** стены «заполните профиль», доступна оплата, `createOrder` вызван с пустым email + телефоном; (б) юзер без телефона → показан inline-ввод телефона; ввод номера → `requestPhoneOtp`; ввод кода → `linkPhone` → `refreshProfile`.

- [ ] **Step 2: Run, verify FAIL** — сейчас `missingContact = !email || !phone` блокирует.

- [ ] **Step 3: Implement.** Убрать email из гейта; добавить состояние inline-OTP (mode `phoneRequest`/`phoneConfirm`) по образцу `ProfileSettingsSection`; `handlePayment`/`submit` передаёт email по правилу выше. Локали ключи `checkout.addPhone*` (ru+uz).

- [ ] **Step 4: Run** — vitest checkout-тесты → PASS; `tsc`/`eslint` чисто.

- [ ] **Step 5: Commit**
```bash
git add frontend/web-app/src/pages/CheckoutDesktop.tsx frontend/web-app/src/pages/CheckoutMobile.tsx frontend/web-app/src/pages/CheckoutDesktop.test.tsx frontend/web-app/src/locales/ru.json frontend/web-app/src/locales/uz.json
git commit -m "feat(web-app): на checkout обязателен телефон (inline-OTP), email не требуется"
```

**Deploy:** web-app (после deploy Task 2 order-service).

---

## Task 7: web-app — скрыть синтетический email в профиле

**Files:**
- Modify: `api/auth.ts` (UserDto 46-56: `+ emailPlaceholder?: boolean`), `pages/ProfileDesktop.tsx:197`, `pages/ProfileMobile.tsx:219`, `components/profile/ProfileSettingsSection.tsx:373`.
- Test: `components/profile/ProfileSettingsSection.test.tsx` (RTL).

**Interfaces:**
- Consumes: Task 1 (`emailPlaceholder` в UserDto с бэка). Если `user.emailPlaceholder` — вместо адреса показать `t('profile.emailNotAdded')` («Почта не добавлена — добавьте, чтобы получать купоны») + CTA «Добавить email» (открывает существующий email-change, `openEmailChange`).

- [ ] **Step 1: Failing RTL test** — `ProfileSettingsSection` с `user.emailPlaceholder=true` → показывается текст «Почта не добавлена», НЕ показывается `phone_...@topdim.uz`; есть кнопка «Добавить email». С реальным email (`emailPlaceholder=false`) → показывается адрес.

- [ ] **Step 2: Run, verify FAIL** — сейчас всегда `{user?.email}`.

- [ ] **Step 3: Implement.** Добавить `emailPlaceholder?: boolean` в UserDto (`api/auth.ts`). В трёх местах отображения email — тернар: `user.emailPlaceholder ? <не добавлена + CTA> : <email + badge>`. Локали `profile.emailNotAdded`, `profile.addEmail` (ru+uz).

- [ ] **Step 4: Run** — vitest → PASS; `tsc`/`eslint` чисто.

- [ ] **Step 5: Commit**
```bash
git add frontend/web-app/src/api/auth.ts frontend/web-app/src/pages/ProfileDesktop.tsx frontend/web-app/src/pages/ProfileMobile.tsx frontend/web-app/src/components/profile/ProfileSettingsSection.tsx frontend/web-app/src/components/profile/ProfileSettingsSection.test.tsx frontend/web-app/src/locales/ru.json frontend/web-app/src/locales/uz.json
git commit -m "feat(web-app): скрыть синтетический email в профиле → «Добавить почту»"
```

**Deploy:** web-app (после deploy Task 1 identity).

---

## Task 8: web-app — точка «Добавить email как канал» + пост-покупочный призыв

**Files:**
- Modify: `components/profile/ProfileSettingsSection.tsx` (копия у email-блока — «чтобы получать купоны на почту»), `pages/PaymentDesktop.tsx:379-406`, `pages/PaymentMobile.tsx:229-273`.
- Test: `pages/PaymentDesktop.test.tsx` (RTL).

**Interfaces:**
- Переиспользует существующий email-change флоу. На экране успеха покупки — блок «Получать купоны на почту? Добавить» → ведёт в профиль-настройки email (или открывает email-change). (Telegram-канал — Этап 2.)

- [ ] **Step 1: Failing RTL test** — `PaymentDesktop` в состоянии `completed` у юзера без реального email → показан призыв «добавить email»; клик ведёт на `/profile?tab=settings` (или открывает флоу).

- [ ] **Step 2: Run, verify FAIL.**

- [ ] **Step 3: Implement.** Вставить блок-призыв в success-блоки (desktop 379-406, mobile 229-273) — показывать, если `user.emailPlaceholder`. Обновить копию email-блока в настройках. Локали `payment.getCouponsByEmail`, `profile.emailChannelHint` (ru+uz).

- [ ] **Step 4: Run** — vitest → PASS; `tsc`/`eslint` чисто.

- [ ] **Step 5: Commit**
```bash
git add frontend/web-app/src/pages/PaymentDesktop.tsx frontend/web-app/src/pages/PaymentMobile.tsx frontend/web-app/src/components/profile/ProfileSettingsSection.tsx frontend/web-app/src/pages/PaymentDesktop.test.tsx frontend/web-app/src/locales/ru.json frontend/web-app/src/locales/uz.json
git commit -m "feat(web-app): точка «Добавить email как канал» в профиле + после покупки"
```

**Deploy:** web-app.

---

## Verification (перед каждым PR)

- Backend: `./gradlew :services:identity-service:test` / `:services:order-service:test` (H2+Mockito, jacoco).
- Frontend: `cd frontend/web-app && npx vitest run <файлы>`, `npx tsc --noEmit`, `npx eslint <изменённые>`.

**E2E вручную после деплоя:**
- Гость: добавить купон → он в корзине (дровер + `/cart`), продолжить выбирать; на «Оформить» → вход → вернулся в оформление; телефон-OTP если не было телефона → оплата.
- Профиль телефонного юзера: вместо `phone_…@topdim.uz` — «Почта не добавлена»; «Добавить email» работает.
- После покупки: призыв «получать купоны на почту».

## PR-группировка

| PR | Задача | Deploy |
|---|---|---|
| PR1 | T1 emailPlaceholder | identity |
| PR2 | T2 email опционален на checkout | order |
| PR3 | T3 intended-destination | web-app |
| PR4 | T4 гостевая корзина на /cart | web-app |
| PR5 | T5 «Купить» → в корзину | web-app |
| PR6 | T6 phone на checkout | web-app |
| PR7 | T7 скрыть placeholder-email | web-app |
| PR8 | T8 канал email + призыв | web-app |
