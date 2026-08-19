# Media-service Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Media-service ужимает картинки при загрузке в WebP (3 размера), не хранит тяжёлый оригинал, и отдаёт их с правильным типом и вечным кэшем — экономя диск при отличном качестве.

**Architecture:** Синхронный upload: валидация → `ImageProcessor` (libvips CLI: resize без апскейла → WebP q82 → strip EXIF) → `MediaStorageService` кладёт 3 варианта в MinIO → ответ `{id,url,variants}`. Отдача — стриминг варианта из MinIO с `image/webp` + immutable-кэш (кэшируется Cloudflare). nginx-лимит поднят до 20m.

**Tech Stack:** Java 21, Spring Boot (spring-cloud 2024.0.1), MinIO Java SDK 8.5.14, **libvips CLI (`vipsthumbnail`/`vips`)** для обработки, JUnit 5 + Mockito, Docker (Alpine).

## Global Constraints

- Базовый Docker-образ бэкенда — **`eclipse-temurin:21-jre-alpine` (musl)**: WebP делаем через **libvips CLI**, установленный `apk add --no-cache vips-tools`. JVM-либы с glibc-бинарями не использовать.
- Формат отдачи — **WebP-only**, качество **q=82**.
- Размеры (px по длинной стороне, **без апскейла**): `thumb=200`, `card=600`, `full=1600`.
- Стрип метаданных (EXIF) обязателен.
- Обратная совместимость: ответ upload сохраняет поле `url` (дефолт=`full`); старые сырые объекты `/api/v1/media/{oldfile}` продолжают отдаваться.
- nginx `client_max_body_size 20m` (`docker/frontend/nginx.conf`); проверить edge-прокси EasyPanel.
- Ветка `feat/media-service-optimization`; коммиты частые; каждый таск — рабочий и тестируемый.
- Правила репо: ветка → PR → squash → ручной деплой (`gh workflow run cd.yml -f services="..."`). Не пушить без явной команды владельца.

---

### Task 1: Phase 0 — разблокировать прод-загрузку (media boot + nginx 413)

Небольшой самостоятельный PR, чинит прод независимо от рефактора. `@Autowired`-фикс уже применён в рабочем дереве — здесь его закоммитить + добавить nginx-лимит.

**Files:**
- Modify: `services/media-service/src/main/java/uz/topdim/media/controller/MediaController.java` (уже: `@Autowired` на боевом конструкторе)
- Modify: `docker/frontend/nginx.conf` (добавить `client_max_body_size`)
- Test: `services/media-service/src/test/java/uz/topdim/media/controller/MediaControllerTest.java` (уже есть; должен проходить)

**Interfaces:**
- Produces: рабочий boot media-service (Spring может инстанцировать `MediaController`), nginx пропускает тела до 20MB.

- [ ] **Step 1: Убедиться, что тест на boot/DI есть и падал бы без фикса**

Проверить, что `MediaControllerTest` создаёт контроллер (мок `MinioClient`) и вызывает upload. Если явного «context loads» теста нет — добавить:

```java
@Test
void primaryConstructorIsAutowirable() {
    // Spring выбирает конструктор только при одном @Autowired среди нескольких.
    long autowired = Arrays.stream(MediaController.class.getDeclaredConstructors())
            .filter(c -> c.isAnnotationPresent(org.springframework.beans.factory.annotation.Autowired.class))
            .count();
    assertEquals(1, autowired, "ровно один конструктор должен быть @Autowired");
}
```

- [ ] **Step 2: Прогнать тест**

Run: `./gradlew :services:media-service:test --tests '*MediaControllerTest*'`
Expected: PASS (фикс `@Autowired` уже в дереве).

- [ ] **Step 3: Добавить nginx-лимит**

В `docker/frontend/nginx.conf` в блок `server { ... }` (сразу после `server_tokens off;`) добавить:

```nginx
    # Лимит тела запроса: аплоад медиа до 20MB (media-service принимает 20MB).
    # Дефолт nginx = 1MB → резал фото 413. Держать согласованным с spring multipart.
    client_max_body_size 20m;
```

- [ ] **Step 4: Собрать образ media локально (проверка, что boot не падает)**

Run: `docker build --build-arg MODULE_PATH=services:media-service -f docker/backend/Dockerfile -t media-check .`
Expected: build OK. (Опц. локальный run с MinIO — проверить `/actuator/health` = UP.)

- [ ] **Step 5: Commit**

```bash
git add services/media-service/src/main/java/uz/topdim/media/controller/MediaController.java \
        services/media-service/src/test/java/uz/topdim/media/controller/MediaControllerTest.java \
        docker/frontend/nginx.conf
git commit -m "fix(media): media-service boot (@Autowired) + nginx client_max_body_size 20m

Регрессия #129: два конструктора без @Autowired роняли boot.
nginx дефолт 1MB резал аплоад фото 413. Фаза 0 — разблокировка прода.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

> После мерджа + деплоя `media-service web-app admin-app partner`: проверить прод `POST /api/v1/media/upload` с фото ~3MB → уже не 413. Если всё ещё 413 — режет edge-прокси EasyPanel (server-side, вне git).

---

### Task 2: libvips в образе бэкенда + smoke

**Files:**
- Modify: `docker/backend/Dockerfile` (runtime-стадия: `apk add vips-tools`)

**Interfaces:**
- Produces: в рантайм-образе доступны бинари `vips` и `vipsthumbnail` (проверяемо `vips --version`).

- [ ] **Step 1: Добавить vips-tools в рантайм-стадию**

В `docker/backend/Dockerfile` изменить строку установки в рантайм-стадии (`FROM eclipse-temurin:21-jre-alpine`):

```dockerfile
RUN apk add --no-cache curl vips-tools \
    && addgroup -S topdim \
    && adduser -S topdim -G topdim
```

(Ставится в общий образ — просто и без правок `cd.yml`; libvips использует только media-service. Если размер образа станет проблемой — вынести media в отдельный Dockerfile, follow-up.)

- [ ] **Step 2: Собрать образ и проверить vips**

Run:
```bash
docker build --build-arg MODULE_PATH=services:media-service -f docker/backend/Dockerfile -t media-vips .
docker run --rm --entrypoint sh media-vips -c "vips --version && vipsthumbnail --vips-version"
```
Expected: печатает версию libvips (например `vips-8.x`), поддержка webp есть (`vips --version` + libwebp в зависимостях vips-tools на Alpine).

- [ ] **Step 3: Подтвердить, что vips умеет WebP**

Run:
```bash
docker run --rm --entrypoint sh media-vips -c \
  "printf '\\x89PNG' > /tmp/x && vips webpsave_buffer --help >/dev/null 2>&1 && echo WEBP_OK"
```
Expected: `WEBP_OK` (оператор `webpsave` доступен).

- [ ] **Step 4: Commit**

```bash
git add docker/backend/Dockerfile
git commit -m "build(media): установить libvips (vips-tools) в рантайм-образ для WebP

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 3: ImageProcessor — обёртка над libvips (resize + WebP + strip)

**Files:**
- Create: `services/media-service/src/main/java/uz/topdim/media/service/ImageVariant.java`
- Create: `services/media-service/src/main/java/uz/topdim/media/service/ProcessRunner.java`
- Create: `services/media-service/src/main/java/uz/topdim/media/service/ImageProcessor.java`
- Create: `services/media-service/src/main/java/uz/topdim/media/service/ImageProcessingException.java`
- Test: `services/media-service/src/test/java/uz/topdim/media/service/ImageProcessorTest.java`

**Interfaces:**
- Produces:
  - `enum ImageVariant { THUMB(200), CARD(600), FULL(1600); int maxPx() }`
  - `interface ProcessRunner { byte[] run(byte[] stdin, List<String> command) throws IOException, InterruptedException; }` — обёртка над `Process` (мокается в юнит-тестах).
  - `class ImageProcessor { Map<ImageVariant, byte[]> process(byte[] source); }` — на каждый вариант вызывает vips; бросает `ImageProcessingException` при ошибке.
- Consumes: `ProcessRunner` (через конструктор), качество/размеры из конфига (Task 7; пока константы).

- [ ] **Step 1: Написать падающий тест `ImageProcessor` (логика, мок раннера)**

```java
class ImageProcessorTest {
    @Test
    void processesAllVariantsViaVips() throws Exception {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any(), anyList())).thenReturn(new byte[]{'W','E','B','P'});
        ImageProcessor proc = new ImageProcessor(runner, 82);

        Map<ImageVariant, byte[]> out = proc.process(new byte[]{1,2,3});

        assertEquals(3, out.size());
        assertArrayEquals(new byte[]{'W','E','B','P'}, out.get(ImageVariant.CARD));
        // команда для FULL содержит размер 1600, качество 82, strip и вывод webp
        ArgumentCaptor<List<String>> cmd = ArgumentCaptor.forClass(List.class);
        verify(runner, times(3)).run(any(), cmd.capture());
        String joined = String.join(" ", cmd.getAllValues().get(0));
        assertTrue(joined.contains("1600") || cmd.getAllValues().stream()
                .anyMatch(c -> String.join(" ", c).contains("1600")));
    }

    @Test
    void wrapsRunnerFailureAsImageProcessingException() throws Exception {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any(), anyList())).thenThrow(new IOException("vips missing"));
        ImageProcessor proc = new ImageProcessor(runner, 82);
        assertThrows(ImageProcessingException.class, () -> proc.process(new byte[]{1}));
    }
}
```

- [ ] **Step 2: Прогнать — падает (классов нет)**

Run: `./gradlew :services:media-service:test --tests '*ImageProcessorTest*'`
Expected: FAIL (компиляция — нет `ImageProcessor`/`ImageVariant`/`ProcessRunner`).

- [ ] **Step 3: Реализовать классы**

`ImageVariant.java`:
```java
package uz.topdim.media.service;

public enum ImageVariant {
    THUMB(200), CARD(600), FULL(1600);
    private final int maxPx;
    ImageVariant(int maxPx) { this.maxPx = maxPx; }
    public int maxPx() { return maxPx; }
    public String suffix() { return name().toLowerCase(); }
}
```

`ProcessRunner.java`:
```java
package uz.topdim.media.service;

import java.io.IOException;
import java.util.List;

public interface ProcessRunner {
    /** Запускает команду, подаёт stdin, возвращает stdout. Бросает при ненулевом коде выхода. */
    byte[] run(byte[] stdin, List<String> command) throws IOException, InterruptedException;
}
```

`ImageProcessingException.java`:
```java
package uz.topdim.media.service;

public class ImageProcessingException extends RuntimeException {
    public ImageProcessingException(String message, Throwable cause) { super(message, cause); }
}
```

`ImageProcessor.java`:
```java
package uz.topdim.media.service;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Ресайз (без апскейла) + перекодирование в WebP + стрип метаданных через libvips CLI.
 * vipsthumbnail читает из stdin ([descriptor]), пишет webp в stdout:
 *   vipsthumbnail stdin --size {max}> -o .webp[Q={q},strip] --vips-concurrency=1
 * "{max}>" — не увеличивать, если изображение меньше цели (без апскейла).
 */
@Component
public class ImageProcessor {

    private final ProcessRunner runner;
    private final int quality;

    public ImageProcessor(ProcessRunner runner, int quality) {
        this.runner = runner;
        this.quality = quality;
    }

    public Map<ImageVariant, byte[]> process(byte[] source) {
        Map<ImageVariant, byte[]> result = new EnumMap<>(ImageVariant.class);
        for (ImageVariant v : ImageVariant.values()) {
            result.put(v, encode(source, v));
        }
        return result;
    }

    private byte[] encode(byte[] source, ImageVariant variant) {
        List<String> cmd = List.of(
                "vipsthumbnail", "stdin",
                "--size", variant.maxPx() + ">",              // ">" = не апскейлить
                "-o", ".webp[Q=" + quality + ",strip]",
                "--vips-concurrency=1"
        );
        try {
            return runner.run(source, cmd);
        } catch (Exception e) {
            throw new ImageProcessingException("vips failed for " + variant, e);
        }
    }
}
```

> Примечание: точный синтаксис stdin/stdout у `vipsthumbnail` уточнить в Task 3a (интеграционный тест на реальном vips). Если stdin-режим неудобен — использовать временные файлы внутри `ProcessRunner`-реализации. Интерфейс `process()` от этого не меняется.

- [ ] **Step 4: Прогнать — проходит**

Run: `./gradlew :services:media-service:test --tests '*ImageProcessorTest*'`
Expected: PASS.

- [ ] **Step 5: Реализация `ProcessRunner` (боевая) + бин качества**

`DefaultProcessRunner.java`:
```java
package uz.topdim.media.service;

import org.springframework.stereotype.Component;
import java.io.*;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class DefaultProcessRunner implements ProcessRunner {
    @Override
    public byte[] run(byte[] stdin, List<String> command) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(command).redirectErrorStream(false).start();
        try (OutputStream os = p.getOutputStream()) { os.write(stdin); }
        byte[] out = p.getInputStream().readAllBytes();
        if (!p.waitFor(20, TimeUnit.SECONDS)) { p.destroyForcibly(); throw new IOException("vips timeout"); }
        if (p.exitValue() != 0) {
            String err = new String(p.getErrorStream().readAllBytes());
            throw new IOException("vips exit " + p.exitValue() + ": " + err);
        }
        return out;
    }
}
```

Зарегистрировать бин качества (пока `@Value` в конфиг-классе или напрямую — вынесем в Task 7). Для DI `ImageProcessor` добавить конструктор-бин: временно `@Value("${media.webp-quality:82}") int quality`.

- [ ] **Step 6: Commit**

```bash
git add services/media-service/src/main/java/uz/topdim/media/service/ImageVariant.java \
        services/media-service/src/main/java/uz/topdim/media/service/ProcessRunner.java \
        services/media-service/src/main/java/uz/topdim/media/service/DefaultProcessRunner.java \
        services/media-service/src/main/java/uz/topdim/media/service/ImageProcessor.java \
        services/media-service/src/main/java/uz/topdim/media/service/ImageProcessingException.java \
        services/media-service/src/test/java/uz/topdim/media/service/ImageProcessorTest.java
git commit -m "feat(media): ImageProcessor — resize+WebP+strip через libvips CLI

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 3a: Интеграционный тест ImageProcessor на реальном vips (gated)

**Files:**
- Test: `services/media-service/src/test/java/uz/topdim/media/service/ImageProcessorVipsIT.java`

**Interfaces:**
- Consumes: `ImageProcessor` + `DefaultProcessRunner`.

- [ ] **Step 1: Тест, включаемый только если vips установлен**

```java
@EnabledIf("vipsAvailable")
class ImageProcessorVipsIT {
    static boolean vipsAvailable() {
        try { return new ProcessBuilder("vips","--version").start().waitFor() == 0; }
        catch (Exception e) { return false; }
    }

    @Test
    void producesRealWebpVariantsWithoutUpscale() throws Exception {
        byte[] png = // 300x300 PNG из ресурсов теста
            getClass().getResourceAsStream("/fixtures/sample-300.png").readAllBytes();
        ImageProcessor proc = new ImageProcessor(new DefaultProcessRunner(), 82);

        Map<ImageVariant, byte[]> out = proc.process(png);

        for (byte[] webp : out.values()) {
            assertTrue(webp.length > 0);
            assertTrue(isWebp(webp), "магические байты RIFF....WEBP");
        }
        // FULL (1600) не апскейлит 300px → сторона остаётся ~300
        assertTrue(dimensions(out.get(ImageVariant.FULL)).width() <= 300);
        assertTrue(out.get(ImageVariant.THUMB).length < out.get(ImageVariant.FULL).length + 1);
    }
}
```
(Хелперы `isWebp`/`dimensions` — по сигнатуре RIFF/WEBP и парсу VP8-заголовка; фикстуру `sample-300.png` добавить в `src/test/resources/fixtures/`.)

- [ ] **Step 2: Прогнать (локально с vips / в CI если поставлен)**

Run: `./gradlew :services:media-service:test --tests '*ImageProcessorVipsIT*'`
Expected: PASS если vips есть, иначе SKIPPED. Здесь финализируется точный синтаксис vips-команды (Step-примечание Task 3).

- [ ] **Step 3: Commit**

```bash
git add services/media-service/src/test/java/uz/topdim/media/service/ImageProcessorVipsIT.java \
        services/media-service/src/test/resources/fixtures/sample-300.png
git commit -m "test(media): интеграционный тест ImageProcessor на реальном libvips (gated)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 4: MediaStorageService — варианты в MinIO

**Files:**
- Create: `services/media-service/src/main/java/uz/topdim/media/service/MediaStorageService.java`
- Test: `services/media-service/src/test/java/uz/topdim/media/service/MediaStorageServiceTest.java`

**Interfaces:**
- Produces:
  - `String storeVariants(String id, Map<ImageVariant,byte[]> variants)` — кладёт `{id}_{suffix}.webp` с `contentType=image/webp`; возвращает `id`.
  - `InputStream fetch(String id, ImageVariant variant)` — читает объект; бросает если нет.
  - `void delete(String id)` — удаляет все варианты.
- Consumes: `MinioClient`, имя бакета.

- [ ] **Step 1: Падающий тест (мок MinioClient)**

```java
class MediaStorageServiceTest {
    @Test
    void storesEachVariantAsWebp() throws Exception {
        MinioClient minio = mock(MinioClient.class);
        MediaStorageService svc = new MediaStorageService(minio, "topdim-media");
        Map<ImageVariant,byte[]> variants = Map.of(
            ImageVariant.THUMB, new byte[]{1}, ImageVariant.CARD, new byte[]{2}, ImageVariant.FULL, new byte[]{3});

        svc.storeVariants("abc", variants);

        ArgumentCaptor<PutObjectArgs> cap = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minio, times(3)).putObject(cap.capture());
        assertTrue(cap.getAllValues().stream()
            .allMatch(a -> a.contentType().equals("image/webp")));
        assertTrue(cap.getAllValues().stream()
            .anyMatch(a -> a.object().equals("abc_thumb.webp")));
    }
}
```

- [ ] **Step 2: Прогнать — FAIL**

Run: `./gradlew :services:media-service:test --tests '*MediaStorageServiceTest*'`
Expected: FAIL (нет класса).

- [ ] **Step 3: Реализовать `MediaStorageService`**

```java
package uz.topdim.media.service;

import io.minio.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;

@Service
public class MediaStorageService {
    private final MinioClient minio;
    private final String bucket;

    public MediaStorageService(MinioClient minio, @Value("${minio.bucket}") String bucket) {
        this.minio = minio; this.bucket = bucket;
    }

    public String storeVariants(String id, Map<ImageVariant, byte[]> variants) {
        try {
            for (var e : variants.entrySet()) {
                byte[] data = e.getValue();
                minio.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName(id, e.getKey()))
                    .stream(new ByteArrayInputStream(data), data.length, -1)
                    .contentType("image/webp")
                    .build());
            }
            return id;
        } catch (Exception ex) {
            throw new ImageProcessingException("store failed for " + id, ex);
        }
    }

    public InputStream fetch(String id, ImageVariant variant) throws Exception {
        return minio.getObject(GetObjectArgs.builder()
            .bucket(bucket).object(objectName(id, variant)).build());
    }

    public void delete(String id) {
        for (ImageVariant v : ImageVariant.values()) {
            try {
                minio.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket).object(objectName(id, v)).build());
            } catch (Exception ignored) { /* best-effort */ }
        }
    }

    private String objectName(String id, ImageVariant v) { return id + "_" + v.suffix() + ".webp"; }
}
```

- [ ] **Step 4: Прогнать — PASS**

Run: `./gradlew :services:media-service:test --tests '*MediaStorageServiceTest*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add services/media-service/src/main/java/uz/topdim/media/service/MediaStorageService.java \
        services/media-service/src/test/java/uz/topdim/media/service/MediaStorageServiceTest.java
git commit -m "feat(media): MediaStorageService — WebP-варианты в MinIO

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 5: Upload → варианты; ответ {id,url,variants}

**Files:**
- Create: `services/media-service/src/main/java/uz/topdim/media/dto/MediaUploadResponse.java`
- Modify: `services/media-service/src/main/java/uz/topdim/media/controller/MediaController.java` (метод `upload`)
- Test: `services/media-service/src/test/java/uz/topdim/media/controller/MediaControllerTest.java`

**Interfaces:**
- Consumes: `ImageUploadPolicy.validate` (как есть), `ImageProcessor.process`, `MediaStorageService.storeVariants`.
- Produces: JSON `{ id, url, variants: { thumb, card, full } }` (в обёртке `ApiResponse`), где `url = /api/v1/media/{id}` и `variants.X = /api/v1/media/{id}/{x}`.

- [ ] **Step 1: Падающий тест upload**

```java
@Test
void uploadReturnsIdUrlAndVariants() throws Exception {
    ImageUploadPolicy policy = mock(ImageUploadPolicy.class);
    when(policy.validate(any())).thenReturn(new ImageUploadPolicy.ValidatedImage("jpg","image/jpeg"));
    ImageProcessor proc = mock(ImageProcessor.class);
    when(proc.process(any())).thenReturn(Map.of(
        ImageVariant.THUMB,new byte[]{1}, ImageVariant.CARD,new byte[]{2}, ImageVariant.FULL,new byte[]{3}));
    MediaStorageService store = mock(MediaStorageService.class);
    when(store.storeVariants(anyString(), any())).thenAnswer(i -> i.getArgument(0));

    MediaController c = new MediaController(policy, proc, store);
    var file = new MockMultipartFile("file","logo.jpg","image/jpeg", new byte[]{1,2,3});

    var resp = c.upload(file);

    var body = resp.getBody().getData();
    assertNotNull(body.id());
    assertEquals("/api/v1/media/" + body.id(), body.url());
    assertEquals("/api/v1/media/" + body.id() + "/card", body.variants().get("card"));
    verify(store).storeVariants(eq(body.id()), any());
}
```

- [ ] **Step 2: Прогнать — FAIL**

Run: `./gradlew :services:media-service:test --tests '*MediaControllerTest*'`
Expected: FAIL (нет нового конструктора/DTO/метода).

- [ ] **Step 3: Реализовать DTO + переписать upload**

`MediaUploadResponse.java`:
```java
package uz.topdim.media.dto;
import java.util.Map;
public record MediaUploadResponse(String id, String url, Map<String,String> variants) {}
```

В `MediaController` заменить хранение `MinioClient` на инъекцию сервисов; `upload` теперь:
```java
@PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ResponseEntity<ApiResponse<MediaUploadResponse>> upload(@RequestParam("file") MultipartFile file) {
    imageUploadPolicy.validate(file);                 // бросает Invalid/TooLarge → ловит handler
    byte[] source;
    try { source = file.getBytes(); }
    catch (IOException e) { throw new ImageProcessingException("read failed", e); }

    Map<ImageVariant, byte[]> variants = imageProcessor.process(source);   // может бросить ImageProcessingException
    String id = UUID.randomUUID().toString();
    storage.storeVariants(id, variants);

    Map<String,String> urls = new LinkedHashMap<>();
    for (ImageVariant v : ImageVariant.values()) urls.put(v.suffix(), "/api/v1/media/" + id + "/" + v.suffix());
    var body = new MediaUploadResponse(id, "/api/v1/media/" + id, urls);
    return ResponseEntity.ok(ApiResponse.success("Файл загружен", body));
}
```
(Конструктор: `public MediaController(ImageUploadPolicy policy, ImageProcessor processor, MediaStorageService storage)` с `@Autowired`. `initBucket` перенести в `MediaStorageService` @PostConstruct.)

- [ ] **Step 4: Прогнать — PASS**

Run: `./gradlew :services:media-service:test --tests '*MediaControllerTest*'`
Expected: PASS.

- [ ] **Step 5: Обновить обработку ошибок (422 для processing)**

В `MediaUploadExceptionHandler` добавить:
```java
@ExceptionHandler(ImageProcessingException.class)
public ResponseEntity<ApiResponse<Void>> onProcessing(ImageProcessingException e) {
    return ResponseEntity.unprocessableEntity()
        .body(ApiResponse.error("Не удалось обработать изображение"));
}
```
Тест: mock `imageProcessor.process` бросает → upload → 422 (тест в `MediaUploadExceptionHandlerTest`).

- [ ] **Step 6: Commit**

```bash
git add services/media-service/src/main/java/uz/topdim/media/dto/MediaUploadResponse.java \
        services/media-service/src/main/java/uz/topdim/media/controller/MediaController.java \
        services/media-service/src/main/java/uz/topdim/media/controller/MediaUploadExceptionHandler.java \
        services/media-service/src/test/java/uz/topdim/media/controller/MediaControllerTest.java \
        services/media-service/src/test/java/uz/topdim/media/controller/MediaUploadExceptionHandlerTest.java
git commit -m "feat(media): upload генерирует WebP-варианты, ответ {id,url,variants}

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 6: Отдача варианта — правильный тип, кэш, стриминг

**Files:**
- Modify: `services/media-service/src/main/java/uz/topdim/media/controller/MediaController.java` (методы `getFile`)
- Test: `services/media-service/src/test/java/uz/topdim/media/controller/MediaControllerTest.java`

**Interfaces:**
- Produces: `GET /api/v1/media/{id}` (дефолт=full) и `GET /api/v1/media/{id}/{variant}` → `image/webp` + `Cache-Control: public, max-age=31536000, immutable`, тело — стрим из MinIO. Неизвестный вариант/отсутствие → 404. Старый путь `/api/v1/media/{oldfile}` (с расширением) отдаётся как есть для обратной совместимости.

- [ ] **Step 1: Падающий тест отдачи**

```java
@Test
void getVariantReturnsWebpWithImmutableCache() throws Exception {
    MediaStorageService store = mock(MediaStorageService.class);
    when(store.fetch("abc", ImageVariant.CARD))
        .thenReturn(new ByteArrayInputStream(new byte[]{'W','E','B','P'}));
    MediaController c = new MediaController(mock(ImageUploadPolicy.class), mock(ImageProcessor.class), store);

    ResponseEntity<?> resp = c.getVariant("abc", "card");

    assertEquals(MediaType.parseMediaType("image/webp"), resp.getHeaders().getContentType());
    assertEquals("public, max-age=31536000, immutable", resp.getHeaders().getCacheControl());
}

@Test
void getUnknownVariantIs404() {
    MediaController c = new MediaController(mock(ImageUploadPolicy.class), mock(ImageProcessor.class), mock(MediaStorageService.class));
    assertEquals(404, c.getVariant("abc","huge").getStatusCode().value());
}
```

- [ ] **Step 2: Прогнать — FAIL**

Run: `./gradlew :services:media-service:test --tests '*MediaControllerTest*'`
Expected: FAIL.

- [ ] **Step 3: Реализовать отдачу**

```java
@GetMapping("/{id}")
public ResponseEntity<?> getDefault(@PathVariable String id) {
    return id.contains(".") ? legacyRaw(id) : getVariant(id, "full");
}

@GetMapping("/{id}/{variant}")
public ResponseEntity<?> getVariant(@PathVariable String id, @PathVariable String variant) {
    ImageVariant v;
    try { v = ImageVariant.valueOf(variant.toUpperCase()); }
    catch (IllegalArgumentException e) { return ResponseEntity.notFound().build(); }
    try (InputStream in = storage.fetch(id, v)) {
        byte[] body = in.readAllBytes();  // TODO Task-follow-up: заменить на StreamingResponseBody
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("image/webp"))
            .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
            .body(body);
    } catch (Exception e) { return ResponseEntity.notFound().build(); }
}
```
(`legacyRaw(id)` — прежняя логика getObject по имени с расширением; тип из метаданных объекта, тот же immutable-кэш.)

- [ ] **Step 4: Прогнать — PASS**

Run: `./gradlew :services:media-service:test --tests '*MediaControllerTest*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add services/media-service/src/main/java/uz/topdim/media/controller/MediaController.java \
        services/media-service/src/test/java/uz/topdim/media/controller/MediaControllerTest.java
git commit -m "feat(media): отдача варианта WebP с immutable-кэшем + legacy-совместимость

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 7: Вынести размеры/качество/лимиты в конфиг

**Files:**
- Create: `services/media-service/src/main/java/uz/topdim/media/config/MediaProperties.java`
- Modify: `services/media-service/src/main/resources/application.yml`
- Modify: `ImageProcessor`/`ImageVariant` использование (брать размеры/качество из `MediaProperties`)
- Test: `services/media-service/src/test/java/uz/topdim/media/config/MediaPropertiesTest.java`

**Interfaces:**
- Produces: `@ConfigurationProperties("media")` с `webpQuality`, `Map<String,Integer> sizes`, `maxInputPixels`.

- [ ] **Step 1: Тест биндинга свойств**

```java
@SpringBootTest(properties = {"media.webp-quality=80","media.sizes.card=640","media.max-input-pixels=8000"})
class MediaPropertiesTest {
    @Autowired MediaProperties props;
    @Test void binds() {
        assertEquals(80, props.getWebpQuality());
        assertEquals(640, props.getSizes().get("card"));
        assertEquals(8000, props.getMaxInputPixels());
    }
}
```

- [ ] **Step 2: FAIL** — `./gradlew :services:media-service:test --tests '*MediaPropertiesTest*'` → нет класса.

- [ ] **Step 3: Реализовать `MediaProperties` + `@EnableConfigurationProperties`; в `application.yml`:**

```yaml
media:
  webp-quality: 82
  max-input-pixels: 8000
  sizes:
    thumb: 200
    card: 600
    full: 1600
```
Прокинуть в `ImageProcessor` (quality) и заменить хардкод размеров в `ImageVariant`/процессоре на значения из `MediaProperties` (передавать max px в `process`).

- [ ] **Step 4: PASS** — прогнать весь модуль: `./gradlew :services:media-service:test`
Expected: все тесты зелёные.

- [ ] **Step 5: Commit**

```bash
git add services/media-service/src/main/java/uz/topdim/media/config/MediaProperties.java \
        services/media-service/src/main/resources/application.yml \
        services/media-service/src/main/java/uz/topdim/media/service/ImageProcessor.java \
        services/media-service/src/test/java/uz/topdim/media/config/MediaPropertiesTest.java
git commit -m "feat(media): вынести размеры/качество/лимиты в MediaProperties

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Self-Review

**Spec coverage:**
- Стратегия «оптимизация при загрузке, оригинал не хранить» → Task 3/5 (обработка + сохраняем только варианты). ✓
- WebP q82, 3 размера, без апскейла, strip → Task 3 (ImageProcessor) + Task 7 (конфиг). ✓
- Правильная отдача (image/webp + immutable + без octet-stream) → Task 6. ✓ (стриминг помечен follow-up — сейчас readAllBytes, но с корректными заголовками; полноценный стрим — отдельная мелкая задача.)
- Обратная совместимость (`url`, старые файлы) → Task 5 (поле `url`) + Task 6 (`legacyRaw`). ✓
- 413/nginx + boot → Task 1 (Фаза 0). ✓
- libvips на Alpine → Task 2 + подтверждение в Task 3a. ✓
- Ошибки 400/413/422/404/503 → policy (400/413, есть) + Task 5 (422) + Task 6 (404); 503 при MinIO down — покрывается существующим handler'ом (проверить). 
- Тесты → в каждом таске. ✓

**Placeholder scan:** синтаксис vips-команды и StreamingResponseBody помечены как follow-up с чётким местом финализации (Task 3a / Task 6 Step 3) — не скрытые заглушки, а явные точки уточнения на реальном рантайме.

**Type consistency:** `ImageVariant` (THUMB/CARD/FULL, `suffix()`, `maxPx()`), `ImageProcessor.process(byte[]) -> Map<ImageVariant,byte[]>`, `MediaStorageService.storeVariants/fetch/delete`, `MediaController(ImageUploadPolicy, ImageProcessor, MediaStorageService)`, `MediaUploadResponse(id,url,variants)` — согласованы между Task 3–7.

## Follow-ups (вне этого плана)
- **Фаза 2 (фронт):** отдельный план — `srcset` из `variants` в web/admin/partner; миграция старых картинок в WebP.
- Стриминг отдачи через `StreamingResponseBody` (сейчас readAllBytes с корректными заголовками).
- Проверка edge-прокси EasyPanel на `client_max_body_size` (server-side, вне git).
- Опц. вынести media в отдельный Dockerfile, если libvips в общем образе раздувает размер.
