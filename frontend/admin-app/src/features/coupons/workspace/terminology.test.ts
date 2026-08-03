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
  let source = withoutComments(readFileSync(resolve(FRONTEND_ROOT, path), 'utf8'));

  // This is the only merchant-authored example in the listed sources. Keep the
  // exact exclusion narrow so future UI literals remain contract-protected.
  if (path === 'admin-app/src/features/coupons/CouponFormPage.tsx') {
    const merchantExample = /placeholder=\{`[\s\S]*?`\}/g;
    expect(source.match(merchantExample)).toHaveLength(1);
    source = source.replace(merchantExample, 'placeholder={merchant-authored example}');
  }

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
  return values.map((value) => value.trim()).filter(Boolean);
}

function violations(pattern: RegExp, paths: readonly string[]): string[] {
  return paths.flatMap((path) => literals(userVisibleSource(path))
    .filter((literal) => pattern.test(literal))
    .map((literal) => `${path}: ${literal}`));
}

describe('active coupon terminology', () => {
  it('keeps admin, partner, and backend user-visible literals free of retired coupon terms', () => {
    expect(violations(/акци(?:я|и|ю|ей|ям|ями|ях|е)|оффер|товар/iu, [...ADMIN_PARTNER_OR_BACKEND]))
      .toEqual([]);
  });

  it('uses sizbiz in public brand literals while preserving technical identifiers', () => {
    expect(violations(/TopDim/u, ACTIVE_SOURCE_FILES)).toEqual([]);
  });
});
