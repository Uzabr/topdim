# Руководство по тестированию — sizbiz

> **Для ручного бизнес-тестирования (QA):** Перейдите к документу [manual-test-plan.md](manual-test-plan.md), который описывает E2E сценарии, роли и подготовку окружения.

> Пошаговое руководство ниже предназначено для разработчиков: как писать автоматизированные тесты для микросервисов (Unit / Integration).

---

## 1. Обзор: Какие бывают тесты

```
           ┌──────────────┐
           │   E2E Tests  │  ← Мало (дорогие, медленные)
           │  (Playwright) │
          ┌┴──────────────┴┐
          │ Integration     │  ← Средне (с реальной БД)
          │ (Testcontainers)│
         ┌┴────────────────┴┐
         │   Unit Tests      │  ← Много (быстрые, дешёвые)
         │  (JUnit + Mockito)│
         └───────────────────┘
```

| Тип | Что тестирует | Инструменты | Скорость |
|---|---|---|---|
| **Unit** | Один класс/метод, без БД | JUnit 5 + Mockito | ⚡ мс |
| **Integration** | Сервис + БД + кэш | Testcontainers + PostgreSQL | 🐢 5-30 сек |
| **E2E** | Весь flow через API | Playwright / REST Assured | 🐌 минуты |

### С чего начать? → **Unit тесты!**
Самые быстрые, не требуют Docker, учат понимать код.

---

## 2. Структура тестового файла

Каждый тест-файл лежит **рядом с основным кодом**, но в `src/test/java`:

```
services/auth-service/
├── src/main/java/uz/topdim/auth/service/AuthService.java      ← код
└── src/test/java/uz/topdim/auth/service/AuthServiceTest.java   ← тест
```

---

## 3. Инструменты (уже установлены)

```groovy
// build.gradle — уже есть во всех сервисах
testImplementation 'org.springframework.boot:spring-boot-starter-test'
// Включает: JUnit 5, Mockito, AssertJ, Spring Test
```

---

## 4. Как писать Unit тест — Пошагово

### Шаблон теста

```java
package uz.topdim.auth.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)  // подключает Mockito
class AuthServiceTest {

    @Mock                              // "фейковый" объект
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks                       // тестируемый класс (mock'и внедряются сюда)
    private AuthService authService;

    @Test
    @DisplayName("Регистрация: успешная")
    void register_shouldCreateUser() {
        // 1. GIVEN — подготовка данных
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@test.com");
        request.setPassword("password123");

        when(userRepository.existsByEmail("test@test.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");

        // 2. WHEN — вызов метода
        AuthResponse response = authService.register(request);

        // 3. THEN — проверка результата
        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isNotBlank();
        verify(userRepository).save(any(User.class));  // проверяем что save вызван
    }

    @Test
    @DisplayName("Регистрация: email уже существует → ошибка")
    void register_duplicateEmail_shouldThrow() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("exists@test.com");

        when(userRepository.existsByEmail("exists@test.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("уже существует");
    }
}
```

### Ключевые аннотации

| Аннотация | Что делает |
|---|---|
| `@Mock` | Создаёт фейковый объект (не обращается к БД) |
| `@InjectMocks` | Создаёт реальный объект, но с mock-зависимостями |
| `@Test` | Помечает метод как тест |
| `@DisplayName("...")` | Человекочитаемое название теста |
| `@BeforeEach` | Выполняется перед каждым тестом |

### Ключевые методы Mockito

```java
// Настройка поведения mock'а:
when(repo.findById(1L)).thenReturn(Optional.of(user));
when(repo.findById(99L)).thenReturn(Optional.empty());
when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

// Проверка вызовов:
verify(repo).save(any());           // save() вызван 1 раз
verify(repo, times(2)).save(any()); // save() вызван 2 раза
verify(repo, never()).delete(any()); // delete() НЕ вызван
```

### Ключевые методы AssertJ

```java
assertThat(result).isNotNull();
assertThat(result.getName()).isEqualTo("TopDim");
assertThat(list).hasSize(3);
assertThat(list).contains(item1, item2);
assertThat(price).isGreaterThan(BigDecimal.ZERO);

// Проверка исключений:
assertThatThrownBy(() -> service.doSomething())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Купон не найден");
```

---

## 5. Что тестировать в каждом сервисе

### auth-service

| Класс | Метод | Тесты |
|---|---|---|
| `AuthService` | `register()` | ✅ успешно, ❌ duplicate email, ❌ duplicate phone |
| `AuthService` | `login()` | ✅ успешно, ❌ неверный пароль |
| `AuthService` | `refreshToken()` | ✅ валидный, ❌ revoked, ❌ expired |
| `AuthService` | `logout()` | ✅ revoke token |
| `JwtService` | `generateAccessToken()` | ✅ token не пустой, содержит claims |
| `JwtService` | `validateToken()` | ✅ валидный, ❌ expired, ❌ tampered |
| `TokenBlacklistService` | `blacklist()` / `isBlacklisted()` | ✅ добавлен → true, ❌ не добавлен → false |

### coupon-service

| Класс | Метод | Тесты |
|---|---|---|
| `CouponOfferService` | `getCatalog()` | ✅ с фильтром, ✅ без фильтра, ✅ пустой результат |
| `CouponOfferService` | `create()` | ✅ успешно, ❌ без обязательных полей |
| `CouponOfferService` | `updateStatus()` | ✅ ACTIVE→PAUSED, ❌ несуществующий ID |
| `CouponOfferService` | `delete()` | ✅ удаление, ❌ несуществующий |
| `MerchantService` | `getAllCategories()` | ✅ вернуть список |

### order-service

| Класс | Метод | Тесты |
|---|---|---|
| `OrderService` | `addToCart()` | ✅ добавить товар, ✅ увеличить количество |
| `OrderService` | `removeFromCart()` | ✅ удалить, ❌ чужой cart |
| `OrderService` | `createOrder()` | ✅ checkout, ❌ пустая корзина, ✅ event published |
| `OrderService` | `redeemCoupon()` | ✅ активный купон → USED, ❌ уже использован |
| `OrderService` | `createRefundRequest()` | ✅ создать, ❌ чужой заказ |
| `OrderService` | `resolveRefundRequest()` | ✅ approve, ✅ reject |

### payment-service

| Класс | Метод | Тесты |
|---|---|---|
| `PaymentService` | `createPayment()` | ✅ создать с статусом PENDING |
| `PaymentService` | `handleCallback()` | ✅ → COMPLETED, ❌ → FAILED |

### bazaar-service

| Класс | Метод | Тесты |
|---|---|---|
| `BazaarService` | `getAllBazaars()` | ✅ список |
| `BazaarService` | `createBazaar()` | ✅ создать |
| `ShopService` | `getShopsByBazaar()` | ✅ возврат магазинов |

### user-service

| Класс | Метод | Тесты |
|---|---|---|
| `UserService` | `getProfile()` | ✅ найден, ❌ не найден |
| `FavoriteService` | `addFavorite()` | ✅ добавить, ❌ дубликат |
| `FavoriteService` | `removeFavorite()` | ✅ удалить |

---

## 6. Как запускать тесты

### Один сервис
```bash
./gradlew :services:auth-service:test
```

### Все сервисы
```bash
./gradlew test
```

### Один тест-класс
```bash
./gradlew :services:auth-service:test --tests "uz.topdim.auth.service.AuthServiceTest"
```

### Один метод
```bash
./gradlew :services:auth-service:test --tests "uz.topdim.auth.service.AuthServiceTest.register_shouldCreateUser"
```

### С отчётом
```bash
./gradlew test
# Отчёт: build/reports/tests/test/index.html
```

---

## 7. Пошаговый план: в каком порядке писать

### Шаг 1: auth-service (начни отсюда!)
Самый понятный сервис. 3 файла:
```
src/test/java/uz/topdim/auth/
├── service/AuthServiceTest.java       ← 6 тестов
└── security/JwtServiceTest.java       ← 3 теста
```

### Шаг 2: coupon-service
Чистый CRUD + кэш:
```
src/test/java/uz/topdim/coupon/
└── service/CouponOfferServiceTest.java  ← 5 тестов
```

### Шаг 3: order-service
Самый сложный — много бизнес-логики:
```
src/test/java/uz/topdim/order/
└── service/OrderServiceTest.java        ← 8 тестов
```

### Шаг 4: остальные (простые)
```
payment-service  → PaymentServiceTest    ← 3 теста
bazaar-service   → BazaarServiceTest     ← 3 теста
user-service     → UserServiceTest       ← 3 теста
```

---

## 8. Первый тест — Скопируй и запусти

Создай файл:
`services/auth-service/src/test/java/uz/topdim/auth/service/AuthServiceTest.java`

```java
package uz.topdim.auth.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import uz.topdim.auth.dto.RegisterRequest;
import uz.topdim.auth.exception.AuthException;
import uz.topdim.auth.repository.RefreshTokenRepository;
import uz.topdim.auth.repository.UserRepository;
import uz.topdim.auth.security.JwtService;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private TokenBlacklistService tokenBlacklistService;

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("Регистрация с дубликатом email должна выбросить AuthException")
    void register_duplicateEmail_shouldThrowException() {
        // Given
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@topdim.uz");
        request.setPassword("password");

        when(userRepository.existsByEmail("test@topdim.uz")).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("уже существует");

        verify(userRepository, never()).save(any());
    }
}
```

Запусти:
```bash
./gradlew :services:auth-service:test --tests "*.AuthServiceTest"
```

Если тест зелёный ✅ — поздравляю, первый тест готов!

---

## 9. Типичные ошибки новичков

| Ошибка | Решение |
|---|---|
| `NullPointerException` в тесте | Забыл `@ExtendWith(MockitoExtension.class)` |
| Mock не работает | Убедись что используешь `@Mock` + `@InjectMocks` |
| Тест не находится | Путь пакета `src/test/java` должен совпадать с `src/main/java` |
| `when()` не срабатывает | Аргументы должны совпадать (используй `any()` для гибкости) |
| Тест зависит от другого теста | Каждый `@Test` должен быть независимым |
