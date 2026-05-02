# Premium Purchased Coupon Card Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign the purchased coupon card in the user profile into a premium wallet/pass-style card that is less visually overloaded while keeping QR/PIN, refund, complaint, and status logic intact.

**Architecture:** This is a frontend-only UI refinement. Keep existing API contracts and profile page flow. Refactor only the purchased coupon card presentation and CSS, using progressive disclosure for QR/PIN and details.

**Tech Stack:** React, TypeScript, Vite, CSS modules/global CSS, `qrcode.react`, `lucide-react`, existing TopDim web-app design tokens.

---

## Required Skills And Project Rules

- Read and follow `AGENTS.md`.
- Use `superpowers:subagent-driven-development` or `superpowers:executing-plans`.
- Use `frontend/.agent/skills/react/SKILL.md`.
- Use `frontend/.agent/skills/frontend-design/SKILL.md`.
- Do not change backend APIs.
- Do not redesign the whole profile page.
- Do not add new dependencies.
- Keep existing refund and complaint callbacks working.

## Scope

### In Scope

- Redesign `PurchasedCouponCard` into a premium wallet/pass card.
- Hide QR/PIN behind a clear primary action.
- Move secondary information into a details reveal.
- Make refund/complaint actions secondary.
- Preserve status-specific behavior.
- Preserve QR generation with `QRCodeSVG`.

### Out Of Scope

- Backend changes.
- Purchased coupon DTO changes.
- Profile page sidebar redesign.
- Order history redesign.
- Real image/logo support.
- New animation libraries.

## Files

- Modify: `frontend/web-app/src/components/profile/PurchasedCouponCard.tsx`
- Modify: `frontend/web-app/src/components/profile/PurchasedCouponCard.css`
- Optional minor spacing only: `frontend/web-app/src/pages/ProfilePage.css`

## Current Problem

The current card shows too much information at once:

- title;
- status;
- PIN/code;
- copy button;
- large QR;
- address;
- phone;
- working hours;
- expiry;
- QR note;
- refund info;
- refund button;
- complaint button;
- help text.

For a customer, the first question is simpler: **what coupon is this, where can I use it, and how do I show it to the cashier?**

## Target UX

The default card should feel like a premium wallet pass. It should show only the essential information first, then reveal QR/PIN and details on demand.

Default compact card should show:

- coupon status;
- expiry or used/refund status copy;
- coupon title;
- option title;
- merchant name;
- short address line;
- primary action `Показать QR/PIN` for active coupons only;
- secondary action `Подробнее`.

Default compact card must not show:

- large QR;
- long QR explanation text;
- phone;
- working hours;
- help block;
- refund and complaint as visually dominant primary actions.

---

## Task 1: Refactor Card State And Derived Helpers

**Files:**
- Modify: `frontend/web-app/src/components/profile/PurchasedCouponCard.tsx`

- [ ] Add local UI state:

```tsx
const [showPass, setShowPass] = useState(false);
const [showDetails, setShowDetails] = useState(false);
```

- [ ] Keep `copyCode` but make it safe:

```tsx
const copyCode = async () => {
  if (!coupon.couponCode) return;
  await navigator.clipboard.writeText(coupon.couponCode);
};
```

- [ ] Add derived booleans:

```tsx
const isActive = coupon.status === 'ACTIVE';
const canShowPass = isActive && Boolean(coupon.qrToken || coupon.couponCode);
const canRefund = coupon.status === 'ACTIVE';
const canComplain = ['ACTIVE', 'USED', 'EXPIRED'].includes(coupon.status);
const hasMerchantDetails = Boolean(
  coupon.merchantAddress || coupon.merchantPhone || coupon.merchantWorkingHours
);
```

- [ ] Add short status meta helper:

```tsx
function getStatusMeta(coupon: PurchasedCoupon): string {
  if (coupon.status === 'USED' && coupon.usedAt) {
    return `Использован: ${formatDate(coupon.usedAt)}`;
  }
  if (coupon.status === 'REFUNDED') {
    return 'Возврат завершён';
  }
  if (coupon.status === 'REFUND_PENDING') {
    if (coupon.refundStatus === 'APPROVED_PROCESSING' && coupon.refundExpectedAt) {
      return `Возврат одобрен до ${formatDate(coupon.refundExpectedAt)}`;
    }
    return 'Возврат на рассмотрении';
  }
  if (coupon.status === 'EXPIRED') {
    return 'Срок действия истёк';
  }
  if (coupon.status === 'CANCELLED') {
    return 'Купон отменён';
  }
  return `Действует до: ${formatDate(coupon.expiresAt)}`;
}
```

---

## Task 2: Build Compact Premium Pass Layout

**Files:**
- Modify: `frontend/web-app/src/components/profile/PurchasedCouponCard.tsx`

- [ ] Replace the default always-expanded layout with this structure:

```tsx
<article className={`purchased-coupon-card purchased-coupon-card--${coupon.status.toLowerCase()}`}>
  <div className="purchased-coupon-card__surface">
    <div className="purchased-coupon-card__topline">
      <span className={`purchased-coupon-card__status purchased-coupon-card__status--${coupon.status.toLowerCase()}`}>
        {getStatusIcon(coupon.status)}
        {getStatusLabel(coupon.status)}
      </span>
      <span className="purchased-coupon-card__meta">{getStatusMeta(coupon)}</span>
    </div>

    <div className="purchased-coupon-card__body">
      <div className="purchased-coupon-card__merchant-mark" aria-hidden="true">
        {(coupon.merchantName || coupon.couponTitle || 'T').charAt(0).toUpperCase()}
      </div>

      <div className="purchased-coupon-card__main">
        <p className="purchased-coupon-card__eyebrow">TopDim pass</p>
        <h3>{coupon.couponTitle}</h3>
        {coupon.optionTitle ? <p className="purchased-coupon-card__option">{coupon.optionTitle}</p> : null}
        <p className="purchased-coupon-card__merchant">{coupon.merchantName || 'Партнёр TopDim'}</p>
      </div>
    </div>

    <div className="purchased-coupon-card__location">
      <MapPin size={15} />
      <span>{coupon.merchantAddress || 'Адрес партнёра уточните перед визитом'}</span>
    </div>

    <div className="purchased-coupon-card__primary-actions">
      {canShowPass ? (
        <button
          type="button"
          className="purchased-coupon-card__primary-btn"
          onClick={() => setShowPass((value) => !value)}
          aria-expanded={showPass}
        >
          <QrCode size={17} />
          {showPass ? 'Скрыть QR/PIN' : 'Показать QR/PIN'}
        </button>
      ) : null}

      <button
        type="button"
        className="purchased-coupon-card__ghost-btn"
        onClick={() => setShowDetails((value) => !value)}
        aria-expanded={showDetails}
      >
        Подробнее
      </button>
    </div>
  </div>

  {showPass ? (
    <div className="purchased-coupon-card__pass-panel">
      ...
    </div>
  ) : null}

  {showDetails ? (
    <div className="purchased-coupon-card__details-panel">
      ...
    </div>
  ) : null}
</article>
```

- [ ] Do not render pass panel for non-active statuses.
- [ ] Keep title and merchant visible without opening details.

---

## Task 3: Add QR/PIN Reveal Panel

**Files:**
- Modify: `frontend/web-app/src/components/profile/PurchasedCouponCard.tsx`

- [ ] Render QR/PIN panel only when `showPass && canShowPass`.

- [ ] QR/PIN panel content:

```tsx
{showPass && canShowPass ? (
  <div className="purchased-coupon-card__pass-panel">
    <div className="purchased-coupon-card__pass-copy">
      <span className="purchased-coupon-card__label">Предъявление купона</span>
      <h4>Покажите кассиру TopDim</h4>
      <p>Кассир сканирует QR или вводит PIN-код для погашения.</p>
    </div>

    {coupon.qrToken ? (
      <div className="purchased-coupon-card__qr-frame" aria-label="QR-код купона TopDim">
        <QRCodeSVG
          value={buildQrPayload(coupon.qrToken)}
          size={156}
          level="M"
          includeMargin
        />
      </div>
    ) : null}

    <div className="purchased-coupon-card__pin-box">
      <span>PIN / код купона</span>
      <strong>{coupon.couponCode || 'Код недоступен'}</strong>
      {coupon.couponCode ? (
        <button type="button" onClick={copyCode}>
          <Copy size={15} />
          Скопировать
        </button>
      ) : null}
    </div>
  </div>
) : null}
```

- [ ] Keep QR value as `TOPDIM-QR:${qrToken}` through existing `buildQrPayload`.
- [ ] Do not display raw `qrToken`.
- [ ] Do not display QR/PIN for `USED`, `EXPIRED`, `REFUND_PENDING`, `REFUNDED`, or `CANCELLED`.

---

## Task 4: Add Details Reveal Panel

**Files:**
- Modify: `frontend/web-app/src/components/profile/PurchasedCouponCard.tsx`

- [ ] Details panel should render when `showDetails`.

- [ ] Details panel content:

```tsx
{showDetails ? (
  <div className="purchased-coupon-card__details-panel">
    <div className="purchased-coupon-card__details-grid">
      <div className="purchased-coupon-card__detail-item">
        <Store size={16} />
        <div>
          <span>Партнёр</span>
          <strong>{coupon.merchantName || 'Партнёр TopDim'}</strong>
        </div>
      </div>

      {coupon.merchantAddress ? (
        <div className="purchased-coupon-card__detail-item">
          <MapPin size={16} />
          <div>
            <span>Адрес</span>
            <strong>{coupon.merchantAddress}</strong>
          </div>
        </div>
      ) : null}

      {coupon.merchantPhone ? (
        <a className="purchased-coupon-card__detail-item" href={`tel:${coupon.merchantPhone}`}>
          <Phone size={16} />
          <div>
            <span>Телефон</span>
            <strong>{coupon.merchantPhone}</strong>
          </div>
        </a>
      ) : null}

      {coupon.merchantWorkingHours ? (
        <div className="purchased-coupon-card__detail-item">
          <CalendarClock size={16} />
          <div>
            <span>Время работы</span>
            <strong>{coupon.merchantWorkingHours}</strong>
          </div>
        </div>
      ) : null}
    </div>

    {(canRefund || canComplain) && (onRefundRequest || onComplaintRequest) ? (
      <div className="purchased-coupon-card__secondary-actions">
        ...
      </div>
    ) : null}
  </div>
) : null}
```

- [ ] Move refund and complaint buttons into `secondary-actions`.

- [ ] Secondary actions:

```tsx
{canRefund && onRefundRequest ? (
  <button
    type="button"
    className="purchased-coupon-card__secondary-btn purchased-coupon-card__secondary-btn--refund"
    onClick={() => onRefundRequest(coupon)}
  >
    <RotateCcw size={14} />
    Запросить возврат
  </button>
) : null}

{canComplain && onComplaintRequest ? (
  <button
    type="button"
    className="purchased-coupon-card__secondary-btn"
    onClick={() => onComplaintRequest(coupon)}
  >
    <MessageSquare size={14} />
    Сообщить о проблеме
  </button>
) : null}
```

- [ ] Remove the always-visible help block from default view.
- [ ] If support text is needed, show it inside details as small muted text.

---

## Task 5: Redesign CSS As Premium Wallet Pass

**Files:**
- Modify: `frontend/web-app/src/components/profile/PurchasedCouponCard.css`

- [ ] Replace heavy always-visible sections with premium pass styling.

- [ ] Card base:

```css
.purchased-coupon-card {
  position: relative;
  border: 0;
  border-radius: 28px;
  background: transparent;
  overflow: hidden;
}

.purchased-coupon-card__surface {
  position: relative;
  overflow: hidden;
  border-radius: 28px;
  padding: 22px;
  color: #17201a;
  background:
    radial-gradient(circle at 14% 10%, rgba(255, 255, 255, 0.95), transparent 34%),
    linear-gradient(135deg, #fff9ec 0%, #f4fff2 48%, #e9f7ff 100%);
  box-shadow: 0 24px 70px rgba(15, 36, 24, 0.12);
  border: 1px solid rgba(255, 255, 255, 0.75);
}
```

- [ ] Add subtle pass pattern:

```css
.purchased-coupon-card__surface::before {
  content: '';
  position: absolute;
  inset: 0;
  opacity: 0.34;
  background:
    linear-gradient(90deg, rgba(31, 92, 67, 0.06) 1px, transparent 1px),
    linear-gradient(rgba(31, 92, 67, 0.05) 1px, transparent 1px);
  background-size: 28px 28px;
  pointer-events: none;
}

.purchased-coupon-card__surface::after {
  content: '';
  position: absolute;
  right: -42px;
  top: -42px;
  width: 160px;
  height: 160px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(249, 115, 22, 0.18), transparent 68%);
  pointer-events: none;
}
```

- [ ] Use status modifiers:

```css
.purchased-coupon-card--used .purchased-coupon-card__surface,
.purchased-coupon-card--expired .purchased-coupon-card__surface,
.purchased-coupon-card--refunded .purchased-coupon-card__surface,
.purchased-coupon-card--cancelled .purchased-coupon-card__surface {
  filter: saturate(0.8);
  opacity: 0.82;
}

.purchased-coupon-card--refund_pending .purchased-coupon-card__surface {
  background:
    radial-gradient(circle at 14% 10%, rgba(255, 255, 255, 0.95), transparent 34%),
    linear-gradient(135deg, #fff8e7 0%, #eef7ff 100%);
}
```

- [ ] Design primary button:

```css
.purchased-coupon-card__primary-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 44px;
  padding: 0 18px;
  border: 0;
  border-radius: 999px;
  background: #1f5c43;
  color: #fff;
  font-weight: 800;
  cursor: pointer;
  box-shadow: 0 14px 32px rgba(31, 92, 67, 0.22);
}
```

- [ ] Details and pass panels should look attached but secondary:

```css
.purchased-coupon-card__pass-panel,
.purchased-coupon-card__details-panel {
  margin-top: 10px;
  border-radius: 24px;
  padding: 18px;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid rgba(20, 20, 20, 0.08);
  box-shadow: 0 16px 44px rgba(19, 33, 68, 0.08);
}
```

- [ ] Make QR/PIN panel responsive:

```css
.purchased-coupon-card__pass-panel {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(160px, 0.8fr);
  gap: 16px;
  align-items: center;
}

@media (max-width: 720px) {
  .purchased-coupon-card__pass-panel {
    grid-template-columns: 1fr;
    text-align: center;
  }
}
```

- [ ] On mobile:
  - card must not overflow horizontally;
  - primary and ghost buttons can stack;
  - QR should stay centered;
  - PIN code should remain readable.

---

## Task 6: Preserve Business Behavior

**Files:**
- Modify: `frontend/web-app/src/components/profile/PurchasedCouponCard.tsx`

- [ ] Keep these rules:

```text
ACTIVE:
- Show primary “Показать QR/PIN” when qrToken or couponCode exists.
- Allow refund request.
- Allow complaint request.

USED:
- Hide QR/PIN.
- Show usedAt if available.
- Allow complaint request.

EXPIRED:
- Hide QR/PIN.
- Show expired status.
- Allow complaint request.

REFUND_PENDING:
- Hide QR/PIN.
- Show refund status.
- If refundStatus is APPROVED_PROCESSING and refundExpectedAt exists, show expected refund date.
- Do not allow refund request.

REFUNDED:
- Hide QR/PIN.
- Show completed refund copy.
- Do not allow refund request.

CANCELLED:
- Hide QR/PIN.
- Do not allow refund request.
```

- [ ] Keep callbacks:

```tsx
onRefundRequest?.(coupon)
onComplaintRequest?.(coupon)
```

- [ ] Do not modify `ordersApi`, `PurchasedCoupon` type, or backend DTOs.

---

## Task 7: Verification

**Files:**
- Verify: `frontend/web-app/src/components/profile/PurchasedCouponCard.tsx`
- Verify: `frontend/web-app/src/components/profile/PurchasedCouponCard.css`

- [ ] Run:

```bash
cd frontend/web-app && npm run build
```

- [ ] Manual check active coupon:

```text
ACTIVE coupon shows compact premium pass.
Primary action says “Показать QR/PIN”.
QR/PIN is hidden by default.
Clicking “Показать QR/PIN” reveals QR and code.
Copy button copies couponCode.
Details reveal shows merchant address/phone/hours.
Refund and complaint actions are not visually dominant.
```

- [ ] Manual check non-active statuses:

```text
USED coupon does not show QR/PIN.
EXPIRED coupon does not show QR/PIN.
REFUND_PENDING coupon does not show QR/PIN and shows refund status.
REFUNDED coupon does not show refund action.
CANCELLED coupon is muted and has no primary QR action.
```

- [ ] Manual mobile check:

```text
No horizontal scroll.
QR is centered.
PIN remains readable.
Buttons are easy to tap.
Card still feels premium and clean.
```

## Acceptance Criteria

- Purchased coupon cards look like premium wallet/pass cards.
- Default card is compact and not overloaded.
- QR/PIN is available in one click for active coupons.
- Refund and complaint actions still work.
- Non-active coupons cannot expose QR/PIN.
- Web-app build passes.
