import { useState, type FormEvent } from 'react';
import { Link, Navigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { isAxiosError } from 'axios';
import { useAuth } from '@/hooks/useAuth';
import { useIsDemoMode } from '@/components/BackendStatusBanner';
import { LoadingScreen } from '@/components/ui/LoadingScreen';
import type { ApiErrorResponse } from '@/types/api';
import { adminAccountKeys, adminAccountsApi, type AdminAccountView } from '../api/adminAccountsApi';

const dateTime = new Intl.DateTimeFormat('en-US', {
  dateStyle: 'medium', timeStyle: 'short', timeZone: 'Asia/Ho_Chi_Minh',
});
const errorMessage = (error: unknown) => isAxiosError<ApiErrorResponse>(error)
  ? error.response?.data?.message || 'Unable to update account.'
  : 'Unable to connect to the server.';

type Action = { kind: 'suspend'; id: string; reason: string; endsAt: string | null }
  | { kind: 'unsuspend' | 'reactivate'; id: string };

function AccountRow({ account, currentId, pending, onAction }: {
  account: AdminAccountView;
  currentId: string;
  pending: boolean;
  onAction: (action: Action) => void;
}) {
  const [reason, setReason] = useState('');
  const [endsAt, setEndsAt] = useState('');
  const suspend = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const trimmed = reason.trim();
    if (!trimmed) return;
    onAction({ kind: 'suspend', id: account.id, reason: trimmed,
      endsAt: endsAt ? new Date(`${endsAt}:00+07:00`).toISOString() : null });
  };
  const own = account.id === currentId;

  return <li className="rounded-xl border border-slate-200 bg-white p-5">
    <div className="flex flex-wrap items-start justify-between gap-3">
      <div><h2 className="font-semibold text-slate-900">{account.username}</h2>
        <p className="text-sm text-slate-600">{account.email} · {account.role}</p></div>
      <span className="rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold">{account.status}</span>
    </div>
    {account.suspensionReason && <p className="mt-3 text-sm">Suspension reason: {account.suspensionReason}</p>}
    {account.suspensionEndsAt && <p className="text-sm">Until: {dateTime.format(new Date(account.suspensionEndsAt))}</p>}
    {account.loginLockedUntil && <p className="text-sm">Login locked until: {dateTime.format(new Date(account.loginLockedUntil))}</p>}
    {own ? <p className="mt-3 text-sm text-slate-600">Cannot change the status of the currently active account.</p>
      : account.status === 'SUSPENDED'
        ? <button type="button" className="mt-4 rounded-lg border border-slate-400 px-4 py-2 text-sm" disabled={pending}
          onClick={() => onAction({ kind: 'unsuspend', id: account.id })}>Unsuspend</button>
        : account.status === 'DEACTIVATED'
          ? <button type="button" className="mt-4 rounded-lg border border-slate-400 px-4 py-2 text-sm" disabled={pending}
            onClick={() => onAction({ kind: 'reactivate', id: account.id })}>Reactivate</button>
          : account.status === 'ACTIVE' || account.status === 'TEMPORARILY_LOCKED'
            ? <form className="mt-4 grid gap-3 sm:grid-cols-[1fr_auto_auto]" onSubmit={suspend}>
              <label className="text-sm">Suspension reason <input className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2" required
                value={reason} onChange={event => setReason(event.target.value)} disabled={pending} /></label>
              <label className="text-sm">Expires at (optional) <input className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
                type="datetime-local"
                value={endsAt} onChange={event => setEndsAt(event.target.value)} disabled={pending} /></label>
              <button className="self-end rounded-lg bg-slate-900 px-4 py-2 text-sm font-semibold text-white" disabled={pending || !reason.trim()}>Suspend</button>
            </form> : null}
  </li>;
}

export function AdminAccountsPage() {
  const { user, isLoading } = useAuth();
  const isDemoMode = useIsDemoMode();
  const client = useQueryClient();
  const [page, setPage] = useState(0);
  const [notice, setNotice] = useState('');
  const accounts = useQuery({
    queryKey: adminAccountKeys.list(page),
    queryFn: () => adminAccountsApi.list(page),
    enabled: user?.role === 'ADMIN' && !isDemoMode, retry: false,
  });
  const update = useMutation({
    mutationFn: (action: Action) => action.kind === 'suspend'
      ? adminAccountsApi.suspend(action.id, { reason: action.reason, endsAt: action.endsAt })
      : action.kind === 'unsuspend' ? adminAccountsApi.unsuspend(action.id) : adminAccountsApi.reactivate(action.id),
    onSuccess: async () => {
      await client.invalidateQueries({ queryKey: adminAccountKeys.all });
      setNotice('Account updated.');
    },
    onError: error => setNotice(errorMessage(error)),
  });

  if (isLoading) return <LoadingScreen variant="page" message="Loading admin accounts…" />;
  if (!user) return <Navigate to="/login" replace />;
  if (user.role !== 'ADMIN') return <Navigate to="/dashboard" replace />;
  return <main className="mx-auto min-h-screen max-w-5xl bg-slate-50 px-4 py-8 text-slate-900">
    <Link to="/dashboard" className="text-sm underline">← Dashboard</Link>
    <h1 className="mt-5 text-3xl font-bold">Account management</h1>
    <p className="mt-2 text-sm text-slate-600">Suspend, unsuspend, or reactivate user accounts.</p>
    {isDemoMode ? <p role="status" className="mt-6">Backend connection is required to manage accounts.</p>
      : accounts.isPending ? <LoadingScreen variant="inline" message="Loading accounts…" />
        : accounts.isError ? <p role="alert" className="mt-6">{errorMessage(accounts.error)} <button onClick={() => void accounts.refetch()} className="underline">Retry</button></p>
          : <><ul className="mt-6 space-y-4">{accounts.data.items.map(account => <AccountRow key={account.id}
            account={account} currentId={user.id} pending={update.isPending}
            onAction={action => { setNotice(''); update.mutate(action); }} />)}</ul>
            {accounts.data.items.length === 0 && <p className="mt-6">No accounts found.</p>}
            <div className="mt-6 flex items-center gap-4 text-sm"><button disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</button>
              <span>Page {page + 1}</span><button disabled={accounts.data.pagination.isLast} onClick={() => setPage(page + 1)}>Next</button></div></>}
    {notice && <p role={update.isError ? 'alert' : 'status'} className="mt-4">{notice}</p>}
  </main>;
}
