import { useEffect, useRef, useState } from 'react';
import { Share2, Check } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import './ShareMenu.css';

interface ShareMenuProps {
  title: string;
}

export default function ShareMenu({ title }: ShareMenuProps) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  const [copied, setCopied] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const onDown = (e: MouseEvent) => {
      if (!ref.current?.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', onDown);
    return () => document.removeEventListener('mousedown', onDown);
  }, [open]);

  const url = typeof window !== 'undefined' ? window.location.href : '';
  const text = encodeURIComponent(title);
  const link = encodeURIComponent(url);

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(url);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      // Clipboard недоступен (нет https / нет разрешения) — оставляем меню открытым.
    }
  };

  return (
    <div className="share" ref={ref}>
      <button type="button" className="share__btn" onClick={() => setOpen((v) => !v)}>
        <Share2 size={15} />
        {t('couponDetail.share')}
      </button>

      {open && (
        <div className="share__menu">
          <a
            className="share__item"
            href={`https://t.me/share/url?url=${link}&text=${text}`}
            target="_blank"
            rel="noopener noreferrer"
          >
            Telegram
          </a>
          <a
            className="share__item"
            href={`https://wa.me/?text=${text}%20${link}`}
            target="_blank"
            rel="noopener noreferrer"
          >
            WhatsApp
          </a>
          <button type="button" className="share__item" onClick={copy}>
            {copied ? (
              <>
                <Check size={14} /> {t('couponDetail.copied')}
              </>
            ) : (
              t('couponDetail.copyLink')
            )}
          </button>
        </div>
      )}
    </div>
  );
}
