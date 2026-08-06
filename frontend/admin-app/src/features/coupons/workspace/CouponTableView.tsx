import { Alert, Button, Empty, Space, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import type { PageResponse } from '../../../types';
import type {
  AdminCouponRow,
  CouponPageSize,
  CouponStatus,
  CouponTab,
} from './types';
import { CouponActionMenu } from './CouponActionMenu';

const { Text } = Typography;

interface CouponTableViewProps {
  activeTab: CouponTab;
  pageSize: CouponPageSize;
  data: PageResponse<AdminCouponRow> | undefined;
  isLoading: boolean;
  error: unknown | null;
  isStaleData: boolean;
  lastSuccessfulAt: number | null;
  onRetry: () => void;
  onPageChange: (page: number, pageSize: CouponPageSize) => void;
}

interface HttpErrorLike {
  message?: string;
  response?: {
    status?: number;
    data?: {
      message?: string;
    };
  };
}

const STATUS_LABELS: Record<CouponStatus, string> = {
  LEAD: 'Новый',
  DRAFT: 'В работе',
  REVISION_REQUESTED: 'Требует изменений',
  WAITING_FOR_MERCHANT: 'Ожидает партнёра',
  ACTIVE: 'Опубликован',
  PAUSED: 'Приостановлен',
  SOLD_OUT: 'Распродан',
  ARCHIVED: 'В архиве',
};

const STATUS_COLORS: Record<CouponStatus, string> = {
  LEAD: 'blue',
  DRAFT: 'default',
  REVISION_REQUESTED: 'orange',
  WAITING_FOR_MERCHANT: 'purple',
  ACTIVE: 'green',
  PAUSED: 'gold',
  SOLD_OUT: 'cyan',
  ARCHIVED: 'default',
};

const EMPTY_TEXT: Record<CouponTab, string> = {
  new: 'Новых купонов нет',
  'in-progress': 'Купонов в работе нет',
  revision: 'Купонов, требующих изменений, нет',
  'waiting-partner': 'Нет купонов, ожидающих партнёра',
  published: 'Опубликованных купонов нет',
  archived: 'Архив купонов пуст',
};

function errorCopy(error: unknown): { title: string; description?: string } {
  const candidate = error as HttpErrorLike;
  const status = candidate?.response?.status;
  if (status === 403) {
    return { title: 'Недостаточно прав для просмотра купонов' };
  }
  if (status === 404) {
    return { title: 'Купон удалён или ссылка устарела' };
  }

  return {
    title: 'Не удалось загрузить купоны',
    description: candidate?.response?.data?.message ?? candidate?.message,
  };
}

function formatMoney(value: number | null): string {
  return value === null ? '—' : `${value.toLocaleString('ru-RU')} сум`;
}

function formatDate(value: string | null): string {
  return value ? dayjs(value).format('DD.MM.YYYY') : '—';
}

const columns: ColumnsType<AdminCouponRow> = [
  { title: 'ID', dataIndex: 'id', width: 72 },
  { title: 'Купон', dataIndex: 'title', minWidth: 220 },
  {
    title: 'Партнёр',
    dataIndex: 'merchant',
    render: (merchant: AdminCouponRow['merchant']) => merchant?.name ?? '—',
  },
  {
    title: 'Цена',
    dataIndex: 'fromPrice',
    render: (value: number) => formatMoney(value),
  },
  {
    title: 'Статус',
    dataIndex: 'status',
    render: (status: CouponStatus) => (
      <Tag color={STATUS_COLORS[status]}>{STATUS_LABELS[status]} · {status}</Tag>
    ),
  },
  {
    title: 'Ответственный',
    dataIndex: 'assignedModeratorName',
    render: (name: string | null) => name ?? 'Не назначен',
  },
  {
    title: 'Купить до',
    dataIndex: 'buyUntil',
    render: formatDate,
  },
  {
    title: 'Использовать до',
    dataIndex: 'useUntil',
    render: formatDate,
  },
  {
    title: 'Действия',
    key: 'actions',
    fixed: 'right',
    width: 150,
    render: (_value, record) => <CouponActionMenu coupon={record} />,
  },
];

export function CouponTableView({
  activeTab,
  pageSize: requestedPageSize,
  data,
  isLoading,
  error,
  isStaleData,
  lastSuccessfulAt,
  onRetry,
  onPageChange,
}: CouponTableViewProps) {
  const pageSize = (data?.pageable.pageSize ?? requestedPageSize) as CouponPageSize;
  const showBlockingError = error !== null && data === undefined;

  if (showBlockingError) {
    const copy = errorCopy(error);
    return (
      <Alert
        type="error"
        showIcon
        title={copy.title}
        description={copy.description}
        action={<Button onClick={onRetry}>Повторить</Button>}
      />
    );
  }

  return (
    <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
      {isStaleData && (
        <Alert
          type="warning"
          showIcon
          title="Показаны последние сохранённые данные"
          description={lastSuccessfulAt === null
            ? 'Не удалось обновить данные.'
            : `Последнее успешное обновление: ${new Date(lastSuccessfulAt).toLocaleString('ru-RU')}`}
          action={<Button onClick={onRetry}>Повторить</Button>}
        />
      )}

      <Table<AdminCouponRow>
        columns={columns}
        dataSource={data?.content ?? []}
        loading={isLoading}
        rowKey="id"
        locale={{
          emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={EMPTY_TEXT[activeTab]} />,
        }}
        pagination={data && data.totalElements > 0 ? {
          current: data.pageable.pageNumber + 1,
          pageSize,
          total: data.totalElements,
          showSizeChanger: false,
          showTotal: (total) => `Всего: ${total}`,
          onChange: (page) => onPageChange(page - 1, pageSize),
        } : false}
        scroll={{ x: 1100 }}
      />

      <Space>
        <Text type="secondary">Строк на странице:</Text>
        <select
          aria-label="Размер страницы таблицы"
          value={pageSize}
          onChange={(event) => onPageChange(
            0,
            Number(event.target.value) as CouponPageSize,
          )}
        >
          <option value={20}>20</option>
          <option value={50}>50</option>
          <option value={100}>100</option>
        </select>
      </Space>
    </Space>
  );
}
