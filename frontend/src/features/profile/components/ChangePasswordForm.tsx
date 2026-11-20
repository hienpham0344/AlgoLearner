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
    if (newPassword.length < 8 || !/[A-Za-z]/.test(newPassword) || !/[0-9]/.test(newPassword)
      || new TextEncoder().encode(newPassword).length > 72) {
      setFeedback({ error: true, message: 'Use at least 8 characters with a letter and a number, within 72 UTF-8 bytes.' });
      return;
    }
    if (newPassword !== confirmPassword) { setFeedback({ error: true, message: 'Passwords do not match.' }); return; }
    setPending(true);
    try {
      const result = await authApi.changePassword({ currentPassword, newPassword, confirmPassword });
      setCurrent(''); setNew(''); setConfirm('');
      setFeedback({ error: false, message: result.message });
    } catch (failure: unknown) {
      setFeedback({ error: true, message: isAxiosError<ApiErrorResponse>(failure) && failure.response?.data.message
        ? failure.response.data.message : 'Could not change your password. Please try again.' });
    } finally { setPending(false); }
  };
  return <form className="folder-settings-list" onSubmit={submit} aria-busy={pending}>
    <div className="folder-editorial-row"><label htmlFor="change-current">Current password</label>
      <input id="change-current" className="folder-input" type="password" autoComplete="current-password" value={currentPassword} onChange={e => setCurrent(e.target.value)} required disabled={pending} /></div>
    <div className="folder-editorial-row"><label htmlFor="change-new">New password</label>
      <input id="change-new" className="folder-input" type="password" autoComplete="new-password" value={newPassword} onChange={e => setNew(e.target.value)} required disabled={pending} /></div>
    <div className="folder-editorial-row"><label htmlFor="change-confirm">Confirm new password</label>
      <input id="change-confirm" className="folder-input" type="password" autoComplete="new-password" value={confirmPassword} onChange={e => setConfirm(e.target.value)} required disabled={pending} /></div>
    {feedback && <p role={feedback.error ? 'alert' : 'status'}>{feedback.message}</p>}
    <button className="folder-action-pill" type="submit" disabled={pending}>{pending ? 'Updating…' : 'Change password'}</button>
  </form>;
}
