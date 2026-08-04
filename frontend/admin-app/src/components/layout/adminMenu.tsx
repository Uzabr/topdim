import type { ReactNode } from 'react';
import {
  AppstoreOutlined,
  AuditOutlined,
  CommentOutlined,
  DashboardOutlined,
  FileTextOutlined,
  SafetyCertificateOutlined,
  SettingOutlined,
  ShopOutlined,
  ShoppingCartOutlined,
  StarOutlined,
  TagOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons';
import type { UserRole } from '../../types';

export interface MenuItem {
  key: string;
  icon: ReactNode;
  label: string;
  roles: UserRole[];
  children?: MenuItem[];
}

export const allMenuItems: MenuItem[] = [
  {
    key: '/dashboard',
    icon: <DashboardOutlined />,
    label: 'Дашборд',
    roles: ['MODERATOR', 'ADMIN', 'SUPER_ADMIN'],
  },
  {
    key: '/coupons',
    icon: <TagOutlined />,
    label: 'Купоны',
    roles: ['MODERATOR', 'ADMIN', 'SUPER_ADMIN'],
  },
  {
    key: '/support',
    icon: <SafetyCertificateOutlined />,
    label: 'Поддержка',
    roles: ['MODERATOR', 'ADMIN', 'SUPER_ADMIN'],
    children: [
      { key: '/support/refunds', icon: <ShoppingCartOutlined />, label: 'Возвраты', roles: ['ADMIN', 'SUPER_ADMIN'] },
      { key: '/support/complaints', icon: <CommentOutlined />, label: 'Жалобы', roles: ['MODERATOR', 'ADMIN', 'SUPER_ADMIN'] },
      { key: '/support/reviews', icon: <StarOutlined />, label: 'Отзывы', roles: ['MODERATOR', 'ADMIN', 'SUPER_ADMIN'] },
    ],
  },
  {
    key: '/catalog',
    icon: <ShopOutlined />,
    label: 'Справочники',
    roles: ['ADMIN', 'SUPER_ADMIN'],
    children: [
      { key: '/catalog/merchants', icon: <ShopOutlined />, label: 'Мерчанты', roles: ['ADMIN', 'SUPER_ADMIN'] },
      { key: '/catalog/categories', icon: <AppstoreOutlined />, label: 'Категории', roles: ['ADMIN', 'SUPER_ADMIN'] },
    ],
  },
  {
    key: '/orders',
    icon: <ShoppingCartOutlined />,
    label: 'Заказы',
    roles: ['ADMIN', 'SUPER_ADMIN'],
    children: [
      { key: '/orders/list', icon: <FileTextOutlined />, label: 'Все заказы', roles: ['ADMIN', 'SUPER_ADMIN'] },
      { key: '/orders/coupon-lookup', icon: <TagOutlined />, label: 'Поиск купона', roles: ['ADMIN', 'SUPER_ADMIN'] },
    ],
  },
  {
    key: '/users',
    icon: <TeamOutlined />,
    label: 'Пользователи',
    roles: ['ADMIN', 'SUPER_ADMIN'],
    children: [
      { key: '/users/list', icon: <UserOutlined />, label: 'Все пользователи', roles: ['ADMIN', 'SUPER_ADMIN'] },
      { key: '/users/partner-applications', icon: <FileTextOutlined />, label: 'Заявки партнёров', roles: ['ADMIN', 'SUPER_ADMIN'] },
    ],
  },
  {
    key: '/system',
    icon: <SettingOutlined />,
    label: 'Система',
    roles: ['SUPER_ADMIN'],
    children: [
      { key: '/system/staff', icon: <TeamOutlined />, label: 'Сотрудники', roles: ['SUPER_ADMIN'] },
      { key: '/system/audit', icon: <AuditOutlined />, label: 'Аудит сотрудников', roles: ['SUPER_ADMIN'] },
    ],
  },
];

export function filterMenuByRole(items: MenuItem[], role: UserRole): MenuItem[] {
  return items
    .filter((item) => item.roles.includes(role))
    .map((item) => ({
      ...item,
      children: item.children ? filterMenuByRole(item.children, role) : undefined,
    }));
}
