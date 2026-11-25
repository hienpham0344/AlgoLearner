/**
 * Timezone and Date-time utilities for AlgoLearner Module 4.6
 * Ensures backend ISO-8601 UTC strings are strictly handled and displayed in Vietnam Time (UTC+7).
 */

const VIETNAM_TIMEZONE = 'Asia/Ho_Chi_Minh';

/**
 * Formats an ISO UTC timestamp string into a readable date-time string in UTC+7
 * Example output: "20/11/2026, 14:30"
 */
export function formatVietnamDateTime(isoString?: string | null): string {
  if (!isoString) return '—';
  try {
    const date = new Date(isoString);
    if (isNaN(date.getTime())) return 'Invalid date';

    return new Intl.DateTimeFormat('en-GB', {
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

/**
 * Formats an ISO UTC timestamp string with seconds included
 */
export function formatVietnamDateTimeFull(isoString?: string | null): string {
  if (!isoString) return '—';
  try {
    const date = new Date(isoString);
    if (isNaN(date.getTime())) return 'Invalid date';

    return new Intl.DateTimeFormat('en-GB', {
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

/**
 * Formats a duration in seconds into MM:SS or HH:MM:SS
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
 * Checks if a given deadline timestamp has passed according to the current client/server time
 */
export function isPastDeadline(deadline?: string | null): boolean {
  if (!deadline) return false;
  const deadlineTime = new Date(deadline).getTime();
  return !isNaN(deadlineTime) && deadlineTime < Date.now();
}

/**
 * Checks if an assignment has a start time in the future
 */
export function isBeforeStartTime(startTime?: string | null): boolean {
  if (!startTime) return false;
  const sTime = new Date(startTime).getTime();
  return !isNaN(sTime) && sTime > Date.now();
}

/**
 * Converts a browser datetime-local input string (YYYY-MM-DDTHH:mm) to an ISO-8601 UTC string (ending with Z).
 */
export function localInputToIsoUtc(localValue?: string | null): string | null {
  if (!localValue) return null;
  const date = new Date(localValue);
  if (isNaN(date.getTime())) return null;
  return date.toISOString();
}

/**
 * Converts an ISO-8601 UTC string to a local string suitable for <input type="datetime-local" />
 * Format: YYYY-MM-DDTHH:mm
 */
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
