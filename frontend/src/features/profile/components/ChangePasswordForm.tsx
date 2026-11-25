import { useState, type FormEvent } from 'react';
import { isAxiosError } from 'axios';
import { authApi } from '@/features/auth/api/authApi';
import type { ApiErrorResponse } from '@/types/api';

export function ChangePasswordForm() {
  const [currentPassword, setCurrent] = useState('');
  const [newPassword, setNew] = useState('');
  const [confirmPassword, setConfirm] = useState('');
  const [pending, setPending] = useState(false);
  const [feedback, setFeedback] = useState<{ error: boolean; message: string } | null>(null);

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (pending) return;
    setFeedback(null);
    if (
      newPassword.length < 8 ||
      !/[A-Za-z]/.test(newPassword) ||
      !/[0-9]/.test(newPassword) ||
      new TextEncoder().encode(newPassword).length > 72
    ) {
      setFeedback({
        error: true,
        message: 'Use at least 8 characters with a letter and a number, within 72 UTF-8 bytes.',
      });
      return;
    }
    if (newPassword !== confirmPassword) {
      setFeedback({ error: true, message: 'Passwords do not match.' });
      return;
    }
    setPending(true);
    try {
      const result = await authApi.changePassword({ currentPassword, newPassword, confirmPassword });
      setCurrent('');
      setNew('');
      setConfirm('');
      setFeedback({ error: false, message: result.message });
    } catch (failure: unknown) {
      setFeedback({
        error: true,
        message:
          isAxiosError<ApiErrorResponse>(failure) && failure.response?.data.message
            ? failure.response.data.message
            : 'Could not change your password. Please try again.',
      });
    } finally {
      setPending(false);
    }
  };

  return (
    <form className="security-form-list" onSubmit={submit} aria-busy={pending}>
      <div className="security-form-row">
        <label htmlFor="change-current" className="security-form-label">
          Current password
        </label>
        <input
          id="change-current"
          className="folder-input"
          type="password"
          autoComplete="current-password"
          value={currentPassword}
          onChange={e => setCurrent(e.target.value)}
          required
          disabled={pending}
        />
      </div>

      <div className="security-form-row">
        <div className="security-label-col">
          <label htmlFor="change-new" className="security-form-label">
            New password
          </label>
          <span className="security-label-hint">8+ chars, letter & number</span>
        </div>
        <input
          id="change-new"
          className="folder-input"
          type="password"
          autoComplete="new-password"
          value={newPassword}
          onChange={e => setNew(e.target.value)}
          required
          disabled={pending}
        />
      </div>

      <div className="security-form-row">
        <label htmlFor="change-confirm" className="security-form-label">
          Confirm new password
        </label>
        <input
          id="change-confirm"
          className="folder-input"
          type="password"
          autoComplete="new-password"
          value={confirmPassword}
          onChange={e => setConfirm(e.target.value)}
          required
          disabled={pending}
        />
      </div>

      <div className="security-form-footer">
        <div>
          {feedback && (
            <div
              role={feedback.error ? 'alert' : 'status'}
              className={`security-status-badge ${feedback.error ? 'is-error' : 'is-success'}`}
            >
              <span className="security-status-dot" aria-hidden="true" />
              <span>{feedback.message}</span>
            </div>
          )}
        </div>
        <button className="folder-btn-save" type="submit" disabled={pending}>
          {pending ? 'Updating…' : 'Change password'}
        </button>
      </div>
    </form>
  );
}
