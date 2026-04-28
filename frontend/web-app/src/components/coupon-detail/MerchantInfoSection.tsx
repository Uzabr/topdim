import type { CouponOffer } from '../../api/coupons';
import ReactMarkdown from 'react-markdown';
import { MapPin, Clock } from 'lucide-react';
import RevealPhone from '../ui/RevealPhone';

interface MerchantInfoSectionProps {
  coupon: CouponOffer;
}

export default function MerchantInfoSection({ coupon }: MerchantInfoSectionProps) {
  const m = coupon.merchant;
  if (!m) return null;

  const loc = m.primaryLocation;

  return (
    <div className="detail-block merchant-info-section">
      <h2 className="detail-section-title">О партнёре</h2>

      <div className="merchant-info-header">
        {m.logoUrl ? (
          <img src={m.logoUrl} alt={m.name} className="merchant-info-logo" />
        ) : (
          <div className="merchant-info-logo merchant-info-logo--placeholder">
            {m.name.charAt(0)}
          </div>
        )}
        <div>
          <div className="merchant-info-name">{m.name}</div>
          {loc?.address && (
            <div className="merchant-info-detail">
              <MapPin size={14} />
              <span>{loc.address}</span>
            </div>
          )}
          {loc?.workingHours && (
            <div className="merchant-info-detail">
              <Clock size={14} />
              <span>{loc.workingHours}</span>
            </div>
          )}
        </div>
      </div>

      {m.description && (
        <div className="markdown-body" style={{ marginTop: 'var(--space-md)' }}>
          <ReactMarkdown>{m.description}</ReactMarkdown>
        </div>
      )}

      {loc?.phone && (
        <div style={{ marginTop: 'var(--space-md)' }}>
          <RevealPhone phone={loc.phone} />
        </div>
      )}
    </div>
  );
}
