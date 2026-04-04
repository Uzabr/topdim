import { useState, useRef, useEffect, useCallback } from 'react';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import './ImageSlider.css';

interface ImageSliderProps {
  images: string[];
  alt?: string;
  fallbackText?: string;
  aspectRatio?: string;
}

export default function ImageSlider({
  images,
  alt = '',
  fallbackText = 'TopDim',
  aspectRatio = '16/10',
}: ImageSliderProps) {
  const [current, setCurrent] = useState(0);
  const [touchStart, setTouchStart] = useState(0);
  const [touchDelta, setTouchDelta] = useState(0);
  const [isDragging, setIsDragging] = useState(false);
  const trackRef = useRef<HTMLDivElement>(null);

  const slides = images.length > 0 ? images : [];
  const total = slides.length;

  const goTo = useCallback(
    (index: number) => {
      if (total === 0) return;
      setCurrent((index + total) % total);
      setTouchDelta(0);
    },
    [total]
  );

  const next = useCallback(() => goTo(current + 1), [current, goTo]);
  const prev = useCallback(() => goTo(current - 1), [current, goTo]);

  // Auto-advance
  useEffect(() => {
    if (total <= 1) return;
    const timer = setInterval(next, 5000);
    return () => clearInterval(timer);
  }, [next, total]);

  // Keyboard
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (e.key === 'ArrowLeft') prev();
      if (e.key === 'ArrowRight') next();
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [next, prev]);

  const handleTouchStart = (e: React.TouchEvent) => {
    setTouchStart(e.touches[0].clientX);
    setIsDragging(true);
  };

  const handleTouchMove = (e: React.TouchEvent) => {
    if (!isDragging) return;
    setTouchDelta(e.touches[0].clientX - touchStart);
  };

  const handleTouchEnd = () => {
    setIsDragging(false);
    if (Math.abs(touchDelta) > 60) {
      if (touchDelta > 0) prev();
      else next();
    }
    setTouchDelta(0);
  };

  if (total === 0) {
    return (
      <div className="img-slider" style={{ aspectRatio }}>
        <div className="img-slider__placeholder">
          <span>{fallbackText}</span>
        </div>
      </div>
    );
  }

  return (
    <div className="img-slider" style={{ aspectRatio }}>
      <div
        ref={trackRef}
        className="img-slider__track"
        style={{
          transform: `translateX(calc(-${current * 100}% + ${touchDelta}px))`,
          transition: isDragging ? 'none' : 'transform 0.45s cubic-bezier(0.22, 1, 0.36, 1)',
        }}
        onTouchStart={handleTouchStart}
        onTouchMove={handleTouchMove}
        onTouchEnd={handleTouchEnd}
      >
        {slides.map((src, i) => (
          <div key={i} className="img-slider__slide">
            <img
              src={src}
              alt={`${alt} ${i + 1}`}
              loading={i === 0 ? 'eager' : 'lazy'}
              draggable={false}
            />
          </div>
        ))}
      </div>

      {total > 1 && (
        <>
          <button className="img-slider__arrow img-slider__arrow--prev" onClick={prev} aria-label="Назад">
            <ChevronLeft size={22} />
          </button>
          <button className="img-slider__arrow img-slider__arrow--next" onClick={next} aria-label="Вперёд">
            <ChevronRight size={22} />
          </button>

          <div className="img-slider__dots">
            {slides.map((_, i) => (
              <button
                key={i}
                className={`img-slider__dot ${i === current ? 'img-slider__dot--active' : ''}`}
                onClick={() => goTo(i)}
                aria-label={`Slide ${i + 1}`}
              />
            ))}
          </div>
        </>
      )}

      {total > 1 && (
        <span className="img-slider__counter">
          {current + 1} / {total}
        </span>
      )}
    </div>
  );
}
