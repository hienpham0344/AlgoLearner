/**
 * Time and Timezone utilities for Classroom Quizzes (Module 4.7)
 * Strictly enforces ISO-8601 UTC storage/transfer and UTC+7 display for Vietnam users.
 */

const VIETNAM_TIMEZONE = 'Asia/Ho_Chi_Minh';

/**
 * Format ISO UTC string to human-readable date in Vietnam timezone (UTC+7)
 * Example: "19/11/2026, 19:30"
 */
export function formatVietnamDateTime(isoString?: string | null): string {
  if (!isoString) return '—';
  try {
    const date = new Date(isoString);
    if (isNaN(date.getTime())) return '—';

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
    return '—';
  }
}

/**
 * Format ISO UTC string with seconds in Vietnam timezone (UTC+7)
 */
export function formatVietnamDateTimeFull(isoString?: string | null): string {
  if (!isoString) return '—';
  try {
    const date = new Date(isoString);
    if (isNaN(date.getTime())) return '—';

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
    return '—';
  }
}

/**
 * Formats seconds into HH:MM:SS or MM:SS
 */
export function formatRemainingTime(seconds: number): string {
  if (seconds <= 0) return '00:00';
  const hrs = Math.floor(seconds / 3600);
  const mins = Math.floor((seconds % 3600) / 60);
  const secs = seconds % 60;

  const pad = (n: number) => n.toString().padStart(2, '0');
  if (hrs > 0) {
    return `${pad(hrs)}:${pad(mins)}:${pad(secs)}`;
  }
  return `${pad(mins)}:${pad(secs)}`;
}

/**
 * Checks whether deadline is in the past
 */
export function isPastDeadline(deadline?: string | null): boolean {
  if (!deadline) return false;
  const deadlineTime = new Date(deadline).getTime();
  return !isNaN(deadlineTime) && deadlineTime < Date.now();
}

/**
 * Checks whether start time is in the future
 */
export function isBeforeStartTime(startTime?: string | null): boolean {
  if (!startTime) return false;
  const sTime = new Date(startTime).getTime();
  return !isNaN(sTime) && sTime > Date.now();
}

/**
 * Converts a datetime-local input string into an ISO-8601 UTC string ending in 'Z'.
 * Uses Asia/Ho_Chi_Minh timezone convention if formatted YYYY-MM-DDTHH:mm
 */
export function localInputToIsoUtc(localValue?: string | null): string | null {
  if (!localValue || !localValue.trim()) return null;
  const trimmed = localValue.trim();
  // If already ends in Z or offset, parse directly
  if (trimmed.endsWith('Z') || /[+-]\d{2}:\d{2}$/.test(trimmed)) {
    return new Date(trimmed).toISOString();
  }
  // Otherwise interpret as Vietnam local (+07:00)
  const isoWithOffset = `${trimmed}:00+07:00`;
  const date = new Date(isoWithOffset);
  if (isNaN(date.getTime())) {
    // fallback to default browser Date parsing
    const fallback = new Date(trimmed);
    return isNaN(fallback.getTime()) ? null : fallback.toISOString();
  }
  return date.toISOString();
}

/**
 * Converts an ISO-8601 UTC string into a datetime-local string (YYYY-MM-DDTHH:mm) in Vietnam timezone
 */
export function isoUtcToLocalInput(isoString?: string | null): string {
  if (!isoString) return '';
  const date = new Date(isoString);
  if (isNaN(date.getTime())) return '';

  const formatter = new Intl.DateTimeFormat('sv-SE', {
    timeZone: VIETNAM_TIMEZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  });
  return formatter.format(date).replace(' ', 'T');
}

/**
 * Formats duration in seconds to a human friendly string (e.g. "45 minutes", "2 hours")
 */
export function formatDurationHuman(seconds?: number | null): string {
  if (!seconds || seconds <= 0) return 'Untimed';
  if (seconds < 60) return `${seconds}s`;
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes} min${minutes > 1 ? 's' : ''}`;
  const hours = Math.floor(minutes / 60);
  const remainingMins = minutes % 60;
  if (remainingMins === 0) return `${hours} hour${hours > 1 ? 's' : ''}`;
  return `${hours}h ${remainingMins}m`;
}
