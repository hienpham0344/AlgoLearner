import React, { useState } from 'react';
import { X, Plus, Trash2 } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import type {
  AddQuestionRequest,
  EssayFormat,
  GradingPolicy,
  OptionRequest,
  QuestionType,
  QuestionView,
  TestCaseSnapshot,
} from '../../types';
import { validateQuestionForm } from '../../utils/assignmentRules';

interface QuestionAuthoringModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (data: AddQuestionRequest) => Promise<void>;
  initialData?: QuestionView | null;
  isLoading?: boolean;
}

export const QuestionAuthoringModal: React.FC<QuestionAuthoringModalProps> = ({
  isOpen,
  onClose,
  onSubmit,
  initialData,
  isLoading = false,
}) => {
  const isEditing = Boolean(initialData);

  const [questionType, setQuestionType] = useState<QuestionType>(initialData?.questionType || 'SINGLE_CHOICE');
  const [title, setTitle] = useState(initialData?.title || '');
  const [prompt, setPrompt] = useState(initialData?.prompt || '');
  const [points, setPoints] = useState<string>(
    initialData?.points !== undefined && initialData?.points !== null ? initialData.points.toString() : '10'
  );
  const [gradingPolicy, setGradingPolicy] = useState<GradingPolicy>(
    initialData?.gradingPolicy || 'ALL_OR_NOTHING'
  );
  const [essayFormat, setEssayFormat] = useState<EssayFormat>(initialData?.essayFormat || 'TEXT_AND_FILE');
  const [explanation, setExplanation] = useState(initialData?.explanation || '');
  const [codeTemplate, setCodeTemplate] = useState(initialData?.codeTemplate || '');
  const [codeLanguage, setCodeLanguage] = useState(initialData?.codeLanguage || 'JAVA');

  // Options for MCQ / Single choice / True False
  const [options, setOptions] = useState<OptionRequest[]>(() => {
    if (initialData?.options && initialData.options.length > 0) {
      return initialData.options.map((o) => ({
        content: o.content,
        isCorrect: Boolean(o.isCorrect),
      }));
    }
    return [
      { content: '', isCorrect: true },
      { content: '', isCorrect: false },
    ];
  });

  // Testcases for Code questions
  const [testCases, setTestCases] = useState<TestCaseSnapshot[]>(() => {
    if (initialData?.testCases && initialData.testCases.length > 0) {
      return initialData.testCases;
    }
    return [{ input: '', expectedOutput: '', sample: true, allowDetails: true }];
  });

  const [formErrors, setFormErrors] = useState<Record<string, string>>({});

  if (!isOpen) return null;

  const handleAddOption = () => {
    setOptions((prev) => [...prev, { content: '', isCorrect: false }]);
  };

  const handleRemoveOption = (index: number) => {
    if (options.length <= 2) return;
    setOptions((prev) => prev.filter((_, i) => i !== index));
  };

  const handleOptionContentChange = (index: number, content: string) => {
    setOptions((prev) =>
      prev.map((opt, i) => (i === index ? { ...opt, content } : opt))
    );
  };

  const handleOptionCorrectToggle = (index: number) => {
    if (questionType === 'SINGLE_CHOICE' || questionType === 'TRUE_FALSE') {
      setOptions((prev) =>
        prev.map((opt, i) => ({ ...opt, isCorrect: i === index }))
      );
    } else {
      setOptions((prev) =>
        prev.map((opt, i) => (i === index ? { ...opt, isCorrect: !opt.isCorrect } : opt))
      );
    }
  };

  const handleAddTestCase = () => {
    setTestCases((prev) => [...prev, { input: '', expectedOutput: '', sample: false, allowDetails: false }]);
  };

  const handleRemoveTestCase = (index: number) => {
    if (testCases.length <= 1) return;
    setTestCases((prev) => prev.filter((_, i) => i !== index));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    const request: AddQuestionRequest = {
      questionType,
      title: title.trim(),
      prompt: prompt.trim(),
      points: parseFloat(points) || 0,
      gradingPolicy,
      explanation: explanation.trim() || undefined,
      questionWeight: 1,
    };

    if (questionType === 'SINGLE_CHOICE' || questionType === 'MULTIPLE_CHOICE' || questionType === 'TRUE_FALSE') {
      request.options = options.map((o) => ({ ...o, content: o.content.trim() }));
    } else if (questionType === 'ESSAY') {
      request.essayFormat = essayFormat;
    } else if (questionType === 'CODE') {
      request.codeLanguage = codeLanguage;
      request.codeTemplate = codeTemplate;
      request.testCases = testCases;
    }

    const errors = validateQuestionForm(request);
    if (Object.keys(errors).length > 0) {
      setFormErrors(errors as Record<string, string>);
      return;
    }

    try {
      await onSubmit(request);
      onClose();
    } catch {
      // Handled by caller
    }
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/50 backdrop-blur-xs"
      role="dialog"
      aria-modal="true"
      aria-labelledby="question-modal-title"
    >
      <div className="bg-white rounded-2xl shadow-2xl border border-slate-200 w-full max-w-2xl max-h-[90vh] flex flex-col overflow-hidden animate-in fade-in zoom-in-95 duration-150">
        {/* Header */}
        <div className="px-6 py-4 border-b border-slate-200 flex items-center justify-between">
          <div>
            <h2 id="question-modal-title" className="text-lg font-bold text-slate-900">
              {isEditing ? 'Edit Question' : 'Add Question'}
            </h2>
            <p className="text-xs text-slate-500 mt-0.5">
              Specify question prompt, scoring, and type-specific parameters.
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-2 text-slate-400 hover:text-slate-600 rounded-lg hover:bg-slate-100 transition-colors"
            aria-label="Close dialog"
          >
            <X size={18} />
          </button>
        </div>

        {/* Form Body */}
        <form onSubmit={handleSubmit} className="flex-1 overflow-y-auto p-6 space-y-4">
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <div className="sm:col-span-2">
              <label htmlFor="q-type" className="block text-xs font-bold text-slate-700 mb-1">
                Question Type
              </label>
              <select
                id="q-type"
                value={questionType}
                onChange={(e) => {
                  const newType = e.target.value as QuestionType;
                  setQuestionType(newType);
                  if (newType === 'TRUE_FALSE') {
                    setOptions([
                      { content: 'True', isCorrect: true },
                      { content: 'False', isCorrect: false },
                    ]);
                  }
                }}
                disabled={isEditing}
                className="w-full px-3.5 py-2 rounded-xl border border-slate-300 text-sm focus:outline-hidden focus:ring-2 focus:ring-slate-900"
              >
                <option value="SINGLE_CHOICE">Single Choice (Radio)</option>
                <option value="MULTIPLE_CHOICE">Multiple Choice (Checkboxes)</option>
                <option value="TRUE_FALSE">True / False</option>
                <option value="ESSAY">Essay / File Upload</option>
                <option value="CODE">Programming (Code Judge)</option>
              </select>
            </div>

            <div>
              <label htmlFor="q-points" className="block text-xs font-bold text-slate-700 mb-1">
                Points <span className="text-rose-500">*</span>
              </label>
              <input
                id="q-points"
                type="number"
                min="0"
                step="0.5"
                value={points}
                onChange={(e) => setPoints(e.target.value)}
                required
                className="w-full px-3.5 py-2 rounded-xl border border-slate-300 text-sm focus:outline-hidden focus:ring-2 focus:ring-slate-900"
              />
            </div>
          </div>

          <div>
            <label htmlFor="q-title" className="block text-xs font-bold text-slate-700 mb-1">
              Title <span className="text-rose-500">*</span>
            </label>
            <input
              id="q-title"
              type="text"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="e.g. Implement Binary Search"
              required
              className="w-full px-3.5 py-2 rounded-xl border border-slate-300 text-sm focus:outline-hidden focus:ring-2 focus:ring-slate-900"
            />
          </div>

          <div>
            <label htmlFor="q-prompt" className="block text-xs font-bold text-slate-700 mb-1">
              Question Prompt / Description (Markdown supported) <span className="text-rose-500">*</span>
            </label>
            <textarea
              id="q-prompt"
              rows={4}
              value={prompt}
              onChange={(e) => setPrompt(e.target.value)}
              placeholder="Write the full problem statement, constraints, and instructions..."
              required
              className="w-full px-3.5 py-2 rounded-xl border border-slate-300 text-sm focus:outline-hidden focus:ring-2 focus:ring-slate-900"
            />
          </div>

          {/* Type-Specific Authoring Fields */}
          {(questionType === 'SINGLE_CHOICE' ||
            questionType === 'MULTIPLE_CHOICE' ||
            questionType === 'TRUE_FALSE') && (
            <div className="space-y-3 pt-2 border-t border-slate-200">
              <div className="flex items-center justify-between">
                <span className="text-xs font-bold text-slate-800">Answer Options</span>
                {questionType !== 'TRUE_FALSE' && (
                  <Button type="button" variant="outline" size="sm" onClick={handleAddOption} className="text-xs h-7">
                    <Plus size={12} className="mr-1" /> Add Option
                  </Button>
                )}
              </div>

              {formErrors.options && <p className="text-xs text-rose-600">{formErrors.options}</p>}

              <div className="space-y-2">
                {options.map((opt, idx) => (
                  <div key={idx} className="flex items-center gap-2">
                    <button
                      type="button"
                      onClick={() => handleOptionCorrectToggle(idx)}
                      className={`w-6 h-6 rounded flex items-center justify-center border transition-colors ${
                        opt.isCorrect
                          ? 'bg-emerald-600 border-emerald-600 text-white'
                          : 'border-slate-300 hover:border-slate-400 bg-white'
                      }`}
                      title={opt.isCorrect ? 'Correct option' : 'Mark as correct'}
                      aria-label={`Toggle correct for option ${idx + 1}`}
                    >
                      {opt.isCorrect ? '✓' : ''}
                    </button>
                    <input
                      type="text"
                      value={opt.content}
                      onChange={(e) => handleOptionContentChange(idx, e.target.value)}
                      placeholder={`Option ${idx + 1}`}
                      disabled={questionType === 'TRUE_FALSE'}
                      required
                      className="flex-1 px-3 py-1.5 rounded-lg border border-slate-300 text-sm focus:outline-hidden focus:ring-2 focus:ring-slate-900"
                    />
                    {questionType !== 'TRUE_FALSE' && options.length > 2 && (
                      <button
                        type="button"
                        onClick={() => handleRemoveOption(idx)}
                        className="p-1.5 text-slate-400 hover:text-rose-600 rounded"
                        aria-label={`Remove option ${idx + 1}`}
                      >
                        <Trash2 size={14} />
                      </button>
                    )}
                  </div>
                ))}
              </div>

              {questionType === 'MULTIPLE_CHOICE' && (
                <div className="pt-2">
                  <label htmlFor="q-grading-policy" className="block text-xs font-bold text-slate-700 mb-1">
                    Grading Policy
                  </label>
                  <select
                    id="q-grading-policy"
                    value={gradingPolicy}
                    onChange={(e) => setGradingPolicy(e.target.value as GradingPolicy)}
                    className="w-full px-3 py-1.5 rounded-lg border border-slate-300 text-sm focus:outline-hidden focus:ring-2 focus:ring-slate-900"
                  >
                    <option value="ALL_OR_NOTHING">All or Nothing (Must pick all correct answers)</option>
                    <option value="PARTIAL_CREDIT">Partial Credit (Proportional scoring)</option>
                  </select>
                </div>
              )}
            </div>
          )}

          {questionType === 'ESSAY' && (
            <div className="pt-2 border-t border-slate-200">
              <label htmlFor="essay-format" className="block text-xs font-bold text-slate-700 mb-1">
                Accepted Submission Format
              </label>
              <select
                id="essay-format"
                value={essayFormat}
                onChange={(e) => setEssayFormat(e.target.value as EssayFormat)}
                className="w-full px-3.5 py-2 rounded-xl border border-slate-300 text-sm focus:outline-hidden focus:ring-2 focus:ring-slate-900"
              >
                <option value="TEXT_AND_FILE">Text Answer and/or File Upload</option>
                <option value="TEXT_ONLY">Text Only (Markdown input)</option>
                <option value="FILE_ONLY">File Upload Only (PDF, DOCX, ZIP)</option>
              </select>
            </div>
          )}

          {questionType === 'CODE' && (
            <div className="space-y-3 pt-2 border-t border-slate-200">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label htmlFor="code-lang" className="block text-xs font-bold text-slate-700 mb-1">
                    Language
                  </label>
                  <select
                    id="code-lang"
                    value={codeLanguage}
                    onChange={(e) => setCodeLanguage(e.target.value)}
                    className="w-full px-3.5 py-2 rounded-xl border border-slate-300 text-sm focus:outline-hidden focus:ring-2 focus:ring-slate-900"
                  >
                    <option value="JAVA">Java 21</option>
                    <option value="PYTHON">Python 3.12</option>
                    <option value="CPP">C++ 20</option>
                  </select>
                </div>
              </div>

              <div>
                <label htmlFor="code-template" className="block text-xs font-bold text-slate-700 mb-1">
                  Starter Code Template
                </label>
                <textarea
                  id="code-template"
                  rows={4}
                  value={codeTemplate}
                  onChange={(e) => setCodeTemplate(e.target.value)}
                  placeholder="// Provide starter function signatures and imports..."
                  className="w-full font-mono text-xs px-3.5 py-2 rounded-xl border border-slate-300 text-slate-900 focus:outline-hidden focus:ring-2 focus:ring-slate-900 bg-slate-50"
                />
              </div>

              <div className="space-y-2">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-bold text-slate-800">Sample Testcases</span>
                  <Button type="button" variant="outline" size="sm" onClick={handleAddTestCase} className="text-xs h-7">
                    <Plus size={12} className="mr-1" /> Add Testcase
                  </Button>
                </div>

                {testCases.map((tc, idx) => (
                  <div key={idx} className="p-3 rounded-xl border border-slate-200 bg-slate-50 space-y-2">
                    <div className="flex items-center justify-between">
                      <span className="text-[11px] font-bold text-slate-700">Testcase #{idx + 1}</span>
                      <div className="flex items-center gap-3">
                        <label className="flex items-center gap-1.5 text-xs text-slate-600 cursor-pointer">
                          <input
                            type="checkbox"
                            checked={tc.sample}
                            onChange={(e) => {
                              const checked = e.target.checked;
                              setTestCases((prev) =>
                                prev.map((t, i) => (i === idx ? { ...t, sample: checked } : t))
                              );
                            }}
                            className="w-3.5 h-3.5 rounded border-slate-300 text-slate-900"
                          />
                          Sample (Visible to student)
                        </label>
                        {testCases.length > 1 && (
                          <button
                            type="button"
                            onClick={() => handleRemoveTestCase(idx)}
                            className="text-slate-400 hover:text-rose-600"
                            aria-label={`Remove testcase ${idx + 1}`}
                          >
                            <Trash2 size={13} />
                          </button>
                        )}
                      </div>
                    </div>

                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 font-mono text-xs">
                      <div>
                        <span className="text-[10px] text-slate-500 block mb-0.5">Standard Input:</span>
                        <textarea
                          rows={2}
                          value={tc.input}
                          onChange={(e) => {
                            const val = e.target.value;
                            setTestCases((prev) =>
                              prev.map((t, i) => (i === idx ? { ...t, input: val } : t))
                            );
                          }}
                          className="w-full p-2 rounded-lg border border-slate-300 bg-white"
                        />
                      </div>
                      <div>
                        <span className="text-[10px] text-slate-500 block mb-0.5">Expected Output:</span>
                        <textarea
                          rows={2}
                          value={tc.expectedOutput}
                          onChange={(e) => {
                            const val = e.target.value;
                            setTestCases((prev) =>
                              prev.map((t, i) => (i === idx ? { ...t, expectedOutput: val } : t))
                            );
                          }}
                          className="w-full p-2 rounded-lg border border-slate-300 bg-white"
                        />
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          <div>
            <label htmlFor="q-explanation" className="block text-xs font-bold text-slate-700 mb-1">
              Explanation & Solution Notes (Shown when answers are released)
            </label>
            <textarea
              id="q-explanation"
              rows={2}
              value={explanation}
              onChange={(e) => setExplanation(e.target.value)}
              placeholder="Explain why the correct answer is right and common pitfalls..."
              className="w-full px-3.5 py-2 rounded-xl border border-slate-300 text-sm focus:outline-hidden focus:ring-2 focus:ring-slate-900"
            />
          </div>

          {/* Footer Actions */}
          <div className="pt-4 border-t border-slate-200 flex items-center justify-end gap-3">
            <Button type="button" variant="outline" size="sm" onClick={onClose} disabled={isLoading}>
              Cancel
            </Button>
            <Button type="submit" size="sm" disabled={isLoading}>
              {isLoading ? 'Saving...' : isEditing ? 'Save Question' : 'Add Question'}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
};
