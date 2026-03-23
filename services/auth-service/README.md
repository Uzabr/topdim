# Auth Service

> Аутентификация и авторизация пользователей

## Порт: 8081

## Стек
Spring Boot 3.4, Spring Security, JWT (jjwt), Redis, PostgreSQL, RabbitMQ

## База данных
`topdim_auth` — таблицы: `users`, `refresh_tokens`

## API

| Method | URL | Auth | Описание |
|---|---|---|---|
| POST | `/api/v1/auth/register` | ❌ | Регистрация |
| POST | `/api/v1/auth/login` | ❌ | Вход |
| POST | `/api/v1/auth/refresh` | ❌ | Обновление токена |
| POST | `/api/v1/auth/logout` | ✅ | Выход |

## Бизнес-логика

### Регистрация
1. Проверяет уникальность email/phone
2. Хэширует пароль (BCrypt)
3. Создаёт `User` (role: USER)
4. Генерирует access token (15 мин) + refresh token (7 дней)

### Login
1. `AuthenticationManager` проверяет credentials
2. Отзывает старые refresh tokens
3. Генерирует новую пару токенов

### Logout
1. Refresh token → `revoked = true` в БД
2. Access token → Redis blacklist с TTL

## Ключевые классы
- `AuthService` — бизнес-логика
- `JwtService` — генерация/валидация JWT
- `TokenBlacklistService` — Redis blacklist
- `SecurityConfig` — Spring Security

## Swagger
http://localhost:8081/swagger-ui.html
