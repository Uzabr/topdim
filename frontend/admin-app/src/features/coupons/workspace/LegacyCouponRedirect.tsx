import { Navigate, useParams } from 'react-router-dom';

export type LegacyCouponRedirectTarget =
  | 'workspace'
  | 'kanban'
  | 'new-tab'
  | 'create'
  | 'edit'
  | 'waiting-partner';

interface LegacyCouponRedirectProps {
  target: LegacyCouponRedirectTarget;
}

const STATIC_TARGETS: Omit<Record<LegacyCouponRedirectTarget, string>, 'edit'> = {
  workspace: '/coupons',
  kanban: '/coupons?view=kanban',
  'new-tab': '/coupons?tab=new',
  create: '/coupons/new',
  'waiting-partner': '/coupons?tab=waiting-partner',
};

function canonicalEditPath(id: string | undefined): string {
  if (id === undefined || !/^\d+$/.test(id)) {
    return '/coupons';
  }

  const parsedId = Number(id);
  if (!Number.isSafeInteger(parsedId) || parsedId <= 0) {
    return '/coupons';
  }

  return `/coupons/${parsedId}/edit`;
}

export function LegacyCouponRedirect({ target }: LegacyCouponRedirectProps) {
  const { id } = useParams<{ id: string }>();
  const destination = target === 'edit'
    ? canonicalEditPath(id)
    : STATIC_TARGETS[target];

  return <Navigate to={destination} replace />;
}
