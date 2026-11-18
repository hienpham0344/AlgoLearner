import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Eye, PencilLine, Plus, X } from 'lucide-react';
import { EMPTY_LESSON, activitySelectable, validateLesson } from '../lessonRules';
import type { Lesson, LessonActivity, LessonInput } from '../types';
import { LessonContent } from './LessonContent';

interface Props {
  lesson?: Lesson;
  activities: LessonActivity[];
  activityLinkingAvailable: boolean;
  pending: boolean;
  onSave: (input: LessonInput) => Promise<void>;
  onCancel: () => void;
}

export function LessonEditor({ lesson, activities, activityLinkingAvailable, pending, onSave, onCancel }: Props) {
  const [input, setInput] = useState<LessonInput>(() => lesson ? {
    title: lesson.title, content: lesson.content, codeExample: lesson.codeExample,
    externalLinks: lesson.externalLinks, completionMode: lesson.completionMode,
    activityIds: lesson.activities.map(activity => activity.id), requiredActivityIds: lesson.requiredActivityIds,
  } : { ...EMPTY_LESSON });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const validation = useRef<HTMLDivElement>(null);
  const [preview, setPreview] = useState(false);
  const [dirty, setDirty] = useState(false);
  useEffect(() => { if (Object.keys(errors).length) validation.current?.focus(); }, [errors]);
  useEffect(() => {
    const prevent = (event: BeforeUnloadEvent) => { if (dirty) event.preventDefault(); };
    window.addEventListener('beforeunload', prevent);
    return () => window.removeEventListener('beforeunload', prevent);
  }, [dirty]);

  function change(update: Partial<LessonInput>) { setInput(current => ({ ...current, ...update })); setDirty(true); }
  async function submit(event: FormEvent) {
    event.preventDefault();
    const next = { ...input, title: input.title.trim(), externalLinks: input.externalLinks.map(link => ({ label: link.label.trim(), url: link.url.trim() })),
      codeExample: input.codeExample?.code ? input.codeExample : null,
      requiredActivityIds: input.completionMode === 'MANUAL' ? [] : input.requiredActivityIds };
    const validation = validateLesson(next, activities, lesson?.id);
    setErrors(validation);
    if (Object.keys(validation).length) return;
    try { await onSave(next); setDirty(false); } catch { /* Parent displays the API error; retain all edits. */ }
  }

  return <section className="lesson-panel lesson-editor">
    <div className="lesson-section-head"><div><p className="lesson-eyebrow">Lesson studio</p><h2>{lesson ? 'Edit lesson' : 'Create a lesson'}</h2></div>
      <button className="lesson-button secondary" type="button" aria-pressed={preview} onClick={() => setPreview(value => !value)}>
        {preview ? <PencilLine size={16} aria-hidden="true" /> : <Eye size={16} aria-hidden="true" />}{preview ? 'Continue editing' : 'Preview'}
      </button>
    </div>
    <form onSubmit={submit}>
      {Object.keys(errors).length > 0 && <div ref={validation} id="lesson-validation" tabIndex={-1} role="alert" className="lesson-alert">{Object.values(errors).map(message => <p key={message}>{message}</p>)}</div>}
      <fieldset disabled={pending || preview} className="lesson-editor-fields" hidden={preview}>
        <label htmlFor="lesson-title">Lesson title <span aria-hidden="true">*</span></label>
        <input id="lesson-title" required maxLength={200} value={input.title} aria-invalid={Boolean(errors.title)} onChange={event => change({ title: event.target.value })} placeholder="e.g. Understanding recursion" />
        <label htmlFor="lesson-theory">Theory & notes <span className="lesson-muted">Markdown</span></label>
        <p id="lesson-theory-help" className="lesson-muted">Use headings, lists, links, fenced code blocks, and LaTeX math ($...$ inline or $$...$$ block). Embedded scripts and media are removed.</p>
        <textarea id="lesson-theory" aria-describedby="lesson-theory-help" rows={12} maxLength={100_000} value={input.content} onChange={event => change({ content: event.target.value })} placeholder="Start with the idea. Build up to an example." />
        <div className="lesson-section-head"><h3>Code example</h3><label className="lesson-inline" htmlFor="lesson-language">Language
          <select id="lesson-language" value={input.codeExample?.language || 'java'} onChange={event => change({ codeExample: { code: input.codeExample?.code || '', language: event.target.value as 'java' | 'python' | 'text' } })}>
            <option value="java">Java</option><option value="python">Python</option><option value="text">Plain text</option>
          </select></label></div>
        <textarea aria-label="Example source code" spellCheck={false} className="lesson-source" rows={6} maxLength={50_000} value={input.codeExample?.code || ''} onChange={event => change({ codeExample: { language: input.codeExample?.language || 'java', code: event.target.value } })} placeholder="Add a code example for students to read" />
        <div className="lesson-section-head"><h3>External links</h3><button className="lesson-button text" type="button" onClick={() => change({ externalLinks: [...input.externalLinks, { label: '', url: '' }] })}><Plus size={15} aria-hidden="true" />Add link</button></div>
        {input.externalLinks.map((link, index) => <div className="lesson-link-fields" key={index}>
          <label>Link {index + 1} label<input required aria-label={`Link ${index + 1} label`} value={link.label} onChange={event => change({ externalLinks: input.externalLinks.map((item, position) => position === index ? { ...item, label: event.target.value } : item) })} /></label>
          <label>Link {index + 1} URL<input required type="url" aria-label={`Link ${index + 1} URL`} value={link.url} placeholder="https://" onChange={event => change({ externalLinks: input.externalLinks.map((item, position) => position === index ? { ...item, url: event.target.value } : item) })} /></label>
          <button type="button" className="lesson-icon-button" aria-label={`Remove link ${index + 1}`} onClick={() => change({ externalLinks: input.externalLinks.filter((_, position) => position !== index) })}><X size={16} aria-hidden="true" /></button>
        </div>)}
        <h3>Linked activities</h3>
        {!activityLinkingAvailable && <p className="lesson-muted">Activity linking is currently unavailable. Existing links will be kept when you save.</p>}
        {activityLinkingAvailable && activities.length === 0 && <p className="lesson-muted">No assignments, quizzes or practice activities are available in this classroom yet.</p>}
        <div className="lesson-activity-options">
          {activities.map(activity => {
            const selected = input.activityIds.includes(activity.id);
            const selectable = activitySelectable(activity, lesson?.id);
            return <label key={activity.id} className="lesson-checkbox"><input type="checkbox" checked={selected} disabled={!activityLinkingAvailable || !selectable} onChange={event => change({
              activityIds: event.target.checked ? [...input.activityIds, activity.id] : input.activityIds.filter(id => id !== activity.id),
              requiredActivityIds: event.target.checked ? input.requiredActivityIds : input.requiredActivityIds.filter(id => id !== activity.id),
            })} /><span>{activity.title}<small>{activity.type.toLowerCase()}{!selectable ? ' · Quiz already linked to another lesson' : ''}</small></span></label>;
          })}
        </div>
        <h3>Completion</h3>
        <label htmlFor="lesson-completion">How do students complete this lesson?</label>
        <select id="lesson-completion" value={input.completionMode} onChange={event => change({ completionMode: event.target.value as LessonInput['completionMode'] })}>
          <option value="MANUAL">Manual — student marks as completed</option>
          <option value="CONDITION_BASED" disabled={!activityLinkingAvailable && input.completionMode !== 'CONDITION_BASED'}>Condition based — linked assignment or quiz</option>
        </select>
        {input.completionMode === 'CONDITION_BASED' && <div><p className="lesson-muted">Select the linked assignments or quizzes required for completion. Their completion conditions are evaluated by the server.</p>
          {activities.filter(activity => activity.type !== 'PRACTICE' && input.activityIds.includes(activity.id)).map(activity => <label key={activity.id} className="lesson-checkbox">
            <input type="checkbox" disabled={!activityLinkingAvailable} checked={input.requiredActivityIds.includes(activity.id)} onChange={event => change({ requiredActivityIds: event.target.checked ? [...input.requiredActivityIds, activity.id] : input.requiredActivityIds.filter(id => id !== activity.id) })} />Require {activity.title}
          </label>)}
        </div>}
      </fieldset>
      {preview && <div className="lesson-preview"><p className="lesson-eyebrow">Student preview · Unsaved</p><h2>{input.title || 'Untitled lesson'}</h2><LessonContent lesson={input} /></div>}
      <div className="lesson-editor-footer"><p className="lesson-muted">{lesson ? 'Saved changes keep the current lesson status.' : 'New lessons are saved as drafts. Attach files after saving.'}</p>
        <div className="lesson-actions"><button className="lesson-button secondary" type="button" disabled={pending} onClick={() => { if (!dirty || window.confirm('Discard your unsaved lesson changes?')) onCancel(); }}>Cancel</button>
          <button className="lesson-button" disabled={pending} aria-busy={pending}>{pending ? 'Saving…' : lesson ? 'Save changes' : 'Save draft'}</button></div>
      </div>
    </form>
  </section>;
}
