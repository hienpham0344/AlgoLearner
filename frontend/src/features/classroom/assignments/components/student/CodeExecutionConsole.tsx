import React from 'react';
import { Play, Loader2, CheckCircle2, XCircle, Clock, Cpu, AlertTriangle } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import type { CodeRunStatus } from '../../hooks/useCodeRunner';
import type { EvaluationResult } from '../../types';

interface CodeExecutionConsoleProps {
  onRunSample: () => void;
  status: CodeRunStatus;
  result: EvaluationResult | null;
  errorMessage?: string | null;
  disabled?: boolean;
}

export const CodeExecutionConsole: React.FC<CodeExecutionConsoleProps> = ({
  onRunSample,
  status,
  result,
  errorMessage,
  disabled = false,
}) => {
  const isExecuting = status === 'RUNNING' || status === 'POLLING';

  const getVerdictBadge = (verdict: string) => {
    switch (verdict) {
      case 'ACCEPTED':
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-bold bg-emerald-100 text-emerald-800">
            <CheckCircle2 size={13} /> Accepted
          </span>
        );
      case 'WRONG_ANSWER':
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-bold bg-rose-100 text-rose-800">
            <XCircle size={13} /> Wrong Answer
          </span>
        );
      case 'TIME_LIMIT_EXCEEDED':
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-bold bg-amber-100 text-amber-800">
            <Clock size={13} /> Time Limit Exceeded
          </span>
        );
      case 'COMPILATION_ERROR':
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-bold bg-purple-100 text-purple-800">
            <AlertTriangle size={13} /> Compilation Error
          </span>
        );
      default:
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-bold bg-slate-100 text-slate-800">
            {verdict}
          </span>
        );
    }
  };

  return (
    <div className="rounded-2xl border border-slate-200 bg-white overflow-hidden shadow-xs mt-3">
      {/* Console Header Bar */}
      <div className="px-4 py-2.5 bg-slate-50 border-b border-slate-200 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <span className="text-xs font-bold text-slate-800 uppercase tracking-wide">
            Sample Testcases Console
          </span>
          <span className="text-[10px] text-slate-400 font-mono">(Max 5 runs/min)</span>
        </div>

        <Button
          type="button"
          size="sm"
          onClick={onRunSample}
          disabled={disabled || isExecuting}
          className="h-8 text-xs font-bold gap-1.5"
        >
          {isExecuting ? (
            <>
              <Loader2 size={14} className="animate-spin" />
              <span>Evaluating...</span>
            </>
          ) : (
            <>
              <Play size={14} />
              <span>Run Sample Code</span>
            </>
          )}
        </Button>
      </div>

      {/* Console Output Body */}
      <div className="p-4 bg-slate-950 text-slate-200 font-mono text-xs min-h-[120px] max-h-[300px] overflow-y-auto">
        {isExecuting && (
          <div className="flex items-center gap-2 text-slate-400 py-4 justify-center">
            <Loader2 size={16} className="animate-spin text-slate-300" />
            <span>Running solution against sample testcases in secure sandbox...</span>
          </div>
        )}

        {errorMessage && (
          <div className="p-3 rounded-lg bg-rose-950/60 border border-rose-800/80 text-rose-300 space-y-1">
            <div className="font-bold flex items-center gap-1.5">
              <AlertTriangle size={14} /> Execution Error
            </div>
            <p className="font-mono text-[11px] whitespace-pre-wrap">{errorMessage}</p>
          </div>
        )}

        {result && (
          <div className="space-y-3">
            <div className="flex items-center justify-between pb-2 border-b border-slate-800">
              <div className="flex items-center gap-2">
                {getVerdictBadge(result.verdict)}
                <span className="text-slate-400 text-xs">
                  Passed {result.passed} / {result.total} sample testcases
                </span>
              </div>

              <div className="flex items-center gap-3 text-[11px] text-slate-400">
                {result.executionTimeMs !== undefined && (
                  <span className="inline-flex items-center gap-1">
                    <Clock size={12} /> {result.executionTimeMs} ms
                  </span>
                )}
                {result.memoryUsedKb !== undefined && (
                  <span className="inline-flex items-center gap-1">
                    <Cpu size={12} /> {Math.round(result.memoryUsedKb / 1024)} MB
                  </span>
                )}
              </div>
            </div>

            {result.compilerOutput && (
              <div className="p-2.5 rounded bg-slate-900 border border-slate-800 text-purple-300 whitespace-pre-wrap text-[11px]">
                {result.compilerOutput}
              </div>
            )}

            {result.cases && result.cases.length > 0 && (
              <div className="space-y-2 pt-1">
                {result.cases.map((c, i) => (
                  <div
                    key={i}
                    className={`p-2.5 rounded-lg border ${
                      c.passed ? 'border-emerald-900/60 bg-emerald-950/20' : 'border-rose-900/60 bg-rose-950/20'
                    }`}
                  >
                    <div className="flex items-center justify-between mb-1.5">
                      <span className="font-bold text-[11px] text-slate-300">Case #{i + 1}</span>
                      <span className={`text-[10px] font-bold ${c.passed ? 'text-emerald-400' : 'text-rose-400'}`}>
                        {c.passed ? 'PASSED' : c.verdict || 'FAILED'}
                      </span>
                    </div>

                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 text-[11px]">
                      {c.input && (
                        <div>
                          <span className="text-slate-500 block">Input:</span>
                          <pre className="text-slate-300 bg-slate-900/80 p-1.5 rounded">{c.input}</pre>
                        </div>
                      )}
                      {c.expectedOutput && (
                        <div>
                          <span className="text-slate-500 block">Expected:</span>
                          <pre className="text-slate-300 bg-slate-900/80 p-1.5 rounded">{c.expectedOutput}</pre>
                        </div>
                      )}
                      {c.actualOutput && (
                        <div className="sm:col-span-2">
                          <span className="text-slate-500 block">Actual Output:</span>
                          <pre className={`p-1.5 rounded ${c.passed ? 'text-emerald-300 bg-slate-900/80' : 'text-rose-300 bg-rose-950/40'}`}>
                            {c.actualOutput}
                          </pre>
                        </div>
                      )}
                      {c.errorMessage && (
                        <div className="sm:col-span-2 text-rose-400 text-[11px]">
                          Error: {c.errorMessage}
                        </div>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {!isExecuting && !result && !errorMessage && (
          <p className="text-slate-500 py-6 text-center italic text-xs">
            Run sample cases to verify your algorithm before final submission.
          </p>
        )}
      </div>
    </div>
  );
};
