import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { isAxiosError } from 'axios';
import { authApi } from '@/features/auth/api/authApi';
import { useIsDemoMode } from '@/components/BackendStatusBanner';
import { clearSession } from '@/services/apiClient';
import type { ApiErrorResponse } from '@/types/api';
import { parseSessionDevice, type DeviceCategory } from '../parseSessionDevice';

const sessionKeys = { all: ['auth', 'sessions'] as const };
const dateTime = new Intl.DateTimeFormat('en-US', {
  dateStyle: 'medium', timeStyle: 'short', timeZone: 'Asia/Ho_Chi_Minh',
});
const message = (error: unknown) => isAxiosError<ApiErrorResponse>(error)
  ? error.response?.data?.message || 'Unable to complete this action.'
  : 'Unable to connect to the server.';

function SessionDeviceIcon({ category }: { category: DeviceCategory }) {
  if (category === 'mobile') {
    return (
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <rect x="7" y="2.5" width="10" height="19" rx="2.2" />
        <line x1="11" y1="18" x2="13" y2="18" />
      </svg>
    );
  }
  if (category === 'tablet') {
    return (
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <rect x="4.5" y="3" width="15" height="18" rx="2.2" />
        <line x1="11" y1="17.5" x2="13" y2="17.5" />
      </svg>
    );
  }
  if (category === 'desktop') {
    return (
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <rect x="2.75" y="4" width="18.5" height="12.5" rx="2" />
        <line x1="8.5" y1="20" x2="15.5" y2="20" />
        <line x1="12" y1="16.5" x2="12" y2="20" />
      </svg>
    );
  }
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <circle cx="12" cy="12" r="9" />
      <path d="M3.6 9h16.8M3.6 15h16.8" />
      <path d="M12 3a13.5 13.5 0 0 1 0 18M12 3a13.5 13.5 0 0 0 0 18" />
    </svg>
  );
}

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
      setFeedback({ error: false, text: 'Device signed out.' });
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

  const orderedSessions = sessions.data
    ? [...sessions.data].sort((a, b) => Number(b.current) - Number(a.current))
    : [];

  return (
    <section className="security-sessions-section" aria-label="Signed-in devices">
      <div className="security-sessions-header">
        <div className="security-sessions-heading">
          <div className="security-sessions-title-row">
            <h3 className="security-sessions-title">Signed-in devices</h3>
            {!isDemoMode && orderedSessions.length > 0 && (
              <span className="security-sessions-count" aria-label={`${orderedSessions.length} active sessions`}>
                {orderedSessions.length}
              </span>
            )}
          </div>
          <p className="security-sessions-subtitle">
            Review active browser sessions and sign out devices you do not recognize.
          </p>
        </div>
        {!isDemoMode && orderedSessions.length > 0 && (
          <button
            type="button"
            className="security-btn-secondary"
            disabled={busyId !== null}
            onClick={() => void logoutAll()}
          >
            {busyId === 'all' ? 'Signing out…' : 'Sign out all devices'}
          </button>
        )}
      </div>

      {isDemoMode ? (
        <p role="status" className="security-empty-text">Backend connection is required to manage sessions.</p>
      ) : sessions.isPending ? (
        <p role="status" className="security-empty-text">Loading signed-in sessions…</p>
      ) : sessions.isError ? (
        <p role="alert" className="security-status-badge is-error">{message(sessions.error)}</p>
      ) : orderedSessions.length === 0 ? (
        <p className="security-empty-text">No active sessions.</p>
      ) : (
        <div className="security-sessions-list" role="list">
          {orderedSessions.map(session => {
            const parsed = parseSessionDevice(session.userAgent);
            const rawTooltip = parsed.isParsedUserAgent && parsed.rawUserAgent ? parsed.rawUserAgent : undefined;
            const titleText = session.current ? 'Current device' : parsed.label;
            return (
              <div
                key={session.id}
                className={`security-session-row ${session.current ? 'is-current' : ''}`}
                role="listitem"
              >
                <div className="security-session-main">
                  <span
                    className={`security-session-icon ${session.current ? 'is-current' : ''}`}
                    aria-hidden="true"
                  >
                    <SessionDeviceIcon category={parsed.category} />
                  </span>
                  <div className="security-session-body">
                    <div className="security-session-title-row">
                      <strong className="security-session-device" title={rawTooltip}>
                        {titleText}
                      </strong>
                      {session.current && parsed.isParsedUserAgent && (
                        <span className="security-session-browser" title={rawTooltip}>
                          {parsed.label}
                        </span>
                      )}
                      {session.rememberMe && (
                        <span className="security-mono-tag">Remembered</span>
                      )}
                    </div>
                    <div className="security-session-meta">
                      <span>Signed in {dateTime.format(new Date(session.createdAt))}</span>
                      <span className="security-session-sep" aria-hidden="true">·</span>
                      <span>Expires {dateTime.format(new Date(session.expiresAt))}</span>
                    </div>
                  </div>
                </div>
                <div className="security-session-action">
                  {session.current ? (
                    <span className="security-session-status">
                      <span className="security-status-dot" aria-hidden="true" />
                      Active now
                    </span>
                  ) : (
                    <button
                      type="button"
                      className="security-btn-danger"
                      disabled={busyId !== null}
                      onClick={() => void revoke(session.id)}
                    >
                      {busyId === session.id ? 'Signing out…' : 'Sign out this device'}
                    </button>
                  )}
                </div>
              </div>
            );
          })}
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
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [confirmed, setConfirmed] = useState(false);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (pending || !confirmed || !password || isDemoMode) return;
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
      </div>

      {isDemoMode && (
        <p className="account-danger-status" role="status">
          Backend connection is required to deactivate your account.
        </p>
      )}

      <form
        className="account-danger-form"
        onSubmit={event => void submit(event)}
        aria-busy={pending}
      >
        <div className="account-danger-field">
          <label className="account-danger-label" htmlFor="deactivate-password">
            Account password
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
            type="submit"
            className="account-danger-btn-solid"
            disabled={pending || !confirmed || !password || isDemoMode}
          >
            {pending ? 'Deactivating…' : 'Deactivate account'}
          </button>
        </div>
      </form>
    </section>
  );
}
