import type { IdentityType } from '../types';
import { useRef, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { CircleAlert } from 'lucide-react';
import { authApi } from '../api/authApi';
import { AuthField, AuthSubmit } from '../components/AuthControls';
import { isAxiosError } from 'axios';
import type { ApiErrorResponse } from '@/types/api';

export const ForgotPasswordPage = () => {
  const navigate = useNavigate();
  const [identity, setIdentity] = useState('');
  const [identityType, setIdentityType] = useState<IdentityType>('AUTO');
  const [isLoading, setIsLoading] = useState(false);
  const [fieldError, setFieldError] = useState<string | null>(null);
  const [requestError, setRequestError] = useState<string | null>(null);
  const inFlight = useRef(false);
  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (inFlight.current) return;
    setFieldError(null);
    setRequestError(null);
    const value = identity.trim();
    if (!value) {
      setFieldError('Enter your username or email.');
      document.getElementById('forgot-email')?.focus();
      return;
    }
    inFlight.current = true;
    setIsLoading(true);
    try {
      await authApi.forgotPassword({ usernameOrEmail: value, identityType });
      navigate(`/verify-otp?usernameOrEmail=${encodeURIComponent(value)}&identityType=${identityType}&purpose=RESET_PASSWORD`);
    } catch (failure: unknown) {
      setRequestError(isAxiosError<ApiErrorResponse>(failure) && failure.response?.data.message
        ? failure.response.data.message : 'We could not send the reset code. Please try again.');
    } finally { setIsLoading(false); inFlight.current = false; }
  };
  return (
    <>
      <div className="auth-form-intro">
        <h1 tabIndex={-1}>Forgot your password?</h1>
        <p className="auth-description">We’ll send you a code to reset it.</p>
        {requestError && <p className="auth-inline-feedback" role="alert"><CircleAlert size={15} strokeWidth={1.8} aria-hidden="true" />{requestError}</p>}
      </div>
      <form className="auth-form" onSubmit={handleSubmit} noValidate aria-busy={isLoading}>
        <AuthField id="forgot-email" label="Username or email" autoComplete="username" placeholder="Username or email" required value={identity} onChange={event => { setIdentity(event.target.value); setFieldError(null); setRequestError(null); }} disabled={isLoading} message={fieldError || undefined} />
      <label htmlFor="identity-type">Identify account by</label>
      <select id="identity-type" className="auth-input" value={identityType} onChange={event => setIdentityType(event.target.value as IdentityType)} disabled={isLoading}>
        <option value="AUTO">Username or email</option><option value="EMAIL">Email</option><option value="USERNAME">Username</option>
      </select>
        <AuthSubmit loading={isLoading}>{isLoading ? 'Sending code…' : 'Send reset code'}</AuthSubmit>
      </form>
      <Link to="/login" className="auth-back-link">Back to sign in</Link>
    </>
  );
};
