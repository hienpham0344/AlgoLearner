/**
 * Timezone and Date-time utilities for Module 4.7 (Classroom Quiz)
 * Enforces ISO-8601 UTC for backend exchange and Vietnam Time (UTC+7) for user display.
 */

const VIETNAM_TIMEZONE = 'Asia/Ho_Chi_Minh';

export function formatVietnamDateTime(isoString?: string | null): string {
  if (!isoString) return '—';
  try {
    const date = new Date(isoString);
    if (isNaN(date.getTime())) return 'Invalid date';

    return new Intl.DateTimeFormat('vi-VN', {
      timeZone: VIETNAM_TIMEZONE,
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      hour12: false,
    }).format(date);
  } catch {
    return 'Invalid date';
  }
}

export function formatVietnamDateTimeFull(isoString?: string | null): string {
  if (!isoString) return '—';
  try {
    const date = new Date(isoString);
    if (isNaN(date.getTime())) return 'Invalid date';

    return new Intl.DateTimeFormat('vi-VN', {
      timeZone: VIETNAM_TIMEZONE,
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hour12: false,
    }).format(date);
  } catch {
    return 'Invalid date';
  }
}

export function localInputToIsoUtc(localValue?: string | null): string | null {
  if (!localValue) return null;
  const date = new Date(localValue);
  if (isNaN(date.getTime())) return null;
  return date.toISOString();
}

export function isoUtcToLocalInput(isoString?: string | null): string {
  if (!isoString) return '';
  const date = new Date(isoString);
  if (isNaN(date.getTime())) return '';

  const pad = (n: number) => n.toString().padStart(2, '0');
  const year = date.getFullYear();
  const month = pad(date.getMonth() + 1);
  const day = pad(date.getDate());
  const hours = pad(date.getHours());
  const minutes = pad(date.getMinutes());

  return `${year}-${month}-${day}T${hours}:${minutes}`;
}

export function formatDurationMinutes(durationSeconds?: number | null): string {
  if (durationSeconds === undefined || durationSeconds === null) return 'No limit';
  if (durationSeconds < 60) return `${durationSeconds}s`;
  const minutes = Math.floor(durationSeconds / 60);
  const remSeconds = durationSeconds % 60;
  if (remSeconds === 0) return `${minutes} mins`;
  return `${minutes}m ${remSeconds}s`;
}

export function isPastDeadline(closesAt?: string | null): boolean {
  if (!closesAt) return false;
  const time = new Date(closesAt).getTime();
  return !isNaN(time) && time < Date.now();
}

export function isBeforeStartTime(opensAt?: string | null): boolean {
  if (!opensAt) return false;
  const time = new Date(opensAt).getTime();
  return !isNaN(time) && time > Date.now();
}

/**
 * Calculates remaining seconds using server cutoff and client-server clock drift.
 * @param cutoffAt ISO-8601 UTC string when attempt expires
 * @param clockDriftMs offset in ms: (serverTime - clientTime)
 */
export function calculateRemainingSeconds(
  cutoffAt?: string | null,
  clockDriftMs: number = 0
): number | null {
  if (!cutoffAt) return null;
  const cutoffTime = new Date(cutoffAt).getTime();
  if (isNaN(cutoffTime)) return null;

  const currentServerTime = Date.now() + clockDriftMs;
  const diffMs = cutoffTime - currentServerTime;
  return Math.max(0, Math.floor(diffMs / 1000));
}

/**
 * Formats countdown seconds into mm:ss or hh:mm:ss string
 */
export function formatCountdown(totalSeconds: number | null): string {
  if (totalSeconds === null) return 'No limit';
  if (totalSeconds <= 0) return '00:00';

  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;

  const pad = (n: number) => n.toString().padStart(2, '0');

  if (hours > 0) {
    return `${pad(hours)}:${pad(minutes)}:${pad(seconds)}`;
  }
  return `${pad(minutes)}:${pad(seconds)}`;
}

/**
 * Returns visual urgency level for remaining time
 */
export function getTimerUrgency(seconds: number | null): 'normal' | 'warning' | 'critical' | 'expired' {
  if (seconds === null) return 'normal';
  if (seconds <= 0) return 'expired';
  if (seconds <= 60) return 'critical'; // < 1 minute: pulse red
  if (seconds <= 300) return 'warning'; // < 5 minutes: amber
  return 'normal';
}
