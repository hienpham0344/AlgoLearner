import React, { useEffect, useState } from 'react';
import {
  ArrowRight,
  Check,
  CheckCircle2,
  ChevronDown,
  ChevronUp,
  Copy,
  CornerDownRight,
  Play,
  RotateCcw,
} from 'lucide-react';
import { Link } from 'react-router-dom';
import type { ClassifiedPracticeError } from '../../api/practiceApi';
import type { PracticeLanguage, PracticeVerdict, RunResult } from '../../types';
import {
  formatLanguageLabel,
  formatMemoryUsage,
  getVerdictMeta,
  parseDiagnosticLine,
  validateCustomInputBytes,
} from '../../utils/practiceFormatters';
import type { ConsolePanelState } from '../../utils/practiceStorage';

export type ConsoleTab = 'testcases' | 'result';

export interface ConsoleSampleCase {
  id: string;
  input: string;
  expectedOutput: string;
}

export interface ConsoleSubmissionOutcome {
  submissionId: string | null;
  verdict: PracticeVerdict;
  language: PracticeLanguage;
  testsPassed: number;
  totalTests: number;
  runtimeMs: number | null;
  memoryKb: number | null;
  testcaseVersion: number | null;
  submittedAt: string;
}

interface ExecutionConsoleProps {
  activeTab: ConsoleTab;
  onTabChange: (tab: ConsoleTab) => void;
  consoleState: ConsolePanelState;
  onConsoleStateChange: (state: ConsolePanelState) => void;
  language: PracticeLanguage;
  onJumpToLine?: (line: number) => void;
  samples: ConsoleSampleCase[];
  supportsCustomInput: boolean;
  customInput: string;
  onCustomInputChange: (value: string) => void;
  isRunning: boolean;
  isSubmitting: boolean;
  runResult: RunResult | null;
  submissionOutcome: ConsoleSubmissionOutcome | null;
  executionError: ClassifiedPracticeError | null;
  isResultStale: boolean;
  canExecute: boolean;
  backHref: string;
  onRun: () => void;
  onRetryLastAction: (() => void) | null;
  onViewSubmission: (submissionId: string) => void;
  onFocusEditor: () => void;
}

export const ExecutionConsole: React.FC<ExecutionConsoleProps> = ({
  activeTab,
  onTabChange,
  consoleState,
  onConsoleStateChange,
  language,
  onJumpToLine,
  samples,
  supportsCustomInput,
  customInput,
  onCustomInputChange,
  isRunning,
  isSubmitting,
  runResult,
  submissionOutcome,
  executionError,
  isResultStale,
  canExecute,
  backHref,
  onRun,
  onRetryLastAction,
  onViewSubmission,
  onFocusEditor,
}) => {
  const [selectedSampleIndex, setSelectedSampleIndex] = useState(0);
  const [selectedRunCaseIndex, setSelectedRunCaseIndex] = useState(0);
  const [showCompleteStderr, setShowCompleteStderr] = useState(false);
  const [copiedStderr, setCopiedStderr] = useState(false);

  useEffect(() => {
    setSelectedSampleIndex(0);
  }, [samples.length]);

  useEffect(() => {
    setShowCompleteStderr(false);
    if (!runResult?.cases.length) {
      setSelectedRunCaseIndex(0);
      return;
    }
    const firstFailed = runResult.cases.findIndex(item => item.verdict !== 'ACCEPTED');
    setSelectedRunCaseIndex(firstFailed >= 0 ? firstFailed : 0);
  }, [runResult]);

  const customInputError = customInput ? validateCustomInputBytes(customInput) : null;
  const customInputBytes = customInput ? new TextEncoder().encode(customInput).length : 0;
  const activeSample = samples[selectedSampleIndex] ?? samples[0] ?? null;
  const activeRunCase = runResult?.cases[selectedRunCaseIndex] ?? runResult?.cases[0] ?? null;
  const isBusy = isRunning || isSubmitting;

  const diagnosticLine =
    activeRunCase?.stderr ? parseDiagnosticLine(activeRunCase.stderr, language) : null;
  const stderrLines = activeRunCase?.stderr ? activeRunCase.stderr.split('\n') : [];
  const hasLongStderr = stderrLines.length > 8;
  const displayedStderr =
    hasLongStderr && !showCompleteStderr
      ? `${stderrLines.slice(0, 8).join('\n')}\n…`
      : activeRunCase?.stderr ?? '';

  const copyDiagnostic = async (text: string) => {
    try {
      await navigator.clipboard.writeText(text);
      setCopiedStderr(true);
      window.setTimeout(() => setCopiedStderr(false), 1500);
    } catch {
      // Ignore clipboard restrictions
    }
  };

  const compactSummary = (() => {
    if (isBusy) return isSubmitting ? 'Judging official submission…' : 'Running trial execution…';
    if (executionError) return executionError.heading;
    if (submissionOutcome) {
      const m = getVerdictMeta(submissionOutcome.verdict);
      return `${m.shortSymbol} ${m.label} · ${submissionOutcome.testsPassed}/${submissionOutcome.totalTests} tests`;
    }
    if (runResult) {
      const m = getVerdictMeta(runResult.verdict);
      const passed = runResult.cases.filter(c => c.verdict === 'ACCEPTED').length;
      return `${m.shortSymbol} ${m.label} · ${passed}/${runResult.cases.length} cases`;
    }
    return 'Ready to run';
  })();

  return (
    <section
      className="practice-console-region"
      data-console-state={consoleState}
      aria-label="Execution console"
    >
      <div className="practice-panel-tabs">
        <div className="practice-tab-list" role="tablist" aria-label="Console views">
          <button
            type="button"
            role="tab"
            className="practice-tab-btn"
            aria-selected={activeTab === 'testcases'}
            onClick={() => {
              onTabChange('testcases');
              if (consoleState === 'collapsed') onConsoleStateChange('compact');
            }}
          >
            Test cases {samples.length > 0 ? `(${samples.length})` : ''}
          </button>
          <button
            type="button"
            role="tab"
            className="practice-tab-btn"
            aria-selected={activeTab === 'result'}
            onClick={() => {
              onTabChange('result');
              if (consoleState === 'collapsed') onConsoleStateChange('expanded');
            }}
          >
            Execution result
          </button>
        </div>

        <div className="practice-console-controls">
          {supportsCustomInput && customInput.trim().length > 0 && (
            <span className="practice-console-badge">Custom stdin</span>
          )}
          {consoleState === 'collapsed' && (
            <span className="practice-console-mini-status">{compactSummary}</span>
          )}
          {consoleState !== 'expanded' ? (
            <button
              type="button"
              className="practice-console-toggle"
              aria-label="Expand console"
              onClick={() => onConsoleStateChange('expanded')}
            >
              <span>Expand</span>
              <ChevronUp size={14} aria-hidden="true" />
            </button>
          ) : (
            <button
              type="button"
              className="practice-console-toggle"
              aria-label="Collapse console"
              onClick={() => onConsoleStateChange('collapsed')}
            >
              <span>Collapse</span>
              <ChevronDown size={14} aria-hidden="true" />
            </button>
          )}
        </div>
      </div>

      {isBusy && <div className="practice-progress-indeterminate" aria-hidden="true" />}

      {consoleState === 'compact' && (
        <div className="practice-console-compact-bar">
          <div className="practice-console-compact-left">
            {samples.map((sample, idx) => (
              <button
                key={sample.id}
                type="button"
                className="practice-case-chip"
                data-selected={selectedSampleIndex === idx}
                onClick={() => {
                  setSelectedSampleIndex(idx);
                  onTabChange('testcases');
                  onConsoleStateChange('expanded');
                }}
              >
                Example {idx + 1}
              </button>
            ))}
            {supportsCustomInput && (
              <button
                type="button"
                className="practice-case-chip"
                onClick={() => {
                  onTabChange('testcases');
                  onConsoleStateChange('expanded');
                  window.requestAnimationFrame(() => {
                    document.getElementById('practice-custom-stdin')?.focus();
                  });
                }}
              >
                Custom input
              </button>
            )}
          </div>

          <div className="practice-console-compact-right">
            <span className="practice-console-mini-status">{compactSummary}</span>
            {(runResult || submissionOutcome || executionError) && (
              <button
                type="button"
                className="practice-clear-btn"
                onClick={() => {
                  onTabChange('result');
                  onConsoleStateChange('expanded');
                }}
              >
                Inspect result
              </button>
            )}
          </div>
        </div>
      )}

      {consoleState === 'expanded' && (
        <div className="practice-console-body light-scroll" aria-live="polite">
          {activeTab === 'testcases' ? (
            <div>
              {samples.length > 0 ? (
                <>
                  <div className="practice-case-strip" role="tablist" aria-label="Sample case selector">
                    {samples.map((sample, idx) => (
                      <button
                        key={sample.id}
                        type="button"
                        role="tab"
                        aria-selected={selectedSampleIndex === idx}
                        data-selected={selectedSampleIndex === idx}
                        className="practice-case-pill"
                        onClick={() => setSelectedSampleIndex(idx)}
                      >
                        Example {idx + 1}
                      </button>
                    ))}
                  </div>

                  {activeSample && (
                    <div className="practice-io-compare">
                      <div className="practice-io-box">
                        <span>Input</span>
                        <pre className="light-scroll">{activeSample.input || '(empty)'}</pre>
                      </div>
                      <div className="practice-io-box">
                        <span>Expected output</span>
                        <pre className="light-scroll">{activeSample.expectedOutput || '(empty)'}</pre>
                      </div>
                    </div>
                  )}
                </>
              ) : (
                <p className="text-xs text-slate-600 m-0">
                  {supportsCustomInput
                    ? 'No sample cases attached. Use custom input below to run a check.'
                    : 'No public sample cases are available for a trial run.'}
                </p>
              )}

              {supportsCustomInput && (
                <div className="practice-custom-input-card">
                  <div className="flex items-center justify-between gap-2 flex-wrap">
                    <label htmlFor="practice-custom-stdin" className="text-xs font-semibold text-slate-800">
                      Custom input <span className="font-normal text-slate-500">(optional · used by Run Code only)</span>
                    </label>
                    <div className="flex items-center gap-3">
                      <span className="practice-mono text-[11px] text-slate-500">
                        {(customInputBytes / 1024).toFixed(1)} / 64.0 KB
                      </span>
                      {customInput && (
                        <button
                          type="button"
                          className="practice-clear-btn"
                          onClick={() => onCustomInputChange('')}
                        >
                          Clear
                        </button>
                      )}
                    </div>
                  </div>

                  <textarea
                    id="practice-custom-stdin"
                    className="practice-custom-textarea light-scroll"
                    value={customInput}
                    onChange={event => onCustomInputChange(event.target.value)}
                    placeholder="Leave empty to execute against official sample test cases…"
                    rows={3}
                  />

                  {customInputError && (
                    <p role="alert" className="text-xs text-rose-800 font-medium mt-1.5 mb-0">
                      {customInputError}
                    </p>
                  )}
                </div>
              )}
            </div>
          ) : (
            <div>
              {isBusy ? (
                <div role="status" className="py-3 space-y-1.5">
                  <p className="text-sm font-semibold text-slate-900 m-0">
                    {isSubmitting
                      ? 'Judging your submission across official test cases…'
                      : 'Running your code in the isolated sandbox…'}
                  </p>
                  <p className="text-xs text-slate-600 m-0">
                    {isSubmitting
                      ? 'Evaluating time and memory boundaries. Hidden test case inputs remain private.'
                      : supportsCustomInput && customInput.trim()
                        ? 'Executing against your custom standard input.'
                        : 'Executing against sample test cases without creating a submission record.'}
                  </p>
                </div>
              ) : executionError ? (
                <div role="alert" className="practice-alert-box" data-kind={executionError.kind}>
                  <div className="flex items-start justify-between gap-3 flex-wrap">
                    <div className="space-y-1 max-w-xl">
                      <span className="practice-kicker" style={{ marginBottom: 0 }}>
                        {executionError.kind === 'infrastructure'
                          ? '⚠ SANDBOX INFRASTRUCTURE NOTICE'
                          : executionError.kind === 'rate_limit'
                            ? 'RATE LIMIT'
                            : 'EXECUTION NOTICE'}
                      </span>
                      <h3 className="text-sm font-bold text-slate-900 m-0">
                        {executionError.heading}
                      </h3>
                      <p className="text-xs text-slate-700 leading-relaxed m-0">
                        {executionError.message}
                      </p>
                    </div>

                    {onRetryLastAction && (
                      <button
                        type="button"
                        className="practice-btn-secondary"
                        onClick={onRetryLastAction}
                      >
                        <RotateCcw size={13} aria-hidden="true" />
                        <span>Retry</span>
                      </button>
                    )}
                  </div>
                </div>
              ) : submissionOutcome ? (
                <div>
                  {isResultStale && (
                    <div className="practice-stale-banner">
                      <span>Editor modified after this submission — run or submit again to evaluate new changes.</span>
                    </div>
                  )}

                  {(() => {
                    const meta = getVerdictMeta(submissionOutcome.verdict);
                    return (
                      <div className="practice-submission-card" data-tone={meta.tone}>
                        <div className="flex items-start justify-between gap-3 flex-wrap">
                          <div>
                            <span className="practice-kicker" style={{ marginBottom: 4 }}>
                              OFFICIAL SUBMISSION SAVED
                            </span>
                            <h3 className="text-lg font-bold text-slate-900 m-0 flex items-center gap-2">
                              <span aria-hidden="true">{meta.shortSymbol}</span>
                              <span>{meta.label}</span>
                            </h3>
                            <p className="text-xs text-slate-600 mt-1 mb-0">
                              {meta.summary}
                            </p>
                          </div>

                          {submissionOutcome.verdict === 'ACCEPTED' && (
                            <span className="practice-status-pill" data-status="COMPLETED">
                              <CheckCircle2 size={12} aria-hidden="true" />
                              <span>Practice completed</span>
                            </span>
                          )}
                        </div>

                        <div className="practice-metrics-grid">
                          <div className="practice-metric-item">
                            <span>Tests passed</span>
                            <strong>
                              {submissionOutcome.testsPassed} / {submissionOutcome.totalTests}
                            </strong>
                          </div>
                          {submissionOutcome.runtimeMs !== null && (
                            <div className="practice-metric-item">
                              <span>Runtime</span>
                              <strong>{submissionOutcome.runtimeMs} ms</strong>
                            </div>
                          )}
                          {submissionOutcome.memoryKb !== null && (
                            <div className="practice-metric-item">
                              <span>Memory</span>
                              <strong>{formatMemoryUsage(submissionOutcome.memoryKb)}</strong>
                            </div>
                          )}
                          <div className="practice-metric-item">
                            <span>Language</span>
                            <strong>{formatLanguageLabel(submissionOutcome.language)}</strong>
                          </div>
                          {submissionOutcome.testcaseVersion !== null && (
                            <div className="practice-metric-item">
                              <span>Test suite</span>
                              <strong>v{submissionOutcome.testcaseVersion}</strong>
                            </div>
                          )}
                        </div>

                        <div className="flex items-center gap-2 flex-wrap">
                          {submissionOutcome.submissionId && (
                            <button
                              type="button"
                              className="practice-btn-primary"
                              onClick={() => onViewSubmission(submissionOutcome.submissionId!)}
                            >
                              <span>View submission</span>
                              <ArrowRight size={14} aria-hidden="true" />
                            </button>
                          )}
                          <button
                            type="button"
                            className="practice-btn-secondary"
                            onClick={onFocusEditor}
                          >
                            Continue editing
                          </button>
                          <Link to={backHref} className="practice-btn-secondary">
                            Back to problems
                          </Link>
                        </div>
                      </div>
                    );
                  })()}
                </div>
              ) : runResult ? (
                <div>
                  {isResultStale && (
                    <div className="practice-stale-banner">
                      <span>Code or custom input changed since this trial run.</span>
                      {canExecute && (
                        <button type="button" className="practice-clear-btn" onClick={onRun}>
                          Re-run
                        </button>
                      )}
                    </div>
                  )}

                  {(() => {
                    const overallMeta = getVerdictMeta(runResult.verdict);
                    const passedCount = runResult.cases.filter(c => c.verdict === 'ACCEPTED').length;
                    return (
                      <div className="practice-warp-head">
                        <div className="flex items-center gap-2.5 flex-wrap">
                          <span className="practice-warp-verdict" data-tone={overallMeta.tone}>
                            {overallMeta.shortSymbol} {overallMeta.label}
                          </span>
                          <span className="text-xs text-slate-600">
                            Trial run · no official submission created
                          </span>
                        </div>
                        <span className="practice-mono text-xs text-slate-500">
                          {passedCount}/{runResult.cases.length} cases matched
                        </span>
                      </div>
                    );
                  })()}

                  {runResult.cases.length > 0 && (
                    <>
                      <div className="practice-case-strip" role="tablist" aria-label="Trial run cases">
                        {runResult.cases.map((item, idx) => {
                          const caseMeta = getVerdictMeta(item.verdict);
                          return (
                            <button
                              key={item.index}
                              type="button"
                              role="tab"
                              aria-selected={selectedRunCaseIndex === idx}
                              data-selected={selectedRunCaseIndex === idx}
                              data-verdict={item.verdict}
                              className="practice-case-pill"
                              onClick={() => setSelectedRunCaseIndex(idx)}
                            >
                              <span aria-hidden="true">{caseMeta.shortSymbol}</span>
                              <span>Case {item.index + 1}</span>
                              {item.runtimeMs !== null && (
                                <span className="opacity-75">· {item.runtimeMs} ms</span>
                              )}
                            </button>
                          );
                        })}
                      </div>

                      {activeRunCase && (
                        <div>
                          {activeRunCase.stderr && (
                            <div className="practice-stderr-block">
                              <div className="practice-stderr-head">
                                <span>
                                  Diagnostics{diagnosticLine ? ` · Line ${diagnosticLine}` : ''}
                                </span>
                                <div className="flex items-center gap-2">
                                  {diagnosticLine && onJumpToLine && (
                                    <button
                                      type="button"
                                      className="practice-stderr-action"
                                      onClick={() => onJumpToLine(diagnosticLine)}
                                    >
                                      <CornerDownRight size={12} aria-hidden="true" />
                                      <span>Jump to line {diagnosticLine}</span>
                                    </button>
                                  )}
                                  <button
                                    type="button"
                                    className="practice-stderr-action"
                                    onClick={() => void copyDiagnostic(activeRunCase.stderr)}
                                  >
                                    {copiedStderr ? (
                                      <Check size={12} aria-hidden="true" />
                                    ) : (
                                      <Copy size={12} aria-hidden="true" />
                                    )}
                                    <span>{copiedStderr ? 'Copied' : 'Copy'}</span>
                                  </button>
                                </div>
                              </div>
                              <pre className="dark-scroll">{displayedStderr}</pre>
                              {hasLongStderr && (
                                <button
                                  type="button"
                                  className="practice-stderr-expand"
                                  onClick={() => setShowCompleteStderr(prev => !prev)}
                                >
                                  {showCompleteStderr
                                    ? 'Show concise output'
                                    : `Show complete output (${stderrLines.length} lines)`}
                                </button>
                              )}
                            </div>
                          )}

                          <div className="practice-io-compare">
                            <div className="practice-io-box">
                              <span>Input</span>
                              <pre className="light-scroll">{activeRunCase.input || '(empty)'}</pre>
                            </div>
                            {activeRunCase.expectedOutput && (
                              <div className="practice-io-box">
                                <span>Expected output</span>
                                <pre className="light-scroll">{activeRunCase.expectedOutput}</pre>
                              </div>
                            )}
                            <div className="practice-io-box">
                              <span>Your output</span>
                              <pre className="light-scroll">{activeRunCase.stdout || '(empty)'}</pre>
                            </div>
                          </div>
                        </div>
                      )}
                    </>
                  )}
                </div>
              ) : (
                <div className="py-3 flex items-center justify-between gap-4 flex-wrap">
                  <div className="space-y-1">
                    <p className="text-sm font-semibold text-slate-900 m-0">
                      Ready for execution
                    </p>
                    <p className="text-xs text-slate-600 m-0">
                      Run your code (<kbd className="practice-mono">Ctrl+Enter</kbd>) to inspect sample output, or submit for official judging.
                    </p>
                  </div>
                  {canExecute && (
                    <button type="button" className="practice-btn-secondary" onClick={onRun}>
                      <Play size={13} aria-hidden="true" />
                      <span>Run sample tests</span>
                    </button>
                  )}
                </div>
              )}
            </div>
          )}
        </div>
      )}
    </section>
  );
};
