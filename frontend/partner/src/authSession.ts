export type PartnerRole = 'OWNER' | 'MANAGER' | 'CASHIER';

export interface PartnerContext {
  role: PartnerRole;
  merchantId: number;
  merchantLocationId?: number;
  staffId?: number;
  staffName?: string;
  canViewDashboard: boolean;
  canRedeem: boolean;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isPositiveInteger(value: unknown): value is number {
  return typeof value === 'number' && Number.isInteger(value) && value > 0;
}

export function parsePartnerContext(value: unknown): PartnerContext | null {
  if (!isRecord(value)) return null;

  const role = value.role;
  if (role !== 'OWNER' && role !== 'MANAGER' && role !== 'CASHIER') return null;
  if (!isPositiveInteger(value.merchantId)) return null;
  if (typeof value.canViewDashboard !== 'boolean' || typeof value.canRedeem !== 'boolean') return null;
  if (role === 'CASHIER' && !isPositiveInteger(value.merchantLocationId)) return null;
  if (value.merchantLocationId != null && !isPositiveInteger(value.merchantLocationId)) return null;
  if (value.staffId != null && !isPositiveInteger(value.staffId)) return null;
  if (value.staffName != null && typeof value.staffName !== 'string') return null;

  return {
    role,
    merchantId: value.merchantId,
    merchantLocationId: isPositiveInteger(value.merchantLocationId) ? value.merchantLocationId : undefined,
    staffId: isPositiveInteger(value.staffId) ? value.staffId : undefined,
    staffName: typeof value.staffName === 'string' ? value.staffName : undefined,
    canViewDashboard: value.canViewDashboard,
    canRedeem: value.canRedeem,
  };
}

export function readPartnerContext(): PartnerContext | null {
  try {
    const raw = localStorage.getItem('partnerContext');
    return raw ? parsePartnerContext(JSON.parse(raw)) : null;
  } catch {
    return null;
  }
}

export function clearPartnerSession(): void {
  localStorage.removeItem('token');
  localStorage.removeItem('user');
  localStorage.removeItem('partnerContext');
}
