import { useEffect } from 'react';
import { useScrollLock } from '../../hooks/useScrollLock';
import { X } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import LoginCard from './LoginCard';
import './LoginModal.css';

interface LoginModalProps {
  onClose: () => void;
  /** Вызывается только после успешного входа (по умолчанию — просто закрыть). */
  onSuccess?: () => void;
}

/** Вход открывается модалкой поверх страницы — пользователь не теряет контекст. */
export default function LoginModal({ onClose, onSuccess }: LoginModalProps) {
  const { t } = useTranslation();
  useScrollLock();

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [onClose]);

  return (
    <div className="lmodal" role="dialog" aria-modal="true" onClick={onClose}>
      <div className="lmodal__body" onClick={(e) => e.stopPropagation()}>
        <button type="button" className="lmodal__close" onClick={onClose} aria-label={t('common.close')}>
          <X size={20} />
        </button>
        <LoginCard onSuccess={onSuccess ?? onClose} />
      </div>
    </div>
  );
}
