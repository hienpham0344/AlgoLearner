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
  if (remSeconds === 0) return `${minutes} min`;
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
