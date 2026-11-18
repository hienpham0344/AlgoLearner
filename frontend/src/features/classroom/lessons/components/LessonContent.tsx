import DOMPurify from 'dompurify';
import { marked } from 'marked';
import { safeExternalUrl } from '../lessonRules';
import type { LessonInput } from '../types';

export function renderLessonMarkdown(markdown: string): string {
  const clean = DOMPurify.sanitize(marked.parse(markdown, { async: false, breaks: true, gfm: true }), {
    ALLOWED_TAGS: ['p', 'br', 'strong', 'em', 'del', 'h1', 'h2', 'h3', 'h4', 'ul', 'ol', 'li', 'blockquote', 'pre', 'code', 'a', 'hr', 'table', 'thead', 'tbody', 'tr', 'th', 'td'],
    ALLOWED_ATTR: ['href', 'title'], ALLOW_DATA_ATTR: false,
  });
  const template = document.createElement('template');
  template.innerHTML = clean;
  template.content.querySelectorAll('pre').forEach(block => { block.tabIndex = 0; });
  template.content.querySelectorAll('a').forEach(link => {
    const href = safeExternalUrl(link.getAttribute('href') || '');
    if (!href) link.removeAttribute('href');
    else { link.href = href; link.target = '_blank'; link.rel = 'noopener noreferrer'; }
  });
  return template.innerHTML;
}

export function LessonContent({ lesson }: { lesson: Pick<LessonInput, 'content' | 'codeExample' | 'externalLinks'> }) {
  return <div className="lesson-content">
    {lesson.content ? <div className="lesson-prose" dangerouslySetInnerHTML={{ __html: renderLessonMarkdown(lesson.content) }} /> : <p className="lesson-muted">No theory content yet.</p>}
    {lesson.codeExample?.code && <section aria-label="Code example" className="lesson-code">
      <p>{lesson.codeExample.language.toUpperCase()} · Code example</p>
      <pre tabIndex={0}><code>{lesson.codeExample.code}</code></pre>
    </section>}
    {lesson.externalLinks.length > 0 && <section className="lesson-links"><h2>Further reading</h2>
      {lesson.externalLinks.map((link, index) => {
        const url = safeExternalUrl(link.url);
        return url ? <a key={index} href={url} target="_blank" rel="noopener noreferrer">{link.label} <span className="lesson-muted">↗ Opens a new tab</span></a> : <p key={index}>This link is unavailable.</p>;
      })}
    </section>}
  </div>;
}
