import React, { useEffect, useMemo, useRef, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { ArrowLeft, Check, Copy, RotateCcw } from 'lucide-react';
import {
  classifyPracticeError,
  practiceApi,
  practiceKeys,
  publicPracticeApi,
} from '../../api/practiceApi';
import type {
  PracticeLanguage,
  PracticeScope,
  PracticeSubmission,
  PracticeVerdict,
} from '../../types';
import {
  formatLanguageLabel,
  formatMemoryUsage,
  formatVietnamDateTime,
  formatVietnamTime,
  getVietnamDayGroup,
  getVerdictMeta,
} from '../../utils/practiceFormatters';

interface NormalizedLedgerRow {
  id: string;
  verdict: PracticeVerdict;
  language: PracticeLanguage | null;
  testsPassed: number | null;
  totalTests: number | null;
  runtimeMs: number | null;
  memoryKb: number | null;
  submittedAt: string;
}

interface SubmissionHistoryPanelProps {
  scope: PracticeScope;
  classroomId: string;
  problemId: string;
  selectedSubmissionId: string | null;
  onSelectSubmission: (submissionId: string | null) => void;
  hasUnsavedDraftChanges: boolean;
  onRestoreCode: (language: PracticeLanguage, code: string) => void;
}

export const SubmissionHistoryPanel: React.FC<SubmissionHistoryPanelProps> = ({
  scope,
  classroomId,
  problemId,
  selectedSubmissionId,
  onSelectSubmission,
  hasUnsavedDraftChanges,
  onRestoreCode,
}) => {
  const [page, setPage] = useState(0);
  const [verdictFilter, setVerdictFilter] = useState<string>('ALL');
  const [langFilter, setLangFilter] = useState<string>('ALL');
  const [confirmingRestore, setConfirmingRestore] = useState(false);
  const [copied, setCopied] = useState(false);
  const scrollContainerRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    scrollContainerRef.current?.scrollTo({ top: 0 });
  }, [selectedSubmissionId, page]);

  const classHistoryQuery = useQuery({
    queryKey: practiceKeys.classHistory(classroomId, problemId, page),
    queryFn: () => practiceApi.history(classroomId, problemId, page),
    enabled: scope === 'classroom' && Boolean(classroomId && problemId),
    retry: false,
  });

  const publicHistoryQuery = useQuery({
    queryKey: practiceKeys.publicHistory(problemId, page),
    queryFn: () => publicPracticeApi.history(problemId, page),
    enabled: scope === 'public' && Boolean(problemId),
    retry: false,
  });

  const submissionDetailQuery = useQuery({
    queryKey: practiceKeys.classSubmission(classroomId, problemId, selectedSubmissionId || ''),
    queryFn: () => practiceApi.submission(classroomId, problemId, selectedSubmissionId!),
    enabled: scope === 'classroom' && Boolean(classroomId && problemId && selectedSubmissionId),
    retry: false,
  });

  const isPending = scope === 'classroom' ? classHistoryQuery.isPending : publicHistoryQuery.isPending;
  const historyError = scope === 'classroom' ? classHistoryQuery.error : publicHistoryQuery.error;
  const pagination =
    scope === 'classroom'
      ? classHistoryQuery.data?.pagination
      : publicHistoryQuery.data?.pagination;

  const rows: NormalizedLedgerRow[] = useMemo(() => {
    if (scope === 'public') {
      return (publicHistoryQuery.data?.items ?? []).map(item => ({
        id: item.submissionId,
        verdict: item.status,
        language: null,
        testsPassed: null,
        totalTests: null,
        runtimeMs: null,
        memoryKb: null,
        submittedAt: item.submittedAt,
      }));
    }
    return (classHistoryQuery.data?.items ?? []).map((item: PracticeSubmission) => ({
      id: item.id,
      verdict: item.verdict,
      language: item.language,
      testsPassed: item.testsPassed,
      totalTests: item.totalTests,
      runtimeMs: item.runtimeMs,
      memoryKb: item.memoryKb,
      submittedAt: item.submittedAt,
    }));
  }, [scope, publicHistoryQuery.data, classHistoryQuery.data]);

  const filteredRows = useMemo(() => {
    return rows.filter(row => {
      if (verdictFilter !== 'ALL') {
        if (verdictFilter === 'COMPILATION_ERROR') {
          if (row.verdict !== 'COMPILATION_ERROR' && row.verdict !== 'COMPILE_ERROR') return false;
        } else if (row.verdict !== verdictFilter) {
          return false;
        }
      }
      if (langFilter !== 'ALL' && row.language !== langFilter) return false;
      return true;
    });
  }, [rows, verdictFilter, langFilter]);

  const isUnfilteredFirstPage =
    page === 0 && verdictFilter === 'ALL' && langFilter === 'ALL' && filteredRows.length > 0;
  const latestRow = isUnfilteredFirstPage ? filteredRows[0] : null;
  const remainingRows = isUnfilteredFirstPage ? filteredRows.slice(1) : filteredRows;

  const groupedByDay = useMemo(() => {
    const groups: Array<{ day: string; items: NormalizedLedgerRow[] }> = [];
    remainingRows.forEach(row => {
      const day = getVietnamDayGroup(row.submittedAt);
      const last = groups[groups.length - 1];
      if (last && last.day === day) {
        last.items.push(row);
      } else {
        groups.push({ day, items: [row] });
      }
    });
    return groups;
  }, [remainingRows]);

  // STU-P07: Read-only Code Review Inspector when a classroom submission is selected
  if (selectedSubmissionId && scope === 'classroom') {
    const detail = submissionDetailQuery.data;
    const detailMeta = detail ? getVerdictMeta(detail.verdict) : null;
    const codeLines = detail?.code ? detail.code.replace(/\r\n/g, '\n').split('\n') : [];

    const handleCopy = async () => {
      if (!detail?.code) return;
      try {
        await navigator.clipboard.writeText(detail.code);
        setCopied(true);
        window.setTimeout(() => setCopied(false), 1800);
      } catch {
        // Ignore clipboard restrictions
      }
    };

    const executeRestore = () => {
      if (!detail) return;
      onRestoreCode(detail.language, detail.code);
      setConfirmingRestore(false);
    };

    return (
      <div
        ref={scrollContainerRef}
        className="practice-statement-scroll light-scroll"
        aria-label="Submission detail"
      >
        <div className="flex items-center justify-between gap-2 mb-4">
          <button
            type="button"
            className="practice-btn-secondary"
            onClick={() => {
              setConfirmingRestore(false);
              onSelectSubmission(null);
            }}
          >
            <ArrowLeft size={14} aria-hidden="true" />
            <span>Close detail</span>
          </button>

          {detail && (
            <span className="practice-mono text-xs text-slate-500">
              {formatVietnamDateTime(detail.submittedAt)} (UTC+7)
            </span>
          )}
        </div>

        {submissionDetailQuery.isPending ? (
          <div role="status" className="py-8 space-y-3">
            <div className="h-5 w-48 bg-slate-200 animate-pulse rounded" />
            <div className="h-32 w-full bg-slate-200 animate-pulse rounded" />
            <span className="sr-only">Loading submission detail…</span>
          </div>
        ) : submissionDetailQuery.isError || !detail || !detailMeta ? (
          <div role="alert" className="practice-alert-box">
            <p className="text-sm font-bold text-slate-900 m-0">
              {classifyPracticeError(submissionDetailQuery.error).heading}
            </p>
            <p className="text-xs text-slate-600 mt-1 mb-2">
              {classifyPracticeError(submissionDetailQuery.error).message}
            </p>
            <button
              type="button"
              className="practice-btn-secondary"
              onClick={() => void submissionDetailQuery.refetch()}
            >
              Retry
            </button>
          </div>
        ) : (
          <div className="space-y-4">
            <div>
              <span className="practice-kicker">SUBMISSION SNAPSHOT · READ-ONLY</span>
              <h3 className="text-lg font-bold text-slate-900 m-0">
                Submitted code · {detail.language}
              </h3>
              <p className="text-xs text-slate-600 mt-1 mb-0">
                {detailMeta.shortSymbol} {detailMeta.label} · {detail.testsPassed}/{detail.totalTests} tests passed
              </p>
            </div>

            <div className="practice-metrics-grid">
              <div className="practice-metric-item">
                <span>Verdict</span>
                <strong>{detailMeta.label}</strong>
              </div>
              <div className="practice-metric-item">
                <span>Tests</span>
                <strong>
                  {detail.testsPassed}/{detail.totalTests}
                </strong>
              </div>
              <div className="practice-metric-item">
                <span>Runtime</span>
                <strong>{detail.runtimeMs !== null ? `${detail.runtimeMs} ms` : '—'}</strong>
              </div>
              <div className="practice-metric-item">
                <span>Memory</span>
                <strong>{formatMemoryUsage(detail.memoryKb)}</strong>
              </div>
              <div className="practice-metric-item">
                <span>Test suite</span>
                <strong>v{detail.testcaseVersion}</strong>
              </div>
              {detail.rejudgeStatus && (
                <div className="practice-metric-item">
                  <span>Rejudge</span>
                  <strong>{detail.rejudgeStatus}</strong>
                </div>
              )}
            </div>

            <div className="flex items-center justify-between gap-2 flex-wrap">
              <span className="practice-mono text-xs text-slate-500">
                {formatLanguageLabel(detail.language)} · {codeLines.length} lines
              </span>

              <div className="flex items-center gap-2 flex-wrap">
                <button type="button" className="practice-btn-secondary" onClick={() => void handleCopy()}>
                  {copied ? <Check size={13} aria-hidden="true" /> : <Copy size={13} aria-hidden="true" />}
                  <span>{copied ? 'Copied' : 'Copy code'}</span>
                </button>

                {confirmingRestore ? (
                  <div className="flex items-center gap-1.5">
                    <button type="button" className="practice-btn-primary" onClick={executeRestore}>
                      Replace active draft
                    </button>
                    <button
                      type="button"
                      className="practice-btn-secondary"
                      onClick={() => setConfirmingRestore(false)}
                    >
                      Cancel
                    </button>
                  </div>
                ) : (
                  <button
                    type="button"
                    className="practice-btn-primary"
                    onClick={() => {
                      if (hasUnsavedDraftChanges) {
                        setConfirmingRestore(true);
                      } else {
                        executeRestore();
                      }
                    }}
                  >
                    <RotateCcw size={13} aria-hidden="true" />
                    <span>Restore to editor</span>
                  </button>
                )}
              </div>
            </div>

            <div className="practice-code-snapshot dark-scroll" role="region" aria-label="Historical source code">
              {codeLines.map((line, idx) => (
                <div key={idx} className="practice-code-line">
                  <span className="practice-code-ln">{idx + 1}</span>
                  <span className="whitespace-pre-wrap break-all">{line || ' '}</span>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    );
  }

  const renderLedgerItem = (item: NormalizedLedgerRow) => {
    const meta = getVerdictMeta(item.verdict);
    const isInteractive = scope === 'classroom';
    const content = (
      <>
        <div className="min-w-0">
          <div className="flex items-center gap-2 flex-wrap">
            <strong className="text-sm font-bold text-slate-900">
              {meta.shortSymbol} {meta.label}
            </strong>
            {item.language && (
              <span className="text-xs font-medium text-slate-600">
                {formatLanguageLabel(item.language)}
              </span>
            )}
          </div>
          <div className="practice-mono text-xs text-slate-500 mt-0.5">
            {item.testsPassed !== null && item.totalTests !== null
              ? `${item.testsPassed}/${item.totalTests} tests`
              : 'Official evaluation'}
            {item.runtimeMs !== null ? ` · ${item.runtimeMs} ms` : ''}
            {item.memoryKb !== null ? ` · ${formatMemoryUsage(item.memoryKb)}` : ''}
          </div>
        </div>
        <time
          dateTime={item.submittedAt}
          className="practice-mono text-xs text-slate-500 shrink-0"
          title={formatVietnamDateTime(item.submittedAt)}
        >
          {formatVietnamTime(item.submittedAt)}
        </time>
      </>
    );

    return isInteractive ? (
      <button
        key={item.id}
        type="button"
        className="practice-ledger-row"
        onClick={() => onSelectSubmission(item.id)}
      >
        {content}
      </button>
    ) : (
      <div key={item.id} className="practice-ledger-row cursor-default">
        {content}
      </div>
    );
  };

  // STU-P06: Flat Technical Execution Ledger
  return (
    <div
      ref={scrollContainerRef}
      className="practice-statement-scroll light-scroll"
      aria-label="Submission history"
    >
      <div className="flex items-center justify-between gap-2 flex-wrap pb-3 mb-4 border-b border-slate-200">
        <div>
          <span className="practice-kicker" style={{ marginBottom: 2 }}>
            EXECUTION LEDGER
          </span>
          <h2 className="text-lg font-bold text-slate-900 m-0">My submissions</h2>
        </div>

        {rows.length > 0 && (
          <div className="flex items-center gap-2 flex-wrap">
            <select
              aria-label="Filter by verdict"
              className="practice-select"
              value={verdictFilter}
              onChange={e => setVerdictFilter(e.target.value)}
            >
              <option value="ALL">All verdicts</option>
              <option value="ACCEPTED">Accepted</option>
              <option value="WRONG_ANSWER">Wrong Answer</option>
              <option value="COMPILATION_ERROR">Compilation Error</option>
              <option value="RUNTIME_ERROR">Runtime Error</option>
              <option value="TIME_LIMIT_EXCEEDED">Time Limit Exceeded</option>
              <option value="MEMORY_LIMIT_EXCEEDED">Memory Limit Exceeded</option>
              {scope === 'public' && (
                <option value="OUTPUT_LIMIT_EXCEEDED">Output Limit Exceeded</option>
              )}
            </select>

            {scope === 'classroom' && (
              <select
                aria-label="Filter by language"
                className="practice-select"
                value={langFilter}
                onChange={e => setLangFilter(e.target.value)}
              >
                <option value="ALL">All languages</option>
                <option value="JAVA">Java 21</option>
                <option value="PYTHON">Python 3</option>
              </select>
            )}
          </div>
        )}
      </div>

      {isPending ? (
        <div role="status" className="py-6 space-y-2.5">
          {[0, 1, 2].map(idx => (
            <div key={idx} className="h-12 w-full bg-slate-200/70 animate-pulse rounded" />
          ))}
          <span className="sr-only">Loading submissions…</span>
        </div>
      ) : historyError ? (
        <div role="alert" className="practice-alert-box">
          <p className="text-sm font-bold text-slate-900 m-0">
            {classifyPracticeError(historyError).heading}
          </p>
          <p className="text-xs text-slate-600 mt-1 mb-2">
            {classifyPracticeError(historyError).message}
          </p>
          <button
            type="button"
            className="practice-btn-secondary"
            onClick={() =>
              void (scope === 'classroom' ? classHistoryQuery.refetch() : publicHistoryQuery.refetch())
            }
          >
            Retry
          </button>
        </div>
      ) : rows.length === 0 ? (
        <div className="py-8 space-y-1.5">
          <p className="text-sm font-semibold text-slate-900 m-0">No official submissions yet.</p>
          <p className="text-xs text-slate-600 m-0">
            Trial runs are never recorded here. Click Submit solution in the workspace when you want an official verdict saved to your history.
          </p>
        </div>
      ) : filteredRows.length === 0 ? (
        <div className="py-8 space-y-2">
          <p className="text-sm font-semibold text-slate-900 m-0">
            No submissions match your active filters.
          </p>
          <button
            type="button"
            className="practice-btn-secondary"
            onClick={() => {
              setVerdictFilter('ALL');
              setLangFilter('ALL');
            }}
          >
            Reset filters
          </button>
        </div>
      ) : (
        <>
          {latestRow && (
            <div className="practice-ledger-latest">
              <div className="practice-ledger-day">LATEST SUBMISSION</div>
              {renderLedgerItem(latestRow)}
            </div>
          )}

          {groupedByDay.length > 0 && (
            <div className="practice-ledger-previous">
              {latestRow && (
                <div className="practice-ledger-section-title">Previous submissions</div>
              )}
              {groupedByDay.map(group => (
                <div key={group.day} className="practice-ledger-group">
                  <div className="practice-ledger-day">{group.day}</div>
                  <div>{group.items.map(renderLedgerItem)}</div>
                </div>
              ))}
            </div>
          )}

          {pagination && pagination.totalPages > 1 && (
            <div className="flex items-center justify-between gap-2 pt-3 border-t border-slate-200">
              <button
                type="button"
                className="practice-btn-secondary"
                disabled={pagination.isFirst}
                onClick={() => setPage(p => Math.max(0, p - 1))}
              >
                Previous
              </button>
              <span className="practice-mono text-xs text-slate-600">
                Page {page + 1} of {pagination.totalPages}
              </span>
              <button
                type="button"
                className="practice-btn-secondary"
                disabled={pagination.isLast}
                onClick={() => setPage(p => p + 1)}
              >
                Next
              </button>
            </div>
          )}
        </>
      )}
    </div>
  );
};
