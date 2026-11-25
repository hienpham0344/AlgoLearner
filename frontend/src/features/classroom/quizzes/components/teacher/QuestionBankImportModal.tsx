import React, { useState } from 'react';
import { X, BookOpen, Plus, Check, Search } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import type { Question, QuizView } from '../../types';
import { quizApi } from '../../api/quizApi';
import { generateClientUuid } from '../../utils/quizRules';

interface QuestionBankImportModalProps {
  isOpen: boolean;
  onClose: () => void;
  onImport: (questions: Question[]) => void;
  classId: string;
  existingQuizzes: QuizView[];
}

// Curated question bank presets for rapid authoring
const CURATED_BANK_PRESETS: Question[] = [
  {
    id: 'preset-time-complexity-1',
    prompt: 'What is the worst-case time complexity of QuickSort on an array of n elements?',
    type: 'SINGLE_CHOICE',
    scoring: 'ALL_OR_NOTHING',
    points: 1,
    options: [
      { id: 'opt-q1-a', text: 'O(n log n)', correct: false, weight: null },
      { id: 'opt-q1-b', text: 'O(n^2)', correct: true, weight: null },
      { id: 'opt-q1-c', text: 'O(n)', correct: false, weight: null },
      { id: 'opt-q1-d', text: 'O(log n)', correct: false, weight: null },
    ],
    explanation: 'QuickSort has a worst-case time complexity of O(n^2) when the pivot choice is consistently the smallest or largest element.',
  },
  {
    id: 'preset-data-structures-1',
    prompt: 'Which of the following data structures operate on a First-In-First-Out (FIFO) basis?',
    type: 'SINGLE_CHOICE',
    scoring: 'ALL_OR_NOTHING',
    points: 1,
    options: [
      { id: 'opt-ds1-a', text: 'Queue', correct: true, weight: null },
      { id: 'opt-ds1-b', text: 'Stack', correct: false, weight: null },
      { id: 'opt-ds1-c', text: 'Binary Search Tree', correct: false, weight: null },
      { id: 'opt-ds1-d', text: 'Priority Queue', correct: false, weight: null },
    ],
    explanation: 'A standard Queue operates on the FIFO principle (First-In, First-Out).',
  },
  {
    id: 'preset-graph-algorithms-1',
    prompt: 'Which algorithms can be used to find the shortest path in a weighted graph with non-negative edge weights? (Select all that apply)',
    type: 'MULTIPLE_CHOICE',
    scoring: 'PARTIAL',
    points: 2,
    options: [
      { id: 'opt-ga1-a', text: "Dijkstra's Algorithm", correct: true, weight: 1 },
      { id: 'opt-ga1-b', text: 'Bellman-Ford Algorithm', correct: true, weight: 1 },
      { id: 'opt-ga1-c', text: "Kruskal's Algorithm", correct: false, weight: 0 },
      { id: 'opt-ga1-d', text: "Prim's Algorithm", correct: false, weight: 0 },
    ],
    explanation: "Dijkstra's and Bellman-Ford find shortest paths. Kruskal and Prim are for Minimum Spanning Trees.",
  },
  {
    id: 'preset-boolean-logic-1',
    prompt: 'In Java and Python, an integer overflow always results in an unhandled runtime exception.',
    type: 'TRUE_FALSE',
    scoring: 'ALL_OR_NOTHING',
    points: 1,
    options: [
      { id: 'opt-bl1-a', text: 'True', correct: false, weight: null },
      { id: 'opt-bl1-b', text: 'False', correct: true, weight: null },
    ],
    explanation: 'False. Java primitive integer operations wrap around silently on overflow, and Python arbitrarily promotes integers to arbitrary precision.',
  },
  {
    id: 'preset-dynamic-prog-1',
    prompt: 'Which two key properties must an optimization problem exhibit to be solved using Dynamic Programming?',
    type: 'MULTIPLE_CHOICE',
    scoring: 'ALL_OR_NOTHING',
    points: 2,
    options: [
      { id: 'opt-dp1-a', text: 'Optimal substructure', correct: true, weight: null },
      { id: 'opt-dp1-b', text: 'Overlapping subproblems', correct: true, weight: null },
      { id: 'opt-dp1-c', text: 'Greedy choice property', correct: false, weight: null },
      { id: 'opt-dp1-d', text: 'Monotonic stack property', correct: false, weight: null },
    ],
    explanation: 'Dynamic Programming requires optimal substructure and overlapping subproblems.',
  },
];

export const QuestionBankImportModal: React.FC<QuestionBankImportModalProps> = ({
  isOpen,
  onClose,
  onImport,
  classId,
  existingQuizzes,
}) => {
  const [source, setSource] = useState<'PRESETS' | 'EXISTING_QUIZZES'>('PRESETS');
  const [selectedQuizId, setSelectedQuizId] = useState<string>(existingQuizzes[0]?.id || '');
  const [quizQuestions, setQuizQuestions] = useState<Question[]>([]);
  const [isLoadingQuizQuestions, setIsLoadingQuizQuestions] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedQuestionIds, setSelectedQuestionIds] = useState<Set<string>>(new Set());

  // Load questions when selecting an existing quiz
  const handleSelectQuiz = async (quizId: string) => {
    setSelectedQuizId(quizId);
    if (!quizId) return;
    setIsLoadingQuizQuestions(true);
    try {
      const config = await quizApi.authoring(classId, quizId);
      setQuizQuestions(config.questions || []);
    } catch {
      setQuizQuestions([]);
    } finally {
      setIsLoadingQuizQuestions(false);
    }
  };

  if (!isOpen) return null;

  const currentPool: Question[] = source === 'PRESETS' ? CURATED_BANK_PRESETS : quizQuestions;
  const filteredQuestions = currentPool.filter((q) =>
    q.prompt.toLowerCase().includes(searchQuery.toLowerCase())
  );

  const toggleSelect = (id: string) => {
    setSelectedQuestionIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  };

  const handleSelectAll = () => {
    if (selectedQuestionIds.size === filteredQuestions.length) {
      setSelectedQuestionIds(new Set());
    } else {
      setSelectedQuestionIds(new Set(filteredQuestions.map((q) => q.id)));
    }
  };

  const handleExecuteImport = () => {
    const questionsToImport = currentPool
      .filter((q) => selectedQuestionIds.has(q.id))
      .map((q) => ({
        ...q,
        id: generateClientUuid(),
        options: q.options.map((opt) => ({
          ...opt,
          id: generateClientUuid(),
        })),
      }));

    onImport(questionsToImport);
    onClose();
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/50 backdrop-blur-xs"
      role="dialog"
      aria-modal="true"
      aria-labelledby="bank-modal-title"
    >
      <div className="bg-white rounded-2xl border border-slate-200 shadow-xl w-full max-w-2xl max-h-[90vh] flex flex-col overflow-hidden">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-200 bg-slate-50">
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-lg bg-indigo-100 text-indigo-700 flex items-center justify-center">
              <BookOpen size={16} />
            </div>
            <div>
              <h2 id="bank-modal-title" className="text-base font-bold text-slate-900">
                Question Bank &amp; Import
              </h2>
              <p className="text-xs text-slate-500">
                Select questions from standard question banks or your classroom quizzes.
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-1 rounded-lg text-slate-400 hover:text-slate-600 transition-colors"
          >
            <X size={18} />
          </button>
        </div>

        {/* Source Switcher */}
        <div className="flex border-b border-slate-200 px-6 bg-slate-50/50">
          <button
            type="button"
            onClick={() => {
              setSource('PRESETS');
              setSelectedQuestionIds(new Set());
            }}
            className={`py-2.5 px-4 text-xs font-semibold border-b-2 transition-colors ${
              source === 'PRESETS'
                ? 'border-[#d64527] text-[#d64527]'
                : 'border-transparent text-slate-500 hover:text-slate-800'
            }`}
          >
            Curated CS Question Bank ({CURATED_BANK_PRESETS.length})
          </button>
          <button
            type="button"
            onClick={() => {
              setSource('EXISTING_QUIZZES');
              setSelectedQuestionIds(new Set());
              if (existingQuizzes[0]) handleSelectQuiz(existingQuizzes[0].id);
            }}
            className={`py-2.5 px-4 text-xs font-semibold border-b-2 transition-colors ${
              source === 'EXISTING_QUIZZES'
                ? 'border-[#d64527] text-[#d64527]'
                : 'border-transparent text-slate-500 hover:text-slate-800'
            }`}
          >
            Import from Class Quizzes ({existingQuizzes.length})
          </button>
        </div>

        {/* Filter bar */}
        <div className="p-4 border-b border-slate-200 bg-white flex flex-col sm:flex-row items-center gap-3">
          {source === 'EXISTING_QUIZZES' && (
            <div className="w-full sm:w-1/2">
              <select
                value={selectedQuizId}
                onChange={(e) => handleSelectQuiz(e.target.value)}
                className="w-full px-3 py-1.5 text-xs border border-slate-300 rounded-lg bg-white focus:outline-none focus:ring-2 focus:ring-indigo-500/20"
              >
                <option value="">-- Choose Quiz Source --</option>
                {existingQuizzes.map((q) => (
                  <option key={q.id} value={q.id}>
                    {q.title} ({q.status})
                  </option>
                ))}
              </select>
            </div>
          )}

          <div className="relative flex-1 w-full">
            <Search size={14} className="absolute left-3 top-2.5 text-slate-400" />
            <input
              type="text"
              placeholder="Search questions by prompt..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full pl-8 pr-3 py-1.5 text-xs border border-slate-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500/20 placeholder:text-slate-400"
            />
          </div>

          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={handleSelectAll}
            className="text-xs h-8 shrink-0"
          >
            {selectedQuestionIds.size === filteredQuestions.length && filteredQuestions.length > 0
              ? 'Deselect All'
              : 'Select All'}
          </Button>
        </div>

        {/* Question Pool */}
        <div className="flex-1 overflow-y-auto p-4 space-y-2.5">
          {isLoadingQuizQuestions ? (
            <div className="p-8 text-center text-xs text-slate-500">
              Loading questions from selected quiz...
            </div>
          ) : filteredQuestions.length === 0 ? (
            <div className="p-8 text-center text-xs text-slate-400">
              No questions found matching your selection.
            </div>
          ) : (
            filteredQuestions.map((q, idx) => {
              const isSelected = selectedQuestionIds.has(q.id);
              return (
                <div
                  key={q.id || idx}
                  onClick={() => toggleSelect(q.id)}
                  className={`p-3 rounded-xl border text-xs cursor-pointer transition-all flex items-start gap-3 ${
                    isSelected
                      ? 'bg-indigo-50/70 border-indigo-400 shadow-xs'
                      : 'bg-white border-slate-200 hover:border-slate-300'
                  }`}
                >
                  <div
                    className={`w-5 h-5 rounded-md border flex items-center justify-center shrink-0 mt-0.5 transition-colors ${
                      isSelected
                        ? 'bg-[#d64527] border-[#d64527] text-white'
                        : 'border-slate-300 bg-white'
                    }`}
                  >
                    {isSelected && <Check size={12} strokeWidth={3} />}
                  </div>

                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 mb-1 flex-wrap">
                      <span className="font-semibold text-slate-800">{q.type}</span>
                      <span className="text-slate-400">·</span>
                      <span className="text-slate-600">{q.scoring}</span>
                      <span className="text-slate-400">·</span>
                      <span className="font-bold text-emerald-700">{q.points} pt</span>
                    </div>
                    <p className="font-medium text-slate-900 mb-2">{q.prompt}</p>

                    <div className="space-y-1 pl-2 border-l border-slate-200">
                      {q.options.map((opt, oIdx) => (
                        <div
                          key={opt.id || oIdx}
                          className={`text-[11px] truncate ${
                            opt.correct ? 'font-semibold text-emerald-800' : 'text-slate-600'
                          }`}
                        >
                          {opt.correct ? '✓ ' : '○ '}
                          {opt.text}
                        </div>
                      ))}
                    </div>
                  </div>
                </div>
              );
            })
          )}
        </div>

        {/* Footer */}
        <div className="flex items-center justify-between px-6 py-4 border-t border-slate-200 bg-slate-50">
          <span className="text-xs font-semibold text-slate-600">
            {selectedQuestionIds.size} question{selectedQuestionIds.size === 1 ? '' : 's'} selected
          </span>
          <div className="flex items-center gap-2">
            <Button type="button" variant="outline" size="sm" onClick={onClose}>
              Cancel
            </Button>
            <Button
              type="button"
              size="sm"
              disabled={selectedQuestionIds.size === 0}
              onClick={handleExecuteImport}
              className="bg-[#d64527] hover:bg-[#be3b20] text-white disabled:opacity-50"
            >
              <Plus size={14} className="mr-1" />
              Import {selectedQuestionIds.size} Question{selectedQuestionIds.size === 1 ? '' : 's'}
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
};
