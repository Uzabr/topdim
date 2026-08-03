import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Button,
  Dropdown,
  Input,
  Modal,
  Space,
  message,
} from 'antd';
import type { MenuProps } from 'antd';
import { useNavigate } from 'react-router-dom';
import api from '../../../api/client';
import { useAuthStore } from '../../../store/authStore';
import { ADMIN_COUPONS_WORKSPACE_QUERY_KEY } from './api';
import { allowedCouponActions } from './permissions';
import type { AdminCouponRow, CouponAction, StaffUser } from './types';

const ACTION_LABELS: Record<CouponAction, string> = {
  view: 'Просмотреть',
  'take-to-work': 'Взять в работу',
  edit: 'Редактировать',
  'send-to-approval': 'Отправить на согласование',
  'support-review': 'Служебное решение',
  pause: 'Приостановить',
  restore: 'Восстановить',
  archive: 'Архивировать',
};

interface HttpErrorLike {
  message?: string;
  response?: {
    status?: number;
    data?: {
      message?: string;
    };
  };
}

function staffUserOrNull(user: ReturnType<typeof useAuthStore.getState>['user']): StaffUser | null {
  if (!user || (
    user.role !== 'MODERATOR'
    && user.role !== 'ADMIN'
    && user.role !== 'SUPER_ADMIN'
  )) {
    return null;
  }

  return { id: user.id, role: user.role };
}

function actionErrorCopy(error: unknown): string {
  const candidate = error as HttpErrorLike;
  if (candidate?.response?.status === 403) {
    return 'Недостаточно прав для выполнения действия';
  }
  if (candidate?.response?.status === 404) {
    return 'Купон удалён или ссылка устарела';
  }

  return candidate?.response?.data?.message
    ?? candidate?.message
    ?? 'Не удалось выполнить действие';
}

interface CouponActionMenuProps {
  coupon: AdminCouponRow;
}

type MutationAction = Exclude<CouponAction, 'view' | 'edit'>;

export function CouponActionMenu({ coupon }: CouponActionMenuProps) {
  const authenticatedUser = useAuthStore((state) => state.user);
  const currentUser = staffUserOrNull(authenticatedUser);

  if (!currentUser) {
    return null;
  }

  return <AuthorizedCouponActionMenu coupon={coupon} currentUser={currentUser} />;
}

function AuthorizedCouponActionMenu({
  coupon,
  currentUser,
}: CouponActionMenuProps & { currentUser: StaffUser }) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [viewOpen, setViewOpen] = useState(false);
  const [supportOpen, setSupportOpen] = useState(false);
  const [archiveOpen, setArchiveOpen] = useState(false);
  const [reason, setReason] = useState('');
  const [errorText, setErrorText] = useState<string | null>(null);

  const actionMutation = useMutation({
    mutationFn: async ({ action, actionReason }: {
      action: MutationAction;
      actionReason?: string;
    }) => {
      switch (action) {
        case 'take-to-work':
          return api.patch(`/api/v1/admin/coupons/${coupon.id}/take-to-work`);
        case 'send-to-approval':
          return api.post(`/api/v1/admin/coupons/${coupon.id}/send-to-approval`);
        case 'pause':
          return api.patch(
            `/api/v1/admin/coupons/${coupon.id}/status`,
            undefined,
            { params: { status: 'PAUSED' } },
          );
        case 'restore':
          return api.patch(
            `/api/v1/admin/coupons/${coupon.id}/status`,
            undefined,
            { params: { status: 'ACTIVE' } },
          );
        case 'archive':
          return api.post(`/api/v1/admin/coupons/${coupon.id}/archive`, { reason: actionReason });
        case 'support-review':
          return api.patch(`/api/v1/mod/coupons/${coupon.id}/review`, {
            status: 'APPROVE',
            reason: actionReason,
          });
      }
    },
    retry: false,
    onSuccess: async () => {
      setSupportOpen(false);
      setArchiveOpen(false);
      setReason('');
      message.success('Действие с купоном выполнено');
      await queryClient.invalidateQueries({ queryKey: ADMIN_COUPONS_WORKSPACE_QUERY_KEY });
    },
    onError: async (error: unknown) => {
      setErrorText(actionErrorCopy(error));
      if ((error as HttpErrorLike)?.response?.status === 409) {
        await queryClient.invalidateQueries({ queryKey: ADMIN_COUPONS_WORKSPACE_QUERY_KEY });
      }
    },
  });

  const actions = allowedCouponActions(coupon, currentUser);
  const runMutation = (action: MutationAction, actionReason?: string) => {
    setErrorText(null);
    actionMutation.mutate({ action, actionReason });
  };
  const onAction = (action: CouponAction) => {
    switch (action) {
      case 'view':
        setViewOpen(true);
        return;
      case 'edit':
        navigate(`/coupons/${coupon.id}/edit`);
        return;
      case 'support-review':
        setReason('');
        setSupportOpen(true);
        return;
      case 'archive':
        setReason('');
        setArchiveOpen(true);
        return;
      default:
        runMutation(action);
    }
  };
  const menuItems: MenuProps['items'] = actions.map((action) => ({
    key: action,
    label: ACTION_LABELS[action],
  }));

  return (
    <>
      <Space orientation="vertical" size={8} style={{ alignItems: 'flex-end' }}>
        {errorText && <Alert type="error" showIcon message={errorText} />}
        <Dropdown
          menu={{
            items: menuItems,
            onClick: ({ key }) => onAction(key as CouponAction),
          }}
          trigger={['click']}
        >
          <Button aria-label="Действия" loading={actionMutation.isPending}>Действия</Button>
        </Dropdown>
      </Space>

      <Modal
        open={viewOpen}
        footer={null}
        title="Купон"
        onCancel={() => setViewOpen(false)}
      >
        <p><strong>{coupon.title}</strong></p>
        <p>Статус: {coupon.status}</p>
        <p>Партнёр: {coupon.merchant?.name ?? 'Не указан'}</p>
      </Modal>

      <Modal
        open={supportOpen}
        title="Служебное решение"
        okText="Подтвердить решение"
        cancelText="Отмена"
        okButtonProps={{ disabled: reason.trim().length === 0, loading: actionMutation.isPending }}
        onCancel={() => setSupportOpen(false)}
        onOk={() => runMutation('support-review', reason.trim())}
      >
        <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
          <Alert
            type="warning"
            showIcon
            title="Внимание: это служебное решение заменяет подтверждение партнёра."
          />
          <Input.TextArea
            aria-label="Причина служебного решения"
            rows={4}
            value={reason}
            onChange={(event) => setReason(event.target.value)}
          />
        </Space>
      </Modal>

      <Modal
        open={archiveOpen}
        title="Архивировать купон"
        okText="Архивировать"
        cancelText="Отмена"
        okButtonProps={{ disabled: reason.trim().length === 0, loading: actionMutation.isPending }}
        onCancel={() => setArchiveOpen(false)}
        onOk={() => runMutation('archive', reason.trim())}
      >
        <Input.TextArea
          aria-label="Причина архивации"
          rows={4}
          value={reason}
          onChange={(event) => setReason(event.target.value)}
        />
      </Modal>
    </>
  );
}
