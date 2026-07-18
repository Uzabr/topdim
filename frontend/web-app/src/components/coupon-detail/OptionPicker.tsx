import { useTranslation } from 'react-i18next';
import type { CouponOption } from '../../api/coupons';
import { optionLeft } from '../../utils/couponStock';
import './OptionPicker.css';

interface OptionPickerProps {
  options: CouponOption[];
  selectedId: number;
  onSelect: (id: number) => void;
}

/**
 * Варианты купона — чёрные строки; выбранная подсвечена жёлтой рамкой и
 * inset-свечением (design_handoff_sizbiz → «Страница купона» → «Информация»).
 */
export default function OptionPicker({ options, selectedId, onSelect }: OptionPickerProps) {
  const { t, i18n } = useTranslation();
  const locale = i18n.language === 'uz' ? 'uz-UZ' : 'ru-RU';
  const currency = t('common.currency.sum');

  return (
    <section className="options">
      <h2 className="detail-h2">{t('couponDetail.variants')}</h2>

      <div className="options__list" role="radiogroup" aria-label={t('couponDetail.variants')}>
        {options.map((o) => {
          const selected = o.id === selectedId;
          const left = optionLeft(o);
          const soldOut = left === 0;
          return (
            <button
              key={o.id}
              type="button"
              role="radio"
              aria-checked={selected}
              disabled={soldOut}
              className={`option${selected ? ' option--selected' : ''}${soldOut ? ' option--out' : ''}`}
              onClick={() => onSelect(o.id)}
            >
              <span className="option__dot" />
              <span className="option__title">{o.title}</span>
              <span className="option__left">
                {soldOut
                  ? t('couponDetail.optionSoldOut')
                  : left !== null
                    ? t('couponDetail.optionLeft', { count: left })
                    : ''}
              </span>
              <span className="option__prices">
                <span className="option__price">
                  {o.couponPrice.toLocaleString(locale)} {currency}
                </span>
                {o.regularPrice > o.couponPrice && (
                  <span className="option__old">{o.regularPrice.toLocaleString(locale)}</span>
                )}
              </span>
            </button>
          );
        })}
      </div>
    </section>
  );
}
