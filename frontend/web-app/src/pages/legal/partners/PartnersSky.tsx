const LETTERS = [
  { ch: 's', x: '3%', y: '5%', size: 110, dur: 19, delay: 0, rot: -10, tone: 'ink' },
  { ch: 'i', x: '90%', y: '9%', size: 72, dur: 22, delay: 1.4, rot: 8, tone: 'wasp' },
  { ch: 'z', x: '86%', y: '28%', size: 96, dur: 17, delay: 0.6, rot: 12, tone: 'ink' },
  { ch: 'b', x: '4%', y: '48%', size: 88, dur: 24, delay: 2.1, rot: -6, tone: 'wasp' },
  { ch: 'i', x: '88%', y: '58%', size: 64, dur: 20, delay: 3.2, rot: 7, tone: 'ink' },
  { ch: 'z', x: '8%', y: '82%', size: 104, dur: 21, delay: 1.1, rot: -8, tone: 'ink' },
] as const;

const LOGOS = [
  { x: '7%', y: '24%', size: 52, dur: 18, delay: 0.8, rot: -14 },
  { x: '89%', y: '78%', size: 44, dur: 23, delay: 2.6, rot: 11 },
] as const;

/** Жёлто-чёрный купон из favicon — декоративный, без ссылки. */
function TicketMark() {
  return (
    <svg viewBox="0 0 64 64" width="100%" height="100%" aria-hidden>
      <rect x="5" y="16" width="26" height="32" rx="7" fill="#ffd23c" />
      <rect x="34" y="16" width="25" height="32" rx="7" fill="#141414" />
      <circle cx="33" cy="32" r="6" fill="#ffd23c" />
      <circle cx="5" cy="32" r="3" fill="#ffffff" />
      <circle cx="59" cy="32" r="3" fill="#ffffff" />
    </svg>
  );
}

/**
 * Тихий фон лендинга: буквы sizbiz и логотип-купон вместо пятен «пыли».
 * Не перехватывает клики, скрыт от скринридеров.
 */
export default function PartnersSky() {
  return (
    <div className="partners-sky" data-testid="partners-sky" aria-hidden="true">
      {LETTERS.map((mark, i) => (
        <span
          key={`l-${mark.ch}-${i}`}
          className={`partners-sky__item partners-sky__item--${mark.tone}`}
          style={{
            left: mark.x,
            top: mark.y,
            fontSize: mark.size,
            ['--dur' as string]: `${mark.dur}s`,
            ['--delay' as string]: `${mark.delay}s`,
            ['--rot' as string]: `${mark.rot}deg`,
          }}
        >
          <span className="partners-sky__float">{mark.ch}</span>
        </span>
      ))}
      {LOGOS.map((mark, i) => (
        <span
          key={`logo-${i}`}
          className="partners-sky__item partners-sky__item--logo"
          style={{
            left: mark.x,
            top: mark.y,
            width: mark.size,
            height: mark.size,
            ['--dur' as string]: `${mark.dur}s`,
            ['--delay' as string]: `${mark.delay}s`,
            ['--rot' as string]: `${mark.rot}deg`,
          }}
        >
          <span className="partners-sky__float">
            <TicketMark />
          </span>
        </span>
      ))}
    </div>
  );
}
