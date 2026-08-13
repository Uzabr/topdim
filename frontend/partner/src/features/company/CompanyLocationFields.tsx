import { App as AntApp, Button, Card, Col, Input, InputNumber, Row, Space, Tag, Typography } from 'antd';
import { DeleteOutlined, PoweroffOutlined, StarOutlined } from '@ant-design/icons';

const { Text } = Typography;

export interface EditorCompanyLocation {
  clientId: number;
  sourceLocationId: number | null;
  title: string | null;
  address: string | null;
  phone: string | null;
  workingHours: string | null;
  latitude: number | null;
  longitude: number | null;
  primary: boolean;
  active: boolean;
}

interface CompanyLocationFieldsProps {
  index: number;
  location: EditorCompanyLocation;
  readOnly: boolean;
  onChange: (changes: Partial<EditorCompanyLocation>) => void;
  onMakePrimary: () => void;
  onRemoveNew: () => void;
}

export default function CompanyLocationFields({
  index,
  location,
  readOnly,
  onChange,
  onMakePrimary,
  onRemoveNew,
}: CompanyLocationFieldsProps) {
  const { message } = AntApp.useApp();
  const prefix = `Филиал ${index + 1}`;

  const disableLocation = () => {
    if (location.primary && location.active) {
      message.warning('Сначала выберите другой основной филиал');
      return;
    }
    onChange({ active: false, primary: false });
  };

  return (
    <Card
      size="small"
      title={(
        <Space wrap>
          <Text strong>{prefix}</Text>
          {location.primary ? <Tag color="blue">Основной</Tag> : null}
          <Tag color={location.active ? 'green' : 'default'}>
            {location.active ? 'Активен' : 'Отключён'}
          </Tag>
        </Space>
      )}
      extra={!readOnly ? (
        <Space wrap>
          {location.active && !location.primary ? (
            <Button icon={<StarOutlined />} onClick={onMakePrimary}>
              Сделать основным
            </Button>
          ) : null}
          {location.sourceLocationId === null ? (
            <Button danger icon={<DeleteOutlined />} onClick={onRemoveNew}>
              Удалить новый филиал
            </Button>
          ) : location.active ? (
            <Button danger icon={<PoweroffOutlined />} onClick={disableLocation}>
              Отключить филиал
            </Button>
          ) : (
            <Button onClick={() => onChange({ active: true })}>Включить филиал</Button>
          )}
        </Space>
      ) : null}
    >
      <Row gutter={[16, 12]}>
        <Col xs={24} md={12}>
          <label htmlFor={`location-${location.clientId}-title`}>Название филиала</label>
          <Input
            id={`location-${location.clientId}-title`}
            value={location.title ?? ''}
            maxLength={255}
            disabled={readOnly}
            onChange={(event) => onChange({ title: event.target.value || null })}
          />
        </Col>
        <Col xs={24} md={12}>
          <label htmlFor={`location-${location.clientId}-phone`}>Телефон филиала</label>
          <Input
            id={`location-${location.clientId}-phone`}
            value={location.phone ?? ''}
            maxLength={50}
            disabled={readOnly}
            status={location.active && !location.phone?.trim() ? 'error' : undefined}
            onChange={(event) => onChange({ phone: event.target.value || null })}
          />
        </Col>
        <Col span={24}>
          <label htmlFor={`location-${location.clientId}-address`}>Адрес филиала</label>
          <Input
            id={`location-${location.clientId}-address`}
            value={location.address ?? ''}
            maxLength={500}
            disabled={readOnly}
            status={location.active && !location.address?.trim() ? 'error' : undefined}
            onChange={(event) => onChange({ address: event.target.value || null })}
          />
        </Col>
        <Col xs={24} md={8}>
          <label htmlFor={`location-${location.clientId}-hours`}>Часы работы</label>
          <Input
            id={`location-${location.clientId}-hours`}
            value={location.workingHours ?? ''}
            maxLength={255}
            disabled={readOnly}
            onChange={(event) => onChange({ workingHours: event.target.value || null })}
          />
        </Col>
        <Col xs={24} md={8}>
          <label htmlFor={`location-${location.clientId}-latitude`}>Широта</label>
          <InputNumber
            id={`location-${location.clientId}-latitude`}
            aria-label="Широта"
            value={location.latitude}
            min={-90}
            max={90}
            precision={6}
            disabled={readOnly}
            style={{ width: '100%' }}
            onChange={(value) => onChange({ latitude: value })}
          />
        </Col>
        <Col xs={24} md={8}>
          <label htmlFor={`location-${location.clientId}-longitude`}>Долгота</label>
          <InputNumber
            id={`location-${location.clientId}-longitude`}
            aria-label="Долгота"
            value={location.longitude}
            min={-180}
            max={180}
            precision={6}
            disabled={readOnly}
            style={{ width: '100%' }}
            onChange={(value) => onChange({ longitude: value })}
          />
        </Col>
      </Row>
    </Card>
  );
}
