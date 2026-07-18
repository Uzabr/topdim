import { useTranslation } from 'react-i18next';
import type { MerchantPrimaryLocation } from '../../api/coupons';
import TwoGisMap from '../map/TwoGisMap';
import './WhereSection.css';

interface WhereSectionProps {
  location?: MerchantPrimaryLocation;
  merchantName?: string;
}

/** Ссылка на маршрут в 2ГИС — карты у нас и так на mapgl. */
function routeUrl(lat: number, lon: number): string {
  return `https://2gis.uz/tashkent/directions/points/%7C${lon}%2C${lat}`;
}

export default function WhereSection({ location, merchantName }: WhereSectionProps) {
  const { t } = useTranslation();
  if (!location?.address) return null;

  const hasCoords = location.latitude != null && location.longitude != null;
  const meta = [location.workingHours, location.phone].filter(Boolean).join(' · ');

  return (
    <section className="where">
      <h2 className="detail-h2">{t('couponDetail.where')}</h2>

      <div className="where__row">
        <div className="where__card">
          <p className="where__address">{location.address}</p>
          {meta && <p className="where__meta">{meta}</p>}
          {hasCoords && (
            <a
              className="where__route"
              href={routeUrl(location.latitude as number, location.longitude as number)}
              target="_blank"
              rel="noopener noreferrer"
            >
              {t('couponDetail.route')}
            </a>
          )}
        </div>

        {hasCoords && (
          <TwoGisMap
            className="where__map"
            center={[location.longitude as number, location.latitude as number]}
            zoom={16}
            staticMarker={{
              lat: location.latitude as number,
              lon: location.longitude as number,
              title: location.title || merchantName,
            }}
          />
        )}
      </div>
    </section>
  );
}
