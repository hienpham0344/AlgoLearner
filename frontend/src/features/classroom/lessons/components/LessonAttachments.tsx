import { useState, type ChangeEvent } from 'react';
import { Download, Paperclip, Trash2, Upload } from 'lucide-react';
import { lessonApi, lessonError } from '../api/lessonApi';
import { lessonMockStore } from '../mock/lessonMockStore';
import { SUPPORTED_EXTENSIONS, formatBytes, validateAttachment } from '../lessonRules';
import type { Lesson, LessonCapabilities } from '../types';

const IS_PROTOTYPE_DEMO = ['auth-demo', 'lessons-demo'].includes(import.meta.env.MODE);

interface Props { lesson: Lesson; editable: boolean; policy: LessonCapabilities | null; onChanged: () => Promise<void> }
export function LessonAttachments({ lesson, editable, policy, onChanged }: Props) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const allowed = policy?.allowedExtensions.filter(extension => SUPPORTED_EXTENSIONS.includes(extension.toLowerCase())).map(extension => `.${extension}`).join(',');
  async function upload(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]; event.target.value = '';
    if (!file) return;
    setNotice('');
    const validation = validateAttachment(file, policy);
    if (validation) { setError(validation); return; }
    setBusy(true); setError('');
    try {
      if (IS_PROTOTYPE_DEMO) await lessonMockStore.uploadAttachment(lesson.classroomId, lesson.id, file);
      else await lessonApi.upload(lesson.classroomId, lesson.id, file);
      await onChanged(); setNotice('File uploaded.');
    }
    catch (failure) { setError(`Upload failed. ${lessonError(failure)} Your saved lesson content is preserved.`); }
    finally { setBusy(false); }
  }
  async function download(id: string, name: string) {
    setBusy(true); setError(''); setNotice('');
    try {
      const blob = IS_PROTOTYPE_DEMO
        ? lessonMockStore.downloadAttachment(id)
        : await lessonApi.download(lesson.classroomId, lesson.id, id);
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a'); anchor.href = url; anchor.download = name.replace(/[\\/\u0000-\u001f]/g, '_');
      document.body.append(anchor); anchor.click(); anchor.remove();
      window.setTimeout(() => URL.revokeObjectURL(url), 1000);
      setNotice('Download started.');
    } catch (failure) { setError(`Download failed. ${lessonError(failure)} You can retry without losing your progress.`); }
    finally { setBusy(false); }
  }
  async function remove(id: string) {
    if (!window.confirm('Remove this attachment from the lesson?')) return;
    setBusy(true); setError(''); setNotice('');
    try {
      if (IS_PROTOTYPE_DEMO) lessonMockStore.removeAttachment(lesson.classroomId, lesson.id, id);
      else await lessonApi.removeAttachment(lesson.classroomId, lesson.id, id);
      await onChanged(); setNotice('Attachment removed.');
    }
    catch (failure) { setError(lessonError(failure)); }
    finally { setBusy(false); }
  }
  return <section className="lesson-panel lesson-attachments" aria-label="Lesson materials">
    <div className="lesson-section-head"><h2><Paperclip size={18} aria-hidden="true" />Lesson materials</h2><span className="lesson-count">{lesson.attachments.length}</span></div>
    {error && <p role="alert" className="lesson-alert">{error}</p>}
    {notice && <p role="status" className="lesson-notice">{notice}</p>}
    {lesson.attachments.length === 0 && <p className="lesson-muted">No files attached to this lesson.</p>}
    <ul className="lesson-file-list">{lesson.attachments.map(file => <li key={file.id}>
      <div><strong>{file.name}</strong><small>{formatBytes(file.size)}</small></div>
      <div className="lesson-actions"><button type="button" className="lesson-icon-button" disabled={busy} aria-label={`Download ${file.name}`} onClick={() => void download(file.id, file.name)}><Download size={17} aria-hidden="true" /></button>
        {editable && <button type="button" className="lesson-icon-button danger" disabled={busy} aria-label={`Remove ${file.name}`} onClick={() => void remove(file.id)}><Trash2 size={17} aria-hidden="true" /></button>}</div>
    </li>)}</ul>
    {editable && <div className="lesson-upload"><label htmlFor="lesson-file"><Upload size={18} aria-hidden="true" />Attach a document or image</label>
      <input id="lesson-file" type="file" accept={allowed} disabled={busy || !policy?.uploadsAvailable || !allowed || policy.maxFileSizeBytes <= 0} onChange={event => void upload(event)} aria-describedby="lesson-upload-help" />
      <p id="lesson-upload-help" className="lesson-muted">{policy?.uploadsAvailable && allowed ? `${allowed} · Up to ${formatBytes(policy.maxFileSizeBytes)}` : 'Uploads are currently unavailable. Your saved content is preserved.'}</p>
    </div>}
  </section>;
}
