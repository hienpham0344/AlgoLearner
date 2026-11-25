import katex from 'katex';

export interface MathPlaceholderEntry {
  token: string;
  latex: string;
  displayMode: boolean;
}

const isWhitespace = (ch: string) => ch === ' ' || ch === '\t' || ch === '\n' || ch === '\r';
const isDigit = (ch: string) => ch >= '0' && ch <= '9';

/**
 * Temporarily mask fenced code blocks and inline code spans so math scanning
 * never pairs `$` characters inside source code examples.
 */
function maskCodeSegments(markdown: string): { masked: string; codeSlices: string[] } {
  const codeSlices: string[] = [];
  let result = '';
  let i = 0;

  while (i < markdown.length) {
    if (i === 0 || markdown[i - 1] === '\n') {
      const fenceMatch = markdown.slice(i).match(/^[ \t]{0,3}(`{3,}|~{3,})/);
      if (fenceMatch) {
        const fenceChar = fenceMatch[1][0];
        const fenceLen = fenceMatch[1].length;
        const firstNewline = markdown.indexOf('\n', i);
        let blockEnd = markdown.length;

        if (firstNewline !== -1) {
          let cursor = firstNewline + 1;
          while (cursor < markdown.length) {
            const nextNl = markdown.indexOf('\n', cursor);
            const lineEnd = nextNl === -1 ? markdown.length : nextNl;
            const closeMatch = markdown.slice(cursor, lineEnd).match(/^[ \t]{0,3}(`{3,}|~{3,})[ \t]*$/);
            if (closeMatch && closeMatch[1][0] === fenceChar && closeMatch[1].length >= fenceLen) {
              blockEnd = nextNl === -1 ? markdown.length : nextNl + 1;
              break;
            }
            if (nextNl === -1) break;
            cursor = nextNl + 1;
          }
        }

        const token = `ALGOXCODE${codeSlices.length}END`;
        codeSlices.push(markdown.slice(i, blockEnd));
        result += token;
        i = blockEnd;
        continue;
      }
    }

    if (markdown[i] === '`') {
      let tickCount = 1;
      while (markdown[i + tickCount] === '`') tickCount += 1;
      const needle = '`'.repeat(tickCount);
      let searchFrom = i + tickCount;
      let matchIdx = -1;
      while (searchFrom < markdown.length) {
        const candidate = markdown.indexOf(needle, searchFrom);
        if (candidate === -1) break;
        if (markdown[candidate - 1] !== '`' && markdown[candidate + tickCount] !== '`') {
          matchIdx = candidate;
          break;
        }
        searchFrom = candidate + 1;
      }
      if (matchIdx !== -1) {
        const end = matchIdx + tickCount;
        const token = `ALGOXCODE${codeSlices.length}END`;
        codeSlices.push(markdown.slice(i, end));
        result += token;
        i = end;
        continue;
      }
      result += needle;
      i += tickCount;
      continue;
    }

    result += markdown[i];
    i += 1;
  }

  return { masked: result, codeSlices };
}

/**
 * Find the closing `$$` for a display-math span starting after opening `$$`.
 */
function findDisplayClosing(source: string, from: number): number {
  let idx = from;
  while (idx < source.length - 1) {
    if (source[idx] === '\\') {
      idx += 2;
      continue;
    }
    if (source[idx] === '$' && source[idx + 1] === '$') {
      return idx;
    }
    idx += 1;
  }
  return -1;
}

/**
 * Find the closing `$` for a single-line inline-math span using currency guards.
 */
function findInlineClosing(source: string, from: number): number {
  if (from >= source.length) return -1;
  if (isWhitespace(source[from]) || source[from] === '$') return -1;

  let idx = from;
  while (idx < source.length) {
    const ch = source[idx];
    if (ch === '\\') {
      idx += 2;
      continue;
    }
    if (ch === '\n' || ch === '\r') {
      return -1;
    }
    if (ch === '$') {
      const prev = source[idx - 1] || '';
      const next = source[idx + 1] || '';
      if (!isWhitespace(prev) && next !== '$' && !isDigit(next)) {
        return idx;
      }
    }
    idx += 1;
  }
  return -1;
}

/**
 * Extract `$$...$$` and `$...$` math segments outside fenced/inline code blocks,
 * replacing them with alphanumeric tokens safe from Marked and DOMPurify mangling.
 */
export function extractMathPlaceholders(markdown: string): {
  maskedMarkdown: string;
  entries: MathPlaceholderEntry[];
} {
  const { masked, codeSlices } = maskCodeSegments(markdown);
  const entries: MathPlaceholderEntry[] = [];
  let scanned = '';
  let i = 0;

  while (i < masked.length) {
    if (masked[i] === '\\' && masked[i + 1] === '$') {
      scanned += '\\$';
      i += 2;
      continue;
    }

    if (masked[i] === '$' && masked[i + 1] === '$') {
      const closeIdx = findDisplayClosing(masked, i + 2);
      if (closeIdx !== -1) {
        const latex = masked.slice(i + 2, closeIdx).trim();
        if (latex) {
          const token = `ALGOXMATHB${entries.length}END`;
          entries.push({ token, latex, displayMode: true });
          const needsLeadingBreak = scanned.length > 0 && !scanned.endsWith('\n\n');
          scanned += `${needsLeadingBreak ? '\n\n' : ''}${token}\n\n`;
          i = closeIdx + 2;
          continue;
        }
      }
    }

    if (masked[i] === '$') {
      const closeIdx = findInlineClosing(masked, i + 1);
      if (closeIdx !== -1) {
        const latex = masked.slice(i + 1, closeIdx).trim();
        if (latex) {
          const token = `ALGOXMATHI${entries.length}END`;
          entries.push({ token, latex, displayMode: false });
          scanned += token;
          i = closeIdx + 1;
          continue;
        }
      }
    }

    scanned += masked[i];
    i += 1;
  }

  let restoredCode = scanned;
  for (let idx = 0; idx < codeSlices.length; idx += 1) {
    restoredCode = restoredCode.split(`ALGOXCODE${idx}END`).join(codeSlices[idx]);
  }

  return { maskedMarkdown: restoredCode, entries };
}

/**
 * Strip math `$` / `$$` wrappers while keeping raw expression text readable for outline labels.
 */
export function stripMathDelimiters(text: string): string {
  return text
    .replace(/\\\$/g, '$')
    .replace(/\$\$([\s\S]+?)\$\$/g, (_, inner: string) => inner.trim())
    .replace(/\$([^$\n]+?)\$/g, (_, inner: string) => inner.trim());
}

/**
 * Replace alphanumeric tokens inside a string with their original LaTeX text (for heading IDs).
 */
export function resolveMathTokensToText(text: string, entries: MathPlaceholderEntry[]): string {
  let out = text;
  for (const entry of entries) {
    out = out.split(entry.token).join(entry.latex);
  }
  return out;
}

/**
 * Render captured math placeholders into sanitized KaTeX HTML spans/blocks.
 */
export function restoreMathPlaceholders(
  sanitizedHtml: string,
  entries: MathPlaceholderEntry[],
): string {
  if (entries.length === 0) return sanitizedHtml;

  let html = sanitizedHtml;
  for (const entry of entries) {
    const rendered = katex.renderToString(entry.latex, {
      displayMode: entry.displayMode,
      throwOnError: false,
      strict: 'ignore',
      trust: false,
      output: 'htmlAndMathml',
    });

    if (entry.displayMode) {
      const blockHtml = `<div class="lesson-math-block">${rendered}</div>`;
      html = html.split(`<p>${entry.token}</p>`).join(blockHtml);
      html = html.split(entry.token).join(blockHtml);
    } else {
      const inlineHtml = `<span class="lesson-math-inline">${rendered}</span>`;
      html = html.split(entry.token).join(inlineHtml);
    }
  }

  return html;
}
