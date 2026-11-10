import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { isAxiosError } from 'axios';
import type { ApiErrorResponse } from '@/types/api';
import { useAuth } from '@/hooks/useAuth';
import { authApi, authKeys } from '../api/authApi';

const field = 'w-full rounded-lg border border-slate-300 px-3 py-2';
const button = 'rounded-lg bg-slate-900 px-4 py-2 font-medium text-white disabled:opacity-50';
const secondary = 'rounded-lg border border-slate-300 px-3 py-2 font-medium disabled:opacity-50';

function errorText(error: unknown): string {
  if (isAxiosError<ApiErrorResponse>(error)) return error.response?.data?.message || 'Request failed. Please try again.';
  return 'Request failed. Please try again.';
}

export function AccountSecurityPage() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [deactivatePassword, setDeactivatePassword] = useState('');
  const [feedback, setFeedback] = useState('');
  const [error, setError] = useState('');

  const sessions = useQuery({ queryKey: authKeys.sessions(), queryFn: authApi.sessions, retry: false });
  const change = useMutation({
    mutationFn: () => authApi.changePassword(currentPassword, newPassword),
    onSuccess: () => { setCurrentPassword(''); setNewPassword(''); setConfirmPassword(''); setError(''); setFeedback('Password updated. Other devices have been signed out.'); queryClient.invalidateQueries({ queryKey: authKeys.sessions() }); },
    onError: cause => setError(errorText(cause)),
  });
  const revoke = useMutation({
    mutationFn: authApi.revokeSession,
    onSuccess: () => { setError(''); setFeedback('Session revoked.'); queryClient.invalidateQueries({ queryKey: authKeys.sessions() }); },
    onError: cause => setError(errorText(cause)),
  });
  const all = useMutation({
    mutationFn: authApi.logoutAll,
    onSuccess: () => { logout(); navigate('/login', { replace: true }); },
    onError: cause => setError(errorText(cause)),
  });
  const deactivate = useMutation({
    mutationFn: () => authApi.deactivate(deactivatePassword),
    onSuccess: () => { logout(); navigate('/login', { replace: true }); },
    onError: cause => setError(errorText(cause)),
  });

  const submitPassword = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault(); setError(''); setFeedback('');
    if (newPassword.length < 8 || !/[A-Za-z]/.test(newPassword) || !/\d/.test(newPassword)) {
      setError('Use at least 8 characters, including a letter and a number.'); return;
    }
    if (newPassword !== confirmPassword) { setError('New passwords do not match.'); return; }
    change.mutate();
  };

  return <main className="min-h-screen bg-slate-50 px-4 py-8 text-slate-900">
    <div className="mx-auto max-w-2xl space-y-8">
      <header><Link to="/dashboard" className="text-sm underline">← Back to workspace</Link><h1 className="mt-4 text-3xl font-bold">Account security</h1><p className="mt-2 text-slate-600">{user?.email}</p></header>
      {feedback && <p role="status" className="rounded-lg bg-green-50 p-3 text-green-900">{feedback}</p>}
      {error && <p role="alert" className="rounded-lg bg-red-50 p-3 text-red-900">{error}</p>}

      <section className="rounded-xl border bg-white p-5" aria-labelledby="change-password-heading">
        <h2 id="change-password-heading" className="text-xl font-semibold">Change password</h2>
        <form onSubmit={submitPassword} className="mt-4 space-y-3">
          <label className="block">Current password<input className={field} type="password" autoComplete="current-password" required value={currentPassword} onChange={event => setCurrentPassword(event.target.value)} /></label>
          <label className="block">New password<input className={field} type="password" autoComplete="new-password" required value={newPassword} onChange={event => setNewPassword(event.target.value)} /></label>
          <label className="block">Confirm new password<input className={field} type="password" autoComplete="new-password" required value={confirmPassword} onChange={event => setConfirmPassword(event.target.value)} /></label>
          <button className={button} disabled={change.isPending}>Save password</button>
        </form>
      </section>

      <section className="rounded-xl border bg-white p-5" aria-labelledby="sessions-heading">
        <h2 id="sessions-heading" className="text-xl font-semibold">Signed-in devices</h2>
        {sessions.isPending && <p className="mt-3">Loading sessions…</p>}
        {sessions.isError && <p role="alert" className="mt-3">{errorText(sessions.error)}</p>}
        {sessions.data?.length === 0 && <p className="mt-3">No active sessions.</p>}
        <ul className="mt-3 divide-y">
          {sessions.data?.map(session => <li key={session.id} className="flex items-center justify-between gap-3 py-3">
            <div><strong>{session.deviceLabel || 'Unknown device'}</strong>{session.current && <span className="ml-2 text-sm">Current</span>}<p className="text-sm text-slate-600">Last active {new Date(session.lastSeenAt).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' })}</p></div>
            {!session.current && <button type="button" className={secondary} disabled={revoke.isPending} onClick={() => revoke.mutate(session.id)}>Revoke</button>}
          </li>)}
        </ul>
        <button type="button" className={secondary} disabled={all.isPending || !sessions.data?.length} onClick={() => all.mutate()}>Sign out all devices</button>
      </section>

      {user?.role !== 'ADMIN' && <section className="rounded-xl border border-red-200 bg-white p-5" aria-labelledby="deactivate-heading">
        <h2 id="deactivate-heading" className="text-xl font-semibold">Deactivate account</h2>
        <p className="mt-2 text-sm text-slate-600">Your account will be disabled and all sessions revoked. An administrator can reactivate it.</p>
        <form className="mt-4 space-y-3" onSubmit={event => { event.preventDefault(); if (window.confirm('Deactivate your account?')) deactivate.mutate(); }}>
          <label className="block">Confirm with your password<input className={field} type="password" autoComplete="current-password" required value={deactivatePassword} onChange={event => setDeactivatePassword(event.target.value)} /></label>
          <button className="rounded-lg bg-red-700 px-4 py-2 font-medium text-white disabled:opacity-50" disabled={deactivate.isPending}>Deactivate account</button>
        </form>
      </section>}
    </div>
  </main>;
}
