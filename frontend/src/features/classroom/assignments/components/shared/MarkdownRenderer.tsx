import React, { useMemo } from 'react';
import { marked } from 'marked';
import DOMPurify from 'dompurify';

interface MarkdownRendererProps {
  content?: string | null;
  className?: string;
}

export const MarkdownRenderer: React.FC<MarkdownRendererProps> = ({ content, className = '' }) => {
  const sanitizedHtml = useMemo(() => {
    if (!content) return '';
    try {
      const rawHtml = marked.parse(content, { async: false, breaks: true }) as string;
      return DOMPurify.sanitize(rawHtml, {
        ALLOWED_TAGS: [
          'p', 'b', 'i', 'em', 'strong', 'a', 'code', 'pre', 'ul', 'ol', 'li',
          'h1', 'h2', 'h3', 'h4', 'blockquote', 'table', 'thead', 'tbody', 'tr', 'th', 'td', 'hr', 'span'
        ],
        ALLOWED_ATTR: ['href', 'target', 'rel', 'class'],
      });
    } catch {
      return content;
    }
  }, [content]);

  if (!content) {
    return null;
  }

  return (
    <div
      className={`prose prose-slate max-w-none text-sm leading-relaxed ${className}`}
      dangerouslySetInnerHTML={{ __html: sanitizedHtml }}
    />
  );
};
