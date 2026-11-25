import React from 'react';
import { Award, CheckCircle2, XCircle, AlertCircle, FileText, Check, HelpCircle } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import type { AttemptView, QuizResult, QuizView, ReviewQuestion } from '../../types';

interface QuizResultSummaryProps {
  quiz: QuizView;
  attempt: AttemptView;
  result?: QuizResult | null;
  reviews?: ReviewQuestion[];
  onBackToClassroom: () => void;
  onRetakeQuiz?: () => void;
  canRetake?: boolean;
  className?: string;
}

export const QuizResultSummary: React.FC<QuizResultSummaryProps> = ({
  quiz,
  attempt,
  result,
  reviews = [],
  onBackToClassroom,
  onRetakeQuiz,
  canRetake = false,
  className = '',
}) => {
  const isGradePublished = attempt.gradePublished;
  const isGraded = quiz.graded;
  const finalScore = result?.finalScore ?? attempt.score;
  const isCompleted = result?.completed ?? false;

  return (
    <div className={`space-y-6 ${className}`}>
      {/* Top Banner: Score & Completion Status */}
      <div className="bg-white rounded-2xl border border-slate-200/80 p-6 sm:p-8 shadow-xs flex flex-col items-center text-center">
        <div
          className={`w-14 h-14 rounded-2xl flex items-center justify-center mb-4 shadow-xs ${
            !isGraded || isCompleted
              ? 'bg-emerald-100 text-emerald-700'
              : 'bg-amber-100 text-amber-700'
          }`}
        >
          {!isGraded || isCompleted ? <Award size={30} /> : <AlertCircle size={30} />}
        </div>

        <h1 className="text-xl sm:text-2xl font-bold text-slate-900">
          Quiz Result
        </h1>
        <p className="text-sm text-slate-500 mt-1 max-w-md">
          {quiz.title}
        </p>

        {/* Score block */}
        <div className="my-6 p-4 sm:p-6 rounded-2xl bg-slate-50 border border-slate-100 w-full max-w-sm flex flex-col items-center">
          {isGraded ? (
            isGradePublished ? (
              <>
                <span className="text-xs font-bold uppercase tracking-wider text-slate-400 mb-1">
                  Final Score
                </span>
                <div className="text-4xl sm:text-5xl font-black text-indigo-600 font-mono">
                  {finalScore !== null && finalScore !== undefined
                    ? Number(finalScore).toFixed(1)
                    : '—'}
                  <span className="text-lg text-slate-400 font-normal"> / 100</span>
                </div>

                {result?.originalScore !== null &&
                  result?.originalScore !== undefined &&
                  result.originalScore !== finalScore && (
                    <span className="text-xs text-slate-400 mt-1 italic">
                      (Initial auto-score: {Number(result.originalScore).toFixed(1)}/100)
                    </span>
                  )}

                <div className="mt-3">
                  {isCompleted ? (
                    <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold bg-emerald-100 text-emerald-800 border border-emerald-300">
                      <CheckCircle2 size={13} />
                      Completed & Passed
                    </span>
                  ) : (
                    <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold bg-rose-100 text-rose-800 border border-rose-300">
                      <XCircle size={13} />
                      Passing threshold not met
                    </span>
                  )}
                </div>
              </>
            ) : (
              <div className="py-2 text-center">
                <span className="text-xs font-bold uppercase tracking-wider text-amber-700 bg-amber-50 border border-amber-200 px-3 py-1 rounded-full inline-block mb-2">
                  Grade Pending Release
                </span>
                <p className="text-xs text-slate-600 leading-relaxed max-w-xs mx-auto">
                  Your submission has been safely recorded. The teacher will release official scores once evaluation is complete.
                </p>
              </div>
            )
          ) : (
            <div className="py-2 text-center">
              <span className="text-xs font-bold uppercase tracking-wider text-emerald-700 bg-emerald-50 border border-emerald-200 px-3 py-1 rounded-full inline-block mb-2">
                Practice Completed
              </span>
              <p className="text-xs text-slate-600 leading-relaxed max-w-xs mx-auto">
                This is an ungraded practice quiz. Your submission has been saved to your learning records.
              </p>
            </div>
          )}
        </div>

        {/* Action buttons */}
        <div className="flex flex-wrap items-center justify-center gap-3">
          <Button
            variant="outline"
            size="sm"
            onClick={onBackToClassroom}
            className="rounded-xl text-xs font-semibold border-slate-200 text-slate-700 hover:bg-slate-50"
          >
            ← Back to Classroom
          </Button>

          {canRetake && onRetakeQuiz && (
            <Button
              size="sm"
              onClick={onRetakeQuiz}
              className="rounded-xl text-xs font-semibold bg-indigo-600 hover:bg-indigo-700 text-white shadow-xs"
            >
              Retake Quiz
            </Button>
          )}
        </div>
      </div>

      {/* Review Section */}
      <section aria-labelledby="review-section-heading" className="bg-white rounded-2xl border border-slate-200/80 p-6 sm:p-8 shadow-xs">
        <div className="border-b border-slate-100 pb-4 mb-6">
          <h2 id="review-section-heading" className="text-base font-bold text-slate-900 flex items-center gap-2">
            <FileText size={18} className="text-indigo-600" />
            <span>Review Questions & Answers</span>
          </h2>
          <p className="text-xs text-slate-500 mt-0.5">
            Compare your selected options with published answer keys
          </p>
        </div>

        {reviews.length > 0 ? (
          <div className="space-y-6">
            {attempt.questions.map((q, idx) => {
              const reviewInfo = reviews.find((r) => r.questionId === q.id);
              const studentAnswer = attempt.answers.find((a) => a.questionId === q.id);
              const selectedIds = studentAnswer?.selectedOptionIds || [];
              const correctIds = reviewInfo?.correctOptionIds || null;
              const explanation = reviewInfo?.explanation || null;

              return (
                <div
                  key={q.id}
                  className="p-5 rounded-xl border border-slate-200/90 bg-slate-50/50 flex flex-col gap-4"
                >
                  <div className="flex items-center justify-between gap-2">
                    <span className="text-xs font-bold px-2.5 py-0.5 rounded-md bg-slate-200 text-slate-800">
                      Question {idx + 1}
                    </span>
                    <span className="text-xs font-semibold text-slate-500">
                      {q.points} pts
                    </span>
                  </div>

                  <p className="text-sm font-bold text-slate-900 leading-relaxed">
                    {q.prompt}
                  </p>

                  {/* Options */}
                  <div className="space-y-2">
                    {q.options.map((opt, optIdx) => {
                      const isSelected = selectedIds.includes(opt.id);
                      const isCorrect = correctIds ? correctIds.includes(opt.id) : null;
                      const letter = String.fromCharCode(65 + optIdx);

                      let optClass = 'bg-white border-slate-200 text-slate-700';

                      if (isSelected) {
                        if (isCorrect === true) {
                          optClass = 'bg-emerald-50 border-emerald-400 text-emerald-950 font-medium ring-1 ring-emerald-400';
                        } else if (isCorrect === false) {
                          optClass = 'bg-rose-50 border-rose-300 text-rose-950 font-medium';
                        } else {
                          optClass = 'bg-indigo-50 border-indigo-300 text-indigo-950 font-medium';
                        }
                      } else if (isCorrect === true) {
                        optClass = 'bg-emerald-50/60 border-emerald-300 text-emerald-900 border-dashed';
                      }

                      return (
                        <div
                          key={opt.id}
                          className={`flex items-center justify-between p-3 rounded-lg border text-xs transition-colors ${optClass}`}
                        >
                          <div className="flex items-center gap-2.5">
                            <span className="w-5 h-5 rounded-md bg-slate-100 text-slate-600 font-bold flex items-center justify-center text-[10px] shrink-0">
                              {letter}
                            </span>
                            <span>{opt.text}</span>
                          </div>

                          <div className="flex items-center gap-2 shrink-0">
                            {isSelected && (
                              <span className="text-[10px] font-bold px-1.5 py-0.5 rounded bg-indigo-100 text-indigo-800">
                                Your choice
                              </span>
                            )}
                            {isCorrect === true && (
                              <span className="text-[10px] font-bold px-1.5 py-0.5 rounded bg-emerald-100 text-emerald-800 flex items-center gap-1">
                                <Check size={11} /> Correct answer
                              </span>
                            )}
                          </div>
                        </div>
                      );
                    })}
                  </div>

                  {/* Explanation if available */}
                  {explanation && (
                    <div className="mt-2 p-3 rounded-lg bg-blue-50/80 border border-blue-200 text-xs text-blue-900 flex items-start gap-2">
                      <HelpCircle size={14} className="text-blue-600 shrink-0 mt-0.5" />
                      <div>
                        <span className="font-bold block mb-0.5">Explanation:</span>
                        <p className="leading-relaxed">{explanation}</p>
                      </div>
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        ) : (
          <div className="p-8 text-center bg-slate-50 rounded-xl border border-dashed border-slate-200 text-slate-500">
            <HelpCircle size={32} className="mx-auto text-slate-400 mb-2" />
            <h3 className="text-xs font-bold text-slate-700">No answer keys or explanations available</h3>
            <p className="text-xs text-slate-500 mt-1 max-w-sm mx-auto">
              {!isGradePublished && isGraded
                ? 'Answer keys and explanations will be revealed once grades are published by your teacher.'
                : 'The teacher has not enabled answer key review for this assessment.'}
            </p>
          </div>
        )}
      </section>
    </div>
  );
};
