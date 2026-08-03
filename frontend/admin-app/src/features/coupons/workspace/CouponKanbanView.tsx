import { Alert, Button, Card, Empty, Flex, Spin, Tag, Typography } from 'antd';
import { useInfiniteQuery } from '@tanstack/react-query';
import {
  ADMIN_COUPONS_WORKSPACE_QUERY_KEY,
  fetchAdminCoupons,
} from './api';
import type {
  AdminCouponRow,
  CouponStatus,
  CouponTab,
  CouponWorkspaceState,
} from './types';

const { Text, Title } = Typography;

interface CouponKanbanViewProps {
  state: CouponWorkspaceState;
}

interface KanbanColumnConfig {
  status: CouponStatus;
  tab: CouponTab;
  title: string;
  color: string;
}

const COLUMNS: KanbanColumnConfig[] = [
  { status: 'LEAD', tab: 'new', title: 'Новые', color: 'blue' },
  { status: 'DRAFT', tab: 'in-progress', title: 'В работе', color: 'default' },
  {
    status: 'REVISION_REQUESTED',
    tab: 'revision',
    title: 'Требуют изменений',
    color: 'orange',
  },
  {
    status: 'WAITING_FOR_MERCHANT',
    tab: 'waiting-partner',
    title: 'Ожидают партнёра',
    color: 'purple',
  },
];

function CouponKanbanColumn({
  config,
  state,
}: {
  config: KanbanColumnConfig;
  state: CouponWorkspaceState;
}) {
  const columnState: CouponWorkspaceState = {
    ...state,
    tab: config.tab,
    page: 0,
    pageSize: 20,
  };
  const query = useInfiniteQuery({
    queryKey: [
      ...ADMIN_COUPONS_WORKSPACE_QUERY_KEY,
      'kanban',
      config.status,
      state.search,
      state.merchantId,
      state.assignedModeratorId,
    ],
    queryFn: ({ pageParam }) => fetchAdminCoupons(columnState, pageParam),
    initialPageParam: 0,
    getNextPageParam: (lastPage) => lastPage.last
      ? undefined
      : lastPage.pageable.pageNumber + 1,
  });

  const coupons = query.data?.pages.flatMap((page) => page.content) ?? [];
  const total = query.data?.pages[0]?.totalElements;

  return (
    <Card
      size="small"
      style={{ width: 320, minWidth: 320, alignSelf: 'flex-start' }}
      title={(
        <Flex justify="space-between" align="center">
          <span>{config.title}</span>
          {total !== undefined && <Tag color={config.color}>{total}</Tag>}
        </Flex>
      )}
    >
      <Flex vertical gap={12}>
        {query.isPending && <Spin aria-label={`Загрузка: ${config.title}`} />}

        {query.isError && (
          <Alert
            type="error"
            showIcon
            title={`Не удалось загрузить колонку «${config.title}»`}
            action={(
              <Button
                size="small"
                aria-label={`Повторить: ${config.title}`}
                onClick={() => { void query.refetch(); }}
              >
                Повторить
              </Button>
            )}
          />
        )}

        {coupons.map((coupon: AdminCouponRow) => (
          <Card key={coupon.id} size="small">
            <Flex vertical gap={6}>
              <Title level={5} style={{ margin: 0 }}>{coupon.title}</Title>
              <Text>{coupon.merchant?.name ?? 'Партнёр не указан'}</Text>
              <Text type="secondary">
                {coupon.assignedModeratorName ?? 'Ответственный не назначен'}
              </Text>
              <Tag color={config.color}>{coupon.status}</Tag>
            </Flex>
          </Card>
        ))}

        {!query.isPending && !query.isError && coupons.length === 0 && (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Купонов нет" />
        )}

        {query.hasNextPage && (
          <Button
            block
            loading={query.isFetchingNextPage}
            aria-label={`Показать ещё: ${config.title}`}
            onClick={() => { void query.fetchNextPage(); }}
          >
            Показать ещё
          </Button>
        )}
      </Flex>
    </Card>
  );
}

export function CouponKanbanView({ state }: CouponKanbanViewProps) {
  return (
    <Flex gap={16} style={{ overflowX: 'auto', paddingBottom: 8 }}>
      {COLUMNS.map((config) => (
        <CouponKanbanColumn key={config.status} config={config} state={state} />
      ))}
    </Flex>
  );
}
