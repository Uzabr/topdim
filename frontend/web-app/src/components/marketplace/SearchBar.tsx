import { Mic, Search } from 'lucide-react';
import './SearchBar.css';

interface ISpeechRecognition {
  lang: string;
  onresult: (event: { results: { transcript: string }[][] }) => void;
  start: () => void;
}

interface WindowWithSpeech extends Window {
  SpeechRecognition?: new () => ISpeechRecognition;
  webkitSpeechRecognition?: new () => ISpeechRecognition;
}

interface SearchBarProps {
  value: string;
  onChange: (value: string) => void;
  placeholder: string;
  suggestions?: string[];
  onSuggestionSelect?: (value: string) => void;
  sticky?: boolean;
}

export default function SearchBar({
  value,
  onChange,
  placeholder,
  suggestions = [],
  onSuggestionSelect,
  sticky = false,
}: SearchBarProps) {
  const showSuggestions = value.trim().length > 0 && suggestions.length > 0;

  return (
    <div className={`search-bar ${sticky ? 'search-bar--sticky' : ''}`}>
      <div className="search-bar__shell">
        <Search size={20} />
        <input
          value={value}
          onChange={(event) => onChange(event.target.value)}
          placeholder={placeholder}
          aria-label={placeholder}
        />

        <button type="button" className="search-bar__voice" aria-label="Голосовой поиск" onClick={() => {
          const SpeechRecognition = (window as unknown as WindowWithSpeech).SpeechRecognition || (window as unknown as WindowWithSpeech).webkitSpeechRecognition;
          if (!SpeechRecognition) {
            alert("Ваш браузер не поддерживает голосовой ввод");
            return;
          }
          const recognition = new SpeechRecognition();
          recognition.lang = 'ru-RU';
          recognition.onresult = (event: { results: { transcript: string }[][] }) => {
            onChange(event.results[0][0].transcript);
          };
          recognition.start();
        }}>
          <Mic size={18} />
        </button>
      </div>

      {showSuggestions && (
        <div className="search-bar__suggestions surface-card">
          {suggestions.map((suggestion) => (
            <button
              key={suggestion}
              type="button"
              className="search-bar__suggestion"
              onClick={() => onSuggestionSelect?.(suggestion)}
            >
              <Search size={16} />
              <span>{suggestion}</span>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
