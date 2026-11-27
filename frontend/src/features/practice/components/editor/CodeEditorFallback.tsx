import React from 'react';

interface CodeEditorFallbackProps {
  value: string;
}

/** Immediate charcoal code preview rendered while the lazy Monaco chunk loads. */
export const CodeEditorFallback: React.FC<CodeEditorFallbackProps> = ({ value }) => {
  const previewLines = value.split('\n').slice(0, 18);
  return (
    <div
      className="practice-editor-skeleton dark-scroll"
      aria-busy="true"
      aria-label="Loading code editor"
    >
      {previewLines.map((line, idx) => (
        <div key={idx} className="practice-editor-skeleton-line">
          <span className="practice-editor-skeleton-ln">{idx + 1}</span>
          <span>{line || ' '}</span>
        </div>
      ))}
    </div>
  );
};

