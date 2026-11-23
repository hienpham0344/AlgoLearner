import React, { useState, useMemo, useCallback } from 'react';
import { ArrowLeft, ArrowRight, Send, CheckCircle2 } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import type { AnswerView, AttemptView, QuestionView, TimeExpiredAction } from '../../types';
import { useAutosaveDraft } from '../../hooks/useAutosaveDraft';
import { useCodeRunner } from '../../hooks/useCodeRunner';
import { WorkspaceTimer } from './WorkspaceTimer';
import { AutosaveStatusIndicator } from './AutosaveStatusIndicator';
import { QuestionNavPalette } from './QuestionNavPalette';
import { ObjectiveAnswerInput } from './ObjectiveAnswerInput';
import { EssayAnswerInput } from './EssayAnswerInput';
import { CodeAnswerInput } from './CodeAnswerInput';
import { CodeExecutionConsole } from './CodeExecutionConsole';
import { SubmitConfirmationDialog } from './SubmitConfirmationDialog';
import { MarkdownRenderer } from '../shared/MarkdownRenderer';

interface AssessmentWorkspaceProps {
  classId: string;
  assignmentId: string;
  assignmentTitle: string;
  timeExpiredAction?: TimeExpiredAction;
  attempt: AttemptView;
  questions: QuestionView[];
  onSubmitAttempt: (attemptId: string) => Promise<void>;
  isSubmitting?: boolean;
}

export const AssessmentWorkspace: React.FC<AssessmentWorkspaceProps> = ({
  classId,
  assignmentId,
  assignmentTitle,
  timeExpiredAction = 'AUTO_SUBMIT',
  attempt,
  questions,
  onSubmitAttempt,
  isSubmitting = false,
}) => {
  const [currentIndex, setCurrentIndex] = useState(0);
  const [isConfirmOpen, setIsConfirmOpen] = useState(false);

  // Local state for answers map: { [questionId]: Partial<AnswerView> }
  const [answersMap, setAnswersMap] = useState<Record<string, Partial<AnswerView>>>(() => {
    const map: Record<string, Partial<AnswerView>> = {};
    (attempt.answers || []).forEach((ans) => {
      map[ans.questionId] = ans;
    });
    return map;
  });

  // Autosave hook
  const { status, lastSavedAt, errorMessage, scheduleAutosave, flushAllPending } = useAutosaveDraft({
    classId,
    assignmentId,
    attemptId: attempt.id,
    debounceMs: 800,
    initialAnswers: attempt.answers,
    onSaved: (saved) => {
      setAnswersMap((prev) => ({
        ...prev,
        [saved.questionId]: {
          ...prev[saved.questionId],
          ...saved,
        },
      }));
    },
  });

  // Code runner hook
  const {
    status: codeStatus,
    result: codeResult,
    errorMessage: codeError,
    runSample,
  } = useCodeRunner({
    classId,
    assignmentId,
    attemptId: attempt.id,
  });

  const currentQuestion = questions[currentIndex];

  // Helper to check if a question is answered
  const isQuestionAnswered = useCallback(
    (questionId: string) => {
      const ans = answersMap[questionId];
      if (!ans) return false;
      if (ans.selectedOptionIds && ans.selectedOptionIds.trim().length > 0) return true;
      if (ans.textAnswer && ans.textAnswer.trim().length > 0) return true;
      if (ans.fileId || ans.fileUrl) return true;
      if (ans.codeSubmission && ans.codeSubmission.trim().length > 0) return true;
      return false;
    },
    [answersMap]
  );

  const answeredCount = useMemo(() => {
    return questions.filter((q) => isQuestionAnswered(q.id)).length;
  }, [questions, isQuestionAnswered]);

  // Handle objective answer change
  const handleObjectiveChange = (selectedIds: string[]) => {
    if (!currentQuestion) return;
    const qId = currentQuestion.id;
    const joined = selectedIds.join(',');

    setAnswersMap((prev) => ({
      ...prev,
      [qId]: { ...prev[qId], selectedOptionIds: joined },
    }));

    scheduleAutosave({
      questionId: qId,
      selectedOptions: selectedIds,
      selectedOptionIds: joined,
    });
  };

  // Handle essay text change
  const handleEssayTextChange = (text: string) => {
    if (!currentQuestion) return;
    const qId = currentQuestion.id;

    setAnswersMap((prev) => ({
      ...prev,
      [qId]: { ...prev[qId], textAnswer: text },
    }));

    scheduleAutosave({
      questionId: qId,
      textAnswer: text,
      fileId: answersMap[qId]?.fileId || null,
    });
  };

  // Handle essay file change
  const handleEssayFileChange = (fileId: string | null) => {
    if (!currentQuestion) return;
    const qId = currentQuestion.id;

    setAnswersMap((prev) => ({
      ...prev,
      [qId]: { ...prev[qId], fileId },
    }));

    scheduleAutosave({
      questionId: qId,
      fileId,
      textAnswer: answersMap[qId]?.textAnswer || '',
    });
  };

  // Handle code change
  const handleCodeChange = (code: string) => {
    if (!currentQuestion) return;
    const qId = currentQuestion.id;

    setAnswersMap((prev) => ({
      ...prev,
      [qId]: { ...prev[qId], codeSubmission: code },
    }));

    scheduleAutosave({
      questionId: qId,
      codeSubmission: code,
    });
  };

  // Handle time expiration
  const handleTimeout = async () => {
    if (timeExpiredAction === 'AUTO_SUBMIT') {
      try {
        await onSubmitAttempt(attempt.id);
      } catch {
        // Submit attempt error handled by caller
      }
    } else {
      alert('Time has expired. This assessment has been locked.');
    }
  };

  // Final submit handler
  const handleConfirmSubmit = async () => {
    try {
      await flushAllPending();
      await onSubmitAttempt(attempt.id);
      setIsConfirmOpen(false);
    } catch {
      // Error handled by caller
    }
  };

  if (!currentQuestion) {
    return <div className="p-8 text-center text-slate-500">No questions available.</div>;
  }

  const currentAnswer = answersMap[currentQuestion.id] || {};
  const selectedOptionIds = currentAnswer.selectedOptionIds
    ? currentAnswer.selectedOptionIds.split(',').filter(Boolean)
    : [];

  return (
    <div className="min-h-screen bg-slate-50 flex flex-col">
      {/* Top Fixed Assessment Bar */}
      <header className="sticky top-0 z-30 bg-white border-b border-slate-200 shadow-xs px-4 sm:px-8 py-3.5 flex items-center justify-between gap-4">
        <div className="flex items-center gap-3 min-w-0">
          <h1 className="text-sm sm:text-base font-bold text-slate-900 truncate">
            {assignmentTitle}
          </h1>
          <AutosaveStatusIndicator
            status={status}
            lastSavedAt={lastSavedAt}
            errorMessage={errorMessage}
            className="hidden sm:inline-flex"
          />
        </div>

        <div className="flex items-center gap-3 shrink-0">
          {attempt.remainingSeconds !== undefined && attempt.remainingSeconds !== null && (
            <WorkspaceTimer
              initialRemainingSeconds={attempt.remainingSeconds}
              onTimeout={handleTimeout}
            />
          )}

          <Button
            size="sm"
            onClick={() => setIsConfirmOpen(true)}
            disabled={isSubmitting}
            className="bg-emerald-600 hover:bg-emerald-700 text-white font-bold gap-1.5 h-8 text-xs"
          >
            <Send size={13} />
            <span>Submit</span>
          </Button>
        </div>
      </header>

      {/* Main Workspace Layout */}
      <div className="flex-1 max-w-7xl w-full mx-auto p-4 sm:p-6 grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
        {/* Left Navigation Palette */}
        <aside className="lg:col-span-3 lg:sticky lg:top-20 order-2 lg:order-1">
          <QuestionNavPalette
            questions={questions}
            currentIndex={currentIndex}
            onSelectIndex={setCurrentIndex}
            isAnswered={isQuestionAnswered}
          />
        </aside>

        {/* Center Work Area */}
        <main className="lg:col-span-9 order-1 lg:order-2 space-y-6">
          <div className="p-6 sm:p-8 rounded-3xl border border-slate-200 bg-white shadow-xs space-y-5">
            {/* Question Header */}
            <div className="flex items-start justify-between gap-4 pb-4 border-b border-slate-100">
              <div className="flex items-center gap-2">
                <span className="w-7 h-7 rounded-xl bg-slate-900 text-white text-xs font-bold flex items-center justify-center">
                  {currentIndex + 1}
                </span>
                <h2 className="text-base font-bold text-slate-900">{currentQuestion.title}</h2>
              </div>

              <span className="text-xs font-bold text-slate-700 px-2.5 py-1 rounded-lg bg-slate-100 border border-slate-200 shrink-0">
                {currentQuestion.points} pts
              </span>
            </div>

            {/* Question Prompt */}
            <div className="text-sm text-slate-700 leading-relaxed">
              <MarkdownRenderer content={currentQuestion.prompt} />
            </div>

            {/* Answer Input Section */}
            <div className="pt-4 border-t border-slate-100">
              {(currentQuestion.questionType === 'SINGLE_CHOICE' ||
                currentQuestion.questionType === 'MULTIPLE_CHOICE' ||
                currentQuestion.questionType === 'TRUE_FALSE') && (
                <ObjectiveAnswerInput
                  questionType={currentQuestion.questionType}
                  options={currentQuestion.options || []}
                  selectedOptionIds={selectedOptionIds}
                  onChange={handleObjectiveChange}
                />
              )}

              {currentQuestion.questionType === 'ESSAY' && (
                <EssayAnswerInput
                  classId={classId}
                  format={currentQuestion.essayFormat}
                  textAnswer={currentAnswer.textAnswer || ''}
                  fileId={currentAnswer.fileId}
                  fileUrl={currentAnswer.fileUrl}
                  onTextChange={handleEssayTextChange}
                  onFileChange={handleEssayFileChange}
                />
              )}

              {currentQuestion.questionType === 'CODE' && (
                <div className="space-y-4">
                  <CodeAnswerInput
                    language={currentQuestion.codeLanguage}
                    sourceCode={currentAnswer.codeSubmission || currentQuestion.codeTemplate || ''}
                    templateCode={currentQuestion.codeTemplate}
                    onChange={handleCodeChange}
                  />

                  <CodeExecutionConsole
                    onRunSample={() =>
                      runSample(
                        currentQuestion.id,
                        currentAnswer.codeSubmission || currentQuestion.codeTemplate || ''
                      )
                    }
                    status={codeStatus}
                    result={codeResult}
                    errorMessage={codeError}
                  />
                </div>
              )}
            </div>

            {/* Question Pagination Buttons */}
            <div className="pt-6 border-t border-slate-100 flex items-center justify-between">
              <Button
                variant="outline"
                size="sm"
                onClick={() => setCurrentIndex((prev) => Math.max(0, prev - 1))}
                disabled={currentIndex === 0}
                className="gap-1.5"
              >
                <ArrowLeft size={14} /> Previous
              </Button>

              {currentIndex < questions.length - 1 ? (
                <Button
                  size="sm"
                  onClick={() => setCurrentIndex((prev) => Math.min(questions.length - 1, prev + 1))}
                  className="gap-1.5 bg-slate-900 text-white"
                >
                  Next <ArrowRight size={14} />
                </Button>
              ) : (
                <Button
                  size="sm"
                  onClick={() => setIsConfirmOpen(true)}
                  className="gap-1.5 bg-emerald-600 hover:bg-emerald-700 text-white font-bold"
                >
                  <CheckCircle2 size={14} /> Review & Submit
                </Button>
              )}
            </div>
          </div>
        </main>
      </div>

      {/* Submit Confirmation Dialog */}
      <SubmitConfirmationDialog
        isOpen={isConfirmOpen}
        onClose={() => setIsConfirmOpen(false)}
        onConfirm={handleConfirmSubmit}
        totalQuestions={questions.length}
        answeredCount={answeredCount}
        isSubmitting={isSubmitting}
      />
    </div>
  );
};
