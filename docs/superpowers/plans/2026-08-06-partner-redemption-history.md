# Partner Redemption History Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a dedicated, server-filtered redemption-history screen for partner owners, managers, and cashiers while preserving trusted merchant and staff isolation.

**Architecture:** Extend the existing order-service history endpoint with validated optional code and date filters, then query one stable paginated repository projection scope. Add a focused partner-frontend page whose normalized URL query is the source of truth, expose it through a shared role route/menu item, and invalidate its React Query cache after redemption.

**Tech Stack:** Java 21, Spring Boot, Spring Data JPA, PostgreSQL/Testcontainers, JUnit 5, Mockito, React 19, TypeScript, React Router 7, TanStack Query 5, Ant Design 6, Vitest, Testing Library.

## Global Constraints

- Route: `/redemptions`; menu label: `История погашений`.
- `OWNER` and `MANAGER` see merchant-wide history; `CASHIER` sees only its trusted `staffId` scope.
- Browser requests never send merchant ID or staff ID.
- Optional filters are partial case-insensitive `couponCode`, `dateFrom`, and `dateTo` in `YYYY-MM-DD` format.
- SQL `%`, `_`, and the escape character `!` in coupon input are literal characters.
- Default view is unfiltered complete history, ordered by `redeemedAt DESC, id DESC`.
- Backend pagination is zero-based; frontend pagination is one-based; page size is fixed at 20 in the UI and limited to 1–100 by the API.
- The selected end date is inclusive by converting it to an exclusive start of the following day.
- First release does not add branch names, exports, employee filters, branch filters, method filters, or a dashboard link.
- No database migration or public response-field removal.
- Manual realistic-data testing remains deferred to the final product-wide testing phase.

---

### Task 1: Filtered and Scoped Repository Query

**Files:**
- Create: `services/order-service/src/test/java/uz/topdim/order/repository/RedemptionHistoryRepositoryTest.java`
- Modify: `services/order-service/src/main/java/uz/topdim/order/repository/RedemptionRepository.java`
- Modify: `services/order-service/src/main/java/uz/topdim/order/service/PartnerService.java`
- Modify: `services/order-service/src/test/java/uz/topdim/order/service/PartnerServiceTest.java`

**Interfaces:**
- Produces repository method:
  `Page<Redemption> findHistory(Long merchantId, Long staffId, String couponFragment, LocalDateTime fromInclusive, LocalDateTime toExclusive, Pageable pageable)`.
- Produces service method:
  `Page<RedemptionResponse> getRedemptions(Long merchantId, Long staffId, String couponCode, LocalDate dateFrom, LocalDate dateTo, int page, int size)`.
- `staffId == null` means trusted merchant-wide scope; a non-null value adds cashier scope.
- `couponFragment` is already trimmed, lower-cased, and escaped with `!` before the repository receives it.

- [ ] **Step 1: Write failing PartnerService tests for normalization and stable boundaries**

Add tests that capture every repository argument:

```java
@Test
@DisplayName("getRedemptions: normalizes filters and uses stable newest-first sorting")
void getRedemptions_normalizesFiltersAndBoundaries() {
    when(redemptionRepository.findHistory(any(), any(), any(), any(), any(), any()))
            .thenReturn(Page.empty());

    partnerService.getRedemptions(
            77L, 5L, " CP_%! ",
            LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 6), 2, 20);

    verify(redemptionRepository).findHistory(
            eq(77L), eq(5L), eq("cp!_!%!!"),
            eq(LocalDateTime.of(2026, 8, 1, 0, 0)),
            eq(LocalDateTime.of(2026, 8, 7, 0, 0)),
            argThat(pageable -> pageable.getPageNumber() == 2
                    && pageable.getPageSize() == 20
                    && pageable.getSort().getOrderFor("redeemedAt").isDescending()
                    && pageable.getSort().getOrderFor("id").isDescending()));
}

@Test
@DisplayName("getRedemptions: blank code and absent dates remain unfiltered")
void getRedemptions_blankFiltersBecomeNull() {
    when(redemptionRepository.findHistory(any(), any(), any(), any(), any(), any()))
            .thenReturn(Page.empty());

    partnerService.getRedemptions(77L, null, "   ", null, null, 0, 20);

    verify(redemptionRepository).findHistory(
            eq(77L), isNull(), isNull(), isNull(), isNull(), any(Pageable.class));
}
```

Update the existing mapping test with the new interface:

```java
when(redemptionRepository.findHistory(
        eq(1L), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(r)));

Page<RedemptionResponse> redemptions = partnerService.getRedemptions(
        1L, null, null, null, null, 0, 20);
```

- [ ] **Step 2: Run the service test and verify RED**

Run:

```bash
./gradlew :services:order-service:test --tests uz.topdim.order.service.PartnerServiceTest
```

Expected: compilation fails because `findHistory` and the new `getRedemptions` signature do not exist.

- [ ] **Step 3: Add the repository query**

Extend `RedemptionRepository` with:

```java
@EntityGraph(attributePaths = "purchasedCoupon")
@Query(value = """
        select r from Redemption r
        join r.purchasedCoupon pc
        where r.merchantId = :merchantId
          and (:staffId is null or r.staffId = :staffId)
          and (:couponFragment is null
               or lower(pc.couponCode) like concat('%', :couponFragment, '%') escape '!')
          and (:fromInclusive is null or r.redeemedAt >= :fromInclusive)
          and (:toExclusive is null or r.redeemedAt < :toExclusive)
        """,
        countQuery = """
        select count(r) from Redemption r
        join r.purchasedCoupon pc
        where r.merchantId = :merchantId
          and (:staffId is null or r.staffId = :staffId)
          and (:couponFragment is null
               or lower(pc.couponCode) like concat('%', :couponFragment, '%') escape '!')
          and (:fromInclusive is null or r.redeemedAt >= :fromInclusive)
          and (:toExclusive is null or r.redeemedAt < :toExclusive)
        """)
Page<Redemption> findHistory(
        @Param("merchantId") Long merchantId,
        @Param("staffId") Long staffId,
        @Param("couponFragment") String couponFragment,
        @Param("fromInclusive") LocalDateTime fromInclusive,
        @Param("toExclusive") LocalDateTime toExclusive,
        Pageable pageable);
```

Add the required Spring Data, `LocalDateTime`, and `Pageable` imports. Keep existing repository methods because dashboard and other callers still use them.

- [ ] **Step 4: Implement normalization and the unified service method**

Replace the two history service methods with:

```java
public Page<RedemptionResponse> getRedemptions(
        Long merchantId, Long staffId, String couponCode,
        LocalDate dateFrom, LocalDate dateTo, int page, int size) {
    PageRequest pageable = PageRequest.of(page, size,
            Sort.by(Sort.Order.desc("redeemedAt"), Sort.Order.desc("id")));
    String couponFragment = normalizeCouponFragment(couponCode);
    LocalDateTime fromInclusive = dateFrom != null ? dateFrom.atStartOfDay() : null;
    LocalDateTime toExclusive = dateTo != null ? dateTo.plusDays(1).atStartOfDay() : null;
    return redemptionRepository.findHistory(
                    merchantId, staffId, couponFragment, fromInclusive, toExclusive, pageable)
            .map(this::mapToResponse);
}

private String normalizeCouponFragment(String couponCode) {
    if (couponCode == null || couponCode.isBlank()) return null;
    return couponCode.trim().toLowerCase(Locale.ROOT)
            .replace("!", "!!")
            .replace("%", "!%")
            .replace("_", "!_");
}
```

Add `LocalDate`, `LocalDateTime`, and `Locale` imports.

- [ ] **Step 5: Run the service test and verify GREEN**

Run the same focused Gradle command. Expected: all `PartnerServiceTest` tests pass.

- [ ] **Step 6: Write PostgreSQL repository integration tests**

Create `RedemptionHistoryRepositoryTest` using the existing `@DataJpaTest`, PostgreSQL Testcontainers, and `disabledWithoutDocker = true` pattern. Seed purchased coupons and redemptions for merchant 77/staff 5, merchant 77/staff 6, and merchant 88. Add these tests:

```java
@Test
@DisplayName("history query keeps merchant and cashier scope while filtering code")
void findHistory_scopesMerchantAndStaffAndMatchesCodeIgnoringCase() {
    Page<Redemption> result = redemptionRepository.findHistory(
            77L, 5L, "cp-vip", null, null,
            PageRequest.of(0, 20, Sort.by(desc("redeemedAt"), desc("id"))));

    assertThat(result.getContent())
            .extracting(r -> r.getPurchasedCoupon().getCouponCode())
            .containsExactly("CP-VIP-001");
}

@Test
@DisplayName("history query treats percent and underscore as literal characters")
void findHistory_escapedWildcardsAreLiteral() {
    Page<Redemption> result = redemptionRepository.findHistory(
            77L, null, "literal!%!_code", null, null,
            PageRequest.of(0, 20));

    assertThat(result.getContent())
            .extracting(r -> r.getPurchasedCoupon().getCouponCode())
            .containsExactly("LITERAL%_CODE");
}

@Test
@DisplayName("history query uses inclusive calendar dates and stable ordering")
void findHistory_filtersInclusiveRangeAndOrdersEqualTimestampsById() {
    Page<Redemption> result = redemptionRepository.findHistory(
            77L, null, null,
            LocalDateTime.of(2026, 8, 1, 0, 0),
            LocalDateTime.of(2026, 8, 7, 0, 0),
            PageRequest.of(0, 20, Sort.by(desc("redeemedAt"), desc("id"))));

    assertThat(result.getContent())
            .allMatch(r -> !r.getRedeemedAt().isBefore(LocalDateTime.of(2026, 8, 1, 0, 0)))
            .allMatch(r -> r.getRedeemedAt().isBefore(LocalDateTime.of(2026, 8, 7, 0, 0)));
    assertThat(result.getContent().get(0).getId())
            .isGreaterThan(result.getContent().get(1).getId());
}

@Test
@DisplayName("history query combines code and date filters and eagerly loads coupon data")
void findHistory_combinesFiltersAndLoadsPurchasedCoupon() {
    Page<Redemption> result = redemptionRepository.findHistory(
            77L, null, "cp-vip",
            LocalDateTime.of(2026, 8, 1, 0, 0),
            LocalDateTime.of(2026, 8, 7, 0, 0),
            PageRequest.of(0, 20, Sort.by(desc("redeemedAt"), desc("id"))));

    assertThat(result.getContent())
            .extracting(r -> r.getPurchasedCoupon().getCouponCode())
            .containsExactly("CP-VIP-001");
    assertThat(result.getContent())
            .allMatch(r -> entityManagerFactory.getPersistenceUnitUtil()
                    .isLoaded(r, "purchasedCoupon"));
}
```

Use this seed shape with distinct values per call:

```java
private Redemption seedRedemption(
        long merchantId, Long staffId, String couponCode,
        String suffix, LocalDateTime redeemedAt) {
    Order order = orderRepository.save(Order.builder()
            .orderNumber("ORD-HISTORY-" + suffix)
            .userId(10L)
            .totalAmount(BigDecimal.valueOf(100_000))
            .status(OrderStatus.PAID)
            .build());
    PurchasedCoupon coupon = purchasedCouponRepository.save(PurchasedCoupon.builder()
            .userId(10L)
            .order(order)
            .couponOfferId(20L)
            .couponOptionId(30L)
            .couponTitle("History coupon " + suffix)
            .optionTitle("VIP")
            .couponCode(couponCode)
            .qrToken("history-qr-" + suffix)
            .merchantId(merchantId)
            .status(PurchasedCouponStatus.USED)
            .build());
    return redemptionRepository.saveAndFlush(Redemption.builder()
            .purchasedCoupon(coupon)
            .redemptionCode("RED-" + suffix)
            .merchantId(merchantId)
            .staffId(staffId)
            .redeemedByStaff("Cashier " + staffId)
            .redeemMethod("PIN")
            .redeemedAt(redeemedAt)
            .build());
}
```

Autowire `OrderRepository`, `PurchasedCouponRepository`, `RedemptionRepository`, and `EntityManagerFactory`. Seed the equal-timestamp rows in two flushes so the generated IDs establish the expected tie-break order.

- [ ] **Step 7: Run repository and service tests**

Run:

```bash
./gradlew :services:order-service:test \
  --tests uz.topdim.order.repository.RedemptionHistoryRepositoryTest \
  --tests uz.topdim.order.service.PartnerServiceTest
```

Expected: PASS. If Docker is unavailable, the repository class is skipped by its declared Testcontainers condition and the service tests still pass.

- [ ] **Step 8: Commit and push Task 1**

```bash
git add services/order-service/src/main/java/uz/topdim/order/repository/RedemptionRepository.java \
  services/order-service/src/main/java/uz/topdim/order/service/PartnerService.java \
  services/order-service/src/test/java/uz/topdim/order/repository/RedemptionHistoryRepositoryTest.java \
  services/order-service/src/test/java/uz/topdim/order/service/PartnerServiceTest.java
git commit -m "feat(order): filter redemption history"
git push origin admin/codex
```

### Task 2: Endpoint Validation and Trusted Role Scoping

**Files:**
- Modify: `services/order-service/src/main/java/uz/topdim/order/controller/PartnerController.java`
- Modify: `services/order-service/src/test/java/uz/topdim/order/controller/PartnerControllerTest.java`

**Interfaces:**
- Consumes Task 1 service method exactly as declared above.
- Produces optional request parameters `couponCode`, `dateFrom`, and `dateTo` without changing the response type.
- Passes `ctx.staffId` only for `CASHIER`; passes `null` for trusted merchant-wide roles.

- [ ] **Step 1: Write failing controller tests for owner and cashier query forwarding**

Add `GET` tests with `Page.empty()` stubs:

```java
@Test
@DisplayName("GET redemptions: owner forwards normalized filters with merchant-wide scope")
void getRedemptions_ownerForwardsFilters() throws Exception {
    when(partnerAccessResolver.resolve(10L)).thenReturn(ownerContext());
    when(partnerService.getRedemptions(
            77L, null, "CP-12", LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 6), 1, 20)).thenReturn(Page.empty());

    mockMvc.perform(get("/api/v1/partner/redemptions")
                    .header("X-User-Id", "10")
                    .param("couponCode", "CP-12")
                    .param("dateFrom", "2026-08-01")
                    .param("dateTo", "2026-08-06")
                    .param("page", "1").param("size", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content").isArray());

    verify(partnerService).getRedemptions(
            77L, null, "CP-12", LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 6), 1, 20);
}

@Test
@DisplayName("GET redemptions: cashier is always scoped to trusted staff id")
void getRedemptions_cashierUsesTrustedStaffScope() throws Exception {
    when(partnerAccessResolver.resolve(20L)).thenReturn(cashierContext());
    when(partnerService.getRedemptions(77L, 5L, null, null, null, 0, 20))
            .thenReturn(Page.empty());

    mockMvc.perform(get("/api/v1/partner/redemptions")
                    .header("X-User-Id", "20"))
            .andExpect(status().isOk());

    verify(partnerService).getRedemptions(77L, 5L, null, null, null, 0, 20);
}
```

- [ ] **Step 2: Write failing invalid-input parameterized tests**

Use these exact invalid cases. Each request must return `400`, contain `success=false`, and never call `partnerAccessResolver.resolve(...)`:

```java
static Stream<String> invalidHistoryQueries() {
    return Stream.of(
            "?page=-1",
            "?size=0",
            "?size=101",
            "?couponCode=" + "A".repeat(51),
            "?dateFrom=2026-02-31",
            "?dateFrom=2026-08-07&dateTo=2026-08-06");
}
```

```java
@ParameterizedTest
@MethodSource("invalidHistoryQueries")
void getRedemptions_invalidQuery_returns400(String query) throws Exception {
    mockMvc.perform(get("/api/v1/partner/redemptions" + query)
                    .header("X-User-Id", "10"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false));

    verifyNoInteractions(partnerAccessResolver, partnerService);
}
```

- [ ] **Step 3: Run controller tests and verify RED**

```bash
./gradlew :services:order-service:test --tests uz.topdim.order.controller.PartnerControllerTest
```

Expected: compilation or verification failures because the controller still calls the old service methods and does not validate filters.

- [ ] **Step 4: Implement request parsing and validation before access resolution**

Accept `String couponCode`, `String dateFrom`, and `String dateTo` request parameters. Parse dates explicitly so malformed calendar values cannot fall through the generic exception handler as `500`:

```java
private LocalDate parseHistoryDate(String field, String value) {
    if (value == null || value.isBlank()) return null;
    try {
        return LocalDate.parse(value);
    } catch (DateTimeParseException exception) {
        throw new IllegalArgumentException(
                field + " должен быть датой в формате YYYY-MM-DD");
    }
}
```

Parse and validate before resolving context:

```java
private void validateRedemptionHistoryQuery(
        int page, int size, String couponCode, LocalDate dateFrom, LocalDate dateTo) {
    if (page < 0) throw new IllegalArgumentException("Номер страницы не может быть отрицательным");
    if (size < 1 || size > 100) {
        throw new IllegalArgumentException("Размер страницы должен быть от 1 до 100");
    }
    if (couponCode != null && couponCode.trim().length() > 50) {
        throw new IllegalArgumentException("Код купона не должен превышать 50 символов");
    }
    if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
        throw new IllegalArgumentException("Начало периода не может быть позже окончания");
    }
}
```

Then call:

```java
LocalDate parsedDateFrom = parseHistoryDate("dateFrom", dateFrom);
LocalDate parsedDateTo = parseHistoryDate("dateTo", dateTo);
validateRedemptionHistoryQuery(page, size, couponCode, parsedDateFrom, parsedDateTo);
PartnerAccessContext ctx = partnerAccessResolver.resolve(userId);
Long staffId = "CASHIER".equals(ctx.getRole()) ? ctx.getStaffId() : null;
return ResponseEntity.ok(ApiResponse.success(partnerService.getRedemptions(
        ctx.getMerchantId(), staffId, couponCode,
        parsedDateFrom, parsedDateTo, page, size)));
```

- [ ] **Step 5: Run controller and service tests**

```bash
./gradlew :services:order-service:test \
  --tests uz.topdim.order.controller.PartnerControllerTest \
  --tests uz.topdim.order.service.PartnerServiceTest
```

Expected: PASS.

- [ ] **Step 6: Commit and push Task 2**

```bash
git add services/order-service/src/main/java/uz/topdim/order/controller/PartnerController.java \
  services/order-service/src/test/java/uz/topdim/order/controller/PartnerControllerTest.java
git commit -m "fix(order): validate redemption history filters"
git push origin admin/codex
```

### Task 3: URL-Normalized Redemption History Page

**Files:**
- Create: `frontend/partner/src/features/redemptions/historyFilters.ts`
- Create: `frontend/partner/src/features/redemptions/historyFilters.test.ts`
- Create: `frontend/partner/src/pages/RedemptionHistoryPage.tsx`
- Create: `frontend/partner/src/pages/RedemptionHistoryPage.test.tsx`

**Interfaces:**
- Produces `RedemptionHistoryFilters` with `{ code: string; from?: string; to?: string; page: number }`.
- Produces `parseHistorySearchParams(URLSearchParams): RedemptionHistoryFilters`.
- Produces `serializeHistoryFilters(RedemptionHistoryFilters): URLSearchParams`.
- Produces `historyApiParams(RedemptionHistoryFilters): Record<string, string | number>`.
- React Query key family starts with `['partner-redemptions']`.

- [ ] **Step 1: Write failing pure filter tests**

```typescript
it('normalizes valid URL filters and converts the page for the API', () => {
  const filters = parseHistorySearchParams(new URLSearchParams(
    'code=%20cp-12%20&from=2026-08-01&to=2026-08-06&page=3',
  ));

  expect(filters).toEqual({ code: 'cp-12', from: '2026-08-01', to: '2026-08-06', page: 3 });
  expect(historyApiParams(filters)).toEqual({
    page: 2, size: 20, couponCode: 'cp-12', dateFrom: '2026-08-01', dateTo: '2026-08-06',
  });
});

it('discards malformed dates, inverted ranges, and invalid pages', () => {
  expect(parseHistorySearchParams(new URLSearchParams(
    'from=2026-02-31&to=2026-01-01&page=-4',
  ))).toEqual({ code: '', to: '2026-01-01', page: 1 });

  expect(parseHistorySearchParams(new URLSearchParams(
    'from=2026-08-07&to=2026-08-06&page=2',
  ))).toEqual({ code: '', page: 2 });
});
```

- [ ] **Step 2: Run the utility test and verify RED**

```bash
cd frontend/partner
npm test -- historyFilters.test.ts
```

Expected: FAIL because the module does not exist.

- [ ] **Step 3: Implement the filter utility**

Implement strict date validation, inverted-range removal, URL serialization, and API conversion:

```typescript
import dayjs from 'dayjs';

export interface RedemptionHistoryFilters {
  code: string;
  from?: string;
  to?: string;
  page: number;
}

function validDate(value: string | null): value is string {
  return Boolean(value && /^\d{4}-\d{2}-\d{2}$/.test(value)
    && dayjs(value).isValid() && dayjs(value).format('YYYY-MM-DD') === value);
}

export function parseHistorySearchParams(params: URLSearchParams): RedemptionHistoryFilters {
  const code = (params.get('code') || '').trim().slice(0, 50);
  let from = validDate(params.get('from')) ? params.get('from')! : undefined;
  let to = validDate(params.get('to')) ? params.get('to')! : undefined;
  if (from && to && from > to) {
    from = undefined;
    to = undefined;
  }
  const rawPage = params.get('page');
  const parsedPage = rawPage && /^\d+$/.test(rawPage) ? Number(rawPage) : 1;
  const page = Number.isSafeInteger(parsedPage) && parsedPage > 0 ? parsedPage : 1;
  return { code, ...(from ? { from } : {}), ...(to ? { to } : {}), page };
}

export function serializeHistoryFilters(filters: RedemptionHistoryFilters): URLSearchParams {
  const params = new URLSearchParams();
  if (filters.code) params.set('code', filters.code);
  if (filters.from) params.set('from', filters.from);
  if (filters.to) params.set('to', filters.to);
  if (filters.page > 1) params.set('page', String(filters.page));
  return params;
}

export function historyApiParams(
  filters: RedemptionHistoryFilters,
): Record<string, string | number> {
  return {
    page: filters.page - 1,
    size: 20,
    ...(filters.code ? { couponCode: filters.code } : {}),
    ...(filters.from ? { dateFrom: filters.from } : {}),
    ...(filters.to ? { dateTo: filters.to } : {}),
  };
}
```

- [ ] **Step 4: Run utility tests and verify GREEN**

Run the same Vitest command. Expected: PASS.

- [ ] **Step 5: Write failing page tests**

Mock `../api`, render the page under `MemoryRouter` and `QueryClientProvider`, and cover:

```typescript
function LocationProbe() {
  const location = useLocation();
  return <span data-testid="location-search">{location.search}</span>;
}

function renderHistory(initialEntry: string) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <AntApp>
        <MemoryRouter initialEntries={[initialEntry]}>
          <Routes>
            <Route path="/redemptions" element={<><RedemptionHistoryPage /><LocationProbe /></>} />
          </Routes>
        </MemoryRouter>
      </AntApp>
    </QueryClientProvider>,
  );
}
```

```typescript
it('loads URL filters and renders the paginated history', async () => {
  mockedApi.get.mockResolvedValue({ data: { data: {
    content: [{
      id: 91, couponTitle: 'SPA', optionTitle: 'VIP', couponCode: 'CP-12',
      redeemedByStaff: 'Али', redeemMethod: 'QR', redeemedAt: '2026-08-06T12:30:00',
    }],
    number: 1, size: 20, totalElements: 21,
  } } });

  renderHistory('/redemptions?code=CP-12&from=2026-08-01&to=2026-08-06&page=2');

  expect(await screen.findByText('SPA')).toBeTruthy();
  expect(screen.getByText('VIP')).toBeTruthy();
  expect(screen.getByText('Али')).toBeTruthy();
  expect(mockedApi.get).toHaveBeenCalledWith('/api/v1/partner/redemptions', {
    params: { page: 1, size: 20, couponCode: 'CP-12', dateFrom: '2026-08-01', dateTo: '2026-08-06' },
  });
});
```

Add these concrete cases to the same test file:

```typescript
it('applies code search and resets the page to one', async () => {
  mockedApi.get.mockResolvedValue(emptyPageResponse);
  renderHistory('/redemptions?page=4');

  fireEvent.change(await screen.findByPlaceholderText('CP-XXXX1234'), {
    target: { value: ' cp-vip ' },
  });
  fireEvent.click(screen.getByRole('button', { name: 'Найти' }));

  await waitFor(() => expect(screen.getByTestId('location-search').textContent)
    .toBe('?code=cp-vip'));
  await waitFor(() => expect(mockedApi.get).toHaveBeenLastCalledWith(
    '/api/v1/partner/redemptions',
    { params: { page: 0, size: 20, couponCode: 'cp-vip' } },
  ));
});

it('distinguishes empty history from an empty filtered result', async () => {
  mockedApi.get.mockResolvedValue(emptyPageResponse);
  const first = renderHistory('/redemptions');
  expect(await screen.findByText('Погашений пока нет')).toBeTruthy();
  first.unmount();

  renderHistory('/redemptions?code=missing');
  expect(await screen.findByText('По заданным фильтрам ничего не найдено')).toBeTruthy();
  fireEvent.click(screen.getByRole('button', { name: 'Сбросить' }));
  await waitFor(() => expect(screen.getByTestId('location-search').textContent).toBe(''));
});

it('shows an error and retries the same query', async () => {
  mockedApi.get
    .mockRejectedValueOnce(new Error('network error'))
    .mockResolvedValueOnce(emptyPageResponse);
  renderHistory('/redemptions?code=CP-1');

  expect(await screen.findByText('Не удалось загрузить историю погашений')).toBeTruthy();
  fireEvent.click(screen.getByRole('button', { name: 'Повторить' }));
  await waitFor(() => expect(mockedApi.get).toHaveBeenCalledTimes(2));
});

it('replaces malformed URL parameters with their normalized form', async () => {
  mockedApi.get.mockResolvedValue(emptyPageResponse);
  renderHistory('/redemptions?from=2026-02-31&page=-2');
  await waitFor(() => expect(screen.getByTestId('location-search').textContent).toBe(''));
});
```

Define `emptyPageResponse` as an API wrapper with `content: []`, `number: 0`, `size: 20`, and `totalElements: 0`. The `renderHistory` helper must include a `LocationProbe` component rendering `useLocation().search` into `data-testid="location-search"`.

- [ ] **Step 6: Run page tests and verify RED**

```bash
npm test -- RedemptionHistoryPage.test.tsx
```

Expected: FAIL because `RedemptionHistoryPage` does not exist.

- [ ] **Step 7: Implement the page**

Use `useSearchParams`, `Form`, `Input`, `DatePicker.RangePicker`, `Table`, `Tag`, `Result`, and `Spin`. Normalize URL parameters with a replace navigation when serialized normalized parameters differ:

```typescript
const [searchParams, setSearchParams] = useSearchParams();
const filters = useMemo(() => parseHistorySearchParams(searchParams), [searchParams]);

useEffect(() => {
  const normalized = serializeHistoryFilters(filters);
  if (normalized.toString() !== searchParams.toString()) {
    setSearchParams(normalized, { replace: true });
  }
}, [filters, searchParams, setSearchParams]);
```

Query with:

```typescript
const query = useQuery({
  queryKey: ['partner-redemptions', filters],
  queryFn: async (): Promise<RedemptionPage> => {
    const response = await api.get('/api/v1/partner/redemptions', {
      params: historyApiParams(filters),
    });
    return response.data.data;
  },
});
```

Use these handlers for filter and pagination transitions:

```typescript
const applyFilters = (values: { code?: string; period?: [Dayjs, Dayjs] }) => {
  setSearchParams(serializeHistoryFilters({
    code: (values.code || '').trim().slice(0, 50),
    ...(values.period?.[0] ? { from: values.period[0].format('YYYY-MM-DD') } : {}),
    ...(values.period?.[1] ? { to: values.period[1].format('YYYY-MM-DD') } : {}),
    page: 1,
  }));
};

const resetFilters = () => {
  form.resetFields();
  setSearchParams(new URLSearchParams());
};

const changePage = (page: number) => {
  setSearchParams(serializeHistoryFilters({ ...filters, page }));
};
```

Ant Design pagination uses `current: filters.page`, `pageSize: 20`, and `total: data.totalElements`. Use `rowKey="id"`. The date picker uses initial `dayjs(filters.from)`/`dayjs(filters.to)` values when present. Render `Result` branches before the table for request error and the two distinct empty states; wire `query.refetch()` to `Повторить`.

- [ ] **Step 8: Run page, utility, lint, and build checks**

```bash
npm test -- historyFilters.test.ts RedemptionHistoryPage.test.tsx
npm run lint
npm run build
```

Expected: PASS; build may retain the already-known large-chunk warning.

- [ ] **Step 9: Commit and push Task 3**

```bash
git add frontend/partner/src/features/redemptions/historyFilters.ts \
  frontend/partner/src/features/redemptions/historyFilters.test.ts \
  frontend/partner/src/pages/RedemptionHistoryPage.tsx \
  frontend/partner/src/pages/RedemptionHistoryPage.test.tsx
git commit -m "feat(partner): add redemption history page"
git push origin admin/codex
```

### Task 4: Shared Route and Navigation Item

**Files:**
- Modify: `frontend/partner/src/App.tsx`
- Modify: `frontend/partner/src/App.test.tsx`
- Modify: `frontend/partner/src/layouts/PartnerLayout.tsx`

**Interfaces:**
- Consumes `RedemptionHistoryPage` from Task 3.
- Produces authenticated route `/redemptions` for every validated partner role.
- The history route is read-only and does not use `RedeemAccessOnly`.

- [ ] **Step 1: Write failing route/menu tests**

Mock `RedemptionHistoryPage` in `App.test.tsx`, then render `/redemptions` with valid `OWNER`, `MANAGER`, and `CASHIER` contexts in a parameterized test:

```typescript
it.each([
  ['OWNER', true, true],
  ['OWNER', true, false],
  ['MANAGER', true, true],
  ['CASHIER', false, true],
])('opens history for %s', async (role, canViewDashboard, canRedeem) => {
  window.history.replaceState({}, '', '/redemptions');
  localStorage.setItem('token', `${role}-token`);
  localStorage.setItem('partnerContext', JSON.stringify({
    role, merchantId: 8,
    ...(role === 'CASHIER' ? { merchantLocationId: 21, staffId: 15 } : {}),
    canViewDashboard, canRedeem,
  }));

  render(<App />);

  expect(await screen.findByText('History content')).toBeTruthy();
  expect(screen.getByText('История погашений')).toBeTruthy();
});
```

- [ ] **Step 2: Run App tests and verify RED**

```bash
cd frontend/partner
npm test -- App.test.tsx
```

Expected: FAIL because the route and menu item do not exist.

- [ ] **Step 3: Add route and navigation**

Import `RedemptionHistoryPage` into `App.tsx` and add:

```tsx
<Route path="redemptions" element={<RedemptionHistoryPage />} />
```

Import `HistoryOutlined` into `PartnerLayout.tsx` and append this item for every valid context:

```tsx
items.push({
  key: '/redemptions',
  icon: <HistoryOutlined />,
  label: 'История погашений',
});
```

Place it directly after `Погашение` in the menu.

- [ ] **Step 4: Run App tests, all frontend tests, lint, and build**

```bash
npm test -- App.test.tsx
npm test
npm run lint
npm run build
```

Expected: all tests pass.

- [ ] **Step 5: Commit and push Task 4**

```bash
git add frontend/partner/src/App.tsx frontend/partner/src/App.test.tsx \
  frontend/partner/src/layouts/PartnerLayout.tsx
git commit -m "feat(partner): expose redemption history navigation"
git push origin admin/codex
```

### Task 5: Refresh History After Successful Redemption

**Files:**
- Modify: `frontend/partner/src/pages/RedeemPage.tsx`
- Create: `frontend/partner/src/pages/RedeemPage.test.tsx`

**Interfaces:**
- Consumes the query-key family `['partner-redemptions']` from Task 3.
- Produces one shared success callback used by both PIN and QR redemption paths.

- [ ] **Step 1: Write failing PIN and QR cache invalidation regression tests**

Mock `../api` and `html5-qrcode`. Define the scanner mock so opening the camera immediately reports one valid sizbiz payload:

```typescript
const scannerStart = vi.hoisted(() => vi.fn());

vi.mock('html5-qrcode', () => ({
  Html5Qrcode: vi.fn().mockImplementation(() => ({
    start: scannerStart,
    stop: vi.fn().mockResolvedValue(undefined),
    getState: vi.fn().mockReturnValue(2),
  })),
}));

vi.mock('../api', () => ({
  default: { post: vi.fn() },
}));

const mockedApi = vi.mocked(api);

function renderRedeem(queryClient: QueryClient) {
  return render(
    <QueryClientProvider client={queryClient}>
      <RedeemPage />
    </QueryClientProvider>,
  );
}
```

Create a real `QueryClient`, seed a history query, submit a PIN, and assert the query family is invalidated:

```typescript
it('invalidates redemption history after successful PIN redemption', async () => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  queryClient.setQueryData(['partner-redemptions', { page: 1 }], { content: [] });
  mockedApi.post.mockResolvedValue({ data: { data: {
    purchasedCouponId: 1, couponTitle: 'SPA', optionTitle: 'VIP',
    couponCode: 'CP-1234', merchantName: 'Oasis', status: 'USED',
  } } });

  renderRedeem(queryClient);
  fireEvent.change(screen.getByPlaceholderText('CP-XXXX1234'), {
    target: { value: 'CP-1234' },
  });
  fireEvent.click(screen.getByRole('button', { name: 'Погасить' }));

  expect(await screen.findByText('Купон погашен по PIN!')).toBeTruthy();
  expect(queryClient.getQueryState(['partner-redemptions', { page: 1 }])?.isInvalidated)
    .toBe(true);
});

it('invalidates redemption history after successful QR redemption', async () => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  queryClient.setQueryData(['partner-redemptions', { page: 2 }], { content: [] });
  mockedApi.post.mockResolvedValue({ data: { data: {
    purchasedCouponId: 2, couponTitle: 'Dinner', optionTitle: 'Two guests',
    couponCode: 'CP-QR-1', merchantName: 'Oasis', status: 'USED',
  } } });
  scannerStart.mockImplementation(async (
    _camera: unknown,
    _config: unknown,
    onSuccess: (decodedText: string) => Promise<void>,
  ) => {
    await onSuccess('TOPDIM-QR:qr-token-1');
  });

  renderRedeem(queryClient);
  fireEvent.click(screen.getByRole('tab', { name: /Сканировать QR/i }));
  fireEvent.click(screen.getByRole('button', { name: 'Открыть камеру' }));

  await waitFor(() => expect(mockedApi.post).toHaveBeenCalledWith(
    '/api/v1/partner/redemptions/qr', { qrToken: 'qr-token-1' },
  ));
  expect(await screen.findByText('Купон погашен по QR!')).toBeTruthy();
  expect(queryClient.getQueryState(['partner-redemptions', { page: 2 }])?.isInvalidated)
    .toBe(true);
});
```

- [ ] **Step 2: Run the test and verify RED**

```bash
cd frontend/partner
npm test -- RedeemPage.test.tsx
```

Expected: FAIL because successful redemption does not invalidate history.

- [ ] **Step 3: Implement a shared success callback**

Add `useQueryClient()` and one callback:

```typescript
const queryClient = useQueryClient();

const completeRedemption = useCallback((data: RedeemResult, method: 'PIN' | 'QR') => {
  setResult(data);
  setRedeemMethod(method);
  queryClient.invalidateQueries({ queryKey: ['partner-redemptions'] });
  message.success(method === 'QR' ? 'Купон погашен по QR!' : 'Купон погашен по PIN!');
}, [queryClient]);
```

Use `completeRedemption(res.data.data, 'QR')` and `completeRedemption(res.data.data, 'PIN')` in the two success branches; remove their duplicated result/method/message statements. Add `completeRedemption` to the QR callback dependency list.

- [ ] **Step 4: Run the regression and full frontend checks**

```bash
npm test -- RedeemPage.test.tsx
npm test
npm run lint
npm run build
```

Expected: PASS.

- [ ] **Step 5: Run related backend regression**

From repository root:

```bash
./gradlew :services:order-service:test \
  :services:identity-service:test \
  :services:coupon-service:test
```

Expected: `BUILD SUCCESSFUL`, including JaCoCo verification.

- [ ] **Step 6: Commit and push Task 5**

```bash
git add frontend/partner/src/pages/RedeemPage.tsx \
  frontend/partner/src/pages/RedeemPage.test.tsx
git commit -m "fix(partner): refresh redemption history after use"
git push origin admin/codex
```

## Final Verification Checklist

- [ ] Run `git diff --check` and confirm no whitespace errors.
- [ ] Run `git status --short` and inspect that only expected files changed before each commit.
- [ ] Run the complete partner frontend test, lint, and build commands.
- [ ] Run order, identity, and coupon service test suites with coverage verification.
- [ ] Confirm `git rev-list --left-right --count origin/admin/codex...HEAD` prints `0 0`.
- [ ] Report automated results, commit hashes, push synchronization, the existing frontend bundle-size warning, and the still-deferred manual realistic-data test phase separately.
