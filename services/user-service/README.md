# User Service

> Профиль пользователя и избранное

## Порт: 8082

## Стек
Spring Boot 3.4, Spring Data JPA, MapStruct, PostgreSQL

## База данных
`topdim_user` — таблица: `favorites`

## API

| Method | URL | Auth | Описание |
|---|---|---|---|
| GET | `/api/v1/users/profile` | ✅ | Мой профиль |
| PUT | `/api/v1/users/profile` | ✅ | Обновить профиль |
| GET | `/api/v1/users/favorites` | ✅ | Избранные купоны |
| POST | `/api/v1/users/favorites` | ✅ | Добавить в избранное |
| DELETE | `/api/v1/users/favorites/{id}` | ✅ | Удалить из избранного |

## Модели
- `Favorite` (userId, couponOfferId, createdAt)

## Swagger
http://localhost:8082/swagger-ui.html
