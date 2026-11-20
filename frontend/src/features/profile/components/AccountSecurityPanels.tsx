import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { isAxiosError } from 'axios';
import { authApi } from '@/features/auth/api/authApi';
import { useIsDemoMode } from '@/components/BackendStatusBanner';
import { clearSession } from '@/services/apiClient';
import type { ApiErrorResponse } from '@/types/api';

const sessionKeys = { all: ['auth', 'sessions'] as const };
const dateTime = new Intl.DateTimeFormat('vi-VN', {
  dateStyle: 'medium', timeStyle: 'short', timeZone: 'Asia/Ho_Chi_Minh',
});
const message = (error: unknown) => isAxiosError<ApiErrorResponse>(error)
  ? error.response?.data?.message || 'Không thể hoàn tất thao tác.'
  : 'Không thể kết nối với máy chủ.';

export function SessionManager() {
  const isDemoMode = useIsDemoMode();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [busyId, setBusyId] = useState<string | null>(null);
  const [feedback, setFeedback] = useState<{ error: boolean; text: string } | null>(null);
  const sessions = useQuery({
    queryKey: sessionKeys.all,
    queryFn: authApi.sessions,
    enabled: !isDemoMode,
    retry: false,
  });

  const revoke = async (id: string) => {
    setBusyId(id); setFeedback(null);
    try {
      await authApi.revokeSession(id);
      await queryClient.invalidateQueries({ queryKey: sessionKeys.all });
      setFeedback({ error: false, text: 'Đã đăng xuất thiết bị.' });
    } catch (error) { setFeedback({ error: true, text: message(error) }); }
    finally { setBusyId(null); }
  };

  const logoutAll = async () => {
    setBusyId('all'); setFeedback(null);
    try {
      await authApi.logoutAll();
      clearSession();
      navigate('/login', { replace: true });
    } catch (error) { setFeedback({ error: true, text: message(error) }); setBusyId(null); }
  };

  return (
    <section className="security-sessions-section" aria-label="Thiết bị đăng nhập">
      <div className="security-sessions-header">
        <strong className="security-sessions-title">Thiết bị đăng nhập</strong>
        {!isDemoMode && sessions.data && sessions.data.length > 0 && (
          <button
            type="button"
            className="security-btn-secondary"
            disabled={busyId !== null}
            onClick={() => void logoutAll()}
          >
            Đăng xuất tất cả thiết bị
          </button>
        )}
      </div>

      {isDemoMode ? (
        <p role="status" className="security-empty-text">Cần kết nối backend để quản lý phiên.</p>
      ) : sessions.isPending ? (
        <p role="status" className="security-empty-text">Đang tải phiên đăng nhập…</p>
      ) : sessions.isError ? (
        <p role="alert" className="security-status-badge is-error">{message(sessions.error)}</p>
      ) : sessions.data.length === 0 ? (
        <p className="security-empty-text">Không có phiên đang hoạt động.</p>
      ) : (
        <div className="security-sessions-list" role="list">
          {sessions.data.map(session => (
            <div key={session.id} className="security-session-row" role="listitem">
              <div className="security-session-info">
                <div className="security-session-headline">
                  <span className={`security-mono-tag ${session.current ? 'is-current' : ''}`}>
                    {session.current ? 'CURRENT' : 'SESSION'}
                  </span>
                  <strong className="security-session-device">
                    {session.current ? 'Thiết bị hiện tại' : session.userAgent || 'Thiết bị khác'}
                  </strong>
                  <span className="security-session-meta">
                    · Đăng nhập: {dateTime.format(new Date(session.createdAt))} · Hết hạn: {dateTime.format(new Date(session.expiresAt))}
                  </span>
                </div>
                {session.current && (
                  <span className="security-session-status">
                    <span className="security-status-dot" aria-hidden="true" />
                    Active now
                  </span>
                )}
              </div>
              {!session.current && (
                <button
                  type="button"
                  className="security-btn-danger"
                  disabled={busyId !== null}
                  onClick={() => void revoke(session.id)}
                >
                  Đăng xuất thiết bị này
                </button>
              )}
            </div>
          ))}
        </div>
      )}

      {feedback && (
        <div
          role={feedback.error ? 'alert' : 'status'}
          className={`security-status-badge ${feedback.error ? 'is-error' : 'is-success'}`}
        >
          <span className="security-status-dot" aria-hidden="true" />
          <span>{feedback.text}</span>
        </div>
      )}
    </section>
  );
}

export function DeactivateAccountForm() {
  const isDemoMode = useIsDemoMode();
  const navigate = useNavigate();
  const [expanded, setExpanded] = useState(false);
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [confirmed, setConfirmed] = useState(false);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');

  const handleCancel = () => {
    setExpanded(false);
    setPassword('');
    setShowPassword(false);
    setConfirmed(false);
    setError('');
  };

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (pending || !confirmed || !password) return;
    setPending(true);
    setError('');
    try {
      await authApi.deactivate(password);
      clearSession();
      navigate('/login', { replace: true });
    } catch (failure) {
      setError(message(failure));
      setPending(false);
    }
  };

  return (
    <section className="account-danger-zone" aria-label="Danger Zone">
      <div className="account-danger-header">
        <div className="account-danger-copy">
          <span className="account-danger-badge">Danger Zone</span>
          <h3 className="account-danger-title">Deactivate account</h3>
          <p className="account-danger-desc">
            You will be signed out of all devices; learning data is preserved. Only an Admin can reactivate your account.
          </p>
        </div>
        {!expanded && (
          <button
            type="button"
            className="account-danger-btn-outline"
            disabled={isDemoMode}
            onClick={() => setExpanded(true)}
          >
            Deactivate…
          </button>
        )}
      </div>

      {isDemoMode && (
        <p className="account-danger-status" role="status">
          Backend connection is required to deactivate your account.
        </p>
      )}

      {expanded && (
        <form
          className="account-danger-form"
          onSubmit={event => void submit(event)}
          aria-busy={pending}
        >
          <div className="account-danger-field">
            <label className="account-danger-label" htmlFor="deactivate-password">
              Current password
            </label>
            <div className="account-danger-input-wrap">
              <input
                id="deactivate-password"
                className="account-danger-input"
                type={showPassword ? 'text' : 'password'}
                autoComplete="current-password"
                placeholder="Enter your current password"
                value={password}
                onChange={event => setPassword(event.target.value)}
                disabled={pending || isDemoMode}
                required
              />
              <button
                type="button"
                className="account-danger-toggle-pw"
                onClick={() => setShowPassword(prev => !prev)}
                disabled={pending || isDemoMode}
                aria-label={showPassword ? 'Hide password' : 'Show password'}
              >
                {showPassword ? 'Hide' : 'Show'}
              </button>
            </div>
          </div>

          <label className="account-danger-check">
            <input
              type="checkbox"
              checked={confirmed}
              onChange={event => setConfirmed(event.target.checked)}
              disabled={pending || isDemoMode}
            />
            <span>I understand that my account will be deactivated until an Admin restores it.</span>
          </label>

          {error && (
            <p className="account-danger-error" role="alert">
              {error}
            </p>
          )}

          <div className="account-danger-actions">
            <button
              type="button"
              className="account-danger-btn-ghost"
              onClick={handleCancel}
              disabled={pending}
            >
              Cancel
            </button>
            <button
              type="submit"
              className="account-danger-btn-solid"
              disabled={pending || !confirmed || !password || isDemoMode}
            >
              {pending ? 'Deactivating…' : 'Deactivate account'}
            </button>
          </div>
        </form>
      )}
    </section>
  );
}
