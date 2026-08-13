import { useCallback, useMemo, useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  App as AntApp,
  Button,
  Card,
  Col,
  Input,
  Modal,
  Result,
  Row,
  Space,
  Spin,
  Tabs,
  Tag,
  Typography,
} from 'antd';
import {
  ArrowLeftOutlined,
  CopyOutlined,
  PlusOutlined,
  SaveOutlined,
  SendOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import { useBeforeUnload, useBlocker, useNavigate, useParams } from 'react-router-dom';
import { companyApi } from './api';
import CompanyLocationFields, { type EditorCompanyLocation } from './CompanyLocationFields';
import CompanyProfilePreview from './CompanyProfilePreview';
import type {
  CompanyChangePayload,
  CompanyChangeRequest,
  CompanyProfileChangeStatus,
  PublishedCompanyProfile,
} from './types';

const { Paragraph, Text, Title } = Typography;
const EDITABLE_STATUSES = new Set<CompanyProfileChangeStatus>(['DRAFT', 'REVISION_REQUESTED']);
const COPYABLE_STATUSES = new Set<CompanyProfileChangeStatus>([
  'APPROVED',
  'REJECTED',
  'WITHDRAWN',
  'OUTDATED',
]);
const STATUS_LABELS: Record<CompanyProfileChangeStatus, string> = {
  DRAFT: 'Черновик',
  PENDING_REVIEW: 'Ожидает проверки',
  IN_REVIEW: 'На проверке',
  REVISION_REQUESTED: 'Нужны исправления',
  APPROVED: 'Одобрена',
  REJECTED: 'Отклонена',
  WITHDRAWN: 'Отозвана',
  OUTDATED: 'Устарела',
};

interface EditorState {
  name: string;
  description: string;
  logoUrl: string | null;
  coverUrl: string | null;
  email: string;
  website: string;
  contactPerson: string;
  locations: EditorCompanyLocation[];
}

let newLocationId = -1;

function initialEditorState(request: CompanyChangeRequest): EditorState {
  return {
    name: request.name,
    description: request.description ?? '',
    logoUrl: request.logoUrl,
    coverUrl: request.coverUrl,
    email: request.email ?? '',
    website: request.website ?? '',
    contactPerson: request.contactPerson ?? '',
    locations: request.locations
      .slice()
      .sort((left, right) => left.sortOrder - right.sortOrder)
      .map((location) => ({
        clientId: location.id,
        sourceLocationId: location.sourceLocationId,
        title: location.title,
        address: location.address,
        phone: location.phone,
        workingHours: location.workingHours,
        latitude: location.latitude,
        longitude: location.longitude,
        primary: location.primary,
        active: location.active,
      })),
  };
}

function nullable(value: string): string | null {
  const trimmed = value.trim();
  return trimmed || null;
}

function toPayload(state: EditorState): CompanyChangePayload {
  return {
    name: state.name.trim(),
    description: nullable(state.description),
    logoUrl: state.logoUrl,
    coverUrl: state.coverUrl,
    email: nullable(state.email),
    website: nullable(state.website),
    contactPerson: nullable(state.contactPerson),
    locations: state.locations.map((location) => ({
      sourceLocationId: location.sourceLocationId,
      title: nullable(location.title ?? ''),
      address: nullable(location.address ?? ''),
      phone: nullable(location.phone ?? ''),
      workingHours: nullable(location.workingHours ?? ''),
      latitude: location.latitude,
      longitude: location.longitude,
      primary: location.primary,
      active: location.active,
    })),
  };
}

function scalarValidationIssues(state: EditorState): string[] {
  const issues: string[] = [];
  if (!state.name.trim()) issues.push('Укажите название компании');
  if (state.email.trim() && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(state.email.trim())) {
    issues.push('Укажите корректный email');
  }
  if (state.website.trim() && !/^https?:\/\/.+/.test(state.website.trim())) {
    issues.push('Сайт должен начинаться с http:// или https://');
  }
  state.locations.forEach((location, index) => {
    if (location.latitude !== null
      && (!Number.isFinite(location.latitude) || location.latitude < -90 || location.latitude > 90)) {
      issues.push(`Филиал ${index + 1}: широта должна быть от -90 до 90`);
    }
    if (location.longitude !== null
      && (!Number.isFinite(location.longitude)
        || location.longitude < -180
        || location.longitude > 180)) {
      issues.push(`Филиал ${index + 1}: долгота должна быть от -180 до 180`);
    }
  });
  return issues;
}

function submissionValidationIssues(state: EditorState): string[] {
  const issues = [...scalarValidationIssues(state)];
  const activePrimaryCount = state.locations.filter(
    (location) => location.active && location.primary,
  ).length;
  if (activePrimaryCount !== 1) {
    issues.push('Выберите ровно один активный основной филиал');
  }
  state.locations.forEach((location, index) => {
    if (location.active && (!location.address?.trim() || !location.phone?.trim())) {
      issues.push(`Филиал ${index + 1}: для активного филиала нужны адрес и телефон`);
    }
    const hasLatitude = location.latitude !== null;
    const hasLongitude = location.longitude !== null;
    if (hasLatitude !== hasLongitude) {
      issues.push(`Филиал ${index + 1}: укажите обе координаты или очистите обе`);
    }
  });
  return issues;
}

function previewProfile(request: CompanyChangeRequest, state: EditorState): PublishedCompanyProfile {
  const locations = state.locations.map((location) => ({
    id: location.clientId,
    title: location.title,
    address: location.address,
    phone: location.phone,
    workingHours: location.workingHours,
    latitude: location.latitude,
    longitude: location.longitude,
    primary: location.primary,
    active: location.active,
  }));
  const issues = submissionValidationIssues(state);
  return {
    id: request.merchantId,
    name: state.name || 'Без названия',
    description: nullable(state.description),
    logoUrl: state.logoUrl,
    coverUrl: state.coverUrl,
    email: nullable(state.email),
    website: nullable(state.website),
    contactPerson: nullable(state.contactPerson),
    userId: request.authorUserId,
    active: true,
    profileVersion: request.baseProfileVersion,
    publicationReady: issues.length === 0,
    publicationBlockReason: issues[0] ?? null,
    primaryLocation: locations.find((location) => location.active && location.primary) ?? null,
    locations,
  };
}

function errorMessage(error: unknown, fallback: string): string {
  const apiError = error as { response?: { data?: { message?: string } } };
  return apiError.response?.data?.message || fallback;
}

export default function CompanyRequestEditorPage() {
  const params = useParams();
  const requestId = Number(params.id);
  const validRequestId = Number.isInteger(requestId) && requestId > 0;
  const requestQuery = useQuery({
    queryKey: ['company-change-request', requestId],
    enabled: validRequestId,
    queryFn: () => companyApi.getRequest(requestId),
  });

  if (!validRequestId) {
    return <Result status="404" title="Заявка не найдена" />;
  }
  if (requestQuery.isLoading) {
    return <Spin size="large" style={{ display: 'block', margin: '100px auto' }} />;
  }
  if (requestQuery.error || !requestQuery.data) {
    return <Result status="error" title="Не удалось загрузить заявку" />;
  }

  return <CompanyRequestEditor key={requestQuery.data.id} request={requestQuery.data} />;
}

function CompanyRequestEditor({ request }: { request: CompanyChangeRequest }) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { message } = AntApp.useApp();
  const [state, setState] = useState(() => initialEditorState(request));
  const [dirty, setDirty] = useState(false);
  const [uploading, setUploading] = useState<'logo' | 'cover' | null>(null);
  const allowNavigationRef = useRef(false);
  const readOnly = !EDITABLE_STATUSES.has(request.status);
  const scalarIssues = useMemo(() => scalarValidationIssues(state), [state]);
  const submitIssues = useMemo(() => submissionValidationIssues(state), [state]);
  const preview = useMemo(() => previewProfile(request, state), [request, state]);
  const blocker = useBlocker(useCallback(
    () => dirty && !allowNavigationRef.current,
    [dirty],
  ));

  useBeforeUnload(useCallback((event) => {
    if (dirty) {
      event.preventDefault();
      event.returnValue = '';
    }
  }, [dirty]));

  const markChanged = (changes: Partial<EditorState>) => {
    setState((current) => ({ ...current, ...changes }));
    setDirty(true);
  };

  const invalidateCompanyQueries = async () => {
    await queryClient.invalidateQueries({ queryKey: ['company-change-request', request.id] });
    await queryClient.invalidateQueries({ queryKey: ['company-change-requests'] });
    await queryClient.invalidateQueries({ queryKey: ['company-profile'] });
  };

  const saveMutation = useMutation({
    mutationFn: () => companyApi.updateRequest(request.id, toPayload(state)),
    onSuccess: async (saved) => {
      queryClient.setQueryData(['company-change-request', request.id], saved);
      setState(initialEditorState(saved));
      setDirty(false);
      await invalidateCompanyQueries();
      message.success('Черновик сохранён');
    },
    onError: (error) => message.error(errorMessage(error, 'Не удалось сохранить черновик')),
  });
  const submitMutation = useMutation({
    mutationFn: async () => {
      const saved = await companyApi.updateRequest(request.id, toPayload(state));
      queryClient.setQueryData(['company-change-request', request.id], saved);
      setState(initialEditorState(saved));
      setDirty(false);
      try {
        return await companyApi.submit(request.id);
      } catch (error) {
        await invalidateCompanyQueries();
        throw error;
      }
    },
    onSuccess: async () => {
      allowNavigationRef.current = true;
      setDirty(false);
      await invalidateCompanyQueries();
      message.success('Заявка отправлена на проверку');
      navigate('/company');
    },
    onError: (error) => message.error(errorMessage(error, 'Не удалось отправить заявку')),
  });
  const copyMutation = useMutation({
    mutationFn: () => companyApi.copy(request.id),
    onSuccess: async (copy) => {
      await invalidateCompanyQueries();
      navigate(`/company/requests/${copy.id}`);
    },
    onError: (error) => message.error(errorMessage(error, 'Не удалось создать копию')),
  });
  const formLocked = readOnly
    || uploading !== null
    || saveMutation.isPending
    || submitMutation.isPending;

  const updateLocation = (clientId: number, changes: Partial<EditorCompanyLocation>) => {
    markChanged({
      locations: state.locations.map((location) => (
        location.clientId === clientId ? { ...location, ...changes } : location
      )),
    });
  };
  const makePrimary = (clientId: number) => {
    markChanged({
      locations: state.locations.map((location) => ({
        ...location,
        primary: location.clientId === clientId,
        active: location.clientId === clientId ? true : location.active,
      })),
    });
  };
  const addLocation = () => {
    const hasActivePrimary = state.locations.some((location) => location.active && location.primary);
    markChanged({
      locations: [...state.locations, {
        clientId: newLocationId--,
        sourceLocationId: null,
        title: null,
        address: null,
        phone: null,
        workingHours: null,
        latitude: null,
        longitude: null,
        primary: !hasActivePrimary,
        active: true,
      }],
    });
  };

  const uploadImage = async (kind: 'logo' | 'cover', file: File | undefined) => {
    if (!file) return;
    setUploading(kind);
    try {
      const uploaded = await companyApi.uploadMedia(file);
      markChanged(kind === 'logo' ? { logoUrl: uploaded.url } : { coverUrl: uploaded.url });
      message.success(kind === 'logo' ? 'Логотип загружен' : 'Обложка загружена');
    } catch (error) {
      message.error(errorMessage(error, 'Не удалось загрузить изображение'));
    } finally {
      setUploading(null);
    }
  };

  const goBack = () => {
    navigate('/company');
  };

  const formTab = (
    <Space orientation="vertical" size={16} style={{ width: '100%' }}>
      <Card title="Данные компании">
        <Row gutter={[16, 12]}>
          <Col span={24}>
            <label htmlFor="company-name">Название компании</label>
            <Input
              id="company-name"
              value={state.name}
              maxLength={255}
              disabled={formLocked}
              status={!state.name.trim() ? 'error' : undefined}
              onChange={(event) => markChanged({ name: event.target.value })}
            />
          </Col>
          <Col span={24}>
            <label htmlFor="company-description">Описание</label>
            <Input.TextArea
              id="company-description"
              value={state.description}
              maxLength={5000}
              rows={4}
              showCount
              disabled={formLocked}
              onChange={(event) => markChanged({ description: event.target.value })}
            />
          </Col>
          <Col xs={24} md={8}>
            <label htmlFor="company-email">Email</label>
            <Input
              id="company-email"
              value={state.email}
              maxLength={255}
              disabled={formLocked}
              onChange={(event) => markChanged({ email: event.target.value })}
            />
          </Col>
          <Col xs={24} md={8}>
            <label htmlFor="company-website">Сайт</label>
            <Input
              id="company-website"
              value={state.website}
              maxLength={500}
              disabled={formLocked}
              onChange={(event) => markChanged({ website: event.target.value })}
            />
          </Col>
          <Col xs={24} md={8}>
            <label htmlFor="company-contact">Контактное лицо</label>
            <Input
              id="company-contact"
              value={state.contactPerson}
              maxLength={255}
              disabled={formLocked}
              onChange={(event) => markChanged({ contactPerson: event.target.value })}
            />
          </Col>
        </Row>
      </Card>

      <Card title="Изображения">
        <Row gutter={[24, 16]}>
          <Col xs={24} md={12}>
            <Space orientation="vertical" style={{ width: '100%' }}>
              <Text strong>Логотип</Text>
              {state.logoUrl ? (
                <img
                  src={state.logoUrl}
                  alt={`Логотип компании ${state.name}`}
                  style={{ width: 96, height: 96, objectFit: 'cover', borderRadius: 8 }}
                />
              ) : <Text type="secondary">Логотип не выбран</Text>}
              {!readOnly ? (
                <Space wrap>
                  <label>
                    <span className="ant-btn ant-btn-default">
                      <UploadOutlined /> {uploading === 'logo' ? 'Загрузка…' : 'Загрузить логотип'}
                    </span>
                    <input
                      type="file"
                      accept="image/*"
                      aria-label="Загрузить логотип"
                      hidden
                      disabled={formLocked}
                      onChange={(event) => void uploadImage('logo', event.target.files?.[0])}
                    />
                  </label>
                  {state.logoUrl ? (
                    <Button onClick={() => markChanged({ logoUrl: null })}>Убрать логотип</Button>
                  ) : null}
                </Space>
              ) : null}
            </Space>
          </Col>
          <Col xs={24} md={12}>
            <Space orientation="vertical" style={{ width: '100%' }}>
              <Text strong>Обложка</Text>
              {state.coverUrl ? (
                <img
                  src={state.coverUrl}
                  alt={`Обложка компании ${state.name}`}
                  style={{ width: '100%', maxWidth: 320, height: 120, objectFit: 'cover', borderRadius: 8 }}
                />
              ) : <Text type="secondary">Обложка не выбрана</Text>}
              {!readOnly ? (
                <Space wrap>
                  <label>
                    <span className="ant-btn ant-btn-default">
                      <UploadOutlined /> {uploading === 'cover' ? 'Загрузка…' : 'Загрузить обложку'}
                    </span>
                    <input
                      type="file"
                      accept="image/*"
                      aria-label="Загрузить обложку"
                      hidden
                      disabled={formLocked}
                      onChange={(event) => void uploadImage('cover', event.target.files?.[0])}
                    />
                  </label>
                  {state.coverUrl ? (
                    <Button onClick={() => markChanged({ coverUrl: null })}>Убрать обложку</Button>
                  ) : null}
                </Space>
              ) : null}
            </Space>
          </Col>
        </Row>
      </Card>

      <Space align="center" style={{ width: '100%', justifyContent: 'space-between' }} wrap>
        <Title level={3} style={{ margin: 0 }}>Филиалы</Title>
        {!readOnly ? (
          <Button icon={<PlusOutlined />} disabled={formLocked} onClick={addLocation}>
            Добавить филиал
          </Button>
        ) : null}
      </Space>
      {state.locations.map((location, index) => (
        <CompanyLocationFields
          key={location.clientId}
          index={index}
          location={location}
          readOnly={formLocked}
          onChange={(changes) => updateLocation(location.clientId, changes)}
          onMakePrimary={() => makePrimary(location.clientId)}
          onRemoveNew={() => markChanged({
            locations: state.locations.filter((item) => item.clientId !== location.clientId),
          })}
        />
      ))}
    </Space>
  );

  return (
    <section aria-label={`Заявка компании #${request.id}`}>
      <Space align="start" style={{ width: '100%', justifyContent: 'space-between' }} wrap>
        <div>
          <Button type="link" icon={<ArrowLeftOutlined />} onClick={goBack}>К профилю компании</Button>
          <Title level={2} style={{ margin: 0 }}>Заявка #{request.id}</Title>
          <Space wrap>
            <Tag>{STATUS_LABELS[request.status]}</Tag>
            <Text type="secondary">Базовая версия {request.baseProfileVersion}</Text>
            {dirty ? <Tag color="orange">Есть несохранённые изменения</Tag> : null}
          </Space>
        </div>
        <Space wrap>
          {!readOnly ? (
            <>
              <Button
                icon={<SaveOutlined />}
                disabled={scalarIssues.length > 0 || formLocked}
                loading={saveMutation.isPending}
                onClick={() => saveMutation.mutate()}
              >
                Сохранить черновик
              </Button>
              <Button
                type="primary"
                icon={<SendOutlined />}
                disabled={submitIssues.length > 0 || formLocked}
                loading={submitMutation.isPending}
                onClick={() => submitMutation.mutate()}
              >
                Отправить на проверку
              </Button>
            </>
          ) : COPYABLE_STATUSES.has(request.status) ? (
            <Button
              type="primary"
              icon={<CopyOutlined />}
              loading={copyMutation.isPending}
              onClick={() => copyMutation.mutate()}
            >
              Создать копию
            </Button>
          ) : null}
        </Space>
      </Space>

      {request.status === 'REVISION_REQUESTED' && request.moderationComment ? (
        <Alert
          type="warning"
          showIcon
          title="Комментарий модератора"
          description={request.moderationComment}
          style={{ marginTop: 16 }}
        />
      ) : null}
      {request.status === 'OUTDATED' ? (
        <Alert
          type="warning"
          showIcon
          title="Заявка устарела"
          description="Профиль компании уже опубликован в более новой версии. Создайте копию на актуальной базе."
          style={{ marginTop: 16 }}
        />
      ) : null}
      {!readOnly && submitIssues.length > 0 ? (
        <Alert
          type="info"
          showIcon
          title="Что нужно исправить перед отправкой"
          description={(
            <ul style={{ margin: 0, paddingLeft: 20 }}>
              {submitIssues.map((issue) => <li key={issue}>{issue}</li>)}
            </ul>
          )}
          style={{ marginTop: 16 }}
        />
      ) : null}

      <Tabs
        style={{ marginTop: 16 }}
        items={[
          { key: 'form', label: readOnly ? 'Снимок заявки' : 'Редактор', children: formTab },
          { key: 'preview', label: 'Предпросмотр', children: <CompanyProfilePreview profile={preview} /> },
        ]}
      />

      <Modal
        open={blocker.state === 'blocked'}
        title="Есть несохранённые изменения"
        onCancel={() => {
          if (blocker.state === 'blocked') blocker.reset();
        }}
        footer={[
          <Button
            key="stay"
            onClick={() => {
              if (blocker.state === 'blocked') blocker.reset();
            }}
          >
            Остаться
          </Button>,
          <Button
            key="leave"
            danger
            type="primary"
            onClick={() => {
              allowNavigationRef.current = true;
              setDirty(false);
              if (blocker.state === 'blocked') blocker.proceed();
              else navigate('/company');
            }}
          >
            Выйти без сохранения
          </Button>,
        ]}
      >
        <Paragraph>Если выйти сейчас, изменения в заявке будут потеряны.</Paragraph>
      </Modal>
    </section>
  );
}
