/**
 * Autosave manager and debounce utilities for student assessment answers
 */

export type AutosaveStatus = 'IDLE' | 'SAVING' | 'SAVED' | 'ERROR';

/**
 * Generates a standard UUID v4 for clientMutationId
 */
export function generateClientMutationId(): string {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID();
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

export interface VersionTracker {
  [questionId: string]: number;
}

export class AutosaveStateStore {
  private versions: VersionTracker = {};

  public getVersion(questionId: string): number | undefined {
    return this.versions[questionId];
  }

  public setVersion(questionId: string, version: number): void {
    const current = this.versions[questionId] ?? 0;
    if (version >= current) {
      this.versions[questionId] = version;
    }
  }

  public clear(): void {
    this.versions = {};
  }
}
