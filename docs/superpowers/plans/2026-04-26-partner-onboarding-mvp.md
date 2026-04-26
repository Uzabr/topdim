# Partner Onboarding MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the minimal partner onboarding flow: public web application, admin review, partner user creation, merchant creation with `Merchant.userId`, and a verified bridge into the existing partner redeem flow.

**Architecture:** `identity-service` remains the source of truth for partner applications and partner user accounts. `coupon-service` remains the source of truth for merchant profiles and merchant locations. On admin approval, `identity-service` creates or promotes a `PARTNER` user, then calls `coupon-service` internal onboarding endpoint to create an active merchant linked by `userId`.

**Tech Stack:** Java 21, Spring Boot 3, Spring Security, Spring Data JPA, Flyway, OpenFeign, JUnit 5, Mockito, MockMvc, React 19, TypeScript, React Query, React Hook Form, Zod, Ant Design 6, Vite.

---

## Required Skills

Before implementation, the AI must read and follow these project skills:

- Root process: `.agent/skills/using-superpowers/SKILL.md`
- Root execution: `.agent/skills/executing-plans/SKILL.md`
- Root TDD: `.agent/skills/test-driven-development/SKILL.md`
- Root verification: `.agent/skills/verification-before-completion/SKILL.md`
- Backend API design: `services/.agent/skills/api-design-principles/SKILL.md`
- Backend database design: `services/.agent/skills/database-schema-designer/SKILL.md`
- Backend tests: `services/.agent/skills/spring-boot-test-patterns/SKILL.md`
- Frontend React: `frontend/.agent/skills/react/SKILL.md`
- Frontend forms: `frontend/.agent/skills/react-hook-form-zod/SKILL.md`
- Frontend design: `frontend/.agent/skills/frontend-design/SKILL.md`

## Product Scope

Implement only:

- Public partner application form on `frontend/web-app` `/partners`.
- Extended partner application data in `identity-service`.
- Admin review improvements in `frontend/admin-app`.
- Admin approve flow that creates or links a `PARTNER` user.
- Internal merchant onboarding endpoint in `coupon-service`.
- `Merchant.userId` must be set so current partner redeem flow works.
- Focused tests and docs.

Do not implement:

- Telegram onboarding.
- Full merchant cabinet.
- Coupon creation wizard.
- Bazaar/shop onboarding.
- Document/KYC upload.
- Real SMS/email delivery of temporary passwords.
- Payment provider changes.

## Business Rules

- A public application is a lead, not an account.
- Public form must not create a user directly.
- Admin can approve only `PENDING` applications.
- Admin can reject only `PENDING` applications and must provide a rejection reason.
- Duplicate `PENDING` application by normalized phone is not allowed.
- Approval must create or link a `PARTNER` user.
- Approval must create or link a merchant in `coupon-service` with `Merchant.userId = partnerUserId`.
- Existing `ADMIN`, `SUPER_ADMIN`, or `MODERATOR` users must not be reused as partner users.
- Merchant onboarding must create an active merchant with one primary location.
- Merchant onboarding must require enough data for later coupon publication and redemption: merchant name, phone, address.

## Existing Context To Respect

- `identity-service` already has:
  - `PartnerApplication`
  - `PartnerApplicationRequest`
  - `PartnerApplicationResponse`
  - `PartnerApplicationService`
  - `PartnerApplicationController`
  - `AdminPartnerApplicationController`
  - migration `V6__create_partner_applications.sql`
- `coupon-service` already has:
  - `Merchant`
  - `MerchantLocation`
  - `CreateMerchantRequest`
  - `MerchantService`
  - `AdminCouponController` merchant endpoints
  - `InternalMerchantController`
  - `MerchantContextResponse`
- `order-service` partner redeem already resolves merchant by trusted `X-User-Id` through `coupon-service` internal merchant context.
- `frontend/admin-app` already has `PartnerApplicationsPage`.
- `frontend/web-app` already has placeholder `PartnersPage`.

---

## Task 1: Extend Partner Application Data Model

**Files:**

- Modify: `services/identity-service/src/main/java/uz/topdim/identity/entity/PartnerApplication.java`
- Modify: `services/identity-service/src/main/java/uz/topdim/identity/dto/PartnerApplicationRequest.java`
- Modify: `services/identity-service/src/main/java/uz/topdim/identity/dto/PartnerApplicationResponse.java`
- Modify: `services/identity-service/src/main/java/uz/topdim/identity/repository/PartnerApplicationRepository.java`
- Create: `services/identity-service/src/main/resources/db/migration/V10__extend_partner_applications_for_onboarding.sql`
- Modify or create tests under: `services/identity-service/src/test/java/uz/topdim/identity/service/PartnerApplicationServiceTest.java`

- [ ] **Step 1: Write failing duplicate pending phone test**

Add a service test proving a second `PENDING` application with same normalized phone is rejected.

```java
@Test
@DisplayName("submit: duplicate pending phone is rejected")
void submit_duplicatePendingPhone_rejected() {
    PartnerApplicationRequest request = new PartnerApplicationRequest();
    request.setFirstName("Ali");
    request.setLastName("Valiev");
    request.setPhone("+998 90 123 45 67");
    request.setCompanyName("Ali Cafe");
    request.setCity("Tashkent");
    request.setAddress("Amir Temur 10");
    request.setBusinessCategory("Cafe");

    when(repository.existsByPhoneAndStatus("+998901234567", ApplicationStatus.PENDING)).thenReturn(true);

    assertThatThrownBy(() -> service.submit(request))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("pending partner application already exists");

    verify(repository, never()).save(any(PartnerApplication.class));
}
```

- [ ] **Step 2: Run RED test**

Run:

```bash
./gradlew :services:identity-service:test --tests "uz.topdim.identity.service.PartnerApplicationServiceTest" -x jacocoTestReport -x jacocoTestCoverageVerification
```

Expected: FAIL because new repository method and fields are not implemented.

- [ ] **Step 3: Add migration**

Create `V10__extend_partner_applications_for_onboarding.sql`:

```sql
ALTER TABLE partner_applications ADD COLUMN IF NOT EXISTS email VARCHAR(255);
ALTER TABLE partner_applications ADD COLUMN IF NOT EXISTS city VARCHAR(100);
ALTER TABLE partner_applications ADD COLUMN IF NOT EXISTS address TEXT;
ALTER TABLE partner_applications ADD COLUMN IF NOT EXISTS working_hours VARCHAR(255);
ALTER TABLE partner_applications ADD COLUMN IF NOT EXISTS business_category VARCHAR(100);
ALTER TABLE partner_applications ADD COLUMN IF NOT EXISTS website VARCHAR(255);
ALTER TABLE partner_applications ADD COLUMN IF NOT EXISTS telegram_username VARCHAR(100);
ALTER TABLE partner_applications ADD COLUMN IF NOT EXISTS source VARCHAR(30) NOT NULL DEFAULT 'WEB';
ALTER TABLE partner_applications ADD COLUMN IF NOT EXISTS rejection_reason TEXT;
ALTER TABLE partner_applications ADD COLUMN IF NOT EXISTS reviewed_by BIGINT;
ALTER TABLE partner_applications ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMP;
ALTER TABLE partner_applications ADD COLUMN IF NOT EXISTS linked_user_id BIGINT;
ALTER TABLE partner_applications ADD COLUMN IF NOT EXISTS linked_merchant_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_partner_applications_status_created_at
    ON partner_applications(status, created_at DESC);

CREATE UNIQUE INDEX IF NOT EXISTS uq_partner_applications_pending_phone
    ON partner_applications(phone)
    WHERE status = 'PENDING';
```

- [ ] **Step 4: Update entity and DTOs**

Add fields to `PartnerApplication`:

```java
@Column(length = 255)
private String email;

@Column(length = 100)
private String city;

@Column(columnDefinition = "TEXT")
private String address;

@Column(name = "working_hours")
private String workingHours;

@Column(name = "business_category", length = 100)
private String businessCategory;

@Column(length = 255)
private String website;

@Column(name = "telegram_username", length = 100)
private String telegramUsername;

@Column(nullable = false, length = 30)
@Builder.Default
private String source = "WEB";

@Column(name = "rejection_reason", columnDefinition = "TEXT")
private String rejectionReason;

@Column(name = "reviewed_by")
private Long reviewedBy;

@Column(name = "reviewed_at")
private LocalDateTime reviewedAt;

@Column(name = "linked_user_id")
private Long linkedUserId;

@Column(name = "linked_merchant_id")
private Long linkedMerchantId;
```

Add matching fields to request/response DTOs. Keep request validation:

```java
@Size(max = 255)
private String email;

@Size(max = 100)
private String city;

@Size(max = 500)
private String address;

@Size(max = 255)
private String workingHours;

@Size(max = 100)
private String businessCategory;

@Size(max = 255)
private String website;

@Size(max = 100)
private String telegramUsername;
```

- [ ] **Step 5: Add repository method**

Add to `PartnerApplicationRepository`:

```java
boolean existsByPhoneAndStatus(String phone, ApplicationStatus status);
```

- [ ] **Step 6: Normalize phone and map new fields**

In `PartnerApplicationService.submit`, normalize phone before duplicate check and save:

```java
String phone = normalizePhone(request.getPhone());
if (repository.existsByPhoneAndStatus(phone, ApplicationStatus.PENDING)) {
    throw new IllegalStateException("pending partner application already exists for phone");
}

PartnerApplication app = PartnerApplication.builder()
        .firstName(request.getFirstName().trim())
        .lastName(request.getLastName().trim())
        .phone(phone)
        .email(blankToNull(request.getEmail()))
        .companyName(request.getCompanyName().trim())
        .city(blankToNull(request.getCity()))
        .address(blankToNull(request.getAddress()))
        .workingHours(blankToNull(request.getWorkingHours()))
        .businessCategory(blankToNull(request.getBusinessCategory()))
        .website(blankToNull(request.getWebsite()))
        .telegramUsername(blankToNull(request.getTelegramUsername()))
        .comment(blankToNull(request.getComment()))
        .source("WEB")
        .status(ApplicationStatus.PENDING)
        .build();
```

Add private helpers:

```java
private String blankToNull(String value) {
    if (value == null || value.isBlank()) {
        return null;
    }
    return value.trim();
}

private String normalizePhone(String phone) {
    if (phone == null) {
        return null;
    }
    String digits = phone.replaceAll("\\D", "");
    if (digits.startsWith("998")) {
        return "+" + digits;
    }
    return digits.isBlank() ? phone.trim() : "+" + digits;
}
```

- [ ] **Step 7: Run GREEN test**

Run:

```bash
./gradlew :services:identity-service:test --tests "uz.topdim.identity.service.PartnerApplicationServiceTest" -x jacocoTestReport -x jacocoTestCoverageVerification --rerun-tasks
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add services/identity-service/src/main/java/uz/topdim/identity/entity/PartnerApplication.java \
        services/identity-service/src/main/java/uz/topdim/identity/dto/PartnerApplicationRequest.java \
        services/identity-service/src/main/java/uz/topdim/identity/dto/PartnerApplicationResponse.java \
        services/identity-service/src/main/java/uz/topdim/identity/repository/PartnerApplicationRepository.java \
        services/identity-service/src/main/resources/db/migration/V10__extend_partner_applications_for_onboarding.sql \
        services/identity-service/src/test/java/uz/topdim/identity/service/PartnerApplicationServiceTest.java
git commit -m "feat: extend partner applications for onboarding"
```

---

## Task 2: Add Coupon-Service Internal Merchant Onboarding

**Files:**

- Create: `services/coupon-service/src/main/java/uz/topdim/coupon/dto/CreateMerchantOnboardingRequest.java`
- Modify: `services/coupon-service/src/main/java/uz/topdim/coupon/controller/InternalMerchantController.java`
- Modify: `services/coupon-service/src/main/java/uz/topdim/coupon/service/MerchantService.java`
- Modify or create test: `services/coupon-service/src/test/java/uz/topdim/coupon/controller/InternalMerchantControllerTest.java`
- Modify or create test: `services/coupon-service/src/test/java/uz/topdim/coupon/service/MerchantServiceTest.java`

- [ ] **Step 1: Write failing controller test**

Add test:

```java
@Test
@DisplayName("POST /api/v1/internal/merchants/onboarding: creates merchant linked to user")
void createOnboardingMerchant_returnsMerchant() throws Exception {
    MerchantResponse response = MerchantResponse.builder()
            .id(77L)
            .name("Ali Cafe")
            .userId(10L)
            .active(true)
            .build();

    when(merchantService.createFromOnboarding(any(CreateMerchantOnboardingRequest.class))).thenReturn(response);

    mockMvc.perform(post("/api/v1/internal/merchants/onboarding")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "userId": 10,
                              "name": "Ali Cafe",
                              "email": "partner@example.uz",
                              "contactPerson": "Ali Valiev",
                              "location": {
                                "address": "Amir Temur 10",
                                "phone": "+998901234567",
                                "workingHours": "10:00-22:00"
                              }
                            }
                            """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(77))
            .andExpect(jsonPath("$.data.userId").value(10));
}
```

- [ ] **Step 2: Run RED test**

Run:

```bash
./gradlew :services:coupon-service:test --tests "uz.topdim.coupon.controller.InternalMerchantControllerTest" -x jacocoTestReport -x jacocoTestCoverageVerification
```

Expected: FAIL because request DTO and endpoint do not exist.

- [ ] **Step 3: Add request DTO**

Create `CreateMerchantOnboardingRequest`:

```java
package uz.topdim.coupon.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateMerchantOnboardingRequest {
    @NotNull(message = "userId is required")
    private Long userId;

    @NotBlank(message = "merchant name is required")
    private String name;

    private String description;
    private String email;
    private String website;
    private String contactPerson;

    @Valid
    @NotNull(message = "location is required")
    private Location location;

    @Data
    public static class Location {
        private String title;

        @NotBlank(message = "address is required")
        private String address;

        @NotBlank(message = "phone is required")
        private String phone;

        private String workingHours;
        private Double latitude;
        private Double longitude;
    }
}
```

- [ ] **Step 4: Add service method**

Add to `MerchantService`:

```java
@CacheEvict(value = "catalog", allEntries = true)
@Transactional
public MerchantResponse createFromOnboarding(CreateMerchantOnboardingRequest request) {
    Merchant existing = merchantRepository.findByUserId(request.getUserId()).orElse(null);
    if (existing != null) {
        return mapMerchant(existing);
    }

    Merchant merchant = Merchant.builder()
            .name(request.getName().trim())
            .description(request.getDescription())
            .email(request.getEmail())
            .website(request.getWebsite())
            .contactPerson(request.getContactPerson())
            .userId(request.getUserId())
            .active(true)
            .build();
    merchant = merchantRepository.save(merchant);

    MerchantLocation location = MerchantLocation.builder()
            .merchant(merchant)
            .title(request.getLocation().getTitle())
            .address(request.getLocation().getAddress())
            .phone(normalize(request.getLocation().getPhone()))
            .workingHours(request.getLocation().getWorkingHours())
            .latitude(request.getLocation().getLatitude())
            .longitude(request.getLocation().getLongitude())
            .primary(true)
            .active(true)
            .build();
    merchantLocationRepository.save(location);

    return mapMerchant(merchantRepository.findById(merchant.getId()).orElseThrow());
}
```

- [ ] **Step 5: Add controller endpoint**

Add to `InternalMerchantController`:

```java
@PostMapping("/onboarding")
public ResponseEntity<ApiResponse<MerchantResponse>> createFromOnboarding(
        @Valid @RequestBody CreateMerchantOnboardingRequest request
) {
    return ResponseEntity.ok(ApiResponse.success(
            "Merchant created from onboarding",
            merchantService.createFromOnboarding(request)
    ));
}
```

- [ ] **Step 6: Add idempotency service test**

Add service test:

```java
@Test
@DisplayName("createFromOnboarding: existing merchant by userId returns existing merchant")
void createFromOnboarding_existingUserMerchant_returnsExisting() {
    Merchant existing = Merchant.builder()
            .id(77L)
            .userId(10L)
            .name("Ali Cafe")
            .active(true)
            .locations(new ArrayList<>())
            .build();

    CreateMerchantOnboardingRequest request = new CreateMerchantOnboardingRequest();
    request.setUserId(10L);
    request.setName("Ali Cafe");

    when(merchantRepository.findByUserId(10L)).thenReturn(Optional.of(existing));

    MerchantResponse result = merchantService.createFromOnboarding(request);

    assertThat(result.getId()).isEqualTo(77L);
    verify(merchantRepository, never()).save(any(Merchant.class));
    verify(merchantLocationRepository, never()).save(any(MerchantLocation.class));
}
```

- [ ] **Step 7: Run coupon tests**

Run:

```bash
./gradlew :services:coupon-service:test --tests "uz.topdim.coupon.controller.InternalMerchantControllerTest" --tests "uz.topdim.coupon.service.MerchantServiceTest" -x jacocoTestReport -x jacocoTestCoverageVerification --rerun-tasks
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add services/coupon-service/src/main/java/uz/topdim/coupon/dto/CreateMerchantOnboardingRequest.java \
        services/coupon-service/src/main/java/uz/topdim/coupon/controller/InternalMerchantController.java \
        services/coupon-service/src/main/java/uz/topdim/coupon/service/MerchantService.java \
        services/coupon-service/src/test/java/uz/topdim/coupon/controller/InternalMerchantControllerTest.java \
        services/coupon-service/src/test/java/uz/topdim/coupon/service/MerchantServiceTest.java
git commit -m "feat: add internal merchant onboarding endpoint"
```

---

## Task 3: Implement Identity Approval Flow

**Files:**

- Modify: `services/identity-service/build.gradle`
- Modify: `services/identity-service/src/main/java/uz/topdim/identity/IdentityServiceApplication.java`
- Create: `services/identity-service/src/main/java/uz/topdim/identity/client/CouponMerchantClient.java`
- Create: `services/identity-service/src/main/java/uz/topdim/identity/client/CreateMerchantOnboardingRequest.java`
- Create: `services/identity-service/src/main/java/uz/topdim/identity/client/MerchantOnboardingResponse.java`
- Create: `services/identity-service/src/main/java/uz/topdim/identity/dto/ApprovePartnerApplicationRequest.java`
- Create: `services/identity-service/src/main/java/uz/topdim/identity/dto/RejectPartnerApplicationRequest.java`
- Modify: `services/identity-service/src/main/java/uz/topdim/identity/service/PartnerApplicationService.java`
- Modify: `services/identity-service/src/main/java/uz/topdim/identity/controller/AdminPartnerApplicationController.java`
- Modify or create test: `services/identity-service/src/test/java/uz/topdim/identity/service/PartnerApplicationServiceTest.java`
- Modify or create test: `services/identity-service/src/test/java/uz/topdim/identity/controller/AdminPartnerApplicationControllerTest.java`

- [ ] **Step 1: Add Feign dependency and enable clients**

In `build.gradle`, add:

```gradle
implementation 'org.springframework.cloud:spring-cloud-starter-openfeign'
```

In `IdentityServiceApplication`, add:

```java
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients
```

- [ ] **Step 2: Add client DTOs and Feign client**

Create `CouponMerchantClient`:

```java
package uz.topdim.identity.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import uz.topdim.common.dto.ApiResponse;

@FeignClient(name = "coupon-service", path = "/api/v1")
public interface CouponMerchantClient {
    @PostMapping("/internal/merchants/onboarding")
    ApiResponse<MerchantOnboardingResponse> createMerchant(@RequestBody CreateMerchantOnboardingRequest request);
}
```

Create `CreateMerchantOnboardingRequest` and `MerchantOnboardingResponse` with fields matching coupon-service DTO/response:

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateMerchantOnboardingRequest {
    private Long userId;
    private String name;
    private String description;
    private String email;
    private String website;
    private String contactPerson;
    private Location location;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Location {
        private String title;
        private String address;
        private String phone;
        private String workingHours;
        private Double latitude;
        private Double longitude;
    }
}
```

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MerchantOnboardingResponse {
    private Long id;
    private String name;
    private Long userId;
    private boolean active;
}
```

- [ ] **Step 3: Add approve/reject request DTOs**

Create `ApprovePartnerApplicationRequest`:

```java
@Data
public class ApprovePartnerApplicationRequest {
    @NotBlank
    @Email
    private String loginEmail;

    @NotBlank
    @Size(min = 8)
    private String temporaryPassword;

    @NotBlank
    private String merchantName;

    private String contactPerson;
    private String city;

    @NotBlank
    private String address;

    @NotBlank
    private String phone;

    private String workingHours;
    private String website;
}
```

Create `RejectPartnerApplicationRequest`:

```java
@Data
public class RejectPartnerApplicationRequest {
    @NotBlank
    @Size(max = 500)
    private String reason;
}
```

- [ ] **Step 4: Write failing approval service test**

Add test:

```java
@Test
@DisplayName("approve: pending application creates partner user and merchant")
void approve_pendingApplication_createsPartnerUserAndMerchant() {
    PartnerApplication app = PartnerApplication.builder()
            .id(5L)
            .firstName("Ali")
            .lastName("Valiev")
            .phone("+998901234567")
            .companyName("Ali Cafe")
            .status(ApplicationStatus.PENDING)
            .build();

    ApprovePartnerApplicationRequest request = new ApprovePartnerApplicationRequest();
    request.setLoginEmail("partner@example.uz");
    request.setTemporaryPassword("Temp12345");
    request.setMerchantName("Ali Cafe");
    request.setContactPerson("Ali Valiev");
    request.setAddress("Amir Temur 10");
    request.setPhone("+998901234567");
    request.setWorkingHours("10:00-22:00");

    User partner = User.builder()
            .id(10L)
            .email("partner@example.uz")
            .phone("+998901234567")
            .firstName("Ali")
            .lastName("Valiev")
            .role(Role.PARTNER)
            .enabled(true)
            .password("encoded")
            .build();

    MerchantOnboardingResponse merchant = new MerchantOnboardingResponse(77L, "Ali Cafe", 10L, true);

    when(repository.findById(5L)).thenReturn(Optional.of(app));
    when(userRepository.findByEmailIgnoreCase("partner@example.uz")).thenReturn(Optional.empty());
    when(userRepository.existsByPhone("+998901234567")).thenReturn(false);
    when(passwordEncoder.encode("Temp12345")).thenReturn("encoded");
    when(userRepository.save(any(User.class))).thenReturn(partner);
    when(couponMerchantClient.createMerchant(any(CreateMerchantOnboardingRequest.class)))
            .thenReturn(ApiResponse.success(merchant));
    when(repository.save(any(PartnerApplication.class))).thenAnswer(inv -> inv.getArgument(0));

    PartnerApplicationResponse result = service.approve(5L, 99L, request);

    assertThat(result.getStatus()).isEqualTo(ApplicationStatus.APPROVED);
    assertThat(result.getLinkedUserId()).isEqualTo(10L);
    assertThat(result.getLinkedMerchantId()).isEqualTo(77L);
    verify(couponMerchantClient).createMerchant(argThat(req ->
            req.getUserId().equals(10L) && req.getName().equals("Ali Cafe")
    ));
}
```

- [ ] **Step 5: Implement approval rules**

In `PartnerApplicationService`, inject:

```java
private final UserRepository userRepository;
private final PasswordEncoder passwordEncoder;
private final CouponMerchantClient couponMerchantClient;
private final SecurityVersionService securityVersionService;
```

Replace old `approve(Long id)` with:

```java
@Transactional
public PartnerApplicationResponse approve(Long id, Long adminId, ApprovePartnerApplicationRequest request) {
    PartnerApplication app = repository.findById(id)
            .orElseThrow(() -> new RuntimeException("Application not found: " + id));

    if (app.getStatus() != ApplicationStatus.PENDING) {
        throw new IllegalStateException("Only PENDING application can be approved");
    }

    User partner = createOrPromotePartnerUser(app, request);

    CreateMerchantOnboardingRequest merchantRequest = CreateMerchantOnboardingRequest.builder()
            .userId(partner.getId())
            .name(request.getMerchantName())
            .email(request.getLoginEmail())
            .website(request.getWebsite())
            .contactPerson(request.getContactPerson())
            .location(CreateMerchantOnboardingRequest.Location.builder()
                    .title(request.getCity())
                    .address(request.getAddress())
                    .phone(request.getPhone())
                    .workingHours(request.getWorkingHours())
                    .build())
            .build();

    ApiResponse<MerchantOnboardingResponse> merchantResponse = couponMerchantClient.createMerchant(merchantRequest);
    MerchantOnboardingResponse merchant = merchantResponse != null ? merchantResponse.getData() : null;
    if (merchant == null || merchant.getId() == null) {
        throw new IllegalStateException("Merchant onboarding failed");
    }

    app.setStatus(ApplicationStatus.APPROVED);
    app.setReviewedBy(adminId);
    app.setReviewedAt(LocalDateTime.now());
    app.setLinkedUserId(partner.getId());
    app.setLinkedMerchantId(merchant.getId());
    app.setRejectionReason(null);

    return toResponse(repository.save(app));
}
```

Add helper:

```java
private User createOrPromotePartnerUser(PartnerApplication app, ApprovePartnerApplicationRequest request) {
    String email = request.getLoginEmail().trim().toLowerCase();
    String phone = normalizePhone(request.getPhone());

    User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
    if (user == null) {
        user = User.builder()
                .email(email)
                .phone(phone)
                .password(passwordEncoder.encode(request.getTemporaryPassword()))
                .firstName(app.getFirstName())
                .lastName(app.getLastName())
                .role(Role.PARTNER)
                .enabled(true)
                .emailVerified(false)
                .phoneVerified(false)
                .build();
        return userRepository.save(user);
    }

    if (user.getRole() == Role.ADMIN || user.getRole() == Role.SUPER_ADMIN || user.getRole() == Role.MODERATOR) {
        throw new IllegalStateException("Admin or moderator account cannot be linked as partner");
    }

    if (user.getRole() != Role.PARTNER) {
        user.setRole(Role.PARTNER);
        user.setSecurityVersion(user.getSecurityVersion() + 1);
        user = userRepository.save(user);
        securityVersionService.publishSecurityVersion(user.getId(), user.getSecurityVersion());
    }

    return user;
}
```

- [ ] **Step 6: Implement reject with reason**

Replace old `reject(Long id)` with:

```java
@Transactional
public PartnerApplicationResponse reject(Long id, Long adminId, RejectPartnerApplicationRequest request) {
    PartnerApplication app = repository.findById(id)
            .orElseThrow(() -> new RuntimeException("Application not found: " + id));

    if (app.getStatus() != ApplicationStatus.PENDING) {
        throw new IllegalStateException("Only PENDING application can be rejected");
    }

    app.setStatus(ApplicationStatus.REJECTED);
    app.setReviewedBy(adminId);
    app.setReviewedAt(LocalDateTime.now());
    app.setRejectionReason(request.getReason().trim());
    return toResponse(repository.save(app));
}
```

- [ ] **Step 7: Update admin controller**

Change endpoints:

```java
@PatchMapping("/{id}/approve")
public ResponseEntity<Map<String, Object>> approve(
        @PathVariable Long id,
        @RequestHeader("X-User-Id") Long adminId,
        @Valid @RequestBody ApprovePartnerApplicationRequest request
) {
    return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Application approved",
            "data", service.approve(id, adminId, request)
    ));
}

@PatchMapping("/{id}/reject")
public ResponseEntity<Map<String, Object>> reject(
        @PathVariable Long id,
        @RequestHeader("X-User-Id") Long adminId,
        @Valid @RequestBody RejectPartnerApplicationRequest request
) {
    return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Application rejected",
            "data", service.reject(id, adminId, request)
    ));
}
```

- [ ] **Step 8: Run identity tests**

Run:

```bash
./gradlew :services:identity-service:test --tests "*PartnerApplication*" -x jacocoTestReport -x jacocoTestCoverageVerification --rerun-tasks
```

Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add services/identity-service/build.gradle \
        services/identity-service/src/main/java/uz/topdim/identity/IdentityServiceApplication.java \
        services/identity-service/src/main/java/uz/topdim/identity/client \
        services/identity-service/src/main/java/uz/topdim/identity/dto/ApprovePartnerApplicationRequest.java \
        services/identity-service/src/main/java/uz/topdim/identity/dto/RejectPartnerApplicationRequest.java \
        services/identity-service/src/main/java/uz/topdim/identity/service/PartnerApplicationService.java \
        services/identity-service/src/main/java/uz/topdim/identity/controller/AdminPartnerApplicationController.java \
        services/identity-service/src/test/java/uz/topdim/identity
git commit -m "feat: approve partner applications into users and merchants"
```

---

## Task 4: Build Public Partner Application Form

**Files:**

- Create: `frontend/web-app/src/api/partners.ts`
- Modify: `frontend/web-app/src/pages/legal/PartnersPage.tsx`
- Create: `frontend/web-app/src/pages/legal/PartnersPage.css`
- Modify if needed: `frontend/web-app/src/locales/ru.json`
- Modify if needed: `frontend/web-app/src/locales/uz.json`

- [ ] **Step 1: Create API client**

Create `partners.ts`:

```ts
import apiClient from './client';

export interface PartnerApplicationRequest {
  firstName: string;
  lastName: string;
  phone: string;
  email?: string;
  companyName: string;
  city?: string;
  address?: string;
  workingHours?: string;
  businessCategory?: string;
  website?: string;
  telegramUsername?: string;
  comment?: string;
}

export const partnersApi = {
  submitApplication: (data: PartnerApplicationRequest) =>
    apiClient.post('/api/v1/partners/applications', data),
};
```

- [ ] **Step 2: Replace placeholder page with form**

Use React Hook Form and Zod. The form must send only application data. It must not create a user account.

Validation:

```ts
const partnerSchema = z.object({
  firstName: z.string().trim().min(2, 'Введите имя'),
  lastName: z.string().trim().min(2, 'Введите фамилию'),
  phone: z.string().trim().min(9, 'Введите телефон'),
  email: z.string().trim().email('Некорректный email').optional().or(z.literal('')),
  companyName: z.string().trim().min(2, 'Введите название бизнеса'),
  city: z.string().trim().optional(),
  address: z.string().trim().optional(),
  workingHours: z.string().trim().optional(),
  businessCategory: z.string().trim().optional(),
  website: z.string().trim().optional(),
  telegramUsername: z.string().trim().optional(),
  comment: z.string().trim().max(1000).optional(),
});
```

UX requirements:

- Hero explains: more customers, controlled offers, simple redemption by PIN/QR.
- Form has clear sections: contact, business, optional details.
- Success state says application was received and team will contact by phone or Telegram.
- Error state shows backend message if present.

- [ ] **Step 3: Run web app checks**

Run:

```bash
npm run build
```

from `frontend/web-app`.

Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add frontend/web-app/src/api/partners.ts \
        frontend/web-app/src/pages/legal/PartnersPage.tsx \
        frontend/web-app/src/pages/legal/PartnersPage.css \
        frontend/web-app/src/locales/ru.json \
        frontend/web-app/src/locales/uz.json
git commit -m "feat: add public partner application form"
```

---

## Task 5: Improve Admin Partner Application Review UI

**Files:**

- Create: `frontend/admin-app/src/features/partners/api.ts`
- Modify: `frontend/admin-app/src/features/partners/PartnerApplicationsPage.tsx`
- Modify: `frontend/admin-app/src/types/index.ts`

- [ ] **Step 1: Update admin types**

Extend `PartnerApplication`:

```ts
export interface PartnerApplication {
  id: number;
  firstName: string;
  lastName: string;
  phone: string;
  email?: string;
  companyName: string;
  city?: string;
  address?: string;
  workingHours?: string;
  businessCategory?: string;
  website?: string;
  telegramUsername?: string;
  comment?: string;
  source?: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  rejectionReason?: string;
  reviewedBy?: number;
  reviewedAt?: string;
  linkedUserId?: number;
  linkedMerchantId?: number;
  createdAt: string;
  updatedAt?: string;
}
```

- [ ] **Step 2: Create partner application admin API module**

Create `frontend/admin-app/src/features/partners/api.ts`:

```ts
import api from '../../api/client';
import type { ApiResponse, PageResponse, PartnerApplication } from '../../types';

export interface ApprovePartnerApplicationRequest {
  loginEmail: string;
  temporaryPassword: string;
  merchantName: string;
  contactPerson?: string;
  city?: string;
  address: string;
  phone: string;
  workingHours?: string;
  website?: string;
}

export interface RejectPartnerApplicationRequest {
  reason: string;
}

export const partnerApplicationsApi = {
  list: (params: { page: number; size: number; status?: string }) =>
    api.get<ApiResponse<PageResponse<PartnerApplication>>>('/api/v1/admin/partner-applications', { params }),

  approve: (id: number, request: ApprovePartnerApplicationRequest) =>
    api.patch<ApiResponse<PartnerApplication>>(`/api/v1/admin/partner-applications/${id}/approve`, request),

  reject: (id: number, request: RejectPartnerApplicationRequest) =>
    api.patch<ApiResponse<PartnerApplication>>(`/api/v1/admin/partner-applications/${id}/reject`, request),
};
```

- [ ] **Step 3: Add status filter and review modals**

Update `PartnerApplicationsPage`:

- Add `status` filter: `ALL`, `PENDING`, `APPROVED`, `REJECTED`.
- Add drawer with full application details.
- Add approve modal prefilled from application:
  - `loginEmail = application.email || ''`
  - `merchantName = application.companyName`
  - `contactPerson = firstName + ' ' + lastName`
  - `address = application.address || ''`
  - `phone = application.phone`
  - `workingHours = application.workingHours || ''`
  - `website = application.website || ''`
- Add reject modal with required reason.
- Disable approve/reject buttons when `status !== 'PENDING'`.

- [ ] **Step 4: Run targeted admin lint/build**

Run:

```bash
npx eslint src/features/partners src/types/index.ts
npm run build
```

from `frontend/admin-app`.

Expected: targeted lint PASS and build PASS. If full `npm run lint` fails due unrelated existing files, record that separately.

- [ ] **Step 5: Commit**

```bash
git add frontend/admin-app/src/features/partners/api.ts \
        frontend/admin-app/src/features/partners/PartnerApplicationsPage.tsx \
        frontend/admin-app/src/types/index.ts
git commit -m "feat: improve partner application review ui"
```

---

## Task 6: Security And Role Cleanup

**Files:**

- Modify: `frontend/admin-app/src/App.tsx`
- Modify: `frontend/admin-app/src/components/layout/AdminLayout.tsx`
- Modify: `services/order-service/src/main/java/uz/topdim/order/config/SecurityConfig.java`

- [ ] **Step 1: Restrict partner redeem UI to PARTNER**

Admin users should not use normal merchant redemption. Update admin app route protection and menu:

```tsx
<Route element={<ProtectedRoute allowedRoles={['PARTNER']} />}>
  <Route path="/partner/redeem" element={<PartnerRedeemPage />} />
</Route>
```

Menu item roles:

```ts
roles: ['PARTNER']
```

- [ ] **Step 2: Restrict order-service partner endpoints**

In `SecurityConfig`, change:

```java
.requestMatchers("/api/v1/partner/**").hasRole("PARTNER")
```

Keep admin support redemption out of scope.

- [ ] **Step 3: Add or update security tests**

If `order-service` has route authorization tests, add:

- PARTNER can access `/api/v1/partner/redemptions`.
- ADMIN cannot access `/api/v1/partner/redemptions`.

If no route authorization tests exist, add focused MockMvc test for `SecurityConfig` or document why existing coverage cannot support this quickly.

- [ ] **Step 4: Run checks**

Run:

```bash
./gradlew :services:order-service:test --tests "*Partner*" -x jacocoTestReport -x jacocoTestCoverageVerification --rerun-tasks
npx eslint src/App.tsx src/components/layout/AdminLayout.tsx src/features/partner-redemptions
npm run build
```

Run frontend commands from `frontend/admin-app`.

- [ ] **Step 5: Commit**

```bash
git add frontend/admin-app/src/App.tsx \
        frontend/admin-app/src/components/layout/AdminLayout.tsx \
        services/order-service/src/main/java/uz/topdim/order/config/SecurityConfig.java \
        services/order-service/src/test/java
git commit -m "fix: restrict partner redeem to partner role"
```

---

## Task 7: Documentation And Final Verification

**Files:**

- Modify: `docs/API_CONTRACT.md`
- Modify: `docs/BACKEND.md`
- Modify: `docs/PRODUCT_REQUIREMENTS_DOCUMENT.md`
- Modify if needed: `docs/DATABASE.md`

- [ ] **Step 1: Document onboarding flow**

Document this sequence:

```text
WEB /partners application
  -> identity-service partner_applications(PENDING)
  -> admin review
  -> identity-service creates/promotes PARTNER user
  -> identity-service calls coupon-service internal merchant onboarding
  -> coupon-service creates Merchant(userId = partnerUser.id) + primary MerchantLocation
  -> partner logs in
  -> partner can redeem PIN/QR through existing partner redeem flow
```

- [ ] **Step 2: Document API changes**

Add/update:

- `POST /api/v1/partners/applications`
- `GET /api/v1/admin/partner-applications`
- `PATCH /api/v1/admin/partner-applications/{id}/approve`
- `PATCH /api/v1/admin/partner-applications/{id}/reject`
- `POST /api/v1/internal/merchants/onboarding`

- [ ] **Step 3: Run backend verification**

Run:

```bash
./gradlew :services:identity-service:test --tests "*PartnerApplication*" -x jacocoTestReport -x jacocoTestCoverageVerification --rerun-tasks
./gradlew :services:coupon-service:test --tests "*Merchant*" -x jacocoTestReport -x jacocoTestCoverageVerification --rerun-tasks
./gradlew :services:order-service:test --tests "*Partner*" -x jacocoTestReport -x jacocoTestCoverageVerification --rerun-tasks
```

Expected: PASS.

- [ ] **Step 4: Run frontend verification**

Run from `frontend/web-app`:

```bash
npm run build
```

Run from `frontend/admin-app`:

```bash
npx eslint src/features/partners src/features/partner-redemptions src/App.tsx src/components/layout/AdminLayout.tsx src/types/index.ts
npm run build
```

Expected: targeted lint PASS and builds PASS. If full lint fails because of unrelated existing admin pages, report exact files separately.

- [ ] **Step 5: Final git hygiene**

Run:

```bash
git diff --check
git status --short --branch
```

Expected: no whitespace errors. Status should show only intended files.

- [ ] **Step 6: Commit docs**

```bash
git add docs/API_CONTRACT.md docs/BACKEND.md docs/PRODUCT_REQUIREMENTS_DOCUMENT.md docs/DATABASE.md
git commit -m "docs: describe partner onboarding mvp"
```

---

## Acceptance Checklist

- Public `/partners` page submits real application data.
- Duplicate pending phone application is rejected.
- Admin can filter and review partner applications.
- Admin approval creates or promotes a `PARTNER` user.
- Admin approval creates an active merchant in `coupon-service`.
- Created merchant has `userId` linked to the partner user.
- Created merchant has one primary location.
- Re-approving approved/rejected application is rejected.
- Rejecting requires reason.
- Normal partner redeem page is accessible only to `PARTNER`.
- Docs describe the onboarding flow and API contracts.
- Focused backend tests pass.
- Targeted frontend lint and builds pass.

## Handoff Notes

- Keep Telegram onboarding out of scope.
- Keep admin manual redemption out of scope.
- Do not make `merchantId` client-controlled in partner flows.
- Do not rely on legacy merchant contact columns. Use `merchant_locations`.
- Prefer small commits after each task.
