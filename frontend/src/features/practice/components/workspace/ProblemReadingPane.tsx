import React, { useEffect, useRef, useState } from 'react';
import { ArrowRight, Check, CheckCircle2, Copy } from 'lucide-react';
import type { PracticeLanguage, PracticeProblemType, PracticeScope } from '../../types';
import { formatLanguageLabel } from '../../utils/practiceFormatters';
import type { ConsoleSampleCase } from '../execution/ExecutionConsole';
import { PracticeMarkdown } from '../shared/PracticeMarkdown';
import { SubmissionHistoryPanel } from '../submissions/SubmissionHistoryPanel';

export type ReadingTab = 'description' | 'history';

interface ProblemReadingPaneProps {
  scope: PracticeScope;
  classroomId: string;
  problemId: string;
  title: string;
  titleHeadingLevel?: 1 | 2;
  problemType: PracticeProblemType;
  timeLimitMs: number;
  memoryLimitMb: number | null;
  allowedLanguages: PracticeLanguage[];
  statement: string;
  samples: ConsoleSampleCase[];
  isCompleted: boolean;
  isDraftModified: boolean;
  activeTab: ReadingTab;
  onTabChange: (tab: ReadingTab) => void;
  selectedSubmissionId: string | null;
  onSelectSubmission: (id: string | null) => void;
  onRestoreCode: (language: PracticeLanguage, code: string) => void;
  onUseCustomInput: (input: string) => void;
  onOpenFolio: () => void;
  folioTriggerRef: React.RefObject<HTMLButtonElement | null>;
}

export const ProblemReadingPane: React.FC<ProblemReadingPaneProps> = ({
  scope,
  classroomId,
  problemId,
  title,
  titleHeadingLevel = 1,
  problemType,
  timeLimitMs,
  memoryLimitMb,
  allowedLanguages,
  statement,
  samples,
  isCompleted,
  isDraftModified,
  activeTab,
  onTabChange,
  selectedSubmissionId,
  onSelectSubmission,
  onRestoreCode,
  onUseCustomInput,
  onOpenFolio,
  folioTriggerRef,
}) => {
  const scrollRef = useRef<HTMLDivElement | null>(null);
  const [copiedId, setCopiedId] = useState<string | null>(null);

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: 0 });
  }, [activeTab, problemId]);

  const copySampleInput = async (sampleId: string, text: string) => {
    try {
      await navigator.clipboard.writeText(text);
      setCopiedId(sampleId);
      window.setTimeout(() => setCopiedId(prev => (prev === sampleId ? null : prev)), 1500);
    } catch {
      // Ignore clipboard restrictions
    }
  };

  const HeadingTag = titleHeadingLevel === 1 ? 'h1' : 'h2';
  const isFunctionStyle = problemType === 'FUNCTION_METHOD';

  return (
    <>
      <div className="practice-panel-tabs">
        <div className="practice-tab-list" role="tablist" aria-label="Problem tabs">
          <button
            type="button"
            role="tab"
            className="practice-tab-btn"
            aria-selected={activeTab === 'description'}
            onClick={() => {
              onTabChange('description');
              onSelectSubmission(null);
            }}
          >
            Problem
          </button>
          <button
            type="button"
            role="tab"
            className="practice-tab-btn"
            aria-selected={activeTab === 'history'}
            onClick={() => onTabChange('history')}
          >
            Submissions
          </button>
        </div>
      </div>

      {activeTab === 'history' ? (
        <SubmissionHistoryPanel
          scope={scope}
          classroomId={classroomId}
          problemId={problemId}
          selectedSubmissionId={selectedSubmissionId}
          onSelectSubmission={onSelectSubmission}
          hasUnsavedDraftChanges={isDraftModified}
          onRestoreCode={onRestoreCode}
        />
      ) : (
        <div ref={scrollRef} className="practice-statement-scroll light-scroll">
          <div className="practice-editorial-header">
            <div className="practice-editorial-status-row">
              {isCompleted ? (
                <span className="practice-editorial-status" data-state="completed">
                  <CheckCircle2 size={13} aria-hidden="true" />
                  <span>Completed</span>
                </span>
              ) : isDraftModified ? (
                <span className="practice-editorial-status" data-state="progress">
                  <span aria-hidden="true">◐</span>
                  <span>In progress</span>
                </span>
              ) : (
                <span className="practice-editorial-status" data-state="unsolved">
                  <span aria-hidden="true">○</span>
                  <span>Practice</span>
                </span>
              )}

              <button
                ref={folioTriggerRef}
                type="button"
                className="practice-editorial-folio-link"
                onClick={onOpenFolio}
              >
                <span>Read full statement</span>
                <ArrowRight size={13} aria-hidden="true" />
              </button>
            </div>

            <HeadingTag className="practice-problem-title">{title}</HeadingTag>

            <dl className="practice-meta-strip">
              <div>
                <dt>Format</dt>
                <dd>{isFunctionStyle ? 'Function / Method' : 'Standard I/O'}</dd>
              </div>
              <div>
                <dt>Time limit</dt>
                <dd>{timeLimitMs} ms</dd>
              </div>
              {memoryLimitMb !== null && (
                <div>
                  <dt>Memory</dt>
                  <dd>{memoryLimitMb} MB</dd>
                </div>
              )}
            </dl>
          </div>

          <div className="practice-prose">
            <PracticeMarkdown content={statement} />
          </div>

          {samples.length > 0 && (
            <section className="practice-samples-section" aria-label="Sample cases">
              <div className="practice-samples-heading-row">
                <h2>{samples.length === 1 ? 'Example 1' : 'Sample cases'}</h2>
                <span className="practice-samples-secondary-meta">
                  {allowedLanguages.map(formatLanguageLabel).join(' · ')}
                  {scope === 'classroom' ? ' · Unlimited attempts' : ''}
                </span>
              </div>

              {samples.map((sample, index) => (
                <div key={sample.id} className="practice-sample-surface">
                  <div className="practice-sample-surface-head">
                    <span>Example {index + 1}</span>
                    <div className="practice-sample-surface-actions">
                      <button
                        type="button"
                        className="practice-sample-action-btn"
                        aria-label={`Copy Example ${index + 1} input`}
                        onClick={() => void copySampleInput(sample.id, sample.input)}
                      >
                        {copiedId === sample.id ? (
                          <Check size={12} aria-hidden="true" />
                        ) : (
                          <Copy size={12} aria-hidden="true" />
                        )}
                        <span>{copiedId === sample.id ? 'Copied' : 'Copy'}</span>
                      </button>

                      {scope === 'classroom' && (
                        <button
                          type="button"
                          className="practice-sample-action-btn"
                          aria-label={`Use test case for example ${index + 1}`}
                          onClick={() => onUseCustomInput(sample.input)}
                        >
                          Use test case
                        </button>
                      )}
                    </div>
                  </div>

                  <div className="practice-sample-grid">
                    <div className="practice-sample-cell">
                      <span>{isFunctionStyle ? 'Arguments / Input' : 'Input'}</span>
                      <pre className="light-scroll">{sample.input || '(empty)'}</pre>
                    </div>
                    <div className="practice-sample-cell">
                      <span>{isFunctionStyle ? 'Expected return' : 'Expected output'}</span>
                      <pre className="light-scroll">{sample.expectedOutput || '(empty)'}</pre>
                    </div>
                  </div>
                </div>
              ))}
            </section>
          )}
        </div>
      )}
    </>
  );
};

