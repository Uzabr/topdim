# fix/actuator-and-logging — M2 + M3

## M2 — Actuator наружу + show-details: always
- [x] Все сервисы (7): `show-details: always` → `show-details: never`
- [x] Все сервисы (7): `exposure.include: health` (убрано info,prometheus,metrics)
- [x] Gateway: убрал `/actuator` из `OPEN_ENDPOINTS`

## M3 — Прод-логи на DEBUG
- [x] 5 existing `application-prod.yml`: добавлено `logging.level.uz.topdim: INFO`
- [x] 2 new `application-prod.yml` (media, notification): создан файл

## Верификация
- [x] `./gradlew compileJava` — BUILD SUCCESSFUL
- [x] `./gradlew test` — BUILD SUCCESSFUL (48 tasks, 27 executed)
- [x] Коммит: `44f4c40`
