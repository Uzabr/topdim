import { Link } from 'react-router-dom';
import { useLocalePath } from '../../hooks/useLocalePath';
import './Logo.css';

interface LogoProps {
  /** sm — в шапке, md — в футере и на входе */
  size?: 'sm' | 'md';
  className?: string;
}

/** Логотип: строчное «sizbiz» на жёлтой таблетке, текст чёрный. */
export default function Logo({ size = 'sm', className = '' }: LogoProps) {
  const lp = useLocalePath();

  return (
    <Link to={lp('/')} className={`logo-pill logo-pill--${size} ${className}`.trim()}>
      sizbiz
    </Link>
  );
}
