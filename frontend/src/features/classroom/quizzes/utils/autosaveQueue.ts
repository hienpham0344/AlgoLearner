/**
 * Concurrency-safe Autosave Manager for Classroom Quizzes
 * Ensures:
 * 1. Sequential execution per question (no older in-flight request overwriting newer selection)
 * 2. Proper debouncing
 * 3. Accurate status reflection ('idle' | 'saving' | 'saved' | 'error')
 * 4. Offline resilience & manual/automatic retry
 */

import type { AutosaveStatus } from '../types';

export interface AutosaveEntry {
  questionId: string;
  optionIds: string[];
  status: AutosaveStatus;
  lastSavedAt?: number;
  errorMessage?: string;
}

export type AutosaveSubscriber = (
  questionId: string,
  entry: AutosaveEntry,
  globalStatus: AutosaveStatus
) => void;

export class QuizAutosaveManager {
  private entries: Map<string, AutosaveEntry> = new Map();
  private pendingPayloads: Map<string, string[]> = new Map();
  private inFlightQuestions: Set<string> = new Set();
  private debounceTimers: Map<string, ReturnType<typeof setTimeout>> = new Map();
  private subscribers: Set<AutosaveSubscriber> = new Set();
  private saveFn: (questionId: string, optionIds: string[]) => Promise<void>;
  private debounceMs: number;

  constructor(
    saveFn: (questionId: string, optionIds: string[]) => Promise<void>,
    debounceMs: number = 400
  ) {
    this.saveFn = saveFn;
    this.debounceMs = debounceMs;
  }

  public subscribe(cb: AutosaveSubscriber): () => void {
    this.subscribers.add(cb);
    return () => {
      this.subscribers.delete(cb);
    };
  }

  public getEntry(questionId: string): AutosaveEntry {
    return (
      this.entries.get(questionId) || {
        questionId,
        optionIds: [],
        status: 'idle',
      }
    );
  }

  public getGlobalStatus(): AutosaveStatus {
    let hasError = false;
    let hasSaving = false;

    for (const entry of this.entries.values()) {
      if (entry.status === 'error') hasError = true;
      if (entry.status === 'saving') hasSaving = true;
    }

    if (this.pendingPayloads.size > 0 || this.inFlightQuestions.size > 0 || hasSaving) {
      return 'saving';
    }
    if (hasError) return 'error';
    return 'saved';
  }

  public notify(questionId: string) {
    const entry = this.getEntry(questionId);
    const globalStatus = this.getGlobalStatus();
    for (const sub of this.subscribers) {
      sub(questionId, entry, globalStatus);
    }
  }

  /**
   * Schedule an autosave for a specific question
   */
  public enqueue(questionId: string, optionIds: string[]) {
    // Clear any active debounce timer for this question
    const existingTimer = this.debounceTimers.get(questionId);
    if (existingTimer) {
      clearTimeout(existingTimer);
      this.debounceTimers.delete(questionId);
    }

    // Record pending payload
    this.pendingPayloads.set(questionId, [...optionIds]);

    // Update status to saving
    this.entries.set(questionId, {
      questionId,
      optionIds: [...optionIds],
      status: 'saving',
    });
    this.notify(questionId);

    // Schedule run after debounce
    const timer = setTimeout(() => {
      this.debounceTimers.delete(questionId);
      void this.processQuestion(questionId);
    }, this.debounceMs);

    this.debounceTimers.set(questionId, timer);
  }

  /**
   * Immediately flush all pending autosaves (e.g. before submitting)
   */
  public async flush(): Promise<void> {
    // Clear all timers and trigger them immediately
    for (const [qId, timer] of this.debounceTimers.entries()) {
      clearTimeout(timer);
      this.debounceTimers.delete(qId);
      void this.processQuestion(qId);
    }

    // Wait until all in-flight requests complete
    while (this.inFlightQuestions.size > 0) {
      await new Promise((res) => setTimeout(res, 50));
    }
  }

  /**
   * Retry failed autosave for a question
   */
  public retry(questionId: string) {
    const entry = this.entries.get(questionId);
    if (entry && entry.status === 'error') {
      this.enqueue(questionId, entry.optionIds);
    }
  }

  /**
   * Retry all failed autosaves
   */
  public retryAll() {
    for (const [qId, entry] of this.entries.entries()) {
      if (entry.status === 'error') {
        this.enqueue(qId, entry.optionIds);
      }
    }
  }

  private async processQuestion(questionId: string) {
    // If another request for this question is currently in flight, let it finish;
    // it will pick up the latest payload when done.
    if (this.inFlightQuestions.has(questionId)) {
      return;
    }

    const payload = this.pendingPayloads.get(questionId);
    if (!payload) return;

    this.pendingPayloads.delete(questionId);
    this.inFlightQuestions.add(questionId);

    this.entries.set(questionId, {
      questionId,
      optionIds: payload,
      status: 'saving',
    });
    this.notify(questionId);

    try {
      await this.saveFn(questionId, payload);

      // Successfully saved
      this.entries.set(questionId, {
        questionId,
        optionIds: payload,
        status: 'saved',
        lastSavedAt: Date.now(),
      });
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to save answer';
      this.entries.set(questionId, {
        questionId,
        optionIds: payload,
        status: 'error',
        errorMessage: msg,
      });
    } finally {
      this.inFlightQuestions.delete(questionId);
      this.notify(questionId);

      // If a newer payload arrived while this request was in flight, process it immediately!
      if (this.pendingPayloads.has(questionId)) {
        void this.processQuestion(questionId);
      }
    }
  }

  public destroy() {
    for (const timer of this.debounceTimers.values()) {
      clearTimeout(timer);
    }
    this.debounceTimers.clear();
    this.subscribers.clear();
  }
}
