import { Share2, Link2, Check } from 'lucide-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import './ShareButton.css';

interface ShareButtonProps {
  title: string;
  text?: string;
  url?: string;
  variant?: 'icon' | 'button';
}

export default function ShareButton({
  title,
  text = '',
  url,
  variant = 'icon',
}: ShareButtonProps) {
  const { t } = useTranslation();
  const [copied, setCopied] = useState(false);

  const shareUrl = url || window.location.href;

  const handleShare = async () => {
    if (navigator.share) {
      try {
        await navigator.share({ title, text, url: shareUrl });
      } catch {
        // User cancelled
      }
    } else {
      await navigator.clipboard.writeText(shareUrl);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  if (variant === 'icon') {
    return (
      <button
        className="share-btn share-btn--icon"
        onClick={handleShare}
        aria-label={t('share.label')}
        title={t('share.label')}
      >
        {copied ? <Check size={18} /> : <Share2 size={18} />}
      </button>
    );
  }

  return (
    <button className="share-btn share-btn--full" onClick={handleShare}>
      {copied ? (
        <>
          <Check size={16} />
          <span>{t('share.copied')}</span>
        </>
      ) : (
        <>
          <Link2 size={16} />
          <span>{t('share.label')}</span>
        </>
      )}
    </button>
  );
}
