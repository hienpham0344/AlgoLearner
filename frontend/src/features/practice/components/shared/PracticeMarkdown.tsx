import { useMemo } from 'react';
import DOMPurify from 'dompurify';
import katex from 'katex';
import { Marked } from 'marked';
import 'katex/dist/katex.min.css';

const markdown = new Marked({ breaks: true });

markdown.use({
  extensions: [
    {
      name: 'blockMath',
      level: 'block',
      start: source => source.indexOf('$$'),
      tokenizer(source) {
        const match = /^\$\$[ \t]*\n?([\s\S]+?)\n?[ \t]*\$\$(?:\n|$)/.exec(source);
        return match ? { type: 'blockMath', raw: match[0], text: match[1] } : undefined;
      },
      renderer(token) {
        return katex.renderToString(token.text, { displayMode: true, throwOnError: false, trust: false });
      },
    },
    {
      name: 'inlineMath',
      level: 'inline',
      start: source => source.indexOf('$'),
      tokenizer(source) {
        const match = /^\$(?!\$)((?:\\.|[^$\\\n])+?)\$(?!\$)/.exec(source);
        return match ? { type: 'inlineMath', raw: match[0], text: match[1] } : undefined;
      },
      renderer(token) {
        return katex.renderToString(token.text, { throwOnError: false, trust: false });
      },
    },
  ],
});

export function PracticeMarkdown({ content }: { content: string }) {
  const html = useMemo(() => {
    try {
      return DOMPurify.sanitize(markdown.parse(content, { async: false }) as string);
    } catch {
      return null;
    }
  }, [content]);

  if (html === null) return <div className="practice-markdown-fallback">{content}</div>;
  return <div className="practice-markdown" dangerouslySetInnerHTML={{ __html: html }} />;
}
