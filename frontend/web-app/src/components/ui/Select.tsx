import { useState, useRef, useEffect, type ReactNode } from 'react';
import { Check, ChevronDown } from 'lucide-react';
import './Select.css';

export interface SelectOption {
  id: string | number;
  label: string;
  icon?: ReactNode;
}

interface SelectProps {
  options: SelectOption[];
  value: string | number;
  onChange: (value: string | number) => void;
  triggerIcon?: ReactNode;
  headerTitle?: string;
  minWidth?: string;
  align?: 'left' | 'right';
  className?: string;
}

export default function Select({
  options,
  value,
  onChange,
  triggerIcon,
  headerTitle,
  minWidth = '160px',
  align = 'left',
  className = '',
}: SelectProps) {
  const [open, setOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const selectedOption = options.find((o) => o.id === value) || options[0];

  return (
    <div className={`select-wrapper ${className}`} ref={dropdownRef}>
      <button
        style={{ minWidth }}
        className="select-trigger"
        onClick={() => setOpen(!open)}
        aria-expanded={open}
        aria-haspopup="listbox"
      >
        <span className="select-trigger-left">
          {triggerIcon}
          <span>{selectedOption?.label}</span>
        </span>
        <ChevronDown
          size={14}
          className={`select-chevron ${open ? 'select-chevron--open' : ''}`}
        />
      </button>

      {open && (
        <div
          className={`select-dropdown ${align === 'right' ? 'select-dropdown--right' : ''}`}
          style={{ minWidth }}
          role="listbox"
        >
          {headerTitle && <div className="select-header">{headerTitle}</div>}
          {options.map((option) => (
            <button
              key={option.id}
              className={`select-option ${
                value === option.id ? 'select-option--active' : ''
              }`}
              onClick={() => {
                onChange(option.id);
                setOpen(false);
              }}
              role="option"
              aria-selected={value === option.id}
            >
              {option.icon}
              <span>{option.label}</span>
              {value === option.id && <Check size={16} className="select-check" />}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
