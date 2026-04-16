# Создание купона — Полный флоу

Этот документ описывает **каждый этап и каждый файл**, участвующий в процессе создания купона от нажатия кнопки до записи в БД.

---

## Общая схема (Путь Админа)

```
Браузер (admin-app)
  └─ CouponFormPage.tsx — форма
       └─ POST http://localhost:8080/api/v1/admin/coupons
            └─ api-gateway (application.yml, маршрут coupon-service)
                 └─ api-gateway → SecurityConfig — проверка JWT, добавляет X-User-Id, X-User-Role
                      └─ coupon-service :8083
                           └─ RoleHeaderAuthenticationFilter — читает X-User-Id, X-User-Role
                                └─ SecurityConfig — @PreAuthorize проверка роли
                                     └─ AdminCouponController.createCoupon()
                                          └─ CouponOfferService.create()
                                               └─ PostgreSQL → coupon_offers + coupon_options + coupon_images
```

---

## Этап 1: Форма в admin-app

### Файл: `frontend/admin-app/src/features/coupons/CouponFormPage.tsx`

Единая форма для создания и редактирования (определяется наличием `id` в URL).

**Что делает при загрузке:**
1. `useQuery(['categories'])` → `GET /api/v1/categories` — загружает список категорий
2. `useQuery(['merchants-list'])` → `GET /api/v1/admin/merchants` — загружает список партнёров
3. Если режим редактирования: `useQuery(['admin-coupon', id])` → `GET /api/v1/admin/coupons/{id}`

**Поля формы (interface CouponFormData):**

| Поле | Тип | Обязательно | Описание |
|------|-----|-------------|----------|
| `title` | string | ✅ | Название акции |
| `categoryId` | number | ✅ | Категория из справочника |
| `merchantId` | number | ❌ | Партнёр (можно создать прямо из формы) |
| `coverImageUrl` | string | ✅ | Главная картинка (Upload → media-service) |
| `images` | string[] | ❌ | Галерея доп. фотографий |
| `shortDescription` | string | ❌ | Краткое описание (на плитке каталога) |
| `fullDescription` | string | ❌ | Полное описание (Markdown) |
| `oldPrice` | number | ❌ | Старая цена (перечёркнутая) |
| `fromPrice` | number | ✅ | Новая цена |
| `discountPercent` | number | ❌ | % скидки (красный бейдж) |
| `options` | array | ❌ | Варианты покупки (сертификаты) |
| `buyUntil` | datetime | ✅ | Дата окончания продаж |
| `useUntil` | datetime | ✅ | Дата окончания использования |
| `terms` | string | ❌ | Условия (Markdown) |
| `usageRules` | string | ❌ | Правила использования (Markdown) |
| `howToUse` | string | ❌ | Инструкция по использованию (Markdown) |
| `address` | string | ❌ | Адрес заведения |
| `contactPhone` | string | ❌ | Телефон |
| `workingHours` | string | ❌ | Часы работы |
| `giftAvailable` | boolean | ❌ | Доступен как подарок |

### Загрузка изображений (промежуточный этап)

Перед отправкой формы, картинки грузятся **отдельно** в `media-service`:

```
Upload компонент → POST http://localhost:8080/api/v1/media/upload
  Headers: { Authorization: Bearer <accessToken> }
  Body: multipart/form-data (file)
  
  Маршрут: api-gateway → media-service :8088
  Ответ: { data: { url: "/api/v1/media/files/abc123.jpg" } }
  
  Фронтенд формирует полный URL: "http://localhost:8080/api/v1/media/files/abc123.jpg"
  Записывает в coverImageUrl или galleryImages[]
```

### Что происходит при нажатии "Опубликовать"

```javascript
// CouponFormPage.tsx, строка 191-211
onFinish(values) → modal.confirm("Публикуем купон?")
  → onOk → createMutation.mutate(values)
```

**Формирование payload (строки 146-154):**
```javascript
const payload = {
  ...values,
  buyUntil: values.buyUntil.format('YYYY-MM-DDTHH:mm:ss'),  // dayjs → string
  useUntil: values.useUntil.format('YYYY-MM-DDTHH:mm:ss'),
  images: galleryImages,  // из state, не из формы
};
// POST /api/v1/admin/coupons
```

**После успеха (строки 156-159):**
```javascript
message.success('Купон успешно создан и опубликован!');
queryClient.invalidateQueries({ queryKey: ['admin-coupons'] });
navigate('/moderation/coupons');  // переход к списку
```

---

## Этап 2: API Gateway

### Файл: `infrastructure/api-gateway/src/main/resources/application.yml`

**Маршрут:**
```yaml
- id: coupon-service
  uri: lb://coupon-service  # через Eureka (service discovery)
  predicates:
    - Path=/api/v1/admin/coupons/**, /api/v1/coupons/**, ...
```

**Что делает Gateway:**
1. Принимает `POST http://localhost:8080/api/v1/admin/coupons`
2. Проверяет JWT токен из `Authorization: Bearer <token>`
3. Добавляет заголовки: `X-User-Id: 1`, `X-User-Role: ADMIN`
4. Проксирует на `coupon-service:8083`

---

## Этап 3: coupon-service — Security

### Файл: `services/coupon-service/src/main/java/uz/topdim/coupon/security/RoleHeaderAuthenticationFilter.java`

1. Читает `X-User-Id` и `X-User-Role` из заголовков
2. Валидирует: userId > 0, роль из whitelist (USER, PARTNER, MODERATOR, ADMIN, SUPER_ADMIN)
3. Создаёт `UsernamePasswordAuthenticationToken` с authorities
4. Помещает в `SecurityContextHolder`

### Файл: `services/coupon-service/src/main/java/uz/topdim/coupon/security/SecurityConfig.java`

Настройка: `/api/v1/admin/**` — только авторизованные запросы

---

## Этап 4: Контроллер

### Файл: `services/coupon-service/src/main/java/uz/topdim/coupon/controller/AdminCouponController.java`

```java
@PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN', 'SUPER_ADMIN')")
@PostMapping("/coupons")
public ResponseEntity<ApiResponse<CouponOfferResponse>> createCoupon(
    @Valid @RequestBody CreateCouponOfferRequest request
) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success("Купон создан", couponOfferService.create(request)));
}
```

**Что происходит:**
1. `@PreAuthorize` — проверяет что роль = MODERATOR, ADMIN или SUPER_ADMIN
2. `@Valid` — валидация DTO (title обязательно, fromPrice > 0, и т.д.)
3. Делегирует в `CouponOfferService.create()`

---

## Этап 5: Бизнес-логика

### Файл: `services/coupon-service/src/main/java/uz/topdim/coupon/service/CouponOfferService.java` (строка 119)

```java
@Transactional
public CouponOfferResponse create(CreateCouponOfferRequest request) {
    // 1. Находит Merchant (если указан)
    Merchant merchant = null;
    if (request.getMerchantId() != null) {
        merchant = merchantRepository.findById(request.getMerchantId())
            .orElseThrow(() -> new ResourceNotFoundException("Партнёр не найден"));
    }

    // 2. Находит Category (обязательно)
    Category category = categoryRepository.findById(request.getCategoryId())
        .orElseThrow(() -> new ResourceNotFoundException("Категория не найдена"));

    // 3. Создаёт CouponOffer → status = ACTIVE
    CouponOffer offer = CouponOffer.builder()
        .title(request.getTitle())
        .merchant(merchant)
        .category(category)
        .oldPrice(request.getOldPrice())
        .fromPrice(request.getFromPrice())
        .discountPercent(request.getDiscountPercent())
        .coverImageUrl(request.getCoverImageUrl())
        .buyUntil(request.getBuyUntil())
        .useUntil(request.getUseUntil())
        .status(CouponStatus.ACTIVE)   // <-- СРАЗУ АКТИВНЫЙ
        .totalSold(0)
        .viewCount(0)
        // ... остальные поля
        .build();
    offer = couponOfferRepository.save(offer);

    // 4. Создаёт CouponOption[] (варианты покупки)
    if (request.getOptions() != null) {
        for (CreateCouponOptionRequest optReq : request.getOptions()) {
            CouponOption option = CouponOption.builder()
                .couponOffer(offer)
                .title(optReq.getTitle())
                .regularPrice(optReq.getRegularPrice())
                .couponPrice(optReq.getCouponPrice())
                .quantityLimit(optReq.getQuantityLimit())
                .status(CouponOptionStatus.ACTIVE)
                .build();
            couponOptionRepository.save(option);
        }
    }

    // 5. Создаёт CouponImage[] (галерея)
    if (request.getImages() != null) {
        for (int i = 0; i < request.getImages().size(); i++) {
            CouponImage image = CouponImage.builder()
                .couponOffer(offer)
                .imageUrl(request.getImages().get(i))
                .sortOrder(i)
                .build();
            couponImageRepository.save(image);
        }
    }

    // 6. Возвращает DTO
    return mapToResponse(couponOfferRepository.findById(offer.getId()).orElseThrow());
}
```

---

## Этап 6: Запись в БД

### Таблицы (PostgreSQL, база `topdim_coupon`):

**coupon_offers** (основная таблица):
```
id | title | short_description | full_description | merchant_id | category_id
old_price | from_price | discount_percent | cover_image_url
buy_until | use_until | status=ACTIVE | total_sold=0 | view_count=0
terms | usage_rules | how_to_use | address | contact_phone | working_hours
gift_available | created_at | updated_at
```

**coupon_options** (варианты покупки):
```
id | coupon_offer_id | title | regular_price | coupon_price
quantity_limit | quantity_sold=0 | status=ACTIVE
```

**coupon_images** (галерея):
```
id | coupon_offer_id | image_url | sort_order
```

### Миграции:
- `V1__create_coupon_tables.sql` — основные таблицы
- `V6__make_merchant_nullable.sql` — merchant может быть NULL

---

## Этап 7: Ответ клиенту

**Ответ API (CouponOfferResponse):**
```json
{
  "success": true,
  "message": "Купон создан",
  "data": {
    "id": 42,
    "title": "Скидка 50% на все сеты роллов",
    "shortDescription": "Лучшие суши в городе",
    "oldPrice": 200000,
    "fromPrice": 100000,
    "discountPercent": 50,
    "coverImageUrl": "http://localhost:8080/api/v1/media/files/abc123.jpg",
    "status": "ACTIVE",
    "totalSold": 0,
    "viewCount": 0,
    "buyUntil": "2026-05-01T23:59:00",
    "useUntil": "2026-06-01T23:59:00",
    "category": { "id": 1, "name": "Рестораны" },
    "merchant": { "id": 5, "name": "PizzaLab" },
    "options": [...],
    "images": [...]
  }
}
```

---

## Все задействованные файлы

| # | Файл | Назначение |
|---|------|------------|
| 1 | `frontend/admin-app/src/features/coupons/CouponFormPage.tsx` | Форма создания/редактирования |
| 2 | `frontend/admin-app/src/api/client.ts` | HTTP клиент (axios, baseURL=localhost:8080) |
| 3 | `frontend/admin-app/src/store/authStore.ts` | Хранилище JWT токена |
| 4 | `infrastructure/api-gateway/src/main/resources/application.yml` | Маршрутизация к coupon-service |
| 5 | `infrastructure/api-gateway/.../JwtAuthenticationFilter.java` | Проверка JWT, добавление X-User-Id |
| 6 | `services/coupon-service/.../security/RoleHeaderAuthenticationFilter.java` | Чтение X-User-Id, X-User-Role |
| 7 | `services/coupon-service/.../security/SecurityConfig.java` | Настройка доступа к /api/v1/admin/** |
| 8 | `services/coupon-service/.../controller/AdminCouponController.java` | Контроллер POST /api/v1/admin/coupons |
| 9 | `services/coupon-service/.../dto/CreateCouponOfferRequest.java` | DTO запроса (валидация) |
| 10 | `services/coupon-service/.../dto/CouponOfferResponse.java` | DTO ответа |
| 11 | `services/coupon-service/.../service/CouponOfferService.java` | Бизнес-логика создания |
| 12 | `services/coupon-service/.../entity/CouponOffer.java` | JPA-сущность купона |
| 13 | `services/coupon-service/.../entity/CouponOption.java` | JPA-сущность варианта покупки |
| 14 | `services/coupon-service/.../entity/CouponImage.java` | JPA-сущность фото |
| 15 | `services/coupon-service/.../repository/CouponOfferRepository.java` | JPA репозиторий |
| 16 | `services/media-service/.../controller/MediaController.java` | Загрузка файлов (отдельный этап) |

---

## Разница: Админ vs Партнёр

| Аспект | Админ | Партнёр |
|--------|-------|---------|
| Endpoint | `POST /api/v1/admin/coupons` | `POST /api/v1/partner/coupons` |
| Контроллер | `AdminCouponController` | `PartnerCouponController` |
| Сервис | `CouponOfferService.create()` | `PartnerCouponService.createCouponOffer()` |
| Начальный статус | **ACTIVE** | **PENDING_REVIEW** |
| Мерчант | Выбирает из списка | Автоматически по userId |
| Модерация | Не нужна | Нужна (ModCouponController) |
| Варианты покупки (options) | ✅ Поддержаны | ❌ Не реализовано |
