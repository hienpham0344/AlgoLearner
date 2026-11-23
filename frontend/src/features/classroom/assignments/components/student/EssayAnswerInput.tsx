import React, { useRef, useState } from 'react';
import { UploadCloud, File, Trash2, CheckCircle2, AlertCircle, Loader2 } from 'lucide-react';
import { assignmentApi, assignmentError } from '../../api/assignmentApi';
import type { EssayFormat } from '../../types';

interface EssayAnswerInputProps {
  classId: string;
  format?: EssayFormat;
  textAnswer?: string;
  fileId?: string | null;
  fileUrl?: string | null;
  onTextChange: (text: string) => void;
  onFileChange: (fileId: string | null) => void;
  disabled?: boolean;
}

export const EssayAnswerInput: React.FC<EssayAnswerInputProps> = ({
  classId,
  format = 'TEXT_AND_FILE',
  textAnswer = '',
  fileId = null,
  fileUrl = null,
  onTextChange,
  onFileChange,
  disabled = false,
}) => {
  const showText = format !== 'FILE_ONLY';
  const showFile = format !== 'TEXT_ONLY';

  const [isUploading, setIsUploading] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [attachedFileName, setAttachedFileName] = useState<string | null>(() => {
    if (fileUrl) return fileUrl.split('/').pop() || 'Attached File';
    if (fileId) return 'Uploaded File';
    return null;
  });

  const fileInputRef = useRef<HTMLInputElement>(null);

  const handleFileUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    // Check size limit: 10MB
    const MAX_SIZE = 10 * 1024 * 1024;
    if (file.size > MAX_SIZE) {
      setUploadError('File size cannot exceed 10MB.');
      return;
    }

    setIsUploading(true);
    setUploadError(null);

    try {
      const response = await assignmentApi.uploadEssayFile(classId, file);
      setAttachedFileName(response.originalName || file.name);
      onFileChange(response.id);
    } catch (err) {
      setUploadError(assignmentError(err));
    } finally {
      setIsUploading(false);
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  };

  const handleRemoveFile = () => {
    setAttachedFileName(null);
    onFileChange(null);
  };

  return (
    <div className="space-y-4">
      {showText && (
        <div>
          <label htmlFor="essay-text-answer" className="block text-xs font-bold text-slate-700 mb-1.5">
            Your Written Answer (Markdown supported)
          </label>
          <textarea
            id="essay-text-answer"
            rows={8}
            value={textAnswer}
            onChange={(e) => onTextChange(e.target.value)}
            disabled={disabled}
            placeholder="Type your explanation, proofs, or structured response here..."
            className="w-full px-4 py-3 rounded-xl border border-slate-300 text-sm focus:outline-hidden focus:ring-2 focus:ring-slate-900 disabled:opacity-60 bg-white"
          />
        </div>
      )}

      {showFile && (
        <div className="space-y-2">
          <label className="block text-xs font-bold text-slate-700">File Attachment</label>

          {fileId || attachedFileName ? (
            <div className="p-3.5 rounded-xl border border-emerald-200 bg-emerald-50/50 flex items-center justify-between gap-3">
              <div className="flex items-center gap-2.5 min-w-0">
                <File size={18} className="text-emerald-700 shrink-0" />
                <span className="text-xs font-bold text-slate-900 truncate">
                  {attachedFileName || 'Submission Attachment'}
                </span>
                <span className="inline-flex items-center gap-1 text-[11px] text-emerald-700 font-medium">
                  <CheckCircle2 size={12} /> Ready
                </span>
              </div>

              {!disabled && (
                <button
                  type="button"
                  onClick={handleRemoveFile}
                  className="p-1.5 text-slate-400 hover:text-rose-600 rounded-lg hover:bg-white transition-colors"
                  title="Remove attachment"
                  aria-label="Remove attachment"
                >
                  <Trash2 size={14} />
                </button>
              )}
            </div>
          ) : (
            <div
              onClick={() => !disabled && !isUploading && fileInputRef.current?.click()}
              className={`p-6 rounded-2xl border-2 border-dashed border-slate-300 bg-slate-50 text-center transition-colors ${
                disabled ? 'opacity-60 cursor-not-allowed' : 'cursor-pointer hover:border-slate-400 hover:bg-slate-100/60'
              }`}
            >
              <input
                ref={fileInputRef}
                type="file"
                onChange={handleFileUpload}
                disabled={disabled || isUploading}
                accept=".pdf,.docx,.doc,.zip,.txt,.py,.java,.cpp"
                className="hidden"
                aria-label="Upload assignment attachment"
              />

              <div className="flex flex-col items-center gap-1.5">
                {isUploading ? (
                  <Loader2 size={24} className="text-slate-700 animate-spin" />
                ) : (
                  <UploadCloud size={24} className="text-slate-400" />
                )}
                <span className="text-xs font-bold text-slate-800">
                  {isUploading ? 'Uploading file...' : 'Click or drop file to attach'}
                </span>
                <span className="text-[11px] text-slate-500">
                  PDF, DOCX, ZIP, or code files (Max 10MB)
                </span>
              </div>
            </div>
          )}

          {uploadError && (
            <p className="text-xs text-rose-600 flex items-center gap-1 mt-1">
              <AlertCircle size={12} /> {uploadError}
            </p>
          )}
        </div>
      )}
    </div>
  );
};
