import { Card, Space, Tag } from 'antd';
import type {
  MerchantProfileLocationSnapshot,
  PublishedMerchantLocation,
} from './types';
import { ProfileFieldDiff } from './ProfileFieldDiff';

interface LocationDiffProps {
  currentLocations: PublishedMerchantLocation[];
  proposedLocations: MerchantProfileLocationSnapshot[];
}

function locationChanged(
  current: PublishedMerchantLocation,
  proposed: MerchantProfileLocationSnapshot,
) {
  return current.title !== proposed.title
    || current.address !== proposed.address
    || current.phone !== proposed.phone
    || current.workingHours !== proposed.workingHours
    || current.latitude !== proposed.latitude
    || current.longitude !== proposed.longitude
    || current.primary !== proposed.primary
    || current.active !== proposed.active;
}

export function LocationDiff({ currentLocations, proposedLocations }: LocationDiffProps) {
  const currentById = new Map(currentLocations.map((location) => [location.id, location]));

  return (
    <Space orientation="vertical" size={12} style={{ width: '100%' }}>
      {proposedLocations
        .slice()
        .sort((a, b) => a.sortOrder - b.sortOrder)
        .map((proposed) => {
          const current = proposed.sourceLocationId === null
            ? undefined
            : currentById.get(proposed.sourceLocationId);
          const isNew = proposed.sourceLocationId === null;
          const isDisabled = !!current?.active && !proposed.active;
          const changed = current ? locationChanged(current, proposed) : true;
          return (
            <Card
              key={isNew ? `new-${proposed.id}` : `existing-${proposed.sourceLocationId}`}
              size="small"
              title={proposed.title || 'Без названия'}
              extra={(
                <Space wrap>
                  {isNew && <Tag color="green">Будет добавлен</Tag>}
                  {isDisabled && <Tag color="red">Филиал будет отключён</Tag>}
                  {!isNew && changed && !isDisabled && <Tag color="gold">Изменён</Tag>}
                </Space>
              )}
            >
              <Space orientation="vertical" size={8} style={{ width: '100%' }}>
                <ProfileFieldDiff label="Название" before={current?.title} after={proposed.title} />
                <ProfileFieldDiff label="Адрес" before={current?.address} after={proposed.address} />
                <ProfileFieldDiff label="Телефон" before={current?.phone} after={proposed.phone} />
                <ProfileFieldDiff label="Часы работы" before={current?.workingHours} after={proposed.workingHours} />
                <ProfileFieldDiff label="Широта" before={current?.latitude} after={proposed.latitude} />
                <ProfileFieldDiff label="Долгота" before={current?.longitude} after={proposed.longitude} />
                <ProfileFieldDiff
                  label="Основной"
                  before={current ? (current.primary ? 'Да' : 'Нет') : null}
                  after={proposed.primary ? 'Да' : 'Нет'}
                />
                <ProfileFieldDiff
                  label="Активен"
                  before={current ? (current.active ? 'Да' : 'Нет') : null}
                  after={proposed.active ? 'Да' : 'Нет'}
                />
              </Space>
            </Card>
          );
        })}
    </Space>
  );
}
