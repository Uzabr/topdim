import { render, screen } from '@testing-library/react';
import { MemoryRouter, Outlet, Route, Routes, useLocation } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import {
  LegacyCouponRedirect,
  type LegacyCouponRedirectTarget,
} from './LegacyCouponRedirect';
import App from '../../../App';

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

const legacyStandaloneHeadings = [
  'Все купоны',
  'Канбан купонов',
  'Заявки на акции от партнёров',
  'Ожидают подтверждения мерчанта',
];

vi.mock('../../../routes/ProtectedRoute', () => ({
  ProtectedRoute: () => <Outlet />,
}));

vi.mock('../../../components/layout/AdminLayout', () => ({
  AdminLayout: () => <Outlet />,
}));

vi.mock('./CouponWorkspacePage', () => ({
  CouponWorkspacePage: () => <CanonicalRouteMarker destination="workspace" />,
}));

vi.mock('../CouponFormPage', () => ({
  CouponFormPage: () => <CanonicalRouteMarker destination="form" />,
}));

function CanonicalRouteMarker({ destination }: { destination: 'workspace' | 'form' }) {
  const location = useLocation();

  return (
    <section>
      <h1>{destination === 'workspace' ? 'Купоны' : 'Форма купона'}</h1>
      <output data-testid="canonical-route">
        {destination}:{location.pathname}{location.search}
      </output>
    </section>
  );
}

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

function renderAppAt(path: string) {
  window.history.pushState({}, '', path);
  render(<App />);
}

describe('legacy coupon redirects', () => {
  it.each(redirectCases)('App routes $from to the canonical $expected destination', async (testCase) => {
    renderAppAt(testCase.from);

    expect((await screen.findByTestId('canonical-route')).textContent).toBe(
      `${testCase.expected.startsWith('/coupons/new') || testCase.expected.includes('/edit') ? 'form' : 'workspace'}:${testCase.expected}`,
    );
    for (const heading of legacyStandaloneHeadings) {
      expect(screen.queryByRole('heading', { name: heading })).toBeNull();
    }
  });

  it.each(['0', '-1', 'not-a-number', '9007199254740992'])(
    'App routes unsafe edit id %s to the workspace',
    async (id) => {
      renderAppAt(`/moderation/coupons/edit/${id}`);

      expect((await screen.findByTestId('canonical-route')).textContent).toBe('workspace:/coupons');
    },
  );

  it('App does not carry arbitrary legacy query parameters into the canonical URL', async () => {
    renderAppAt('/moderation/coupons?redirect=https://evil.example&tab=bad');

    expect((await screen.findByTestId('canonical-route')).textContent).toBe('workspace:/coupons');
  });

  it.each(redirectCases)('redirect component maps $from → $expected', (testCase) => {
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
