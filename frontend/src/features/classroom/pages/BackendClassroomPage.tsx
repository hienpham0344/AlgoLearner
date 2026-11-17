import { useEffect, useState, type FormEvent } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useAuth } from '@/hooks/useAuth';
import { ClassroomLayout } from '../components/ClassroomLayout';
import { classroomApi, classroomError } from '../api/classroomApi';
import type { DemoPersona, DemoRole, JoinMode } from '../types';

const field = 'w-full rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-900';
const button = 'rounded-lg bg-slate-900 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50';
const secondary = 'rounded-lg border border-slate-300 px-4 py-2 text-sm font-semibold text-slate-800 disabled:opacity-50';
const toVietnamInput = (value: string) => new Intl.DateTimeFormat('sv-SE', {
  timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
}).format(new Date(value)).replace(' ', 'T');
const fromVietnamInput = (value: string) => new Date(`${value}:00+07:00`).toISOString();

function PageControls({ page, totalPages, onPage }: { page: number; totalPages: number; onPage: (page: number) => void }) {
  if (totalPages <= 1) return null;
  return <div className="mt-5 flex items-center gap-3 text-sm">
    <button className={secondary} type="button" disabled={page === 0} onClick={() => onPage(page - 1)}>Previous</button>
    <span>Page {page + 1} of {totalPages}</span>
    <button className={secondary} type="button" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)}>Next</button>
  </div>;
}

export function BackendClassroomPage({ role }: { role: DemoRole }) {
  const { id } = useParams<{ id: string }>();
  const { user } = useAuth();
  const navigate = useNavigate();
  const client = useQueryClient();
  const [pageNumber, setPageNumber] = useState(0);
  const [memberPage, setMemberPage] = useState(0);
  const [requestPage, setRequestPage] = useState(0);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [joinMode, setJoinMode] = useState<JoinMode>('AUTO_JOIN');
  const [ownerId, setOwnerId] = useState('');
  const [classCode, setClassCode] = useState('');
  const [invitationId, setInvitationId] = useState('');
  const [student, setStudent] = useState('');
  const [email, setEmail] = useState('');
  const [expiresAt, setExpiresAt] = useState('');
  const [codeExpiresAt, setCodeExpiresAt] = useState('');
  const [codeLocked, setCodeLocked] = useState(false);
  const [joiningOpen, setJoiningOpen] = useState(true);

  const persona: DemoPersona = {
    id: user?.id || '', username: user?.username || '', email: user?.email || '',
    fullName: user?.fullName || user?.username || role, role,
  };
  const list = useQuery({
    queryKey: ['classrooms', 'list', pageNumber],
    queryFn: () => classroomApi.list(pageNumber),
    enabled: !id,
  });
  const detail = useQuery({
    queryKey: ['classrooms', 'detail', id],
    queryFn: () => classroomApi.get(id!),
    enabled: Boolean(id),
  });
  const members = useQuery({
    queryKey: ['classrooms', 'members', id, memberPage],
    queryFn: () => classroomApi.members(id!, memberPage),
    enabled: Boolean(id) && role !== 'STUDENT',
  });
  const requests = useQuery({
    queryKey: ['classrooms', 'requests', id, requestPage],
    queryFn: () => classroomApi.requests(id!, requestPage),
    enabled: Boolean(id) && role === 'TEACHER',
  });

  useEffect(() => {
    const classroom = detail.data;
    if (!classroom) return;
    setName(classroom.name);
    setDescription(classroom.description || '');
    setJoinMode(classroom.joinMode);
    setCodeExpiresAt(classroom.codeExpiresAt ? toVietnamInput(classroom.codeExpiresAt) : '');
    setCodeLocked(classroom.codeLocked);
    setJoiningOpen(classroom.joiningOpen);
  }, [detail.data]);

  async function run<T>(action: () => Promise<T>, success: string | ((result: T) => string), next?: (result: T) => void) {
    setPending(true); setError(''); setNotice('');
    try {
      const result = await action();
      await client.invalidateQueries({ queryKey: ['classrooms'] });
      setNotice(typeof success === 'string' ? success : success(result));
      next?.(result);
    } catch (failure) {
      setError(classroomError(failure));
    } finally {
      setPending(false);
    }
  }

  function submit<T>(event: FormEvent<HTMLFormElement>, action: () => Promise<T>, success: string | ((result: T) => string), next?: (result: T) => void) {
    event.preventDefault();
    void run(action, success, next);
  }

  const base = `/${role.toLowerCase()}/classrooms`;
  const content = id ? detail.data : undefined;

  return <ClassroomLayout role={role} persona={persona} breadcrumbChild={content?.name}>
    <div className="mx-auto max-w-5xl space-y-6 p-4 sm:p-8">
      <header>
        {id && <Link className="text-sm text-slate-600 underline" to={base}>← Back to classrooms</Link>}
        <p className="mt-2 text-xs font-bold uppercase tracking-widest text-slate-500">{role} · Classroom workspace</p>
        <h1 className="mt-1 text-3xl font-bold text-slate-900">{id ? content?.name || 'Classroom' : 'Classrooms'}</h1>
      </header>
      {error && <p role="alert" className="rounded-lg bg-rose-50 p-3 text-sm text-rose-800">{error}</p>}
      {notice && <p role="status" className="rounded-lg bg-emerald-50 p-3 text-sm text-emerald-800">{notice}</p>}

      {!id && <>
        {list.isPending && <p role="status">Loading classrooms…</p>}
        {list.isError && <p role="alert">{classroomError(list.error)} <button type="button" className="underline" onClick={() => list.refetch()}>Retry</button></p>}
        {list.data && <>
          <p className="text-sm text-slate-600">{role === 'STUDENT' ? 'Your enrolled classrooms' : role === 'TEACHER' ? 'Classrooms you own' : 'All classrooms'} · {list.data.pagination.totalElements}</p>
          <div className="grid gap-3 md:grid-cols-2">
            {list.data.items.map(classroom => <Link key={classroom.id} to={`${base}/${classroom.id}`} className="rounded-xl border border-slate-200 bg-white p-5 hover:border-slate-400">
              <h2 className="font-bold text-slate-900">{classroom.name}</h2>
              <p className="mt-1 line-clamp-2 text-sm text-slate-600">{classroom.description || 'No description'}</p>
              <p className="mt-3 text-xs font-semibold text-slate-500">{classroom.status} · {classroom.joinMode === 'AUTO_JOIN' ? 'Auto join' : 'Approval required'}</p>
            </Link>)}
          </div>
          {list.data.items.length === 0 && <p className="rounded-xl border border-slate-200 p-5 text-sm">No classrooms yet.</p>}
          <PageControls page={pageNumber} totalPages={list.data.pagination.totalPages} onPage={setPageNumber} />
        </>}
        {role !== 'STUDENT' ? <section className="rounded-xl border border-slate-200 bg-white p-5">
          <h2 className="mb-3 text-lg font-bold">Create classroom</h2>
          <form className="grid gap-3" onSubmit={event => submit(event,
            () => classroomApi.create({ name: name.trim(), description: description.trim(), joinMode, codeExpiresAt: null, ...(role === 'ADMIN' ? { ownerId: ownerId.trim() } : {}) }),
            'Classroom created.', created => navigate(`${base}/${created.id}`))}>
            <input className={field} aria-label="Classroom name" placeholder="Classroom name" maxLength={150} required value={name} onChange={event => setName(event.target.value)} />
            <textarea className={field} aria-label="Description" placeholder="Description" maxLength={2000} value={description} onChange={event => setDescription(event.target.value)} />
            <select className={field} aria-label="Join mode" value={joinMode} onChange={event => setJoinMode(event.target.value as JoinMode)}><option value="AUTO_JOIN">Auto join</option><option value="REQUIRE_APPROVAL">Require approval</option></select>
            {role === 'ADMIN' && <input className={field} aria-label="Teacher account UUID" placeholder="Teacher account UUID" required value={ownerId} onChange={event => setOwnerId(event.target.value)} />}
            <button className={button} disabled={pending}>Create</button>
          </form>
        </section> : <>
          <section className="rounded-xl border border-slate-200 bg-white p-5">
            <h2 className="mb-3 text-lg font-bold">Join with class code</h2>
            <form className="flex gap-3" onSubmit={event => submit(event,
              () => classroomApi.join(classCode.trim()),
              result => result.outcome === 'JOINED' ? 'Joined classroom.' : 'Request sent for teacher approval.',
              result => { if (result.outcome === 'JOINED') navigate(`${base}/${result.classroomId}`); })}>
              <input className={field} aria-label="Class code" placeholder="Class code" required value={classCode} onChange={event => setClassCode(event.target.value)} />
              <button className={button} disabled={pending}>Join</button>
            </form>
          </section>
          <section className="rounded-xl border border-slate-200 bg-white p-5">
            <h2 className="mb-2 text-lg font-bold">Invitation</h2>
            <p className="mb-3 text-sm text-slate-600">Enter the invitation ID from your notification.</p>
            <form className="flex flex-wrap gap-3" onSubmit={event => submit(event, () => classroomApi.acceptInvitation(invitationId.trim()), 'Invitation accepted.')}>
              <input className={field} aria-label="Invitation UUID" placeholder="Invitation UUID" required value={invitationId} onChange={event => setInvitationId(event.target.value)} />
              <button className={button} disabled={pending}>Accept</button>
              <button className={secondary} type="button" disabled={pending || !invitationId.trim()} onClick={() => void run(() => classroomApi.declineInvitation(invitationId.trim()), 'Invitation declined.')}>Decline</button>
            </form>
          </section>
        </>}
      </>}

      {id && <>
        {detail.isPending && <p role="status">Loading classroom…</p>}
        {detail.isError && <p role="alert">{classroomError(detail.error)} <button type="button" className="underline" onClick={() => detail.refetch()}>Retry</button></p>}
        {content && <>
          <section className="rounded-xl border border-slate-200 bg-white p-5 text-sm">
            <p>{content.description || 'No description'}</p>
            <p className="mt-3 text-slate-600">Status: {content.status} · Join mode: {content.joinMode}</p>
            {role !== 'STUDENT' && <p className="mt-2 font-mono">Class code: {content.classCode || 'Hidden'}</p>}
          </section>
          {role === 'STUDENT' ? <button className={secondary} type="button" disabled={pending || content.status !== 'ACTIVE'} onClick={() => void run(() => classroomApi.leave(id), 'Left classroom.', () => navigate(base))}>Leave classroom</button> : <>
            {role === 'TEACHER' && content.status === 'ACTIVE' && <>
              <section className="rounded-xl border border-slate-200 bg-white p-5">
                <h2 className="mb-3 text-lg font-bold">Edit classroom</h2>
                <form className="grid gap-3" onSubmit={event => submit(event, () => classroomApi.update(id, { name: name.trim(), description: description.trim() }), 'Classroom updated.')}>
                  <input className={field} aria-label="Classroom name" maxLength={150} required value={name} onChange={event => setName(event.target.value)} />
                  <textarea className={field} aria-label="Description" maxLength={2000} value={description} onChange={event => setDescription(event.target.value)} />
                  <button className={button} disabled={pending}>Save</button>
                </form>
              </section>
              <section className="rounded-xl border border-slate-200 bg-white p-5">
                <h2 className="mb-3 text-lg font-bold">Join settings</h2>
                <form className="grid gap-3" onSubmit={event => submit(event, () => classroomApi.configure(id, { codeExpiresAt: codeExpiresAt ? fromVietnamInput(codeExpiresAt) : null, codeLocked, joiningOpen, joinMode }), 'Join settings saved.')}>
                  <label className="text-sm">Class code expires at <input className={field} type="datetime-local" value={codeExpiresAt} onChange={event => setCodeExpiresAt(event.target.value)} /></label>
                  <label className="flex gap-2 text-sm"><input type="checkbox" checked={codeLocked} onChange={event => setCodeLocked(event.target.checked)} /> Lock class code</label>
                  <label className="flex gap-2 text-sm"><input type="checkbox" checked={joiningOpen} onChange={event => setJoiningOpen(event.target.checked)} /> Allow joining</label>
                  <select className={field} aria-label="Join mode" value={joinMode} onChange={event => setJoinMode(event.target.value as JoinMode)}><option value="AUTO_JOIN">Auto join</option><option value="REQUIRE_APPROVAL">Require approval</option></select>
                  <div className="flex gap-3"><button className={button} disabled={pending}>Save settings</button><button className={secondary} type="button" disabled={pending} onClick={() => void run(() => classroomApi.regenerateCode(id), 'Class code regenerated.')}>Regenerate code</button></div>
                </form>
              </section>
            </>}
            <div className="flex gap-3">
              {content.status === 'ACTIVE' && <button className={secondary} type="button" disabled={pending} onClick={() => void run(() => classroomApi.archive(id), 'Classroom archived.')}>Archive classroom</button>}
              {role === 'ADMIN' && <button className={secondary} type="button" disabled={pending} onClick={() => { if (window.confirm('Delete this classroom permanently?')) void run(() => classroomApi.remove(id), 'Classroom deleted.', () => navigate(base)); }}>Delete classroom</button>}
            </div>
            {role === 'ADMIN' && <section className="rounded-xl border border-slate-200 bg-white p-5">
              <h2 className="mb-3 text-lg font-bold">Transfer ownership</h2>
              <form className="flex gap-3" onSubmit={event => submit(event, () => classroomApi.transferOwner(id, ownerId.trim()), 'Ownership transferred.')}><input className={field} aria-label="New teacher account UUID" placeholder="New teacher account UUID" required value={ownerId} onChange={event => setOwnerId(event.target.value)} /><button className={button} disabled={pending}>Transfer</button></form>
            </section>}
            <section className="rounded-xl border border-slate-200 bg-white p-5">
              <h2 className="mb-3 text-lg font-bold">Members</h2>
              {members.isPending && <p role="status">Loading members…</p>}
              {members.isError && <p role="alert">{classroomError(members.error)}</p>}
              {members.data && <><ul className="divide-y divide-slate-100">{members.data.items.map(member => <li key={member.studentId} className="flex items-center justify-between gap-3 py-2 text-sm"><span>{member.username} · {member.email}</span>{role === 'TEACHER' && content.status === 'ACTIVE' && <button className={secondary} type="button" disabled={pending} onClick={() => void run(() => classroomApi.removeMember(id, member.studentId), 'Student removed.')}>Remove</button>}</li>)}</ul><PageControls page={memberPage} totalPages={members.data.pagination.totalPages} onPage={setMemberPage} /></>}
              {role === 'TEACHER' && content.status === 'ACTIVE' && <form className="mt-4 flex gap-3" onSubmit={event => submit(event, () => classroomApi.directAdd(id, student.trim()), 'Student added.')}><input className={field} aria-label="Student username or email" placeholder="Student username or email" required value={student} onChange={event => setStudent(event.target.value)} /><button className={button} disabled={pending}>Add</button></form>}
            </section>
            {role === 'TEACHER' && content.status === 'ACTIVE' && <>
              <section className="rounded-xl border border-slate-200 bg-white p-5">
                <h2 className="mb-3 text-lg font-bold">Pending join requests</h2>
                {requests.isPending && <p role="status">Loading requests…</p>}
                {requests.isError && <p role="alert">{classroomError(requests.error)}</p>}
                {requests.data && <><ul className="divide-y divide-slate-100">{requests.data.items.map(request => <li key={request.id} className="flex items-center justify-between gap-3 py-2 text-sm"><span>{request.username}</span><button className={secondary} type="button" disabled={pending} onClick={() => void run(() => classroomApi.approve(id, request.id), 'Join request approved.')}>Approve</button></li>)}</ul><PageControls page={requestPage} totalPages={requests.data.pagination.totalPages} onPage={setRequestPage} /></>}
              </section>
              <section className="rounded-xl border border-slate-200 bg-white p-5">
                <h2 className="mb-3 text-lg font-bold">Invite student</h2>
                <form className="grid gap-3" onSubmit={event => submit(event, () => classroomApi.invite(id, email.trim(), fromVietnamInput(expiresAt)), inviteId => `Invitation created: ${inviteId}`)}>
                  <input className={field} type="email" aria-label="Student email" placeholder="Student email" required value={email} onChange={event => setEmail(event.target.value)} />
                  <label className="text-sm">Expires at <input className={field} type="datetime-local" required value={expiresAt} onChange={event => setExpiresAt(event.target.value)} /></label>
                  <button className={button} disabled={pending}>Invite</button>
                </form>
                <form className="mt-4 flex gap-3" onSubmit={event => submit(event, () => classroomApi.revoke(id, invitationId.trim()), 'Invitation revoked.')}><input className={field} aria-label="Invitation UUID to revoke" placeholder="Invitation UUID to revoke" required value={invitationId} onChange={event => setInvitationId(event.target.value)} /><button className={secondary} disabled={pending}>Revoke</button></form>
              </section>
            </>}
          </>}
        </>}
      </>}
    </div>
  </ClassroomLayout>;
}
