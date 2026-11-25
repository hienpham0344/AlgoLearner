import { generateClientUuid } from './quizRules';
import { quizApi } from '../api/quizApi';
import type { SaveReceipt } from '../types';

export type AutosaveStatus = 'idle' | 'saving' | 'saved' | 'error';

export interface AutosaveEntry {
  questionId: string;
  selectedOptionIds: string[];
  expectedVersion: number;
  idempotencyKey: string;
  retryCount: number;
}

export interface AutosaveQueueOptions {
  classId: string;
  quizId: string;
  attemptId: string;
  onStatusChange?: (status: AutosaveStatus, error?: string | null) => void;
  onAnswerSaved?: (receipt: SaveReceipt) => void;
  debounceMs?: number;
}

export class AutosaveQueue {
  private classId: string;
  private quizId: string;
  private attemptId: string;
  private onStatusChange?: (status: AutosaveStatus, error?: string | null) => void;
  private onAnswerSaved?: (receipt: SaveReceipt) => void;
  private debounceMs: number;

  // Question version registry: questionId -> latest expectedVersion
  private versions: Map<string, number> = new Map();
  // Pending entries per question awaiting debounce timer
  private debounceTimers: Map<string, ReturnType<typeof setTimeout>> = new Map();
  // Latest desired state per question
  private latestSelections: Map<string, string[]> = new Map();
  // Active in-flight promise per question to guarantee sequential execution
  private inFlightPerQuestion: Map<string, Promise<void>> = new Map();
  // Number of active in-flight requests across all questions
  private activeRequestCount = 0;
  // Last encountered error
  private lastError: string | null = null;
  // Current overall status
  private currentStatus: AutosaveStatus = 'idle';

  constructor(options: AutosaveQueueOptions) {
    this.classId = options.classId;
    this.quizId = options.quizId;
    this.attemptId = options.attemptId;
    this.onStatusChange = options.onStatusChange;
    this.onAnswerSaved = options.onAnswerSaved;
    this.debounceMs = options.debounceMs ?? 350;
  }

  /**
   * Initialize or update version map from existing saved answers
   */
  public initializeVersions(initialAnswers: Array<{ questionId: string; version: number }>) {
    initialAnswers.forEach((ans) => {
      this.versions.set(ans.questionId, ans.version);
    });
  }

  public getVersion(questionId: string): number {
    return this.versions.get(questionId) ?? 0;
  }

  public setVersion(questionId: string, version: number): void {
    this.versions.set(questionId, version);
  }

  public getStatus(): AutosaveStatus {
    return this.currentStatus;
  }

  public getLastError(): string | null {
    return this.lastError;
  }

  private setStatus(status: AutosaveStatus, error?: string | null) {
    this.currentStatus = status;
    this.lastError = error ?? null;
    this.onStatusChange?.(status, this.lastError);
  }

  /**
   * Enqueue answer change for a question with debounce
   */
  public enqueue(questionId: string, selectedOptionIds: string[]): void {
    this.latestSelections.set(questionId, selectedOptionIds);

    // Cancel existing debounce timer for this question if any
    const existingTimer = this.debounceTimers.get(questionId);
    if (existingTimer) {
      clearTimeout(existingTimer);
    }

    this.setStatus('saving');

    const timer = setTimeout(() => {
      this.debounceTimers.delete(questionId);
      this.dispatch(questionId);
    }, this.debounceMs);

    this.debounceTimers.set(questionId, timer);
  }

  /**
   * Dispatches the latest desired selection for questionId sequentially
   */
  private dispatch(questionId: string): void {
    const previousPromise = this.inFlightPerQuestion.get(questionId) || Promise.resolve();

    const nextPromise = previousPromise
      .then(async () => {
        const selections = this.latestSelections.get(questionId);
        if (!selections) return;

        const expectedVersion = this.getVersion(questionId);
        const idempotencyKey = generateClientUuid();

        this.activeRequestCount++;
        this.setStatus('saving');

        try {
          const receipt = await quizApi.saveAnswer(this.classId, this.quizId, this.attemptId, {
            questionId,
            selectedOptionIds: selections,
            expectedVersion,
            idempotencyKey,
          });

          // Update question version
          this.versions.set(questionId, receipt.version);
          this.onAnswerSaved?.(receipt);

          this.activeRequestCount--;
          if (this.activeRequestCount === 0 && this.debounceTimers.size === 0) {
            this.setStatus('saved');
          }
        } catch (err) {
          this.activeRequestCount--;
          const message = err instanceof Error ? err.message : 'Không thể tự động lưu câu trả lời.';
          this.setStatus('error', message);
          throw err;
        }
      })
      .catch(() => {
        // Suppress unhandled rejection in the chain since error state is recorded in status
      })
      .finally(() => {
        if (this.inFlightPerQuestion.get(questionId) === nextPromise) {
          this.inFlightPerQuestion.delete(questionId);
        }
      });

    this.inFlightPerQuestion.set(questionId, nextPromise);
  }

  /**
   * Forces flush of any pending debounced answers immediately (e.g. before submit)
   */
  public async flush(): Promise<void> {
    const timers = Array.from(this.debounceTimers.entries());
    for (const [questionId, timer] of timers) {
      clearTimeout(timer);
      this.debounceTimers.delete(questionId);
      this.dispatch(questionId);
    }

    const allInFlight = Array.from(this.inFlightPerQuestion.values());
    if (allInFlight.length > 0) {
      await Promise.allSettled(allInFlight);
    }
  }

  /**
   * Cleans up timers when component unmounts
   */
  public destroy(): void {
    for (const timer of this.debounceTimers.values()) {
      clearTimeout(timer);
    }
    this.debounceTimers.clear();
    this.latestSelections.clear();
    this.inFlightPerQuestion.clear();
  }
}
