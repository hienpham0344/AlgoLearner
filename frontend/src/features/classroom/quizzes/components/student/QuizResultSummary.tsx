import React from 'react';
import { Award, CheckCircle2, XCircle, AlertCircle, HelpCircle } from 'lucide-react';
import type { AttemptView, QuizResult, QuizKind } from '../../types';

interface QuizResultSummaryProps {
  quizTitle: string;
  quizKind: QuizKind;
  attempt?: AttemptView | null;
  result?: QuizResult | null;
  className?: string;
}

export const QuizResultSummary: React.FC<QuizResultSummaryProps> = ({
  quizTitle,
  quizKind,
  attempt,
  result,
  className = '',
}) => {
  // Check if scores are available (released by teacher or auto-published)
  const scoreAvailable =
    result?.finalScore != null || (attempt?.status === 'SUBMITTED' && attempt.score != null);

  const displayScore = result?.finalScore ?? attempt?.score;
  const isCompleted = result?.completed ?? attempt?.completed;
  const isOverridden =
    result?.originalAutoScore != null &&
    result.finalScore != null &&
    Number(result.originalAutoScore) !== Number(result.finalScore);

  return (
    <div className={`space-y-6 ${className}`}>
      {/* Top Banner Card */}
      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-xs text-center sm:p-8">
        <h2 className="text-xl font-bold text-slate-900">{quizTitle}</h2>

        {quizKind === 'GRADED' ? (
          scoreAvailable ? (
            <div className="mt-6 flex flex-col items-center">
              <div className="flex h-20 w-20 items-center justify-center rounded-full bg-indigo-50 text-indigo-600 mb-3">
                <Award size={40} />
              </div>
              <p className="text-4xl font-extrabold text-slate-900 tracking-tight">
                {Number(displayScore).toFixed(2)}%
              </p>

              {isOverridden && (
                <p className="mt-1 text-xs text-slate-500">
                  Original Auto-Grade: {Number(result?.originalAutoScore).toFixed(2)}% · (Adjusted by Teacher)
                </p>
              )}

              {isCompleted != null && (
                <div className="mt-3">
                  <span
                    className={`inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-bold ${
                      isCompleted
                        ? 'border border-emerald-200 bg-emerald-50 text-emerald-800'
                        : 'border border-rose-200 bg-rose-50 text-rose-800'
                    }`}
                  >
                    {isCompleted ? (
                      <>
                        <CheckCircle2 size={14} /> Passed
                      </>
                    ) : (
                      <>
                        <XCircle size={14} /> Needs Improvement
                      </>
                    )}
                  </span>
                </div>
              )}
            </div>
          ) : (
            <div className="mt-6 flex flex-col items-center">
              <div className="flex h-16 w-16 items-center justify-center rounded-full bg-amber-50 text-amber-600 mb-3">
                <AlertCircle size={32} />
              </div>
              <h3 className="text-base font-bold text-slate-900">Grades Pending Publication</h3>
              <p className="mt-1 max-w-md text-xs text-slate-600">
                Your submission was successfully recorded. The instructor has configured manual grade publication for this quiz. Your score and review will appear here once released.
              </p>
            </div>
          )
        ) : (
          <div className="mt-6 flex flex-col items-center">
            <div className="flex h-16 w-16 items-center justify-center rounded-full bg-emerald-50 text-emerald-600 mb-3">
              <CheckCircle2 size={32} />
            </div>
            <h3 className="text-base font-bold text-slate-900">Quiz Completed</h3>
            <p className="mt-1 text-xs text-slate-600">
              This practice quiz is ungraded. Your completion has been saved.
            </p>
          </div>
        )}
      </div>

      {/* Question Review Section (if questions view contains answer keys or released options) */}
      {attempt?.questions && attempt.questions.length > 0 && (
        <div className="space-y-4">
          <h3 className="text-sm font-bold uppercase tracking-wider text-slate-600">
            Submission Review ({attempt.questions.length} questions)
          </h3>

          {attempt.questions.map((question, qIdx) => {
            const studentSelectedOptionIds = attempt.answers?.[question.id] || [];

            return (
              <div
                key={question.id}
                className="rounded-xl border border-slate-200 bg-white p-5 shadow-xs space-y-3"
              >
                <div className="flex items-start justify-between gap-2 border-b border-slate-100 pb-2">
                  <span className="text-xs font-bold text-slate-900">
                    Question {qIdx + 1} ({question.points} {question.points === 1 ? 'pt' : 'pts'})
                  </span>
                </div>

                <p className="text-sm font-semibold text-slate-900 whitespace-pre-wrap">
                  {question.text}
                </p>

                {/* Options Review */}
                <div className="space-y-2 pt-1">
                  {question.options.map((opt) => {
                    const isSelected = studentSelectedOptionIds.includes(opt.id);
                    const isCorrect = opt.correct; // only non-null if released

                    let badgeColor = 'border-slate-200 bg-slate-50 text-slate-700';
                    if (isSelected && isCorrect === true) {
                      badgeColor = 'border-emerald-300 bg-emerald-50 text-emerald-900 font-semibold';
                    } else if (isSelected && isCorrect === false) {
                      badgeColor = 'border-rose-300 bg-rose-50 text-rose-900 font-semibold';
                    } else if (!isSelected && isCorrect === true) {
                      badgeColor = 'border-emerald-200 bg-emerald-50/50 text-emerald-800';
                    }

                    return (
                      <div
                        key={opt.id}
                        className={`flex items-center justify-between rounded-lg border p-3 text-xs ${badgeColor}`}
                      >
                        <div className="flex items-center gap-2">
                          <span
                            className={`flex h-4 w-4 items-center justify-center rounded-sm text-[10px] font-bold ${
                              isSelected ? 'bg-slate-900 text-white' : 'border border-slate-300'
                            }`}
                          >
                            {isSelected ? '✓' : ''}
                          </span>
                          <span>{opt.text}</span>
                        </div>

                        <div className="flex items-center gap-2 font-medium">
                          {isSelected && <span className="opacity-75">(Your answer)</span>}
                          {isCorrect === true && (
                            <span className="text-emerald-700 font-bold">✓ Correct</span>
                          )}
                        </div>
                      </div>
                    );
                  })}
                </div>

                {/* Explanation */}
                {question.explanation && (
                  <div className="mt-3 flex items-start gap-1.5 rounded-lg border border-slate-200 bg-slate-50 p-3 text-xs text-slate-600">
                    <HelpCircle size={14} className="mt-0.5 shrink-0 text-slate-400" />
                    <div>
                      <strong className="font-semibold text-slate-800">Explanation: </strong>
                      <span>{question.explanation}</span>
                    </div>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
};
