import { useRef, useState, type FormEvent } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { authApi } from '../api/authApi';
import { AuthField, AuthSubmit } from '../components/AuthControls';
import { AuthFeedback } from '../components/AuthFeedback';

interface ResetState {
  email: string;
  resetToken: string;
}

function isResetState(value: unknown): value is ResetState {
  return typeof value === 'object' && value !== null
    && 'email' in value && typeof value.email === 'string' && value.email.length > 0
    && 'resetToken' in value && typeof value.resetToken === 'string' && value.resetToken.length > 0;
}

export const ResetPasswordPage = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const resetState = isResetState(location.state) ? location.state : null;
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState<{ field?: string; message: string } | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const inFlight = useRef(false);

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (inFlight.current || !resetState) return;
    setError(null);
    let issue: typeof error = null;
    if (newPassword.length < 8) issue = { field: 'reset-new-password', message: 'Use at least 8 characters for your password.' };
    else if (newPassword !== confirmPassword) issue = { field: 'reset-confirm-password', message: "Your passwords don't match." };
    if (issue) { setError(issue); if (issue.field) document.getElementById(issue.field)?.focus(); return; }
    inFlight.current = true;
    setIsLoading(true);
    try {
      await authApi.resetPassword({ ...resetState, newPassword, confirmPassword });
      navigate('/login?resetSuccess=true', { replace: true, state: null });
    } catch { setError({ message: 'This reset session has expired or could not be used. Request a new code and try again.' }); }
    finally { setIsLoading(false); inFlight.current = false; }
  };

  if (!resetState) return <Navigate to="/forgot-password" replace />;

  return (
    <>
      <h1 tabIndex={-1}>Set a new password.</h1>
      <AuthFeedback title={error ? 'Check your details.' : undefined} description={error?.message || <>Email verified for <strong>{resetState.email}</strong>. Choose a new password.</>} />
      <form className="auth-form" onSubmit={handleSubmit} noValidate aria-busy={isLoading}>
        <input className="auth-visually-hidden" type="text" name="username" autoComplete="username" value={resetState.email} readOnly tabIndex={-1} aria-hidden="true" />
        <AuthField id="reset-new-password" label="New password" type="password" autoComplete="new-password" placeholder="At least 8 characters" value={newPassword} onChange={event => setNewPassword(event.target.value)} required disabled={isLoading} aria-invalid={error?.field === 'reset-new-password'} aria-describedby="auth-feedback" />
        <AuthField id="reset-confirm-password" label="Confirm password" type="password" autoComplete="new-password" placeholder="Repeat your password" value={confirmPassword} onChange={event => setConfirmPassword(event.target.value)} required disabled={isLoading} aria-invalid={error?.field === 'reset-confirm-password'} aria-describedby="auth-feedback" />
        <AuthSubmit loading={isLoading}>{isLoading ? 'Updating password…' : 'Update password'}</AuthSubmit>
      </form>
      <div className="auth-form-actions"><Link to="/login" className="auth-back-link">Back to sign in</Link><Link to="/forgot-password" className="auth-back-link">Request a new code</Link></div>
    </>
  );
};
