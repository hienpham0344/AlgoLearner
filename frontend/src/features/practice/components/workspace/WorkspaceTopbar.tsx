import React from 'react';
import { ArrowLeft, CheckCircle2, Play, Send } from 'lucide-react';
import { Link } from 'react-router-dom';
import type { WorkspaceMode } from '../../utils/practiceStorage';
import { WorkspaceLayoutSelector } from './WorkspaceLayoutSelector';

interface WorkspaceTopbarProps {
  backHref: string;
  backLabel: string;
  contextLabel: string;
  problemTitle: string | null;
  layoutMode: WorkspaceMode;
  onLayoutModeChange: (mode: WorkspaceMode) => void;
  isCompleted: boolean;
  isDraftModified: boolean;
  hasProblem: boolean;
  canRun: boolean;
  canSubmit: boolean;
  isRunning: boolean;
  isSubmitting: boolean;
  onRun: () => void;
  onSubmit: () => void;
}

export const WorkspaceTopbar: React.FC<WorkspaceTopbarProps> = ({
  backHref,
  backLabel,
  contextLabel,
  problemTitle,
  layoutMode,
  onLayoutModeChange,
  isCompleted,
  isDraftModified,
  hasProblem,
  canRun,
  canSubmit,
  isRunning,
  isSubmitting,
  onRun,
  onSubmit,
}) => {
  const tone = layoutMode === 'focus' ? 'dark' : 'light';

  return (
    <header className="practice-ws-topbar" data-tone={tone}>
      <div className="practice-ws-topbar-left">
        <span className="practice-ws-brand">
          AlgoLearn <span aria-hidden="true">/</span>{' '}
          <span className="practice-ws-brand-muted">Practice</span>
        </span>

        <Link to={backHref} className="practice-ws-back">
          <ArrowLeft size={14} aria-hidden="true" />
          <span>{backLabel}</span>
        </Link>

        {layoutMode === 'focus' && problemTitle ? (
          <div className="practice-ws-crumb">
            <h1>{problemTitle}</h1>
            {isCompleted ? (
              <span className="practice-ws-status-tag" data-completed="true">
                <CheckCircle2 size={12} aria-hidden="true" />
                <span>Completed</span>
              </span>
            ) : isDraftModified ? (
              <span className="practice-ws-status-tag">In progress</span>
            ) : null}
          </div>
        ) : (
          <span className="practice-ws-context-crumb">{contextLabel}</span>
        )}
      </div>

      {hasProblem && (
        <div className="practice-ws-topbar-right">
          <div className="practice-ws-layout-desktop">
            <WorkspaceLayoutSelector
              value={layoutMode}
              onChange={onLayoutModeChange}
              tone={tone}
            />
          </div>

          <div className="practice-ws-topbar-actions-desktop">
            <button
              type="button"
              className="practice-ws-run-btn"
              disabled={!canRun}
              onClick={onRun}
            >
              <Play size={13} aria-hidden="true" />
              <span>{isRunning ? 'Running…' : 'Run code'}</span>
              <kbd className="practice-ws-kbd">Ctrl+Enter</kbd>
            </button>

            <button
              type="button"
              className="practice-ws-submit-btn"
              disabled={!canSubmit}
              onClick={onSubmit}
            >
              <Send size={13} aria-hidden="true" />
              <span>{isSubmitting ? 'Submitting…' : 'Submit solution'}</span>
            </button>
          </div>
        </div>
      )}
    </header>
  );
};

