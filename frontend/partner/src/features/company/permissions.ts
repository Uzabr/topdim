import type { PartnerContext } from '../../authSession';

export function canManageCompany(
  context: Pick<PartnerContext, 'role'> | null,
): boolean {
  return context?.role === 'OWNER' || context?.role === 'MANAGER';
}
