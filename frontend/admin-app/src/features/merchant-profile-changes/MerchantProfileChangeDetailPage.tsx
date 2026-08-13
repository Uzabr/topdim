import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Form,
  Input,
  Modal,
  Result,
  Select,
  Space,
  Spin,
  Tag,
  Timeline,
  Typography,
} from 'antd';
import { ArrowLeftOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useAuthStore } from '../../store/authStore';
import {
  approveMerchantProfileChange,
  fetchMerchantProfileChangeDetail,
  fetchMerchantProfileChangeHistory,
  fetchPublishedMerchantProfile,
  fetchModerationAssignees,
  MERCHANT_PROFILE_CHANGES_QUERY_KEY,
  reassignMerchantProfileChange,
  rejectMerchantProfileChange,
  releaseMerchantProfileChange,
  requestMerchantProfileChangeRevision,
} from './api';
import { LocationDiff } from './LocationDiff';
import { ProfileFieldDiff } from './ProfileFieldDiff';

const { Title, Text } = Typography;

type DecisionAction = 'approve' | 'revision' | 'reject';

const STATUS_LABELS: Record<string, { label: string; color: string }> = {
  PENDING_REVIEW: { label: 'Ожидает проверки', color: 'orange' },
  IN_REVIEW: { label: 'В работе', color: 'blue' },
  REVISION_REQUESTED: { label: 'На доработке', color: 'gold' },
  APPROVED: { label: 'Одобрена', color: 'green' },
  REJECTED: { label: 'Отклонена', color: 'red' },
  WITHDRAWN: { label: 'Отозвана', color: 'default' },
  OUTDATED: { label: 'Устарела', color: 'default' },
};

function isConflict(error: unknown) {
  return (error as { response?: { status?: number } }).response?.status === 409;
}

function apiMessage(error: unknown, fallback: string) {
  return (error as { response?: { data?: { message?: string } } })
    .response?.data?.message || fallback;
}

export function MerchantProfileChangeDetailPage() {
  const { id } = useParams<{ id: string }>();
  const requestId = Number(id);
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const user = useAuthStore((state) => state.user);
  const [decision, setDecision] = useState<DecisionAction | null>(null);
  const [comment, setComment] = useState('');
  const [commentError, setCommentError] = useState<string | null>(null);
  const [reassignOpen, setReassignOpen] = useState(false);
  const [newAssigneeId, setNewAssigneeId] = useState<number | null>(null);
  const [feedback, setFeedback] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  const detailQuery = useQuery({
    queryKey: [...MERCHANT_PROFILE_CHANGES_QUERY_KEY, 'detail', requestId],
    queryFn: () => fetchMerchantProfileChangeDetail(requestId),
    enabled: Number.isInteger(requestId) && requestId > 0,
  });

  const historyQuery = useQuery({
    queryKey: [...MERCHANT_PROFILE_CHANGES_QUERY_KEY, 'detail', requestId, 'history'],
    queryFn: () => fetchMerchantProfileChangeHistory(requestId),
    enabled: Number.isInteger(requestId) && requestId > 0,
  });

  const request = detailQuery.data;
  const publishedQuery = useQuery({
    queryKey: [...MERCHANT_PROFILE_CHANGES_QUERY_KEY, 'published', request?.merchantId, user?.role],
    queryFn: () => fetchPublishedMerchantProfile(request!.merchantId, user!.role),
    enabled: !!request && !!user,
  });

  const assigneesQuery = useQuery({
    queryKey: [...MERCHANT_PROFILE_CHANGES_QUERY_KEY, 'assignees'],
    queryFn: fetchModerationAssignees,
    enabled: reassignOpen,
  });

  const handleMutationError = (error: unknown, fallback: string) => {
    setFeedback({
      type: 'error',
      text: isConflict(error) ? 'Заявка уже изменена' : apiMessage(error, fallback),
    });
    if (isConflict(error)) {
      queryClient.invalidateQueries({
        queryKey: [...MERCHANT_PROFILE_CHANGES_QUERY_KEY, 'detail', requestId],
      });
    }
  };

  const afterAction = (message: string) => {
    setFeedback({ type: 'success', text: message });
    setDecision(null);
    setComment('');
    setCommentError(null);
    setReassignOpen(false);
    setNewAssigneeId(null);
    queryClient.invalidateQueries({ queryKey: MERCHANT_PROFILE_CHANGES_QUERY_KEY });
  };

  const actionMutation = useMutation({
    mutationFn: async (args: { action: DecisionAction; comment?: string }) => {
      if (args.action === 'approve') return approveMerchantProfileChange(requestId);
      if (args.action === 'revision') {
        return requestMerchantProfileChangeRevision(requestId, args.comment ?? '');
      }
      return rejectMerchantProfileChange(requestId, args.comment ?? '');
    },
    onSuccess: (_data, variables) => afterAction(
      variables.action === 'approve'
        ? 'Изменения одобрены'
        : variables.action === 'revision'
          ? 'Заявка возвращена на доработку'
          : 'Заявка отклонена',
    ),
    onError: (error) => handleMutationError(error, 'Не удалось сохранить решение'),
  });

  const releaseMutation = useMutation({
    mutationFn: () => releaseMerchantProfileChange(requestId),
    onSuccess: () => afterAction('Заявка возвращена в очередь'),
    onError: (error) => handleMutationError(error, 'Не удалось освободить заявку'),
  });

  const reassignMutation = useMutation({
    mutationFn: (assigneeUserId: number) => reassignMerchantProfileChange(requestId, assigneeUserId),
    onSuccess: () => afterAction('Исполнитель изменён'),
    onError: (error) => handleMutationError(error, 'Не удалось изменить исполнителя'),
  });

  const confirmDecision = () => {
    if (!decision) return;
    if (decision !== 'approve' && !comment.trim()) {
      setCommentError('Укажите комментарий');
      return;
    }
    actionMutation.mutate({ action: decision, comment: comment.trim() });
  };

  if (!Number.isInteger(requestId) || requestId <= 0) {
    return <Result status="error" title="Некорректный ID заявки" />;
  }
  if (detailQuery.isLoading) return <Spin size="large" style={{ display: 'block', margin: 80 }} />;
  if (detailQuery.error || !request) {
    return (
      <Result
        status="error"
        title="Не удалось загрузить заявку"
        extra={<Button onClick={() => detailQuery.refetch()}>Повторить</Button>}
      />
    );
  }
  if (publishedQuery.isLoading) return <Spin size="large" style={{ display: 'block', margin: 80 }} />;
  if (publishedQuery.error || !publishedQuery.data) {
    return (
      <Result
        status="error"
        title="Не удалось загрузить опубликованный профиль"
        extra={<Button onClick={() => publishedQuery.refetch()}>Повторить</Button>}
      />
    );
  }

  const published = publishedQuery.data;
  const isAdminRole = user?.role === 'ADMIN' || user?.role === 'SUPER_ADMIN';
  const isAssignedReviewer = request.status === 'IN_REVIEW'
    && request.assigneeUserId === user?.id;
  const isAuthor = request.authorUserId === user?.id;
  const canDecide = isAssignedReviewer && !isAuthor;
  const statusInfo = STATUS_LABELS[request.status] ?? { label: request.status, color: 'default' };

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/merchants/profile-changes')}>
          К очереди
        </Button>
      </Space>

      <Title level={4}>Изменение компании #{request.id}</Title>

      {feedback && (
        <Alert
          type={feedback.type}
          title={feedback.text}
          showIcon
          closable
          onClose={() => setFeedback(null)}
          style={{ marginBottom: 16 }}
        />
      )}
      {isAuthor && (
        <Alert
          type="warning"
          title="Автор не может принять решение по собственной заявке"
          showIcon
          style={{ marginBottom: 16 }}
        />
      )}
      {!isAuthor && request.status === 'IN_REVIEW' && !isAssignedReviewer && (
        <Alert
          type="info"
          title={`Решение доступно назначенному исполнителю User ID: ${request.assigneeUserId ?? '—'}`}
          showIcon
          style={{ marginBottom: 16 }}
        />
      )}

      <Card size="small" title="Заявка" style={{ marginBottom: 16 }}>
        <Descriptions column={{ xs: 1, md: 3 }} size="small">
          <Descriptions.Item label="Статус"><Tag color={statusInfo.color}>{statusInfo.label}</Tag></Descriptions.Item>
          <Descriptions.Item label="Автор">User ID: {request.authorUserId} · {request.authorRole}</Descriptions.Item>
          <Descriptions.Item label="Исполнитель">{request.assigneeUserId ? `User ID: ${request.assigneeUserId}` : 'Не назначен'}</Descriptions.Item>
          <Descriptions.Item label="Базовая версия"><Tag>Версия {request.baseProfileVersion}</Tag></Descriptions.Item>
          <Descriptions.Item label="Текущая версия"><Tag>Версия {published.profileVersion}</Tag></Descriptions.Item>
          <Descriptions.Item label="Lock version">{request.lockVersion}</Descriptions.Item>
        </Descriptions>
      </Card>

      <Space orientation="vertical" size={12} style={{ width: '100%', marginBottom: 16 }}>
        <ProfileFieldDiff label="Название" before={published.name} after={request.name} />
        <ProfileFieldDiff label="Описание" before={published.description} after={request.description} />
        <ProfileFieldDiff label="Логотип" before={published.logoUrl} after={request.logoUrl} />
        <ProfileFieldDiff label="Обложка" before={published.coverUrl} after={request.coverUrl} />
        <ProfileFieldDiff label="Email" before={published.email} after={request.email} />
        <ProfileFieldDiff label="Сайт" before={published.website} after={request.website} />
        <ProfileFieldDiff label="Контактное лицо" before={published.contactPerson} after={request.contactPerson} />
      </Space>

      <Card title="Филиалы" size="small" style={{ marginBottom: 16 }}>
        <LocationDiff
          currentLocations={published.locations}
          proposedLocations={request.locations}
        />
      </Card>

      <Card title="История заявки" size="small" style={{ marginBottom: 16 }}>
        {historyQuery.isLoading && <Spin size="small" />}
        {historyQuery.isError && (
          <Alert
            type="error"
            title="Не удалось загрузить историю заявки"
            action={<Button size="small" onClick={() => historyQuery.refetch()}>Повторить</Button>}
          />
        )}
        {!historyQuery.isLoading && !historyQuery.isError && historyQuery.data?.length === 0 && (
          <Text type="secondary">История пока пуста</Text>
        )}
        {!historyQuery.isLoading && !historyQuery.isError && !!historyQuery.data?.length && (
          <Timeline
            items={historyQuery.data.map((event) => {
              const previous = event.previousStatus
                ? (STATUS_LABELS[event.previousStatus]?.label ?? event.previousStatus)
                : 'Создана';
              const next = STATUS_LABELS[event.newStatus]?.label ?? event.newStatus;
              return {
                content: (
                  <Space orientation="vertical" size={2}>
                    <Text strong>{previous} → {next}</Text>
                    <Text type="secondary">{event.actorRole} · User ID: {event.actorUserId}</Text>
                    {event.comment && <Text>{event.comment}</Text>}
                    <Text type="secondary">{dayjs(event.createdAt).format('DD.MM.YYYY HH:mm')}</Text>
                  </Space>
                ),
              };
            })}
          />
        )}
      </Card>

      <Space wrap>
        {canDecide && (
          <>
            <Button type="primary" onClick={() => setDecision('approve')}>Одобрить</Button>
            <Button onClick={() => setDecision('revision')}>Вернуть на доработку</Button>
            <Button danger onClick={() => setDecision('reject')}>Отклонить</Button>
          </>
        )}
        {isAdminRole && request.status === 'IN_REVIEW' && (
          <>
            <Button loading={releaseMutation.isPending} onClick={() => releaseMutation.mutate()}>
              Освободить
            </Button>
            <Button onClick={() => setReassignOpen(true)}>Переназначить</Button>
          </>
        )}
      </Space>

      <Modal
        open={decision !== null}
        title={decision === 'approve'
          ? 'Одобрить изменения?'
          : decision === 'revision'
            ? 'Вернуть на доработку'
            : 'Отклонить заявку'}
        okText="Подтвердить"
        cancelText="Отмена"
        confirmLoading={actionMutation.isPending}
        onCancel={() => { setDecision(null); setComment(''); setCommentError(null); }}
        onOk={confirmDecision}
      >
        {decision !== 'approve' && (
          <Form.Item
            label="Комментарий"
            validateStatus={commentError ? 'error' : undefined}
            help={commentError}
          >
            <Input.TextArea
              aria-label="Комментарий"
              value={comment}
              maxLength={2000}
              rows={4}
              onChange={(event) => { setComment(event.target.value); setCommentError(null); }}
            />
          </Form.Item>
        )}
        {decision === 'approve' && <Text>Профиль компании будет опубликован.</Text>}
      </Modal>

      <Modal
        open={reassignOpen}
        title="Переназначить заявку"
        okText="Переназначить"
        cancelText="Отмена"
        confirmLoading={reassignMutation.isPending}
        onCancel={() => { setReassignOpen(false); setNewAssigneeId(null); }}
        onOk={() => newAssigneeId && reassignMutation.mutate(newAssigneeId)}
        okButtonProps={{ disabled: !newAssigneeId }}
      >
        {assigneesQuery.isError && (
          <Alert
            type="error"
            title="Не удалось загрузить список исполнителей"
            action={<Button size="small" onClick={() => assigneesQuery.refetch()}>Повторить</Button>}
            style={{ marginBottom: 12 }}
          />
        )}
        <Select
          aria-label="Новый исполнитель"
          placeholder="Выберите сотрудника"
          loading={assigneesQuery.isLoading}
          disabled={assigneesQuery.isError}
          value={newAssigneeId}
          onChange={setNewAssigneeId}
          options={(assigneesQuery.data ?? [])
            .filter((assignee) => assignee.userId !== request.authorUserId
              && assignee.userId !== request.assigneeUserId)
            .map((assignee) => ({
              value: assignee.userId,
              label: `${assignee.name} · ${assignee.role}`,
            }))}
          style={{ width: '100%' }}
        />
      </Modal>
    </div>
  );
}
