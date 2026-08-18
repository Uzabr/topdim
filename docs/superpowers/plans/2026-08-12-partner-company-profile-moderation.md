# Partner Company Profile Moderation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Позволить OWNER и MANAGER готовить до 10 параллельных полных заявок на изменение компании и филиалов, а MODERATOR/ADMIN/SUPER_ADMIN — атомарно одобрять, возвращать или отклонять их с in-app и Telegram-уведомлениями.

**Architecture:** Coupon-service владеет опубликованным merchant-профилем, версионированными снимками заявок, модерацией и transactional outbox. Identity-service остаётся источником partner access context и контактных каналов получателей. Partner/admin SPA используют отдельные feature-модули; notification-service идемпотентно создаёт in-app уведомления и независимо доставляет Telegram/email.

**Tech Stack:** Java 21, Spring Boot 3.4, Spring Data JPA, PostgreSQL 17/Flyway, RabbitMQ, OpenFeign, React 19, TypeScript, Ant Design, React Query, Vitest/Testing Library, JUnit 5, Testcontainers.

## Global Constraints

- Публичный профиль не изменяется до одобрения заявки.
- Каждая заявка хранит полный снимок компании и филиалов и ссылается на `baseProfileVersion`.
- OWNER и MANAGER создают и отправляют заявки; CASHIER не видит и не вызывает этот контур.
- Активные статусы: `DRAFT`, `PENDING_REVIEW`, `IN_REVIEW`, `REVISION_REQUESTED`; максимум 10 на merchant, включая конкурентные запросы.
- MODERATOR, ADMIN и SUPER_ADMIN рассматривают заявки; решение принимает назначенный исполнитель, а ADMIN/SUPER_ADMIN могут освободить или переназначить.
- `REVISION_REQUESTED` и `REJECTED` требуют комментарий; `WITHDRAWN` после отправки требует причину.
- Одобрение профиля и перевод конкурирующих заявок прежней версии в `OUTDATED` выполняются одной транзакцией.
- Логотип и обложка необязательны; браузер не задаёт multipart `Content-Type` вручную.
- Филиалы не удаляются физически; нельзя отключить primary-филиал или филиал с активными кассирами.
- In-app создаётся всегда; Telegram доставляется при привязке; внешний сбой не откатывает бизнес-решение.
- Юридические/банковские реквизиты, документы, выплаты и частичное одобрение не входят в этот план.
- Перед началом реализации убедиться, что PR #123 с исправлением multipart-загрузки влит в `main`; если нет — не копировать старый ручной заголовок в новые API.
- Каждый task выполняется через RED → GREEN → refactor, завершается отдельным коммитом и push после проверки.

## File Structure

### Coupon-service

- `entity/MerchantProfileChangeRequest.java` — корень заявки и status/version metadata.
- `entity/MerchantProfileChangeLocation.java` — снимок филиала.
- `entity/MerchantProfileChangeHistory.java` — append-only история переходов.
- `entity/MerchantProfileChangeStatus.java` — конечный enum состояний.
- `entity/NotificationOutbox.java` — надёжная публикация событий получателям.
- `repository/MerchantProfileChangeRequestRepository.java` — блокировки, очередь, лимит, устаревание.
- `repository/MerchantProfileChangeLocationRepository.java` — загрузка снимка филиалов.
- `repository/MerchantProfileChangeHistoryRepository.java` — аудит переходов.
- `repository/NotificationOutboxRepository.java` — pending outbox и idempotency.
- `client/IdentityPartnerAccessClient.java` — access context и активные location ID сотрудников.
- `dto/merchantprofile/*` — partner/admin request/response/filter contracts.
- `service/PartnerAccessResolver.java` — fail-closed OWNER/MANAGER/CASHIER resolution.
- `service/MerchantProfileDraftService.java` — draft CRUD, copy, submit, withdraw.
- `service/MerchantProfileModerationService.java` — claim/release/reassign/decision/approval.
- `service/MerchantProfileMapper.java` — снимки и ответы без бизнес-переходов.
- `service/MerchantProfileOutboxService.java` — recipient de-duplication и запись событий.
- `service/NotificationOutboxPublisher.java` — публикация committed outbox в RabbitMQ.
- `controller/PartnerMerchantProfileChangeController.java` — partner API.
- `controller/AdminMerchantProfileChangeController.java` — moderation API.
- `resources/db/migration/V32__merchant_profile_change_requests.sql` — versioned requests/history.
- `resources/db/migration/V33__merchant_profile_notification_outbox.sql` — outbox.

### Identity-service

- `PartnerStaffService.java` — строгие роли STAFF и полноценный MANAGER context.
- `InternalPartnerAccessController.java` — active staff location IDs.
- `InternalNotificationTargetController.java` — email/Telegram target by user ID.
- `dto/InternalNotificationTargetResponse.java` — защищённый internal response.

### Notification-service/shared events

- `shared/common-events/.../NotificationEvent.java` — `eventKey`, recipient and deep link metadata.
- `notification/entity/Notification.java` — unique event key for in-app idempotency.
- `notification/entity/NotificationDelivery.java` — Telegram/email delivery status.
- `notification/service/TelegramNotificationSender.java` — Telegram Bot API adapter.
- `notification/service/NotificationDeliveryService.java` — channel fan-out/retry state.
- `notification/client/IdentityNotificationTargetClient.java` — protected target resolution.
- `notification/resources/db/migration/V2__notification_idempotency_and_delivery.sql` — unique constraints/delivery table.

### Partner frontend

- `features/company/types.ts` — exact API models/statuses.
- `features/company/api.ts` — company/change-request/media API.
- `features/company/permissions.ts` — role/status actions.
- `features/company/CompanyProfilePage.tsx` — published profile + applications tabs.
- `features/company/CompanyRequestsTable.tsx` — paginated history/actions.
- `features/company/CompanyRequestEditorPage.tsx` — full snapshot form.
- `features/company/CompanyLocationFields.tsx` — isolated location editor.
- `features/company/CompanyProfilePreview.tsx` — read-only preview.

### Admin frontend

- `features/merchant-profile-changes/types.ts` — moderation contracts.
- `features/merchant-profile-changes/api.ts` — filters/actions.
- `features/merchant-profile-changes/MerchantProfileChangeQueuePage.tsx` — queue.
- `features/merchant-profile-changes/MerchantProfileChangeDetailPage.tsx` — side-by-side diff/actions.
- `features/merchant-profile-changes/ProfileFieldDiff.tsx` — scalar comparison.
- `features/merchant-profile-changes/LocationDiff.tsx` — added/changed/disabled locations.

---

### Task 1: Persist versioned profile requests

**Files:**
- Create: `services/coupon-service/src/main/resources/db/migration/V32__merchant_profile_change_requests.sql`
- Create: `services/coupon-service/src/main/java/uz/topdim/coupon/entity/MerchantProfileChangeStatus.java`
- Create: `services/coupon-service/src/main/java/uz/topdim/coupon/entity/MerchantProfileChangeRequest.java`
- Create: `services/coupon-service/src/main/java/uz/topdim/coupon/entity/MerchantProfileChangeLocation.java`
- Create: `services/coupon-service/src/main/java/uz/topdim/coupon/entity/MerchantProfileChangeHistory.java`
- Create: `services/coupon-service/src/main/java/uz/topdim/coupon/repository/MerchantProfileChangeRequestRepository.java`
- Create: `services/coupon-service/src/main/java/uz/topdim/coupon/repository/MerchantProfileChangeLocationRepository.java`
- Create: `services/coupon-service/src/main/java/uz/topdim/coupon/repository/MerchantProfileChangeHistoryRepository.java`
- Modify: `services/coupon-service/src/main/java/uz/topdim/coupon/entity/Merchant.java`
- Modify: `services/coupon-service/src/main/java/uz/topdim/coupon/repository/MerchantRepository.java`
- Test: `services/coupon-service/src/test/java/uz/topdim/coupon/repository/MerchantProfileChangeRepositoryTest.java`

**Interfaces:**
- Produces: `Merchant.profileVersion`, `MerchantProfileChangeStatus`, locked repository reads used by every later backend task.

- [ ] **Step 1: Write the failing repository tests**

```java
@Test
void storesFullSnapshotWithLocationsAndHistory() {
    MerchantProfileChangeRequest saved = requestRepository.save(requestFor(merchant, 1L));
    locationRepository.save(locationFor(saved, primaryLocation.getId(), true, true));
    historyRepository.save(history(saved, null, MerchantProfileChangeStatus.DRAFT, authorId));

    MerchantProfileChangeRequest loaded = requestRepository.findDetailedById(saved.getId()).orElseThrow();
    assertThat(loaded.getBaseProfileVersion()).isEqualTo(1L);
    assertThat(loaded.getLocations()).singleElement().satisfies(location -> {
        assertThat(location.getSourceLocationId()).isEqualTo(primaryLocation.getId());
        assertThat(location.isPrimary()).isTrue();
    });
}

@Test
void locksMerchantBeforeCountingActiveRequests() {
    assertThat(merchantRepository.findByIdForUpdate(merchant.getId())).isPresent();
    assertThat(requestRepository.countByMerchantIdAndStatusIn(
        merchant.getId(), MerchantProfileChangeStatus.activeStatuses())).isZero();
}
```

- [ ] **Step 2: Run RED**

Run: `./gradlew :services:coupon-service:test --tests '*MerchantProfileChangeRepositoryTest'`

Expected: compilation FAIL because entities/repositories and `profileVersion` do not exist.

- [ ] **Step 3: Add migration and minimal persistence model**

```sql
ALTER TABLE merchants ADD COLUMN profile_version BIGINT NOT NULL DEFAULT 1;

CREATE TABLE merchant_profile_change_requests (
  id BIGSERIAL PRIMARY KEY,
  merchant_id BIGINT NOT NULL REFERENCES merchants(id),
  author_user_id BIGINT NOT NULL,
  author_staff_id BIGINT,
  author_role VARCHAR(16) NOT NULL CHECK (author_role IN ('OWNER','MANAGER')),
  base_profile_version BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL,
  assignee_user_id BIGINT,
  moderation_comment VARCHAR(2000),
  name VARCHAR(255) NOT NULL,
  description TEXT,
  logo_url VARCHAR(500), cover_url VARCHAR(500),
  email VARCHAR(255), website VARCHAR(500), contact_person VARCHAR(255),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  submitted_at TIMESTAMP, assigned_at TIMESTAMP, decided_at TIMESTAMP, withdrawn_at TIMESTAMP,
  lock_version BIGINT NOT NULL DEFAULT 0
);
```

Add `merchant_profile_change_locations`, `merchant_profile_change_history`, the three indexes from the design, and JPA mappings with `@Version private long lockVersion`.

```java
public enum MerchantProfileChangeStatus {
    DRAFT, PENDING_REVIEW, IN_REVIEW, REVISION_REQUESTED,
    APPROVED, REJECTED, WITHDRAWN, OUTDATED;

    public static List<MerchantProfileChangeStatus> activeStatuses() {
        return List.of(DRAFT, PENDING_REVIEW, IN_REVIEW, REVISION_REQUESTED);
    }
}
```

Add pessimistic locks:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select m from Merchant m where m.id = :id")
Optional<Merchant> findByIdForUpdate(@Param("id") Long id);

@Lock(LockModeType.PESSIMISTIC_WRITE)
@EntityGraph(attributePaths = "locations")
@Query("select r from MerchantProfileChangeRequest r where r.id = :id")
Optional<MerchantProfileChangeRequest> findDetailedByIdForUpdate(@Param("id") Long id);
```

- [ ] **Step 4: Run GREEN and migration validation**

Run: `./gradlew :services:coupon-service:test --tests '*MerchantProfileChangeRepositoryTest'`

Expected: PASS, including Flyway startup against the repository integration test database.

- [ ] **Step 5: Commit and push**

```bash
git add services/coupon-service/src/main services/coupon-service/src/test/java/uz/topdim/coupon/repository/MerchantProfileChangeRepositoryTest.java
git commit -m "feat(merchant): persist profile change requests"
git push
```

### Task 2: Enable real MANAGER accounts safely

**Files:**
- Modify: `services/identity-service/src/main/java/uz/topdim/identity/service/PartnerStaffService.java`
- Modify: `services/identity-service/src/main/java/uz/topdim/identity/repository/StaffRepository.java`
- Modify: `services/identity-service/src/main/java/uz/topdim/identity/dto/CreateStaffRequest.java`
- Modify: `frontend/partner/src/pages/StaffPage.tsx`
- Test: `services/identity-service/src/test/java/uz/topdim/identity/service/PartnerStaffServiceTest.java`
- Test: `frontend/partner/src/pages/StaffPage.test.tsx`

**Interfaces:**
- Produces: staff roles restricted to `CASHIER|MANAGER`; MANAGER context with company-wide access and no location requirement.

- [ ] **Step 1: Add RED tests for MANAGER creation and invalid roles**

```java
@Test
void addManager_createsLoginWithoutLocation() {
    CreateStaffRequest request = staffRequest("MANAGER", null, "manager@sizbiz.uz", "Strong123!");
    PartnerStaffResponse response = service.addStaff(ownerId, request);
    assertThat(response.getRole()).isEqualTo("MANAGER");
    assertThat(response.getMerchantLocationId()).isNull();
}

@Test
void addStaff_rejectsUnknownRole() {
    assertThatThrownBy(() -> service.addStaff(ownerId, staffRequest("OWNER", null, "x@sizbiz.uz", "Strong123!")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Допустимы роли CASHIER или MANAGER");
}
```

Frontend test:

```tsx
fireEvent.mouseDown(screen.getByLabelText('Роль'));
expect(await screen.findByText('Менеджер')).toBeTruthy();
fireEvent.click(screen.getByText('Менеджер'));
expect(screen.queryByLabelText('Филиал (обязательно)')).toBeNull();
```

- [ ] **Step 2: Run RED**

Run: `./gradlew :services:identity-service:test --tests '*PartnerStaffServiceTest'`

Run: `npm test -- --run src/pages/StaffPage.test.tsx` from `frontend/partner`.

Expected: backend rejects/handles role incorrectly and frontend lacks MANAGER option/test file.

- [ ] **Step 3: Implement strict role validation and conditional form**

```java
private String normalizeStaffRole(String rawRole) {
    String role = rawRole == null ? "CASHIER" : rawRole.trim().toUpperCase(Locale.ROOT);
    if (!Set.of("CASHIER", "MANAGER").contains(role)) {
        throw new IllegalArgumentException("Допустимы роли CASHIER или MANAGER");
    }
    return role;
}
```

Require login email and strong temporary password for both roles; require/validate location only for CASHIER. In `StaffPage`, watch `role`, show MANAGER, and conditionally require location.

- [ ] **Step 4: Run GREEN**

Run both commands from Step 2. Expected: PASS.

- [ ] **Step 5: Commit and push**

```bash
git add services/identity-service frontend/partner/src/pages/StaffPage.tsx frontend/partner/src/pages/StaffPage.test.tsx
git commit -m "feat(partner): enable manager staff accounts"
git push
```

### Task 3: Resolve partner access in coupon-service

**Files:**
- Create: `services/coupon-service/src/main/java/uz/topdim/coupon/client/IdentityPartnerAccessClient.java`
- Create: `services/coupon-service/src/main/java/uz/topdim/coupon/client/PartnerAccessContext.java`
- Create: `services/coupon-service/src/main/java/uz/topdim/coupon/service/PartnerAccessResolver.java`
- Modify: `services/coupon-service/src/main/java/uz/topdim/coupon/config/FeignInternalAuthConfig.java`
- Modify: `services/coupon-service/src/main/java/uz/topdim/coupon/controller/PartnerMerchantController.java`
- Modify: `services/coupon-service/src/main/java/uz/topdim/coupon/service/MerchantService.java`
- Modify: `services/coupon-service/src/main/java/uz/topdim/coupon/dto/MerchantResponse.java`
- Test: `services/coupon-service/src/test/java/uz/topdim/coupon/service/PartnerAccessResolverTest.java`
- Test: `services/coupon-service/src/test/java/uz/topdim/coupon/controller/PartnerMerchantControllerTest.java`

**Interfaces:**
- Consumes: identity `GET /api/v1/internal/partner-access/{userId}`.
- Produces: `ResolvedPartnerAccess resolveOwnerOrManager(Long userId)` with merchantId/role/staffId; published profile includes `profileVersion`.

- [ ] **Step 1: Write RED access tests**

```java
@Test
void managerReadsPublishedMerchantThroughResolvedMerchantId() {
    when(identityClient.getPartnerAccessContext(44L)).thenReturn(success(context("MANAGER", 7L, 12L)));
    assertThat(resolver.resolveOwnerOrManager(44L))
        .extracting(ResolvedPartnerAccess::merchantId, ResolvedPartnerAccess::role)
        .containsExactly(7L, "MANAGER");
}

@Test
void cashierIsForbiddenAndDependencyFailureIsFailClosed() {
    when(identityClient.getPartnerAccessContext(45L)).thenReturn(success(context("CASHIER", 7L, 13L)));
    assertThatThrownBy(() -> resolver.resolveOwnerOrManager(45L)).isInstanceOf(AccessDeniedException.class);
    when(identityClient.getPartnerAccessContext(46L)).thenThrow(new FeignException.ServiceUnavailable("down", request(), null, null));
    assertThatThrownBy(() -> resolver.resolveOwnerOrManager(46L)).isInstanceOf(PartnerAccessUnavailableException.class);
}
```

- [ ] **Step 2: Run RED**

Run: `./gradlew :services:coupon-service:test --tests '*PartnerAccessResolverTest' --tests '*PartnerMerchantControllerTest'`

Expected: compilation FAIL because client/resolver/profile version do not exist.

- [ ] **Step 3: Implement client, resolver and published read**

```java
@FeignClient(name = "identity-service", path = "/api/v1", configuration = FeignInternalAuthConfig.class)
public interface IdentityPartnerAccessClient {
    @GetMapping("/internal/partner-access/{userId}")
    ApiResponse<PartnerAccessContext> getPartnerAccessContext(@PathVariable Long userId);

    @GetMapping("/internal/partner-access/merchants/{merchantId}/active-staff-location-ids")
    ApiResponse<Set<Long>> getActiveStaffLocationIds(@PathVariable Long merchantId);
}
```

Controller calls resolver first and then `merchantService.getMerchant(resolved.merchantId())`; never uses a client-provided merchant ID.

- [ ] **Step 4: Run GREEN**

Run command from Step 2. Expected: PASS.

- [ ] **Step 5: Commit and push**

```bash
git add services/coupon-service/src/main services/coupon-service/src/test
git commit -m "feat(merchant): resolve owner and manager access"
git push
```

### Task 4: Implement partner draft CRUD

**Files:**
- Create: `services/coupon-service/src/main/java/uz/topdim/coupon/dto/merchantprofile/MerchantProfileChangePayload.java`
- Create: `services/coupon-service/src/main/java/uz/topdim/coupon/dto/merchantprofile/MerchantProfileChangeResponse.java`
- Create: `services/coupon-service/src/main/java/uz/topdim/coupon/dto/merchantprofile/MerchantProfileChangeSummary.java`
- Create: `services/coupon-service/src/main/java/uz/topdim/coupon/service/MerchantProfileMapper.java`
- Create: `services/coupon-service/src/main/java/uz/topdim/coupon/service/MerchantProfileDraftService.java`
- Create: `services/coupon-service/src/main/java/uz/topdim/coupon/controller/PartnerMerchantProfileChangeController.java`
- Test: `services/coupon-service/src/test/java/uz/topdim/coupon/service/MerchantProfileDraftServiceTest.java`
- Test: `services/coupon-service/src/test/java/uz/topdim/coupon/controller/PartnerMerchantProfileChangeControllerTest.java`

**Interfaces:**
- Produces: create/list/get/update/delete contracts; `MerchantProfileChangePayload` is reused by partner UI.

- [ ] **Step 1: Write RED tests for owned full snapshots**

```java
@Test
void createDraft_copiesPublishedCompanyAndEveryLocation() {
    MerchantProfileChangeResponse draft = service.createDraft(ownerAccess);
    assertThat(draft.status()).isEqualTo(DRAFT);
    assertThat(draft.baseProfileVersion()).isEqualTo(merchant.getProfileVersion());
    assertThat(draft.locations()).extracting(LocationSnapshotResponse::sourceLocationId)
        .containsExactlyInAnyOrder(11L, 12L);
}

@Test
void getForeignRequest_returnsNotFound() {
    assertThatThrownBy(() -> service.get(requestFromMerchantB, accessForMerchantA))
        .isInstanceOf(ResourceNotFoundException.class);
}

@Test
void delete_onlyRemovesDraft() {
    service.deleteDraft(draftId, access);
    assertThat(repository.findById(draftId)).isEmpty();
    assertThatThrownBy(() -> service.deleteDraft(pendingId, access)).isInstanceOf(IllegalStateException.class);
}
```

- [ ] **Step 2: Run RED**

Run: `./gradlew :services:coupon-service:test --tests '*MerchantProfileDraftServiceTest' --tests '*PartnerMerchantProfileChangeControllerTest'`

Expected: compilation FAIL.

- [ ] **Step 3: Implement minimal draft API**

```java
public record MerchantProfileChangePayload(
    @NotBlank @Size(max = 255) String name,
    @Size(max = 5000) String description,
    @Size(max = 500) String logoUrl,
    @Size(max = 500) String coverUrl,
    @Email String email,
    @Pattern(regexp = "^$|https?://.+") String website,
    @Size(max = 255) String contactPerson,
    @Valid @NotNull List<LocationPayload> locations) {}
```

Create from published merchant under `findByIdForUpdate`, enforce the active-count limit, append DRAFT history, and expose paginated endpoints. Update replaces only the owned `DRAFT` or `REVISION_REQUESTED` snapshot; delete only `DRAFT`.

- [ ] **Step 4: Run GREEN**

Run command from Step 2. Expected: PASS.

- [ ] **Step 5: Commit and push**

```bash
git add services/coupon-service/src/main services/coupon-service/src/test
git commit -m "feat(merchant): add profile change draft API"
git push
```

### Task 5: Validate, submit, withdraw and copy requests

**Files:**
- Modify: `services/coupon-service/src/main/java/uz/topdim/coupon/service/MerchantProfileDraftService.java`
- Create: `services/coupon-service/src/main/java/uz/topdim/coupon/dto/merchantprofile/WithdrawMerchantProfileChangeRequest.java`
- Modify: `services/coupon-service/src/main/java/uz/topdim/coupon/controller/PartnerMerchantProfileChangeController.java`
- Modify: `services/identity-service/src/main/java/uz/topdim/identity/controller/InternalPartnerAccessController.java`
- Modify: `services/identity-service/src/main/java/uz/topdim/identity/repository/StaffRepository.java`
- Test: `services/coupon-service/src/test/java/uz/topdim/coupon/service/MerchantProfileSubmissionTest.java`
- Test: `services/identity-service/src/test/java/uz/topdim/identity/controller/InternalPartnerAccessControllerTest.java`

**Interfaces:**
- Produces: `submit`, `withdraw`, `copy`; identity returns `Set<Long> activeStaffLocationIds`.

- [ ] **Step 1: Write RED validation and lifecycle tests**

```java
@Test
void submit_requiresExactlyOneActivePrimaryAndKeepsImagesOptional() {
    update(draft, payloadWithLocations(primary(true, true), branch(false, true)).withImages(null, null));
    assertThat(service.submit(draft.getId(), access).status()).isEqualTo(PENDING_REVIEW);
}

@Test
void submit_rejectsDisabledPrimaryOrActiveCashierLocation() {
    assertThatThrownBy(() -> submit(payloadWithLocations(primary(true, false))))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("основной филиал");
    when(identityClient.getActiveStaffLocationIds(merchantId)).thenReturn(success(Set.of(12L)));
    assertThatThrownBy(() -> submit(payloadDisabling(12L)))
        .hasMessageContaining("активные кассиры");
}

@Test
void copyOutdatedRequest_rebasesOnCurrentVersion() {
    MerchantProfileChangeResponse copy = service.copy(outdatedId, access);
    assertThat(copy.status()).isEqualTo(DRAFT);
    assertThat(copy.baseProfileVersion()).isEqualTo(merchant.getProfileVersion());
}
```

- [ ] **Step 2: Run RED**

Run: `./gradlew :services:coupon-service:test --tests '*MerchantProfileSubmissionTest'`

Run: `./gradlew :services:identity-service:test --tests '*InternalPartnerAccessControllerTest'`

Expected: FAIL because lifecycle endpoints and staff-location contract do not exist.

- [ ] **Step 3: Implement lifecycle and validation**

Use a single `validateSnapshot(request, activeStaffLocationIds)` method. On submit lock merchant and request, re-count active requests, require current base version, set `PENDING_REVIEW`, clear assignee, set `submittedAt`, append history. Withdraw accepts `PENDING_REVIEW|IN_REVIEW|REVISION_REQUESTED`, requires a trimmed reason, sets `WITHDRAWN`. Copy accepts terminal source and creates a new DRAFT with current published locations merged by `sourceLocationId`.

Identity query:

```java
@Query("select distinct s.merchantLocationId from Staff s where s.merchantId = :merchantId and s.active = true and s.merchantLocationId is not null")
Set<Long> findActiveLocationIdsByMerchantId(@Param("merchantId") Long merchantId);
```

- [ ] **Step 4: Run GREEN and concurrent limit test**

Run commands from Step 2 plus:

`./gradlew :services:coupon-service:test --tests '*MerchantProfileActiveLimitConcurrencyTest'`

Expected: ten successes maximum and one `409`-mapped conflict when eleven creators race.

- [ ] **Step 5: Commit and push**

```bash
git add services/coupon-service services/identity-service
git commit -m "feat(merchant): submit and rebase profile requests"
git push
```

### Task 6: Add moderation queue and assignment

**Files:**
- Create: `services/coupon-service/src/main/java/uz/topdim/coupon/dto/merchantprofile/AdminMerchantProfileChangeFilter.java`
- Create: `services/coupon-service/src/main/java/uz/topdim/coupon/dto/merchantprofile/AssignMerchantProfileChangeRequest.java`
- Create: `services/coupon-service/src/main/java/uz/topdim/coupon/service/MerchantProfileModerationService.java`
- Create: `services/coupon-service/src/main/java/uz/topdim/coupon/controller/AdminMerchantProfileChangeController.java`
- Modify: `services/coupon-service/src/main/java/uz/topdim/coupon/repository/MerchantProfileChangeRequestRepository.java`
- Modify: `infrastructure/api-gateway/src/main/resources/application.yml`
- Test: `services/coupon-service/src/test/java/uz/topdim/coupon/service/MerchantProfileModerationAssignmentTest.java`
- Test: `services/coupon-service/src/test/java/uz/topdim/coupon/controller/AdminMerchantProfileChangeControllerSecurityTest.java`
- Test: `infrastructure/api-gateway/src/test/java/uz/topdim/gateway/config/MerchantProfileRouteTest.java`

**Interfaces:**
- Produces: filtered queue, take/release/reassign operations.

- [ ] **Step 1: Write RED tests**

```java
@Test
void takeToWork_claimsPendingRequestExactlyOnce() {
    assertThat(service.takeToWork(id, moderatorId).status()).isEqualTo(IN_REVIEW);
    assertThatThrownBy(() -> service.takeToWork(id, secondModeratorId))
        .isInstanceOf(IllegalStateException.class);
}

@Test
@WithMockUser(roles = "MODERATOR")
void moderatorCanListAndClaimButCannotReassign() throws Exception {
    mvc.perform(get("/api/v1/admin/merchant-change-requests")).andExpect(status().isOk());
    mvc.perform(post("/api/v1/admin/merchant-change-requests/7/reassign"))
        .andExpect(status().isForbidden());
}
```

- [ ] **Step 2: Run RED**

Run coupon assignment/security tests and `./gradlew :infrastructure:api-gateway:test --tests '*MerchantProfileRouteTest'`.

Expected: missing controller/service/route.

- [ ] **Step 3: Implement atomic claim and admin-only reassignment**

```java
@Modifying(clearAutomatically = true, flushAutomatically = true)
@Query("update MerchantProfileChangeRequest r set r.status = :inReview, r.assigneeUserId = :actor, r.assignedAt = :now where r.id = :id and r.status = :pending")
int claim(@Param("id") Long id, @Param("actor") Long actor,
          @Param("pending") MerchantProfileChangeStatus pending,
          @Param("inReview") MerchantProfileChangeStatus inReview,
          @Param("now") LocalDateTime now);
```

Add gateway path `/api/v1/admin/merchant-change-requests/**` to coupon-service. `release` changes `IN_REVIEW -> PENDING_REVIEW`; `reassign` keeps `IN_REVIEW`, changes assignee, and appends history.

- [ ] **Step 4: Run GREEN**

Run commands from Step 2. Expected: PASS.

- [ ] **Step 5: Commit and push**

```bash
git add services/coupon-service infrastructure/api-gateway
git commit -m "feat(admin): add merchant profile moderation queue"
git push
```

### Task 7: Implement decisions and atomic publication

**Files:**
- Modify: `services/coupon-service/src/main/java/uz/topdim/coupon/service/MerchantProfileModerationService.java`
- Modify: `services/coupon-service/src/main/java/uz/topdim/coupon/service/MerchantService.java`
- Modify: `services/coupon-service/src/main/java/uz/topdim/coupon/repository/MerchantProfileChangeRequestRepository.java`
- Create: `services/coupon-service/src/main/java/uz/topdim/coupon/dto/merchantprofile/ModerationCommentRequest.java`
- Test: `services/coupon-service/src/test/java/uz/topdim/coupon/service/MerchantProfileModerationDecisionTest.java`
- Test: `services/coupon-service/src/test/java/uz/topdim/coupon/service/MerchantProfileApprovalIntegrationTest.java`
- Test: `services/coupon-service/src/test/java/uz/topdim/coupon/service/MerchantProfileApprovalConcurrencyTest.java`

**Interfaces:**
- Produces: `approve`, `requestRevision`, `reject`; admin direct merchant update participates in versioning.

- [ ] **Step 1: Write RED decision tests**

```java
@Test
void revisionAndReject_requireAssignedActorAndComment() {
    assertThatThrownBy(() -> service.requestRevision(id, assigneeId, " "))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.reject(id, anotherModeratorId, "duplicate"))
        .isInstanceOf(AccessDeniedException.class);
}

@Test
void approvePublishesSnapshotAndOutdatesCompetingRequestsAtomically() {
    service.approve(requestA, assigneeId);
    Merchant reloaded = merchantRepository.findById(merchantId).orElseThrow();
    assertThat(reloaded.getName()).isEqualTo("Новое название");
    assertThat(reloaded.getProfileVersion()).isEqualTo(2L);
    assertThat(requestRepository.findById(requestB).orElseThrow().getStatus()).isEqualTo(OUTDATED);
}
```

Concurrency assertion: two simultaneous approvals based on version 1 produce exactly one `APPROVED` and one `409`/`OUTDATED`, never two publications.

- [ ] **Step 2: Run RED**

Run: `./gradlew :services:coupon-service:test --tests '*MerchantProfileModerationDecisionTest' --tests '*MerchantProfileApprovalIntegrationTest' --tests '*MerchantProfileApprovalConcurrencyTest'`

Expected: FAIL because decision logic does not exist.

- [ ] **Step 3: Implement one transactional approval boundary**

```java
@Transactional
public MerchantProfileChangeResponse approve(Long requestId, Long actorId) {
    MerchantProfileChangeRequest request = requests.findDetailedByIdForUpdate(requestId).orElseThrow(notFound());
    requireAssignedInReview(request, actorId);
    Merchant merchant = merchants.findByIdForUpdate(request.getMerchant().getId()).orElseThrow(notFound());
    if (merchant.getProfileVersion() != request.getBaseProfileVersion()) throw conflict("Заявка устарела");
    validateSnapshot(request, activeStaffLocations(merchant.getId()));
    mapper.applySnapshot(request, merchant);
    merchant.setProfileVersion(merchant.getProfileVersion() + 1);
    request.approve(actorId, clock.instant());
    requests.markOutdated(merchant.getId(), request.getBaseProfileVersion(), requestId);
    history.recordDecision(request, actorId, APPROVED, null);
    outbox.enqueueDecisionEvents(request);
    return mapper.toResponse(request);
}
```

Ensure admin `MerchantService.updateMerchant` also locks merchant, increments `profileVersion`, and marks old active requests `OUTDATED`.

- [ ] **Step 4: Run GREEN and rollback mutation**

Run command from Step 2. Add a test that throws after saving the first location and proves company/locations/version/status all roll back.

- [ ] **Step 5: Commit and push**

```bash
git add services/coupon-service
git commit -m "feat(merchant): publish approved profile versions"
git push
```

### Task 8: Record and publish idempotent notification outbox events

**Files:**
- Create: `services/coupon-service/src/main/resources/db/migration/V33__merchant_profile_notification_outbox.sql`
- Create: `services/coupon-service/src/main/java/uz/topdim/coupon/entity/NotificationOutbox.java`
- Create: `services/coupon-service/src/main/java/uz/topdim/coupon/repository/NotificationOutboxRepository.java`
- Create: `services/coupon-service/src/main/java/uz/topdim/coupon/service/MerchantProfileOutboxService.java`
- Create: `services/coupon-service/src/main/java/uz/topdim/coupon/service/NotificationOutboxPublisher.java`
- Modify: `shared/common-events/src/main/java/uz/topdim/common/events/NotificationEvent.java`
- Modify: lifecycle/moderation services from Tasks 5–7
- Test: `services/coupon-service/src/test/java/uz/topdim/coupon/service/MerchantProfileOutboxServiceTest.java`
- Test: `services/coupon-service/src/test/java/uz/topdim/coupon/service/NotificationOutboxPublisherTest.java`

**Interfaces:**
- Produces: `NotificationEvent(eventKey, userId, title, message, type, deepLink, timestamp)`.

- [ ] **Step 1: Write RED recipient and idempotency tests**

```java
@Test
void managerAuthoredDecisionTargetsAuthorAndOwnerWithoutDuplicates() {
    service.enqueue(requestBy(managerId), "APPROVED");
    assertThat(outbox.findAll()).extracting(NotificationOutbox::getRecipientUserId)
        .containsExactlyInAnyOrder(managerId, ownerId);
}

@Test
void ownerAuthoredDecisionCreatesOneRecipient() {
    service.enqueue(requestBy(ownerId), "APPROVED");
    assertThat(outbox.findAll()).singleElement();
}
```

- [ ] **Step 2: Run RED**

Run coupon outbox tests. Expected: missing table/classes and old event contract.

- [ ] **Step 3: Implement transactional enqueue and after-commit publisher**

Migration adds unique `event_key`, JSON payload, `status`, `attempt_count`, `next_attempt_at`, `published_at`. Event keys follow `merchant-profile:{requestId}:{historyId}:{recipientUserId}`.

Publisher claims a bounded page of pending rows, publishes to `notification.exchange/notification.sent`, then marks published. A failed publish increments attempts and schedules bounded backoff; it does not mutate request state.

- [ ] **Step 4: Run GREEN**

Run: `./gradlew :services:coupon-service:test --tests '*MerchantProfileOutboxServiceTest' --tests '*NotificationOutboxPublisherTest'`

Expected: PASS and duplicate event key produces one row/event.

- [ ] **Step 5: Commit and push**

```bash
git add services/coupon-service shared/common-events
git commit -m "feat(notifications): publish merchant profile events"
git push
```

### Task 9: Deliver in-app and Telegram notifications idempotently

**Files:**
- Create: `services/notification-service/src/main/resources/db/migration/V2__notification_idempotency_and_delivery.sql`
- Modify: `services/notification-service/src/main/java/uz/topdim/notification/entity/Notification.java`
- Create: `services/notification-service/src/main/java/uz/topdim/notification/entity/NotificationDelivery.java`
- Create: `services/notification-service/src/main/java/uz/topdim/notification/repository/NotificationDeliveryRepository.java`
- Create: `services/notification-service/src/main/java/uz/topdim/notification/client/IdentityNotificationTargetClient.java`
- Create: `services/notification-service/src/main/java/uz/topdim/notification/service/TelegramNotificationSender.java`
- Create: `services/notification-service/src/main/java/uz/topdim/notification/service/NotificationDeliveryService.java`
- Modify: `services/notification-service/src/main/java/uz/topdim/notification/listener/NotificationEventListener.java`
- Create: `services/identity-service/src/main/java/uz/topdim/identity/controller/InternalNotificationTargetController.java`
- Create: `services/identity-service/src/main/java/uz/topdim/identity/dto/InternalNotificationTargetResponse.java`
- Test: `services/notification-service/src/test/java/uz/topdim/notification/listener/NotificationEventListenerTest.java`
- Test: `services/notification-service/src/test/java/uz/topdim/notification/service/TelegramNotificationSenderTest.java`
- Test: `services/identity-service/src/test/java/uz/topdim/identity/controller/InternalNotificationTargetControllerTest.java`

**Interfaces:**
- Consumes: extended `NotificationEvent`.
- Produces: one in-app row per `(eventKey,userId)` and independent Telegram/email delivery rows.

- [ ] **Step 1: Write RED notification tests**

```java
@Test
void duplicateEventCreatesOneInAppNotificationAndOneTelegramDelivery() {
    listener.handleNotificationEvent(event("evt-7", userId));
    listener.handleNotificationEvent(event("evt-7", userId));
    assertThat(notificationRepository.count()).isEqualTo(1);
    assertThat(deliveryRepository.findAll()).filteredOn(d -> d.getChannel() == TELEGRAM).hasSize(1);
}

@Test
void telegramFailureDoesNotRemoveInAppNotification() {
    doThrow(new TelegramDeliveryException("timeout")).when(sender).send(chatId, message, link);
    listener.handleNotificationEvent(event("evt-8", userId));
    assertThat(notificationRepository.findByEventKeyAndUserId("evt-8", userId)).isPresent();
    assertThat(deliveryRepository.findByEventKeyAndChannel("evt-8", TELEGRAM).orElseThrow().getStatus()).isEqualTo(RETRY);
}
```

- [ ] **Step 2: Run RED**

Run notification and identity target tests. Expected: missing eventKey/delivery/client.

- [ ] **Step 3: Implement target resolution and channel fan-out**

Identity response:

```java
public record InternalNotificationTargetResponse(
    Long userId, String email, boolean emailVerified,
    Long telegramChatId, boolean telegramLinked) {}
```

Protect the internal endpoint with existing `X-Gateway-Auth`. Telegram sender uses configured `TELEGRAM_BOT_TOKEN`, URL-encodes the payload, sets connect/read timeouts, and never logs the token/chat ID. Email delivery is created only when Telegram is unavailable or after its terminal failure, matching the approved fallback rule.

- [ ] **Step 4: Run GREEN and full notification suite**

Run: `./gradlew :services:notification-service:test :services:identity-service:test --tests '*InternalNotificationTargetControllerTest'`

Expected: PASS; a Telegram failure leaves business/in-app state committed.

- [ ] **Step 5: Commit and push**

```bash
git add services/notification-service services/identity-service shared/common-events
git commit -m "feat(notifications): add Telegram profile updates"
git push
```

### Task 10: Add partner company API models and route permissions

**Files:**
- Create: `frontend/partner/src/features/company/types.ts`
- Create: `frontend/partner/src/features/company/api.ts`
- Create: `frontend/partner/src/features/company/permissions.ts`
- Modify: `frontend/partner/src/App.tsx`
- Modify: `frontend/partner/src/layouts/PartnerLayout.tsx`
- Test: `frontend/partner/src/features/company/api.test.ts`
- Test: `frontend/partner/src/features/company/permissions.test.ts`
- Modify: `frontend/partner/src/App.test.tsx`

**Interfaces:**
- Consumes: Task 4–7 partner endpoints.
- Produces: `CompanyProfileChangeStatus`, API functions and route guards used by Tasks 11–12.

- [ ] **Step 1: Write RED API and role tests**

```ts
expect(canManageCompany({ role: 'OWNER' })).toBe(true);
expect(canManageCompany({ role: 'MANAGER' })).toBe(true);
expect(canManageCompany({ role: 'CASHIER' })).toBe(false);

await companyApi.createDraft();
expect(api.post).toHaveBeenCalledWith('/api/v1/partner/merchant/change-requests');
```

App test asserts OWNER/MANAGER can resolve `/company`, CASHIER redirects to `/` and does not see «Моя компания».

- [ ] **Step 2: Run RED**

Run: `npm test -- --run src/features/company/api.test.ts src/features/company/permissions.test.ts src/App.test.tsx` from `frontend/partner`.

Expected: missing modules/routes/menu.

- [ ] **Step 3: Implement typed API and guard**

```ts
export type CompanyProfileChangeStatus =
  | 'DRAFT' | 'PENDING_REVIEW' | 'IN_REVIEW' | 'REVISION_REQUESTED'
  | 'APPROVED' | 'REJECTED' | 'WITHDRAWN' | 'OUTDATED';

export const canManageCompany = (ctx: Pick<PartnerContext, 'role'> | null) =>
  ctx?.role === 'OWNER' || ctx?.role === 'MANAGER';
```

Add `/company` and `/company/requests/:id` behind `CompanyManagerOnly`. Add media upload helper using `FormData` with no manual headers.

- [ ] **Step 4: Run GREEN**

Run command from Step 2. Expected: PASS.

- [ ] **Step 5: Commit and push**

```bash
git add frontend/partner/src
git commit -m "feat(partner): add company profile routes"
git push
```

### Task 11: Build published profile and request history UI

**Files:**
- Create: `frontend/partner/src/features/company/CompanyProfilePage.tsx`
- Create: `frontend/partner/src/features/company/CompanyRequestsTable.tsx`
- Create: `frontend/partner/src/features/company/CompanyProfilePreview.tsx`
- Test: `frontend/partner/src/features/company/CompanyProfilePage.test.tsx`
- Test: `frontend/partner/src/features/company/CompanyRequestsTable.test.tsx`

**Interfaces:**
- Consumes: `companyApi.getPublishedProfile`, `listRequests`, `createDraft`, `withdraw`, `copy`.
- Produces: discoverable partner entry point and status actions.

- [ ] **Step 1: Write RED UI behavior tests**

```tsx
expect(await screen.findByText('Опубликованный профиль')).toBeTruthy();
expect(screen.getByText('4 из 10 активных')).toBeTruthy();
fireEvent.click(screen.getByRole('button', { name: 'Создать заявку на изменение' }));
await waitFor(() => expect(companyApi.createDraft).toHaveBeenCalledOnce());
```

Verify no-logo renders first letter, `REVISION_REQUESTED` shows moderator comment, `OUTDATED` exposes copy, and withdraw requires nonblank reason.

- [ ] **Step 2: Run RED**

Run the two test files. Expected: missing components.

- [ ] **Step 3: Implement profile tabs and status table**

Use React Query keys `['company-profile']`, `['company-change-requests', filters]`, invalidate both after mutation. Paginate on the server and render `publicationBlockReason` as a concrete checklist, not a generic «Не готов».

- [ ] **Step 4: Run GREEN**

Run command from Step 2. Expected: PASS.

- [ ] **Step 5: Commit and push**

```bash
git add frontend/partner/src/features/company
git commit -m "feat(partner): show company profile requests"
git push
```

### Task 12: Build full snapshot editor and preview

**Files:**
- Create: `frontend/partner/src/features/company/CompanyRequestEditorPage.tsx`
- Create: `frontend/partner/src/features/company/CompanyLocationFields.tsx`
- Modify: `frontend/partner/src/features/company/CompanyProfilePreview.tsx`
- Test: `frontend/partner/src/features/company/CompanyRequestEditorPage.test.tsx`
- Test: `frontend/partner/src/features/company/CompanyLocationFields.test.tsx`

**Interfaces:**
- Consumes: Task 10 types/API and Task 5 validations.
- Produces: draft/revision editing, media upload, preview and submit UI.

- [ ] **Step 1: Write RED editor tests**

```tsx
fireEvent.click(screen.getByRole('button', { name: 'Отключить филиал' }));
expect(await screen.findByText('Сначала выберите другой основной филиал')).toBeTruthy();

const logo = new File(['image'], 'logo.png', { type: 'image/png' });
fireEvent.change(screen.getByLabelText('Загрузить логотип'), { target: { files: [logo] } });
await waitFor(() => expect(api.post).toHaveBeenCalledWith('/api/v1/media/upload', expect.any(FormData)));
```

Also verify logo/cover may be cleared, REVISION comment persists, OUTDATED is read-only, submit requires exactly one active primary, and unsaved navigation prompts.

- [ ] **Step 2: Run RED**

Run the two editor test files. Expected: missing components.

- [ ] **Step 3: Implement editor as focused components**

Keep company scalar fields in the page, each location in `CompanyLocationFields`, and derive:

```ts
const activePrimaryCount = locations.filter((l) => l.active && l.primary).length;
const canSubmit = formValid && activePrimaryCount === 1 && !saveMutation.isPending;
```

Save explicit `null` for removed logo/cover. Never physically drop a persisted location from payload; set `active=false`.

- [ ] **Step 4: Run GREEN and partner build**

Run editor tests, then `npm test && npm run lint && npm run build` in `frontend/partner`.

Expected: all PASS; only pre-existing bundle-size warning is acceptable.

- [ ] **Step 5: Commit and push**

```bash
git add frontend/partner/src/features/company
git commit -m "feat(partner): edit company profile drafts"
git push
```

### Task 13: Add admin moderation queue UI

**Files:**
- Create: `frontend/admin-app/src/features/merchant-profile-changes/types.ts`
- Create: `frontend/admin-app/src/features/merchant-profile-changes/api.ts`
- Create: `frontend/admin-app/src/features/merchant-profile-changes/MerchantProfileChangeQueuePage.tsx`
- Modify: `frontend/admin-app/src/components/layout/adminMenu.tsx`
- Modify: `frontend/admin-app/src/App.tsx`
- Test: `frontend/admin-app/src/features/merchant-profile-changes/api.test.ts`
- Test: `frontend/admin-app/src/features/merchant-profile-changes/MerchantProfileChangeQueuePage.test.tsx`
- Modify: `frontend/admin-app/src/components/layout/AdminLayout.test.tsx`
- Modify: `frontend/admin-app/src/routes/AppRoleAccess.test.tsx`

**Interfaces:**
- Consumes: Task 6 filtered queue/take API.
- Produces: `/merchants/profile-changes` route for all staff roles.

- [ ] **Step 1: Write RED queue/menu tests**

```tsx
expect(filterMenuByRole(allMenuItems, 'MODERATOR'))
  .toEqual(expect.arrayContaining([expect.objectContaining({ key: '/merchants/profile-changes' })]));
expect(await screen.findByText('Изменения компаний')).toBeTruthy();
fireEvent.click(screen.getByRole('button', { name: 'Взять в работу' }));
await waitFor(() => expect(profileChangesApi.takeToWork).toHaveBeenCalledWith(71));
```

Verify search, status, assignee and date filters are sent to API with server page/size.

- [ ] **Step 2: Run RED**

Run the four admin tests. Expected: missing feature/route/menu.

- [ ] **Step 3: Implement queue and route permissions**

Add a top-level menu item for `MODERATOR|ADMIN|SUPER_ADMIN`, status tabs, filters, pending count badge, pagination, and claim action. Route lives inside existing `STAFF_ROLES` boundary.

- [ ] **Step 4: Run GREEN**

Run command from Step 2. Expected: PASS.

- [ ] **Step 5: Commit and push**

```bash
git add frontend/admin-app/src
git commit -m "feat(admin): add company change queue"
git push
```

### Task 14: Add comparison and moderation actions UI

**Files:**
- Create: `frontend/admin-app/src/features/merchant-profile-changes/MerchantProfileChangeDetailPage.tsx`
- Create: `frontend/admin-app/src/features/merchant-profile-changes/ProfileFieldDiff.tsx`
- Create: `frontend/admin-app/src/features/merchant-profile-changes/LocationDiff.tsx`
- Modify: `frontend/admin-app/src/features/merchant-profile-changes/api.ts`
- Modify: `frontend/admin-app/src/App.tsx`
- Test: `frontend/admin-app/src/features/merchant-profile-changes/MerchantProfileChangeDetailPage.test.tsx`
- Test: `frontend/admin-app/src/features/merchant-profile-changes/ProfileFieldDiff.test.tsx`
- Test: `frontend/admin-app/src/features/merchant-profile-changes/LocationDiff.test.tsx`

**Interfaces:**
- Consumes: Task 6–7 detail/decision/reassign APIs.
- Produces: moderator comparison and complete decision workflow.

- [ ] **Step 1: Write RED comparison/action tests**

```tsx
expect(screen.getByText('Сейчас')).toBeTruthy();
expect(screen.getByText('Предлагается')).toBeTruthy();
expect(screen.getByText('Филиал будет отключён')).toBeTruthy();

fireEvent.click(screen.getByRole('button', { name: 'Вернуть на доработку' }));
fireEvent.click(screen.getByRole('button', { name: 'Подтвердить' }));
expect(await screen.findByText('Укажите комментарий')).toBeTruthy();
```

Verify MODERATOR cannot see release/reassign, ADMIN can, author cannot decide own request, and `409` refreshes detail with an «Заявка уже изменена» message.

- [ ] **Step 2: Run RED**

Run the three detail/diff tests. Expected: missing components.

- [ ] **Step 3: Implement semantic diff and actions**

`ProfileFieldDiff` receives literal `before/after/label`; `LocationDiff` matches existing locations by `sourceLocationId` and new locations by request-location ID. Do not compare JSON strings. Require comment client-side for revision/reject but preserve server validation as source of truth.

- [ ] **Step 4: Run GREEN and full admin verification**

Run detail tests, then `npm test && npm run lint && npm run build` in `frontend/admin-app`.

Expected: all PASS.

- [ ] **Step 5: Commit and push**

```bash
git add frontend/admin-app/src
git commit -m "feat(admin): review company profile changes"
git push
```

### Task 15: Complete cross-service verification and operational documentation

**Files:**
- Modify: `docs/product/roles.md`
- Modify: `docs/product/flows/coupon-flow.md`
- Modify: `docs/backend/services-overview.md`
- Modify: `docs/frontend/README.md`
- Create: `docs/qa/partner-company-profile-manual-test-plan.md`
- Modify: `docs/PROJECT_STATE.md` only if deployment/configuration facts change.

**Interfaces:**
- Consumes: all previous tasks.
- Produces: release evidence and manual acceptance plan.

- [ ] **Step 1: Write the manual acceptance matrix before final verification**

Document exact accounts/roles and scenarios:

```text
OWNER: create request A
MANAGER: create request B from same base version
CASHIER: verify route/menu/API forbidden
MODERATOR: claim A and approve
Expected: public profile becomes A; request B becomes OUTDATED
Expected notifications: author A + OWNER, one in-app each, Telegram when linked
```

Include revision/resubmit, reject, withdraw, 10/11 limit, active cashier branch, image-less profile, duplicate notification and external-channel failure.

- [ ] **Step 2: Run complete fresh verification**

```bash
./gradlew :services:coupon-service:test :services:identity-service:test :services:notification-service:test :infrastructure:api-gateway:test
(cd frontend/partner && npm test && npm run lint && npm run build)
(cd frontend/admin-app && npm test && npm run lint && npm run build)
git diff --check
```

Expected: exit 0 for every command; no failing tests or lint errors.

- [ ] **Step 3: Run focused security and concurrency evidence**

```bash
./gradlew :services:coupon-service:test \
  --tests '*MerchantProfileApprovalConcurrencyTest' \
  --tests '*MerchantProfileActiveLimitConcurrencyTest' \
  --tests '*AdminMerchantProfileChangeControllerSecurityTest'
```

Expected: one approval winner, active request count never exceeds ten, role matrix passes.

- [ ] **Step 4: Update documentation with implemented facts only**

Mark endpoints/UI/notifications implemented only when their verification from Steps 2–3 passes. Preserve the distinction between in-app persistence and external Telegram/email delivery configuration.

- [ ] **Step 5: Commit and push final docs**

```bash
git add docs
git commit -m "docs(partner): document company profile moderation"
git push
```

- [ ] **Step 6: Request final whole-branch review and open PR**

Compare `origin/main...HEAD`, review every changed service boundary, confirm branch is clean and pushed, then create a ready PR describing migrations V32/V33, role changes, APIs, UI, notification delivery, test evidence and deployment order.
