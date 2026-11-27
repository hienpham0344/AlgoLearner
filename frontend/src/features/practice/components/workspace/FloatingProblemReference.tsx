import React, { useEffect, useRef } from 'react';
import { ArrowRight, PanelLeft, X } from 'lucide-react';
import type { PracticeProblemType } from '../../types';
import type { ConsoleSampleCase } from '../execution/ExecutionConsole';
import { PracticeMarkdown } from '../shared/PracticeMarkdown';

interface FloatingProblemReferenceProps {
  open: boolean;
  title: string;
  problemType: PracticeProblemType;
  timeLimitMs: number;
  memoryLimitMb: number | null;
  statement: string;
  samples: ConsoleSampleCase[];
  onClose: () => void;
  onDockSplit: () => void;
  onOpenFolio: () => void;
}

export const FloatingProblemReference: React.FC<FloatingProblemReferenceProps> = ({
  open,
  title,
  problemType,
  timeLimitMs,
  memoryLimitMb,
  statement,
  samples,
  onClose,
  onDockSplit,
  onOpenFolio,
}) => {
  const closeBtnRef = useRef<HTMLButtonElement | null>(null);

  useEffect(() => {
    if (!open) return;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        onClose();
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [open, onClose]);

  if (!open) return null;

  const isFunctionStyle = problemType === 'FUNCTION_METHOD';
  const firstSample = samples[0] ?? null;

  return (
    <aside
      className="practice-quicklook"
      role="region"
      aria-label={`Problem reference: ${title}`}
    >
      <header className="practice-quicklook-head">
        <span>Problem reference</span>
        <div className="practice-quicklook-head-actions">
          <button
            type="button"
            className="practice-quicklook-dock"
            onClick={onDockSplit}
            title="Dock into split view"
          >
            <PanelLeft size={13} aria-hidden="true" />
            <span>Dock</span>
          </button>
          <button
            ref={closeBtnRef}
            type="button"
            className="practice-quicklook-close"
            onClick={onClose}
            aria-label="Close problem reference"
          >
            <X size={15} aria-hidden="true" />
          </button>
        </div>
      </header>

      <div className="practice-quicklook-scroll light-scroll">
        <h2>{title}</h2>
        <p className="practice-quicklook-meta">
          {isFunctionStyle ? 'Function / Method' : 'Standard I/O'} · {timeLimitMs} ms
          {memoryLimitMb !== null ? ` · ${memoryLimitMb} MB` : ''}
        </p>

        <div className="practice-prose practice-quicklook-prose">
          <PracticeMarkdown content={statement} />
        </div>

        {firstSample && (
          <div className="practice-sample-surface mt-4">
            <div className="practice-sample-surface-head">
              <span>Example 1</span>
            </div>
            <div className="practice-sample-grid">
              <div className="practice-sample-cell">
                <span>{isFunctionStyle ? 'Arguments / Input' : 'Input'}</span>
                <pre className="light-scroll">{firstSample.input || '(empty)'}</pre>
              </div>
              <div className="practice-sample-cell">
                <span>{isFunctionStyle ? 'Expected return' : 'Expected output'}</span>
                <pre className="light-scroll">{firstSample.expectedOutput || '(empty)'}</pre>
              </div>
            </div>
          </div>
        )}
      </div>

      <footer className="practice-quicklook-foot">
        <button type="button" className="practice-editorial-folio-link" onClick={onOpenFolio}>
          <span>Read full statement</span>
          <ArrowRight size={13} aria-hidden="true" />
        </button>
      </footer>
    </aside>
  );
};

