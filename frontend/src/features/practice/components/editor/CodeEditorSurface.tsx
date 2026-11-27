import React from 'react';
import Editor, { loader, type OnMount } from '@monaco-editor/react';
import * as monaco from 'monaco-editor/editor/editor.api.js';
import 'monaco-editor/languages/definitions/java/register.js';
import 'monaco-editor/languages/definitions/python/register.js';
import EditorWorker from 'monaco-editor/editor/editor.worker.js?worker';
import type { PracticeLanguage } from '../../types';

type MonacoGlobal = typeof globalThis & { MonacoEnvironment?: { getWorker(): Worker } };
if (typeof globalThis !== 'undefined' && !(globalThis as MonacoGlobal).MonacoEnvironment) {
  (globalThis as MonacoGlobal).MonacoEnvironment = { getWorker: () => new EditorWorker() };
}
loader.config({ monaco });

let studioThemeRegistered = false;
function registerEditorialTheme(): void {
  if (studioThemeRegistered) return;
  studioThemeRegistered = true;
  monaco.editor.defineTheme('algolearn-editorial', {
    base: 'vs-dark',
    inherit: true,
    rules: [],
    colors: {
      'editor.background': '#1c1d1f',
      'editor.foreground': '#d7d7d7',
      'editor.lineHighlightBackground': '#252628',
      'editorLineNumber.foreground': '#686b72',
      'editorLineNumber.activeForeground': '#e4e4e7',
      'editorGutter.background': '#1c1d1f',
      'editor.selectionBackground': '#353840',
      'editorCursor.foreground': '#f5f5f3',
      'editorIndentGuide.background1': '#303236',
      'editorIndentGuide.activeBackground1': '#5e6268',
    },
  });
}

export type EditorialCodeEditor = monaco.editor.IStandaloneCodeEditor;

interface CodeEditorSurfaceProps {
  language: PracticeLanguage;
  value: string;
  readOnly: boolean;
  onChange: (value: string) => void;
  onCursorChange: (pos: { line: number; column: number }) => void;
  onRunShortcut: () => void;
  onEditorReady: (editor: EditorialCodeEditor) => void;
}

export const CodeEditorSurface: React.FC<CodeEditorSurfaceProps> = ({
  language,
  value,
  readOnly,
  onChange,
  onCursorChange,
  onRunShortcut,
  onEditorReady,
}) => {
  const runShortcutRef = React.useRef(onRunShortcut);
  runShortcutRef.current = onRunShortcut;

  const handleMount: OnMount = editor => {
    onEditorReady(editor);
    editor.onDidChangeCursorPosition(e => {
      onCursorChange({ line: e.position.lineNumber, column: e.position.column });
    });
    editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.Enter, () => {
      runShortcutRef.current();
    });
  };

  return (
    <Editor
      height="100%"
      language={language === 'JAVA' ? 'java' : 'python'}
      theme="algolearn-editorial"
      beforeMount={registerEditorialTheme}
      onMount={handleMount}
      value={value}
      onChange={val => onChange(val ?? '')}
      options={{
        minimap: { enabled: false },
        fontSize: 14,
        lineHeight: 24,
        fontFamily: "'JetBrains Mono', ui-monospace, Consolas, monospace",
        wordWrap: 'on',
        automaticLayout: true,
        scrollBeyondLastLine: false,
        readOnly,
        tabSize: 4,
        renderLineHighlight: 'all',
        guides: { indentation: true, highlightActiveIndentation: true },
        padding: { top: 16, bottom: 16 },
        scrollbar: {
          verticalScrollbarSize: 7,
          horizontalScrollbarSize: 7,
          useShadows: false,
        },
      }}
    />
  );
};

export default CodeEditorSurface;
