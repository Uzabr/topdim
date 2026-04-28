# Partner Coupon Approval MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the current partner coupon flow gap so a merchant owner can review a prepared coupon in the partner cabinet, approve it to publish, or request revisions with a comment.

**Architecture:** Keep coupon state transitions in `coupon-service`. Add partner-owned wrapper methods that verify `Merchant.userId == X-User-Id` before delegating to the existing canonical `CouponOfferService.approveByMerchant()` and `requestRevisionByMerchant()`. Add a focused partner frontend review page that fetches the partner-owned coupon preview and exposes only the two valid actions for `WAITING_FOR_MERCHANT`.

**Tech Stack:** Java 21/Spring Boot, JUnit 5/Mockito/AssertJ, React 19, React Router 7, TanStack Query, Ant Design 6, Axios.

---

## Required Project Instructions

- [ ] Read `/Users/abror/Projects/copy-topdim-repo/topdim/AGENTS.md` before coding.
- [ ] Search for local skills before coding: `rg --files -g 'SKILL.md'`.
- [ ] If local folder skills exist in the worker environment, use the relevant ones before editing those areas.
- [ ] Use `superpowers:test-driven-development` for backend logic changes.
- [ ] Use `superpowers:systematic-debugging` for any failing test or unexpected behavior.
- [ ] Use `superpowers:verification-before-completion` before reporting completion.
- [ ] Do not rewrite the whole partner cabinet or admin moderation. This plan only closes the merchant approval/revision gap.

## Current Flow And Gap

Current working path:

1. Partner creates coupon request in `frontend/partner/src/pages/CouponRequestFormPage.tsx`.
2. Backend stores it through `POST /api/v1/partner/coupons` as `LEAD`.
3. Admin/moderator takes request to work: `LEAD -> DRAFT`.
4. Admin/moderator sends prepared coupon to merchant approval: `DRAFT -> WAITING_FOR_MERCHANT`.

Current missing path:

1. Partner cabinet only shows `WAITING_FOR_MERCHANT` status in `CouponsPage.tsx`.
2. Partner has no page to preview the final coupon.
3. Partner has no `approve` or `request revision` action.
4. A temporary admin screen currently imitates merchant review. That is acceptable only as support fallback, not as the main MVP partner flow.

## Business Rules

- Only a merchant owner resolved by `Merchant.userId == X-User-Id` can approve/request revision in this MVP.
- Cashiers must not approve or revise coupon offers.
- Staff manager approval is out of scope until `coupon-service` resolves partner access through `identity-service`; do not fake manager access.
- Coupon can be approved only from `WAITING_FOR_MERCHANT`.
- Coupon can request revision only from `WAITING_FOR_MERCHANT`.
- Revision comment is required and trimmed.
- Approval must keep existing publication readiness guards inside `CouponOfferService.approveByMerchant()`.
- Partner can review only coupons owned by their merchant.
- Public catalog must still show only `ACTIVE` coupons.

## Files

- Modify: `services/coupon-service/src/main/java/uz/topdim/coupon/service/PartnerCouponService.java`
- Modify: `services/coupon-service/src/main/java/uz/topdim/coupon/controller/PartnerCouponController.java`
- Create: `services/coupon-service/src/main/java/uz/topdim/coupon/dto/RequestCouponRevisionRequest.java`
- Modify: `services/coupon-service/src/test/java/uz/topdim/coupon/service/PartnerCouponServiceTest.java`
- Modify: `services/coupon-service/src/test/java/uz/topdim/coupon/controller/CouponOfferControllerValidationTest.java`
- Create: `frontend/partner/src/pages/CouponApprovalPage.tsx`
- Modify: `frontend/partner/src/App.tsx`
- Modify: `frontend/partner/src/pages/CouponsPage.tsx`
- Modify: `frontend/admin-app/src/features/coupons/MerchantReviewPage.tsx`

---

### Task 1: Backend Service Tests For Partner Approval

**Files:**
- Modify: `services/coupon-service/src/test/java/uz/topdim/coupon/service/PartnerCouponServiceTest.java`
- Test: `services/coupon-service/src/test/java/uz/topdim/coupon/service/PartnerCouponServiceTest.java`

- [ ] **Step 1: Add `CouponOfferService` mock**

Add the mock near existing repository mocks:

```java
@Mock private CouponOfferService couponOfferService;
```

- [ ] **Step 2: Add response helper**

Add this helper near `createRequest()`:

```java
private CouponOfferResponse response(Long id, String status) {
    return CouponOfferResponse.builder()
            .id(id)
            .title("Тест купон")
            .status(status)
            .build();
}
```

- [ ] **Step 3: Add approval happy-path test**

Add this test after `getMyCouponById` tests:

```java
@Test
@DisplayName("approveMyCoupon: свой WAITING_FOR_MERCHANT → делегирует approveByMerchant")
void approveMyCoupon_ownWaitingCoupon_delegatesToCanonicalService() {
    Merchant merchant = createMerchant();
    CouponOffer offer = createOffer(merchant, CouponStatus.WAITING_FOR_MERCHANT);
    when(merchantRepository.findByUserId(10L)).thenReturn(Optional.of(merchant));
    when(couponOfferRepository.findById(100L)).thenReturn(Optional.of(offer));
    when(couponOfferService.approveByMerchant(100L)).thenReturn(response(100L, "ACTIVE"));

    CouponOfferResponse result = partnerCouponService.approveMyCoupon(10L, 100L);

    assertThat(result.getStatus()).isEqualTo("ACTIVE");
    verify(couponOfferService).approveByMerchant(100L);
}
```

- [ ] **Step 4: Add foreign coupon approval test**

```java
@Test
@DisplayName("approveMyCoupon: чужой купон → IllegalStateException и не публикует")
void approveMyCoupon_otherMerchant_throwsBeforeDelegation() {
    Merchant myMerchant = createMerchant();
    Merchant other = Merchant.builder().id(2L).name("Другой").userId(20L).build();
    CouponOffer offer = createOffer(other, CouponStatus.WAITING_FOR_MERCHANT);
    when(merchantRepository.findByUserId(10L)).thenReturn(Optional.of(myMerchant));
    when(couponOfferRepository.findById(100L)).thenReturn(Optional.of(offer));

    assertThatThrownBy(() -> partnerCouponService.approveMyCoupon(10L, 100L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("другому партнёру");

    verifyNoInteractions(couponOfferService);
}
```

- [ ] **Step 5: Add wrong status approval test**

```java
@Test
@DisplayName("approveMyCoupon: DRAFT → IllegalStateException")
void approveMyCoupon_draft_throws() {
    Merchant merchant = createMerchant();
    CouponOffer offer = createOffer(merchant, CouponStatus.DRAFT);
    when(merchantRepository.findByUserId(10L)).thenReturn(Optional.of(merchant));
    when(couponOfferRepository.findById(100L)).thenReturn(Optional.of(offer));

    assertThatThrownBy(() -> partnerCouponService.approveMyCoupon(10L, 100L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("WAITING_FOR_MERCHANT");

    verifyNoInteractions(couponOfferService);
}
```

- [ ] **Step 6: Add revision happy-path test**

```java
@Test
@DisplayName("requestRevisionForMyCoupon: свой WAITING_FOR_MERCHANT → сохраняет trimmed comment")
void requestRevisionForMyCoupon_ownWaitingCoupon_delegatesWithTrimmedComment() {
    Merchant merchant = createMerchant();
    CouponOffer offer = createOffer(merchant, CouponStatus.WAITING_FOR_MERCHANT);
    when(merchantRepository.findByUserId(10L)).thenReturn(Optional.of(merchant));
    when(couponOfferRepository.findById(100L)).thenReturn(Optional.of(offer));
    when(couponOfferService.requestRevisionByMerchant(100L, "Исправить цену"))
            .thenReturn(response(100L, "REVISION_REQUESTED"));

    CouponOfferResponse result = partnerCouponService.requestRevisionForMyCoupon(
            10L, 100L, "  Исправить цену  ");

    assertThat(result.getStatus()).isEqualTo("REVISION_REQUESTED");
    verify(couponOfferService).requestRevisionByMerchant(100L, "Исправить цену");
}
```

- [ ] **Step 7: Add blank revision comment test**

```java
@Test
@DisplayName("requestRevisionForMyCoupon: пустой комментарий → IllegalArgumentException")
void requestRevisionForMyCoupon_blankComment_throws() {
    Merchant merchant = createMerchant();
    CouponOffer offer = createOffer(merchant, CouponStatus.WAITING_FOR_MERCHANT);
    when(merchantRepository.findByUserId(10L)).thenReturn(Optional.of(merchant));
    when(couponOfferRepository.findById(100L)).thenReturn(Optional.of(offer));

    assertThatThrownBy(() -> partnerCouponService.requestRevisionForMyCoupon(10L, 100L, "   "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Комментарий");

    verifyNoInteractions(couponOfferService);
}
```

- [ ] **Step 8: Run tests and verify they fail before implementation**

Run:

```bash
./gradlew :services:coupon-service:test --tests "uz.topdim.coupon.service.PartnerCouponServiceTest"
```

Expected result: FAIL because `approveMyCoupon()` and `requestRevisionForMyCoupon()` do not exist.

---

### Task 2: Backend Partner Approval Endpoints

**Files:**
- Create: `services/coupon-service/src/main/java/uz/topdim/coupon/dto/RequestCouponRevisionRequest.java`
- Modify: `services/coupon-service/src/main/java/uz/topdim/coupon/service/PartnerCouponService.java`
- Modify: `services/coupon-service/src/main/java/uz/topdim/coupon/controller/PartnerCouponController.java`
- Test: `services/coupon-service/src/test/java/uz/topdim/coupon/service/PartnerCouponServiceTest.java`

- [ ] **Step 1: Create request DTO**

Create `RequestCouponRevisionRequest.java`:

```java
package uz.topdim.coupon.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для запроса правок партнёром при финальном согласовании купона.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RequestCouponRevisionRequest {

    @NotBlank(message = "Укажите, что нужно исправить")
    @Size(max = 2000, message = "Комментарий не более 2000 символов")
    private String comment;
}
```

- [ ] **Step 2: Inject canonical coupon service**

Modify constructor dependencies in `PartnerCouponService.java` by adding:

```java
private final CouponOfferService couponOfferService;
```

- [ ] **Step 3: Add owned-offer helper**

Add this private helper in `PartnerCouponService.java`:

```java
private CouponOffer getOwnedOffer(Long userId, Long couponId) {
    Merchant merchant = getMerchantForOwner(userId);
    CouponOffer offer = couponOfferRepository.findById(couponId)
            .orElseThrow(() -> new ResourceNotFoundException("Купон не найден"));

    if (offer.getMerchant() == null || !offer.getMerchant().getId().equals(merchant.getId())) {
        throw new IllegalStateException("Купон принадлежит другому партнёру");
    }

    return offer;
}
```

- [ ] **Step 4: Replace duplicate ownership checks**

Use `getOwnedOffer(userId, couponId)` inside `getMyCouponById()` and `updateMyCoupon()` instead of duplicating merchant lookup and ownership comparison. Keep existing behavior and messages.

- [ ] **Step 5: Add partner approval methods**

Add these public methods to `PartnerCouponService.java`:

```java
/**
 * Партнёр одобряет свой купон после подготовки TopDim.
 * Доступно только владельцу мерчанта в MVP.
 */
@Transactional
public CouponOfferResponse approveMyCoupon(Long userId, Long couponId) {
    CouponOffer offer = getOwnedOffer(userId, couponId);

    if (offer.getStatus() != CouponStatus.WAITING_FOR_MERCHANT) {
        throw new IllegalStateException(
                "Одобрить можно только купон в статусе WAITING_FOR_MERCHANT. Текущий: " + offer.getStatus());
    }

    return couponOfferService.approveByMerchant(couponId);
}

/**
 * Партнёр возвращает свой купон на доработку с обязательным комментарием.
 */
@Transactional
public CouponOfferResponse requestRevisionForMyCoupon(Long userId, Long couponId, String comment) {
    CouponOffer offer = getOwnedOffer(userId, couponId);

    if (offer.getStatus() != CouponStatus.WAITING_FOR_MERCHANT) {
        throw new IllegalStateException(
                "Запросить правки можно только из статуса WAITING_FOR_MERCHANT. Текущий: " + offer.getStatus());
    }

    String trimmedComment = comment == null ? "" : comment.trim();
    if (trimmedComment.isBlank()) {
        throw new IllegalArgumentException("Комментарий к правкам обязателен");
    }

    return couponOfferService.requestRevisionByMerchant(couponId, trimmedComment);
}
```

- [ ] **Step 6: Add controller endpoints**

Modify `PartnerCouponController.java` imports to include `RequestCouponRevisionRequest`.

Add endpoints after `updateCoupon()`:

```java
/** Одобрить подготовленный купон → ACTIVE. */
@PostMapping("/{id}/approve")
public ResponseEntity<ApiResponse<CouponOfferResponse>> approveCoupon(
        @RequestHeader("X-User-Id") Long userId,
        @PathVariable Long id
) {
    return ResponseEntity.ok(ApiResponse.success(
            "Купон одобрен и опубликован",
            partnerCouponService.approveMyCoupon(userId, id)));
}

/** Запросить правки по подготовленному купону → REVISION_REQUESTED. */
@PostMapping("/{id}/request-revision")
public ResponseEntity<ApiResponse<CouponOfferResponse>> requestRevision(
        @RequestHeader("X-User-Id") Long userId,
        @PathVariable Long id,
        @Valid @RequestBody RequestCouponRevisionRequest request
) {
    return ResponseEntity.ok(ApiResponse.success(
            "Купон возвращён на доработку",
            partnerCouponService.requestRevisionForMyCoupon(userId, id, request.getComment())));
}
```

- [ ] **Step 7: Run backend service tests**

Run:

```bash
./gradlew :services:coupon-service:test --tests "uz.topdim.coupon.service.PartnerCouponServiceTest"
```

Expected result: PASS.

---

### Task 3: Controller Validation Test For Revision Comment

**Files:**
- Modify: `services/coupon-service/src/test/java/uz/topdim/coupon/controller/CouponOfferControllerValidationTest.java`
- Test: `services/coupon-service/src/test/java/uz/topdim/coupon/controller/CouponOfferControllerValidationTest.java`

- [ ] **Step 1: Add validation test**

Add this test:

```java
@Test
@DisplayName("Partner requestRevision: пустой comment возвращает 400 и не вызывает сервис")
void partnerRequestRevision_blankComment_returnsBadRequest() throws Exception {
    partnerMockMvc.perform(post("/api/v1/partner/coupons/100/request-revision")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-User-Id", 77)
                    .content("{\"comment\":\"   \"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("Ошибка валидации"));

    verifyNoInteractions(partnerCouponService);
}
```

- [ ] **Step 2: Run controller validation test**

Run:

```bash
./gradlew :services:coupon-service:test --tests "uz.topdim.coupon.controller.CouponOfferControllerValidationTest"
```

Expected result: PASS.

---

### Task 4: Partner Frontend Approval Page

**Files:**
- Create: `frontend/partner/src/pages/CouponApprovalPage.tsx`
- Modify: `frontend/partner/src/App.tsx`
- Modify: `frontend/partner/src/pages/CouponsPage.tsx`

- [ ] **Step 1: Create approval page**

Create `CouponApprovalPage.tsx` with this structure:

```tsx
import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert, App as AntApp, Button, Card, Col, Descriptions, Divider,
  Image, Input, Modal, Result, Row, Space, Spin, Tag, Typography
} from 'antd';
import {
  ArrowLeftOutlined, CheckCircleOutlined, CloseCircleOutlined
} from '@ant-design/icons';
import dayjs from 'dayjs';
import api from '../api';

const { Title, Text, Paragraph } = Typography;
const { TextArea } = Input;

interface CouponOption {
  id: number;
  title: string;
  regularPrice: number;
  couponPrice: number;
  quantityLimit: number | null;
  quantitySold: number;
  status: string;
}

interface CouponDetail {
  id: number;
  title: string;
  offerDescription?: string;
  oldPrice?: number;
  fromPrice: number;
  discountPercent?: number;
  coverImageUrl?: string;
  status: string;
  revisionComment?: string;
  buyUntil?: string;
  useUntil?: string;
  images?: string[];
  options?: CouponOption[];
}

function formatPrice(value?: number | null) {
  if (typeof value !== 'number') return '—';
  return `${value.toLocaleString('ru-RU')} сум`;
}

function formatDate(value?: string | null) {
  if (!value) return '—';
  return dayjs(value).format('DD.MM.YYYY HH:mm');
}

export default function CouponApprovalPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { message, modal } = AntApp.useApp();
  const [revisionOpen, setRevisionOpen] = useState(false);
  const [revisionComment, setRevisionComment] = useState('');

  const couponId = Number(id);

  const { data: coupon, isLoading, error } = useQuery({
    queryKey: ['partner-coupon', couponId],
    enabled: Number.isFinite(couponId),
    queryFn: async (): Promise<CouponDetail> => {
      const res = await api.get(`/api/v1/partner/coupons/${couponId}`);
      return res.data.data;
    },
  });

  const approveMutation = useMutation({
    mutationFn: () => api.post(`/api/v1/partner/coupons/${couponId}/approve`),
    onSuccess: async () => {
      message.success('Купон одобрен и опубликован');
      await queryClient.invalidateQueries({ queryKey: ['partner-coupons'] });
      navigate('/coupons');
    },
    onError: (err: any) => message.error(err.response?.data?.message || 'Не удалось одобрить купон'),
  });

  const revisionMutation = useMutation({
    mutationFn: (comment: string) =>
      api.post(`/api/v1/partner/coupons/${couponId}/request-revision`, { comment }),
    onSuccess: async () => {
      message.success('Купон возвращён TopDim на доработку');
      await queryClient.invalidateQueries({ queryKey: ['partner-coupons'] });
      navigate('/coupons');
    },
    onError: (err: any) => message.error(err.response?.data?.message || 'Не удалось отправить правки'),
  });

  const handleApprove = () => {
    if (!coupon) return;
    modal.confirm({
      title: 'Одобрить и опубликовать купон?',
      icon: <CheckCircleOutlined style={{ color: '#52c41a' }} />,
      content: `Купон "${coupon.title}" станет доступен клиентам.`,
      okText: 'Одобрить и опубликовать',
      cancelText: 'Отмена',
      onOk: () => approveMutation.mutate(),
    });
  };

  const handleRevisionSubmit = () => {
    const trimmed = revisionComment.trim();
    if (!trimmed) {
      message.warning('Опишите, что нужно исправить');
      return;
    }
    revisionMutation.mutate(trimmed);
  };

  if (isLoading) return <Spin size="large" style={{ display: 'block', margin: '100px auto' }} />;
  if (error || !coupon) return <Result status="error" title="Купон не найден" />;

  const isWaiting = coupon.status === 'WAITING_FOR_MERCHANT';

  return (
    <div style={{ maxWidth: 1040, margin: '0 auto' }}>
      <Space style={{ marginBottom: 16 }}>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/coupons')}>
          Назад к купонам
        </Button>
        <Tag color={isWaiting ? 'orange' : 'default'}>{coupon.status}</Tag>
      </Space>

      <Row gutter={[24, 24]}>
        <Col xs={24} lg={10}>
          <Card style={{ borderRadius: 16 }}>
            {coupon.coverImageUrl ? (
              <Image
                src={coupon.coverImageUrl}
                alt={coupon.title}
                style={{ width: '100%', maxHeight: 360, objectFit: 'cover', borderRadius: 12 }}
              />
            ) : (
              <Alert type="warning" showIcon message="У купона нет обложки" />
            )}
            {coupon.images && coupon.images.length > 0 && (
              <>
                <Divider />
                <Image.PreviewGroup>
                  <Space wrap>
                    {coupon.images.map((url, index) => (
                      <Image key={url + index} src={url} width={86} height={64} style={{ objectFit: 'cover', borderRadius: 8 }} />
                    ))}
                  </Space>
                </Image.PreviewGroup>
              </>
            )}
          </Card>
        </Col>

        <Col xs={24} lg={14}>
          <Card style={{ borderRadius: 16 }}>
            <Title level={3}>{coupon.title}</Title>
            <Space align="baseline" wrap>
              <Text strong style={{ fontSize: 24, color: '#1677ff' }}>
                {formatPrice(coupon.fromPrice)}
              </Text>
              {coupon.oldPrice ? <Text delete type="secondary">{formatPrice(coupon.oldPrice)}</Text> : null}
              {coupon.discountPercent ? <Tag color="red">-{coupon.discountPercent}%</Tag> : null}
            </Space>

            <Divider />
            <Paragraph style={{ whiteSpace: 'pre-wrap' }}>
              {coupon.offerDescription || 'Описание не указано'}
            </Paragraph>

            <Descriptions bordered column={1} size="small">
              <Descriptions.Item label="Купить до">{formatDate(coupon.buyUntil)}</Descriptions.Item>
              <Descriptions.Item label="Использовать до">{formatDate(coupon.useUntil)}</Descriptions.Item>
            </Descriptions>

            {coupon.options && coupon.options.length > 0 && (
              <>
                <Divider orientation="left">Варианты</Divider>
                <Space direction="vertical" style={{ width: '100%' }}>
                  {coupon.options.map((option) => (
                    <Card key={option.id} size="small">
                      <Space direction="vertical" size={2}>
                        <Text strong>{option.title}</Text>
                        <Text>
                          {formatPrice(option.couponPrice)}
                          {' '}
                          <Text delete type="secondary">{formatPrice(option.regularPrice)}</Text>
                        </Text>
                        <Text type="secondary">
                          Лимит: {option.quantityLimit || 'без лимита'}, продано: {option.quantitySold}
                        </Text>
                      </Space>
                    </Card>
                  ))}
                </Space>
              </>
            )}

            <Divider />
            {!isWaiting ? (
              <Alert
                type="info"
                showIcon
                message="Этот купон сейчас не ожидает вашего согласования"
                description="Действия доступны только для статуса WAITING_FOR_MERCHANT."
              />
            ) : (
              <Space wrap>
                <Button
                  type="primary"
                  size="large"
                  icon={<CheckCircleOutlined />}
                  loading={approveMutation.isPending}
                  onClick={handleApprove}
                >
                  Одобрить и опубликовать
                </Button>
                <Button
                  danger
                  size="large"
                  icon={<CloseCircleOutlined />}
                  onClick={() => setRevisionOpen(true)}
                >
                  Запросить правки
                </Button>
              </Space>
            )}
          </Card>
        </Col>
      </Row>

      <Modal
        title="Что нужно исправить?"
        open={revisionOpen}
        onCancel={() => setRevisionOpen(false)}
        onOk={handleRevisionSubmit}
        okText="Отправить правки"
        cancelText="Отмена"
        confirmLoading={revisionMutation.isPending}
        okButtonProps={{ danger: true }}
      >
        <Alert
          type="info"
          showIcon
          message="Комментарий увидит команда TopDim"
          description="Напишите конкретно: цена, текст, сроки, фото или условия акции."
          style={{ marginBottom: 16 }}
        />
        <TextArea
          rows={5}
          value={revisionComment}
          onChange={(event) => setRevisionComment(event.target.value)}
          maxLength={2000}
          showCount
          placeholder="Например: нужно изменить цену на 89 000 сум и добавить условие только по будням."
        />
      </Modal>
    </div>
  );
}
```

- [ ] **Step 2: Add route**

Modify `frontend/partner/src/App.tsx`:

```tsx
import CouponApprovalPage from './pages/CouponApprovalPage';
```

Add route inside the private `/` route:

```tsx
<Route path="coupons/:id/review" element={<OwnerOnly><CouponApprovalPage /></OwnerOnly>} />
```

- [ ] **Step 3: Add list action for waiting coupons**

Modify `frontend/partner/src/pages/CouponsPage.tsx` actions column:

```tsx
render: (_: unknown, record: CouponItem) => {
  if (record.status === 'WAITING_FOR_MERCHANT') {
    return (
      <Button
        type="link"
        icon={<EyeOutlined />}
        size="small"
        onClick={() => navigate(`/coupons/${record.id}/review`)}
      >
        Согласовать
      </Button>
    );
  }

  if (EDITABLE_STATUSES.has(record.status)) {
    return (
      <Button type="link" icon={<EditOutlined />} size="small">
        Изменить
      </Button>
    );
  }

  return null;
}
```

- [ ] **Step 4: Add visual hint in status column**

Inside `WAITING_FOR_MERCHANT` status rendering, show a short helper:

```tsx
{record.status === 'WAITING_FOR_MERCHANT' && (
  <Text type="secondary" style={{ fontSize: 11 }}>
    Откройте preview и подтвердите запуск
  </Text>
)}
```

- [ ] **Step 5: Build partner frontend**

Run:

```bash
cd frontend/partner
npm run build
```

Expected result: TypeScript and Vite build complete successfully.

---

### Task 5: Mark Admin Merchant Review As Support Fallback

**Files:**
- Modify: `frontend/admin-app/src/features/coupons/MerchantReviewPage.tsx`

- [ ] **Step 1: Update screen description**

Replace the subtitle text:

```tsx
<Text type="secondary">
  Support-only экран: используйте только если партнёр не может подтвердить купон в своём кабинете.
</Text>
```

- [ ] **Step 2: Update info alert description**

Replace the alert description:

```tsx
description="Основной MVP-флоу: партнёр подтверждает купон в partner cabinet. Этот экран нужен только как ручной support fallback."
```

- [ ] **Step 3: Build admin frontend**

Run:

```bash
cd frontend/admin-app
npm run build
```

Expected result: TypeScript and Vite build complete successfully.

---

### Task 6: End-To-End Manual QA

**Files:**
- No code files unless a bug is discovered.

- [ ] **Step 1: Run backend tests**

Run:

```bash
./gradlew :services:coupon-service:test
```

Expected result: PASS.

- [ ] **Step 2: Verify happy path manually**

Manual scenario:

1. Login as partner owner.
2. Open partner cabinet `/coupons`.
3. Create coupon request.
4. Login as admin/moderator.
5. Open admin coupon requests.
6. Take the request to work.
7. Fill/finalize coupon details if needed.
8. Send coupon to approval.
9. Login as partner owner.
10. Open `/coupons`.
11. See status `На согласовании`.
12. Click `Согласовать`.
13. Review final preview.
14. Click `Одобрить и опубликовать`.
15. Verify coupon returns to list as `Опубликована`.
16. Verify public web app can open the active coupon.

- [ ] **Step 3: Verify revision path manually**

Manual scenario:

1. Put another coupon into `WAITING_FOR_MERCHANT`.
2. Login as partner owner.
3. Open review page.
4. Click `Запросить правки`.
5. Submit a clear comment.
6. Verify coupon status becomes `REVISION_REQUESTED`.
7. Login as admin/moderator.
8. Verify the revision comment is visible in admin coupon requests/kanban.
9. Moderator edits coupon and sends to approval again.
10. Partner approves on second review.

- [ ] **Step 4: Verify forbidden cases manually**

Manual checks:

1. Cashier opens `/coupons`: should redirect or fail gracefully, not show approval actions.
2. Partner owner tries to open another merchant coupon by ID: backend must reject.
3. Partner opens an `ACTIVE` coupon review URL: page shows read-only warning and no approve/revision buttons.
4. Partner submits blank revision comment: frontend warns and backend validation rejects if forced.

- [ ] **Step 5: Record result**

Update the task notes or final report with:

- Backend test command and result.
- Partner frontend build command and result.
- Admin frontend build command and result.
- Manual happy-path result.
- Manual revision-path result.
- Any bugs found and fixed.

## Completion Criteria

- Partner owner can approve their own `WAITING_FOR_MERCHANT` coupon from partner cabinet.
- Partner owner can request revision with required comment.
- Partner cannot approve/revise another merchant coupon.
- Partner cannot approve/revise coupons in invalid statuses.
- Cashier has no approval access.
- Admin temporary merchant review page is clearly labeled support-only.
- `./gradlew :services:coupon-service:test` passes.
- `npm run build` passes in `frontend/partner`.
- `npm run build` passes in `frontend/admin-app`.
