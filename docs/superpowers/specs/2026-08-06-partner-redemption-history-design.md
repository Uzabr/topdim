# Partner Redemption History Design

**Date:** 2026-08-06

**Status:** Approved
**Scope:** Partner and cashier portal, plus the existing partner redemption-history API

## Goal

Add a dedicated, operationally useful redemption-history screen to the partner portal. Owners and managers must see all redemptions for their merchant; cashiers must see only redemptions performed by their own staff account. Users can search the complete history by coupon code and restrict it to an inclusive date range.

## Product Decisions

- The portal gets a separate navigation item named `История погашений`.
- The screen route is `/redemptions`.
- `OWNER`, `MANAGER`, and `CASHIER` can open the screen.
- Access to read-only history is independent of the `canRedeem` flag. The backend remains the authority for merchant and staff scoping.
- The default view shows the complete history, newest first.
- Filters in the first release are limited to partial coupon-code search and an optional date range.
- The table does not show a branch. The current API only exposes a technical location ID, and displaying that ID is not useful to the merchant.
- No export, employee filter, branch filter, method filter, or dashboard `Показать всё` link is included in this stage.

## User Interface

The page contains a title, one compact filter card, and a paginated table.

The filter card contains:

- `Код купона` text input;
- an optional date-range picker;
- `Найти` button;
- `Сбросить` button.

Filters are applied explicitly with `Найти`; typing does not issue requests. Applying or resetting filters returns the user to the first page. Active filters and the page number are stored in URL query parameters:

- `code` for the coupon-code fragment;
- `from` and `to` in `YYYY-MM-DD` format;
- `page` as the one-based page shown to the user.

Invalid URL values are normalized: invalid dates are discarded, an inverted range is discarded, and an invalid or non-positive page becomes page 1.

The table uses a fixed server page size of 20 and shows:

1. coupon title;
2. option title;
3. coupon code;
4. employee name;
5. redemption method as a `PIN` or `QR` tag;
6. redemption date and time.

Missing optional values render as `—`. The page distinguishes between an entirely empty history and a filtered search with no matches. A load error shows a clear error state and a retry action.

## API Contract

The existing endpoint remains backward compatible:

`GET /api/v1/partner/redemptions`

Existing parameters remain:

- `page`, zero-based, default `0`;
- `size`, default `20`.

New optional parameters are:

- `couponCode`: trimmed coupon-code fragment, maximum 50 characters;
- `dateFrom`: local calendar date in `YYYY-MM-DD` format;
- `dateTo`: local calendar date in `YYYY-MM-DD` format.

Coupon-code matching is case-insensitive and partial. SQL wildcard characters `%` and `_` supplied by a user are escaped and treated literally.

Date boundaries follow the product calendar used by the stored `LocalDateTime` values:

- `dateFrom` means `redeemedAt >= dateFrom at 00:00`;
- `dateTo` means `redeemedAt < dateTo + 1 day at 00:00`.

This makes both selected dates fully inclusive without relying on a fragile `23:59:59.999` boundary.

The response remains `ApiResponse<Page<RedemptionResponse>>`; no public response fields are removed or renamed.

## Authorization and Data Isolation

The controller continues to resolve access from trusted `X-User-Id`; it never accepts a merchant or staff ID from the browser.

- `OWNER` and `MANAGER`: query by resolved `merchantId`.
- `CASHIER`: query by resolved `merchantId` and the cashier's resolved `staffId`.
- A cashier without a valid staff context is rejected by the existing access resolver rather than falling back to merchant-wide history.

All search and date predicates are added after these trusted scope predicates. Filters cannot widen the user's merchant or staff scope.

## Backend Validation and Querying

The endpoint validates:

- `page >= 0`;
- `1 <= size <= 100`;
- `couponCode` length after trimming is at most 50 characters;
- when both dates exist, `dateFrom <= dateTo`.

Invalid inputs return `400 Bad Request` with a user-readable message. They must not surface as `PageRequest` exceptions or generic `500` responses.

The repository query applies optional code and date predicates, plus an optional trusted `staffId` predicate for cashiers. Results use the stable order `redeemedAt DESC, id DESC`, so adjacent pages cannot reorder rows that have identical timestamps. The purchased coupon association is loaded as part of the history query to avoid one query per table row.

## Frontend Data Flow

The page parses and normalizes URL filters, then requests:

`/api/v1/partner/redemptions?page={zeroBasedPage}&size=20&couponCode={code}&dateFrom={from}&dateTo={to}`

Only active, non-empty optional parameters are sent. React Query uses a key containing the normalized page and filters, so navigating back can reuse the correct result without mixing pages.

After a successful PIN or QR redemption, `RedeemPage` invalidates the redemption-history query family. Opening history afterward therefore fetches the newly created record.

## Error and Empty States

- Initial load: centered loading indicator.
- No redemptions and no filters: `Погашений пока нет`.
- No matches with active filters: `По заданным фильтрам ничего не найдено` and a reset action.
- Request failure: `Не удалось загрузить историю погашений` and a `Повторить` action.
- Invalid user-entered range: prevented in the UI and still rejected by the backend for direct API calls.

## Test Strategy

### Backend

- owner and manager receive merchant-wide history;
- cashier receives only records matching the resolved staff ID;
- partial coupon-code matching is case-insensitive;
- `%` and `_` are treated as literal search characters;
- lower and upper date boundaries are inclusive as designed;
- date-only, code-only, combined, and empty filters work;
- inverted dates, negative page, zero size, size above 100, and overlong code return `400`;
- empty results return an empty page;
- sorting is `redeemedAt DESC, id DESC`;
- purchased coupon data is available to response mapping without per-row fetching.

### Frontend

- the menu item and route are available to owner, manager, and cashier contexts;
- the page renders response rows and total pagination;
- cashier receives the same UI but cannot influence backend scope;
- submitting code and dates emits normalized API parameters and resets page 1;
- pagination emits the correct zero-based backend page;
- reset clears URL filters and reloads unfiltered history;
- malformed URL parameters are normalized;
- loading, unfiltered-empty, filtered-empty, error, and retry states render correctly;
- a successful PIN or QR redemption invalidates the history query.

## Delivery Boundaries

This is one implementation stage with focused backend and partner-frontend changes. It requires no database migration and no public contract removal. Automated frontend and related order/identity regression suites run before the implementation commit. Manual testing with realistic data remains part of the final product-wide testing phase, as previously agreed.
