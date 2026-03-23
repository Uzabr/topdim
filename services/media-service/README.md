# Media Service

> Загрузка и хранение файлов (MinIO)

## Порт: 8087

## Стек
Spring Boot 3.4, MinIO SDK 8.5

## API

| Method | URL | Описание |
|---|---|---|
| POST | `/api/v1/media/upload` | Загрузить файл (multipart/form-data, param: `file`) |
| GET | `/api/v1/media/{fileName}` | Скачать/просмотреть файл |
| DELETE | `/api/v1/media/{fileName}` | Удалить файл |

## Конфигурация

```yaml
minio:
  url: http://localhost:9000
  access-key: topdim
  secret-key: topdim_secret
  bucket: topdim-media
```

## MinIO Console
http://localhost:9001 (login: topdim / topdim_secret)

## Пример загрузки
```bash
curl -X POST http://localhost:8087/api/v1/media/upload \
  -F "file=@photo.jpg"

# Response:
# { "data": { "fileName": "uuid_photo.jpg", "url": "/api/v1/media/uuid_photo.jpg" } }
```
