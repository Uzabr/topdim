/// <reference types="node" />

import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

const FRONTEND_ROOT = resolve(process.cwd(), '..');

const ACTIVE_SOURCE_FILES = [
  'admin-app/src/components/layout/AdminLayout.tsx',
  'admin-app/src/features/auth/LoginPage.tsx',
  'admin-app/src/features/coupons/CouponFormPage.tsx',
  'admin-app/src/features/coupons/workspace/CouponWorkspacePage.tsx',
  'admin-app/src/features/coupons/workspace/CouponWorkspaceToolbar.tsx',
  'admin-app/src/features/coupons/workspace/CouponStatusTabs.tsx',
  'admin-app/src/features/coupons/workspace/CouponTableView.tsx',
  'admin-app/src/features/coupons/workspace/CouponKanbanView.tsx',
  'admin-app/src/features/coupons/workspace/CouponActionMenu.tsx',
  'partner/src/layouts/PartnerLayout.tsx',
  'partner/src/pages/CouponRequestFormPage.tsx',
  'partner/src/pages/CouponsPage.tsx',
  'partner/src/pages/CouponApprovalPage.tsx',
  'partner/src/pages/LoginPage.tsx',
  'partner/src/pages/RedeemPage.tsx',
  'web-app/src/locales/ru.json',
  'web-app/src/locales/uz.json',
  'web-app/src/pages/legal/partners/PartnerLandingPage.tsx',
  '../services/coupon-service/src/main/java/uz/topdim/coupon/controller/PartnerCouponController.java',
  '../services/coupon-service/src/main/java/uz/topdim/coupon/dto/CreateCouponOfferRequest.java',
  '../services/coupon-service/src/main/java/uz/topdim/coupon/dto/CreatePartnerCouponRequest.java',
  '../services/coupon-service/src/main/java/uz/topdim/coupon/service/CouponOfferService.java',
  '../services/coupon-service/src/main/java/uz/topdim/coupon/service/PartnerCouponService.java',
] as const;

const ADMIN_PARTNER_OR_BACKEND = new Set(ACTIVE_SOURCE_FILES.filter((path) =>
  !path.startsWith('web-app/'),
));

const PARTNER_PROPOSAL_SOURCES = {
  layout: 'partner/src/layouts/PartnerLayout.tsx',
  request: 'partner/src/pages/CouponRequestFormPage.tsx',
  list: 'partner/src/pages/CouponsPage.tsx',
  approval: 'partner/src/pages/CouponApprovalPage.tsx',
  controller: '../services/coupon-service/src/main/java/uz/topdim/coupon/controller/PartnerCouponController.java',
} as const;

const TECHNICAL_LITERALS = new Set([
  'TOPDIM-QR:',
  'topdim-qr-reader',
  'offerDescription',
]);
const EMAIL_LITERAL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/u;
const APPROVED_COUPON_ADJECTIVE = /купонное\s+предложение/giu;
const STANDALONE_COUPON_NOUN = /(?<![\p{L}])купон(?:ами|ов|ам|ах|ы|а|у|ом|е)?(?![\p{L}])/iu;

function withoutComments(source: string): string {
  let result = '';
  let quote: "'" | '"' | '`' | null = null;

  for (let index = 0; index < source.length; index += 1) {
    const current = source[index];
    const next = source[index + 1];

    if (quote !== null) {
      result += current;
      if (current === '\\') {
        result += next ?? '';
        index += 1;
      } else if (current === quote) {
        quote = null;
      }
      continue;
    }

    if (current === "'" || current === '"' || current === '`') {
      quote = current;
      result += current;
      continue;
    }
    if (current === '/' && next === '/') {
      index = source.indexOf('\n', index);
      if (index === -1) break;
      result += '\n';
      continue;
    }
    if (current === '/' && next === '*') {
      const end = source.indexOf('*/', index + 2);
      index = end === -1 ? source.length : end + 1;
      continue;
    }
    result += current;
  }

  return result;
}

function userVisibleSource(path: string): string {
  const source = withoutComments(readFileSync(resolve(FRONTEND_ROOT, path), 'utf8'));

  // Logs are deliberately excluded by the Task 9 scope; API validation and
  // notification strings remain in the source checked below.
  return source.replace(/\blog\.(?:trace|debug|info|warn|error)\([\s\S]*?\);/g, '');
}

function literals(source: string): string[] {
  const values: string[] = [];
  const jsxText = />([^<>{}]+)</g;

  for (let index = 0; index < source.length; index += 1) {
    const quote = source[index];
    if (quote !== "'" && quote !== '"' && quote !== '`') continue;

    let value = '';
    for (index += 1; index < source.length; index += 1) {
      if (source[index] === '\\') {
        value += source[index] + (source[index + 1] ?? '');
        index += 1;
      } else if (source[index] === quote) {
        break;
      } else {
        value += source[index];
      }
    }
    values.push(value);
  }
  for (const match of source.matchAll(jsxText)) values.push(match[1]);
  return values
    .map((value) => value.trim())
    .filter(Boolean)
    .filter((value) => !isExplicitlyExcludedLiteral(value));
}

function isExplicitlyExcludedLiteral(value: string): boolean {
  return EMAIL_LITERAL.test(value)
    || TECHNICAL_LITERALS.has(value)
    || value.startsWith('/api/');
}

function violations(pattern: RegExp, paths: readonly string[]): string[] {
  return paths.flatMap((path) => literals(userVisibleSource(path))
    .filter((literal) => pattern.test(literal))
    .map((literal) => `${path}: ${literal}`));
}

function proposalVocabularyViolations(
  values: string[],
  scope: 'proposal-management' | 'redemption' = 'proposal-management',
): string[] {
  if (scope === 'redemption') return [];

  return values.filter((value) => STANDALONE_COUPON_NOUN.test(
    value.replace(APPROVED_COUPON_ADJECTIVE, ''),
  ));
}

describe('active coupon terminology', () => {
  it('keeps admin, partner, and backend user-visible literals free of retired coupon terms', () => {
    expect(violations(/акци(?:я|и|ю|ей|ям|ями|ях|е)|оффер|товар/iu, [...ADMIN_PARTNER_OR_BACKEND]))
      .toEqual([]);
  });

  it('uses sizbiz in public brand literals while preserving technical identifiers', () => {
    expect(violations(/TopDim/u, ACTIVE_SOURCE_FILES)).toEqual([]);
    expect(literals(userVisibleSource('admin-app/src/components/layout/AdminLayout.tsx')))
      .not.toContain('TD');
  });

  it('keeps active coupon-flow documentation free of retired offer wording', () => {
    const activeDocs = [
      '../docs/product/roles.md',
      '../docs/product/flows/coupon-flow.md',
    ];
    const docViolations = activeDocs.filter((path) =>
      /оффер/iu.test(readFileSync(resolve(FRONTEND_ROOT, path), 'utf8')));

    expect(docViolations).toEqual([]);
  });

  it('uses proposal wording throughout partner proposal management without restricting redemption', () => {
    const expectedLiterals: Array<[string, string]> = [
      [PARTNER_PROPOSAL_SOURCES.layout, 'Мои предложения'],
      [PARTNER_PROPOSAL_SOURCES.request, 'Цена по предложению (сум)'],
      [PARTNER_PROPOSAL_SOURCES.list, 'Ошибка загрузки предложений'],
      [PARTNER_PROPOSAL_SOURCES.approval, 'Предложение одобрено и опубликовано'],
      [PARTNER_PROPOSAL_SOURCES.approval, 'Не удалось одобрить предложение'],
      [PARTNER_PROPOSAL_SOURCES.approval, 'Предложение возвращено sizbiz на доработку'],
      [PARTNER_PROPOSAL_SOURCES.approval, 'Одобрить и опубликовать предложение?'],
      [PARTNER_PROPOSAL_SOURCES.approval, 'Предложение "${coupon.title}" станет доступно клиентам.'],
      [PARTNER_PROPOSAL_SOURCES.approval, 'Предложение не найдено'],
      [PARTNER_PROPOSAL_SOURCES.approval, 'Назад к предложениям'],
      [PARTNER_PROPOSAL_SOURCES.approval, 'У предложения нет обложки'],
      [PARTNER_PROPOSAL_SOURCES.approval, 'Без корректного адреса предложение может не пройти публикацию. Проверьте данные перед одобрением.'],
      [PARTNER_PROPOSAL_SOURCES.approval, 'Это предложение сейчас не ожидает вашего согласования'],
      [PARTNER_PROPOSAL_SOURCES.controller, 'Предложение обновлено'],
      [PARTNER_PROPOSAL_SOURCES.controller, 'Предложение одобрено и опубликовано'],
      [PARTNER_PROPOSAL_SOURCES.controller, 'Предложение возвращено на доработку'],
    ];

    for (const [path, expected] of expectedLiterals) {
      expect(literals(userVisibleSource(path)), `${path}: ${expected}`).toContain(expected);
    }
  });

  it('rejects a new standalone coupon noun in proposal-management copy', () => {
    const syntheticBadProposalLiteral = 'Купоны ожидают согласования';

    expect(proposalVocabularyViolations([syntheticBadProposalLiteral]))
      .toEqual([syntheticBadProposalLiteral]);
  });

  it('allows the approved coupon adjective and redemption copy independently', () => {
    expect(proposalVocabularyViolations(['Купонное предложение принято'])).toEqual([]);
    expect(proposalVocabularyViolations(['Купон погашен по QR!'], 'redemption')).toEqual([]);
  });

  it('keeps every proposal-management source free of standalone coupon nouns', () => {
    const sourceViolations = Object.values(PARTNER_PROPOSAL_SOURCES).flatMap((path) =>
      proposalVocabularyViolations(literals(userVisibleSource(path)))
        .map((literal) => `${path}: ${literal}`));

    expect(sourceViolations).toEqual([]);
  });

  it('mirrors the partner setup terminology in Russian and Uzbek', () => {
    const ru = JSON.parse(userVisibleSource('web-app/src/locales/ru.json'));
    const uz = JSON.parse(userVisibleSource('web-app/src/locales/uz.json'));

    expect(ru.partners.steps.setup.title).toBe('Настройка предложения');
    expect(uz.partners.steps.setup.title).toBe('Taklifni sozlash');
  });

  it('excludes only documented email and technical literals from copy checks', () => {
    expect(isExplicitlyExcludedLiteral('merchant@example.uz')).toBe(true);
    expect(isExplicitlyExcludedLiteral('TOPDIM-QR:')).toBe(true);
    expect(isExplicitlyExcludedLiteral('topdim-qr-reader')).toBe(true);
    expect(isExplicitlyExcludedLiteral('offerDescription')).toBe(true);
    expect(isExplicitlyExcludedLiteral('/api/v1/partner/coupons')).toBe(true);
    expect(isExplicitlyExcludedLiteral('Предложение одобрено и опубликовано')).toBe(false);
  });
});
