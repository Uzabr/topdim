import { useAuthStore } from '../store/authStore';
import type { UserRole } from '../types';

interface CanProps {
  roles: UserRole[];
  children: React.ReactNode;
  fallback?: React.ReactNode;
}

/**
 * RBAC-компонент: отображает children только если роль текущего юзера
 * входит в список разрешённых ролей.
 *
 * Пример:
 * <Can roles={['SUPER_ADMIN']}>
 *   <Button danger>Удалить пользователя</Button>
 * </Can>
 */
export const Can = ({ roles, children, fallback = null }: CanProps) => {
  const user = useAuthStore((s) => s.user);

  if (!user || !roles.includes(user.role)) {
    return <>{fallback}</>;
  }

  return <>{children}</>;
};
