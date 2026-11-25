import React from 'react';
import { CheckCircle2, FileText, MessageSquare } from 'lucide-react';
import type { AttemptView, QuestionView } from '../../types';
import { formatVietnamDateTime } from '../../utils/timeUtils';
import { ScoreDisplay } from '../shared/ScoreDisplay';
import { MarkdownRenderer } from '../shared/MarkdownRenderer';

interface StudentAttemptDetailProps {
  attempt: AttemptView;
  questions?: QuestionView[];
  className?: string;
}

export const StudentAttemptDetail: React.FC<StudentAttemptDetailProps> = ({
  attempt,
  questions = attempt.questions || [],
  className = '',
}) => {
  return (
    <div className={`space-y-6 ${className}`}>
      {/* Overview Banner */}
      <div className="p-6 rounded-2xl border border-slate-200 bg-white shadow-xs space-y-4">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-slate-100">
          <div>
            <div className="flex items-center gap-2">
              <span className="px-2.5 py-0.5 rounded-full text-xs font-bold bg-slate-100 text-slate-800">
                Attempt #{attempt.attemptNumber}
              </span>
              <span className="px-2.5 py-0.5 rounded-full text-xs font-bold bg-emerald-50 text-emerald-800 border border-emerald-200">
                {attempt.status}
              </span>
              {attempt.isLate && (
                <span className="px-2.5 py-0.5 rounded-full text-xs font-bold bg-amber-50 text-amber-800 border border-amber-200">
                  Late Submission
                </span>
              )}
            </div>

            <div className="flex items-center gap-4 text-xs text-slate-500 mt-2">
              <span>Started: {formatVietnamDateTime(attempt.startedAt)}</span>
              <span>•</span>
              <span>Submitted: {formatVietnamDateTime(attempt.submittedAt)}</span>
            </div>
          </div>

          <div className="text-right">
            <span className="text-xs text-slate-500 block">Final Score</span>
            <div className="text-2xl font-black text-slate-900">
              <ScoreDisplay
                score={attempt.finalScore}
                isPublished={attempt.finalScore !== null && attempt.finalScore !== undefined}
                isOverridden={Boolean(attempt.overriddenScore)}
              />
            </div>
            {attempt.overriddenScore !== null && attempt.overriddenScore !== undefined && (
              <span className="text-[11px] text-amber-700 italic block mt-0.5">
                Overridden reason: {attempt.overrideReason || 'Adjusted by teacher'}
              </span>
            )}
          </div>
        </div>

        {attempt.teacherFeedback && (
          <div className="p-4 rounded-xl bg-blue-50/60 border border-blue-200 text-xs text-blue-900 space-y-1">
            <div className="font-bold flex items-center gap-1.5 text-blue-800">
              <MessageSquare size={14} /> Teacher Feedback
            </div>
            <p className="leading-relaxed">{attempt.teacherFeedback}</p>
          </div>
        )}
      </div>

      {/* Answered Questions Review */}
      <div className="space-y-4">
        <h3 className="text-sm font-bold text-slate-900 uppercase tracking-wide">
          Answers & Question Breakdown
        </h3>

        {questions.map((q, idx) => {
          const answer = attempt.answers?.find((a) => a.questionId === q.id);

          return (
            <div
              key={q.id || idx}
              className="p-5 rounded-2xl border border-slate-200 bg-white space-y-3 shadow-xs"
            >
              <div className="flex items-start justify-between gap-3">
                <div className="flex items-center gap-2">
                  <span className="w-6 h-6 rounded-lg bg-slate-100 text-slate-800 text-xs font-bold flex items-center justify-center">
                    {idx + 1}
                  </span>
                  <h4 className="text-sm font-bold text-slate-900">{q.title}</h4>
                </div>

                <div className="text-right shrink-0">
                  <ScoreDisplay score={answer?.score} maxScore={q.points} />
                </div>
              </div>

              <div className="text-xs text-slate-600">
                <MarkdownRenderer content={q.prompt} />
              </div>

              {/* Objective Question Review */}
              {q.options && q.options.length > 0 && (
                <div className="space-y-1.5 pt-2 border-t border-slate-100">
                  <span className="text-[11px] font-bold text-slate-500 uppercase tracking-wider block">
                    Options:
                  </span>
                  {q.options.map((opt, i) => {
                    const isSelected = answer?.selectedOptionIds?.includes(opt.id);
                    const isCorrect = opt.isCorrect;

                    return (
                      <div
                        key={opt.id || i}
                        className={`p-2.5 rounded-xl border text-xs flex items-center justify-between gap-2 ${
                          isSelected && isCorrect
                            ? 'bg-emerald-50 border-emerald-300 text-emerald-900 font-medium'
                            : isSelected && !isCorrect
                            ? 'bg-rose-50 border-rose-300 text-rose-900'
                            : isCorrect
                            ? 'bg-emerald-50/50 border-emerald-200 text-emerald-800'
                            : 'bg-slate-50 border-slate-200 text-slate-600'
                        }`}
                      >
                        <div className="flex items-center gap-2">
                          <span className="font-bold text-[11px]">{String.fromCharCode(65 + i)}.</span>
                          <span>{opt.content}</span>
                        </div>

                        <div className="flex items-center gap-2 shrink-0 text-[11px]">
                          {isSelected && <span className="font-bold">Your Choice</span>}
                          {isCorrect && (
                            <span className="text-emerald-700 font-bold flex items-center gap-1">
                              <CheckCircle2 size={12} /> Correct Answer
                            </span>
                          )}
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}

              {/* Essay Answer Review */}
              {q.questionType === 'ESSAY' && (
                <div className="space-y-2 pt-2 border-t border-slate-100 text-xs">
                  {answer?.textAnswer && (
                    <div>
                      <span className="font-bold text-slate-600 block mb-1">Your Written Answer:</span>
                      <div className="p-3 bg-slate-50 rounded-xl border border-slate-200">
                        <MarkdownRenderer content={answer.textAnswer} />
                      </div>
                    </div>
                  )}

                  {answer?.fileUrl && (
                    <div className="flex items-center gap-2 p-2.5 rounded-xl bg-slate-50 border border-slate-200">
                      <FileText size={16} className="text-slate-600" />
                      <a
                        href={answer.fileUrl}
                        target="_blank"
                        rel="noreferrer"
                        className="text-xs font-bold text-blue-600 hover:underline truncate"
                      >
                        Download Submitted File
                      </a>
                    </div>
                  )}
                </div>
              )}

              {/* Code Answer Review */}
              {q.questionType === 'CODE' && answer?.codeSubmission && (
                <div className="space-y-2 pt-2 border-t border-slate-100">
                  <span className="font-bold text-xs text-slate-700 block">Submitted Code:</span>
                  <pre className="p-3 rounded-xl bg-slate-950 text-slate-100 font-mono text-xs overflow-x-auto">
                    {answer.codeSubmission}
                  </pre>
                </div>
              )}

              {/* Teacher explanation if revealed */}
              {q.explanation && (
                <div className="p-3 rounded-xl bg-amber-50/60 border border-amber-200 text-xs text-amber-900 space-y-1 mt-2">
                  <div className="font-bold text-amber-800">Explanation:</div>
                  <MarkdownRenderer content={q.explanation} />
                </div>
              )}

              {/* Question Feedback */}
              {answer?.feedback && (
                <div className="p-3 rounded-xl bg-slate-50 border border-slate-200 text-xs text-slate-700 space-y-1">
                  <span className="font-bold text-slate-800">Feedback:</span>
                  <p>{answer.feedback}</p>
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
};
