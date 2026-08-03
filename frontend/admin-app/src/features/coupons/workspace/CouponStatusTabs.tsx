import { Tabs } from 'antd';
import type { CouponTab } from './types';

interface CouponStatusTabsProps {
  activeTab: CouponTab;
  onChange: (tab: CouponTab) => void;
}

const TAB_ITEMS: Array<{ key: CouponTab; label: string }> = [
  { key: 'new', label: 'Новые' },
  { key: 'in-progress', label: 'В работе' },
  { key: 'revision', label: 'Требуют изменений' },
  { key: 'waiting-partner', label: 'Ожидают партнёра' },
  { key: 'published', label: 'Опубликованные' },
  { key: 'archived', label: 'Архив' },
];

export function CouponStatusTabs({ activeTab, onChange }: CouponStatusTabsProps) {
  return (
    <Tabs
      activeKey={activeTab}
      items={TAB_ITEMS}
      onChange={(key) => onChange(key as CouponTab)}
    />
  );
}
