import { Badge, Space } from 'antd';
import { useQuery } from '@tanstack/react-query';
import {
  fetchPendingMerchantProfileChangeCount,
  MERCHANT_PROFILE_CHANGES_QUERY_KEY,
} from './api';

export function MerchantProfileChangesMenuLabel() {
  const { data = 0 } = useQuery({
    queryKey: [...MERCHANT_PROFILE_CHANGES_QUERY_KEY, 'pending-count'],
    queryFn: fetchPendingMerchantProfileChangeCount,
  });

  return (
    <Space size={6}>
      <span>Изменения компаний</span>
      <Badge count={data} showZero size="small" />
    </Space>
  );
}
