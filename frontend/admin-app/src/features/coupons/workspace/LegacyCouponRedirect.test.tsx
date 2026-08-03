import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import {
  LegacyCouponRedirect,
  type LegacyCouponRedirectTarget,
} from './LegacyCouponRedirect';

interface RedirectCase {
  from: string;
  route: string;
  target: LegacyCouponRedirectTarget;
  expected: string;
}

const redirectCases: RedirectCase[] = [
  {
    from: '/moderation/coupons',
    route: '/moderation/coupons',
    target: 'workspace',
    expected: '/coupons',
  },
  {
    from: '/moderation/coupons/kanban',
    route: '/moderation/coupons/kanban',
    target: 'kanban',
    expected: '/coupons?view=kanban',
  },
  {
    from: '/moderation/requests',
    route: '/moderation/requests',
    target: 'new-tab',
    expected: '/coupons?tab=new',
  },
  {
    from: '/moderation/coupons/create',
    route: '/moderation/coupons/create',
    target: 'create',
    expected: '/coupons/new',
  },
  {
    from: '/moderation/coupons/edit/42',
    route: '/moderation/coupons/edit/:id',
    target: 'edit',
    expected: '/coupons/42/edit',
  },
  {
    from: '/moderation/coupons/review',
    route: '/moderation/coupons/review',
    target: 'waiting-partner',
    expected: '/coupons?tab=waiting-partner',
  },
];

function LocationProbe() {
  const location = useLocation();
  return <output data-testid="location">{location.pathname}{location.search}</output>;
}

function renderRedirect(testCase: RedirectCase) {
  render(
    <MemoryRouter initialEntries={[testCase.from]}>
      <Routes>
        <Route
          path={testCase.route}
          element={<LegacyCouponRedirect target={testCase.target} />}
        />
        <Route path="*" element={<LocationProbe />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('legacy coupon redirects', () => {
  it.each(redirectCases)('$from → $expected', (testCase) => {
    renderRedirect(testCase);

    expect(screen.getByTestId('location').textContent).toBe(testCase.expected);
  });

  it.each(['0', '-1', 'not-a-number', '9007199254740992'])(
    'rejects unsafe edit id %s',
    (id) => {
      renderRedirect({
        from: `/moderation/coupons/edit/${id}`,
        route: '/moderation/coupons/edit/:id',
        target: 'edit',
        expected: '/coupons',
      });

      expect(screen.getByTestId('location').textContent).toBe('/coupons');
    },
  );

  it('does not carry arbitrary legacy query parameters into the canonical URL', () => {
    renderRedirect({
      from: '/moderation/coupons?redirect=https://evil.example&tab=bad',
      route: '/moderation/coupons',
      target: 'workspace',
      expected: '/coupons',
    });

    expect(screen.getByTestId('location').textContent).toBe('/coupons');
  });
});
