import React, { useRef } from 'react';
import { RotateCcw, Code } from 'lucide-react';

interface CodeAnswerInputProps {
  language?: string;
  sourceCode: string;
  templateCode?: string;
  onChange: (code: string) => void;
  disabled?: boolean;
}

export const CodeAnswerInput: React.FC<CodeAnswerInputProps> = ({
  language = 'Java',
  sourceCode,
  templateCode = '',
  onChange,
  disabled = false,
}) => {
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Tab') {
      e.preventDefault();
      const textarea = textareaRef.current;
      if (!textarea) return;

      const start = textarea.selectionStart;
      const end = textarea.selectionEnd;
      const value = textarea.value;

      const newValue = value.substring(0, start) + '    ' + value.substring(end);
      onChange(newValue);

      setTimeout(() => {
        if (textareaRef.current) {
          textareaRef.current.selectionStart = textareaRef.current.selectionEnd = start + 4;
        }
      }, 0);
    }
  };

  const handleReset = () => {
    if (window.confirm('Reset code to starter template? Your current code will be overwritten.')) {
      onChange(templateCode);
    }
  };

  return (
    <div className="rounded-2xl border border-slate-300 bg-slate-900 text-slate-100 overflow-hidden shadow-xs">
      <div className="px-4 py-2.5 bg-slate-800/80 border-b border-slate-700/80 flex items-center justify-between text-xs">
        <div className="flex items-center gap-2">
          <Code size={14} className="text-blue-400" />
          <span className="font-semibold text-slate-300">Language:</span>
          <span className="px-2 py-0.5 rounded bg-slate-700 font-mono text-[11px] text-white font-bold">
            {language}
          </span>
        </div>

        {!disabled && templateCode && (
          <button
            type="button"
            onClick={handleReset}
            className="inline-flex items-center gap-1 text-[11px] text-slate-400 hover:text-slate-200 transition-colors"
            title="Reset code to starter template"
          >
            <RotateCcw size={12} /> Reset to template
          </button>
        )}
      </div>

      <div className="relative">
        <textarea
          ref={textareaRef}
          value={sourceCode}
          onChange={(e) => onChange(e.target.value)}
          onKeyDown={handleKeyDown}
          disabled={disabled}
          rows={16}
          spellCheck={false}
          className="w-full p-4 font-mono text-xs leading-relaxed bg-slate-950 text-slate-100 focus:outline-hidden resize-y border-none"
          placeholder="// Write your code solution here..."
          aria-label="Code solution editor"
        />
      </div>
    </div>
  );
};
