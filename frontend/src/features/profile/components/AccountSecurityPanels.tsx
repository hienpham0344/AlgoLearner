import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { isAxiosError } from 'axios';
import { authApi } from '@/features/auth/api/authApi';
import { useApiSource } from '@/components/BackendStatusBanner';
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
  const source = useApiSource();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [busyId, setBusyId] = useState<string | null>(null);
  const [feedback, setFeedback] = useState<{ error: boolean; text: string } | null>(null);
  const sessions = useQuery({
    queryKey: sessionKeys.all,
    queryFn: authApi.sessions,
    enabled: source === 'backend',
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

  return <section className="folder-settings-list" aria-label="Thiết bị đăng nhập">
    <div className="folder-editorial-row"><div className="folder-row-info">
      <strong className="folder-row-title">Thiết bị đăng nhập</strong>
      <p className="folder-row-desc">Xem và thu hồi các phiên của tài khoản này.</p>
    </div></div>
    {source !== 'backend' ? <p role="status">Cần kết nối backend để quản lý phiên.</p>
      : sessions.isPending ? <p role="status">Đang tải phiên đăng nhập…</p>
        : sessions.isError ? <p role="alert">{message(sessions.error)}</p>
          : sessions.data.length === 0 ? <p>Không có phiên đang hoạt động.</p>
            : <ul className="space-y-3">{sessions.data.map(session => <li key={session.id} className="rounded-lg border border-slate-200 p-3 text-sm">
              <strong>{session.current ? 'Thiết bị hiện tại' : session.userAgent || 'Thiết bị khác'}</strong>
              <p>Đăng nhập: {dateTime.format(new Date(session.createdAt))} · Hết hạn: {dateTime.format(new Date(session.expiresAt))}</p>
              {!session.current && <button type="button" className="folder-action-pill" disabled={busyId !== null}
                onClick={() => void revoke(session.id)}>Đăng xuất thiết bị này</button>}
            </li>)}</ul>}
    {source === 'backend' && sessions.data && sessions.data.length > 0 &&
      <button type="button" className="folder-action-pill" disabled={busyId !== null}
        onClick={() => void logoutAll()}>Đăng xuất tất cả thiết bị</button>}
    {feedback && <p role={feedback.error ? 'alert' : 'status'}>{feedback.text}</p>}
  </section>;
}

export function DeactivateAccountForm() {
  const source = useApiSource();
  const navigate = useNavigate();
  const [password, setPassword] = useState('');
  const [confirmed, setConfirmed] = useState(false);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (pending || !confirmed || !password) return;
    setPending(true); setError('');
    try {
      await authApi.deactivate(password);
      clearSession();
      navigate('/login', { replace: true });
    } catch (failure) { setError(message(failure)); setPending(false); }
  };

  return <form className="folder-settings-list" onSubmit={event => void submit(event)} aria-busy={pending}>
    <div className="folder-editorial-row"><div className="folder-row-info">
      <strong className="folder-row-title">Vô hiệu hóa tài khoản</strong>
      <p className="folder-row-desc">Bạn sẽ đăng xuất khỏi mọi thiết bị; dữ liệu học tập được giữ lại. Chỉ Admin có thể kích hoạt lại.</p>
    </div></div>
    <label htmlFor="deactivate-password">Mật khẩu hiện tại</label>
    <input id="deactivate-password" className="folder-input" type="password" autoComplete="current-password"
      value={password} onChange={event => setPassword(event.target.value)} disabled={pending || source !== 'backend'} required />
    <label className="flex items-start gap-2 text-sm"><input type="checkbox" checked={confirmed}
      onChange={event => setConfirmed(event.target.checked)} disabled={pending || source !== 'backend'} />
      Tôi xác nhận muốn vô hiệu hóa tài khoản.</label>
    <button className="folder-action-pill" type="submit" disabled={pending || !confirmed || !password || source !== 'backend'}>Vô hiệu hóa</button>
    {source !== 'backend' && <p role="status">Cần kết nối backend để vô hiệu hóa tài khoản.</p>}
    {error && <p role="alert">{error}</p>}
  </form>;
}
