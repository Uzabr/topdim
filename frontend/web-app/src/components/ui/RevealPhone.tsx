import { useState } from 'react';
import { Phone, Eye } from 'lucide-react';
import './RevealPhone.css';

interface RevealPhoneProps {
  phone: string;
  label?: string;
}

export default function RevealPhone({ phone, label = 'Телефон' }: RevealPhoneProps) {
  const [revealed, setRevealed] = useState(false);

  const maskedPhone = phone.replace(/(\+\d{3}\s?\d{2})\s?(\d{3})/, '$1 ***');

  return (
    <div className="reveal-phone">
      <Phone size={16} className="reveal-phone__icon" />
      <div className="reveal-phone__content">
        <span className="reveal-phone__label">{label}</span>
        {revealed ? (
          <a href={`tel:${phone.replace(/\s|-/g, '')}`} className="reveal-phone__number">
            {phone}
          </a>
        ) : (
          <button
            className="reveal-phone__btn"
            onClick={() => setRevealed(true)}
          >
            <span>{maskedPhone}</span>
            <span className="reveal-phone__show">
              <Eye size={14} />
              Показать
            </span>
          </button>
        )}
      </div>
    </div>
  );
}
