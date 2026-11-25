import React from 'react';
import { Loader2 } from 'lucide-react';
import { cn } from '@/utils/cn';

const AlgoLogoMark: React.FC<{ className?: string }> = ({ className }) => (
  <svg
    xmlns="http://www.w3.org/2000/svg"
    viewBox="0 0 76 80"
    fill="none"
    className={className}
    aria-hidden="true"
  >
    <g transform="translate(0 16) rotate(-7)">
      <path
        d="M15 0H38C47 0 52 6 52 15V37C52 47 46 52 37 52H15C5 52 0 46 0 37V16C0 6 5 0 15 0Z"
        fill="#111617"
      />
      <path
        d="M0 0L9 8L0 16"
        stroke="#EDF5F7"
        strokeWidth="4.5"
        transform="translate(15 18)"
      />
      <path
        d="M0 0H9"
        stroke="#EDF5F7"
        strokeWidth="4.5"
        strokeLinejoin="round"
        transform="translate(31 35)"
      />
    </g>
  </svg>
);

export interface LoadingScreenProps {
  /**
   * Chế độ hiển thị:
   * - `fullscreen`: Chiếm toàn bộ viewport (cho init Auth, route lớn)
   * - `page`: Vừa vặn bên trong Workspace/Layout hiện tại
   * - `inline` / `card`: Gọn gàng cho từng block, widget hoặc modal
   */
  variant?: 'fullscreen' | 'page' | 'inline';
  /** Thông điệp chính hiển thị */
  message?: string;
  /** Thông điệp phụ giải thích chi tiết hơn */
  submessage?: string;
  /** Hiển thị logo AlgoLearner (mặc định: true cho fullscreen và page) */
  showLogo?: boolean;
  /** Tùy biến className ngoài */
  className?: string;
}

export const LoadingScreen: React.FC<LoadingScreenProps> = ({
  variant = 'fullscreen',
  message = 'Đang chuẩn bị không gian làm việc…',
  submessage,
  showLogo = variant !== 'inline',
  className,
}) => {
  if (variant === 'inline') {
    return (
      <div
        role="status"
        aria-live="polite"
        className={cn(
          'flex items-center justify-center gap-3 p-6 text-slate-600',
          className
        )}
      >
        <Loader2 className="w-5 h-5 text-emerald-700 animate-spin shrink-0" aria-hidden="true" />
        <span className="text-sm font-medium">{message}</span>
      </div>
    );
  }

  const isFullscreen = variant === 'fullscreen';

  return (
    <div
      role="status"
      aria-live="polite"
      className={cn(
        'flex flex-col items-center justify-center transition-opacity duration-300',
        isFullscreen
          ? 'fixed inset-0 z-50 min-h-screen w-screen bg-[#f6f6f4]/95 backdrop-blur-sm'
          : 'w-full min-h-[360px] py-16 px-4',
        className
      )}
    >
      <div className="flex flex-col items-center max-w-sm w-full mx-auto text-center px-4">
        {/* Brand Mark with glowing aura */}
        {showLogo && (
          <div className="relative mb-6">
            <div
              className="absolute -inset-3 bg-emerald-500/15 rounded-3xl blur-xl animate-pulse"
              aria-hidden="true"
            />
            <div className="relative w-16 h-16 rounded-2xl bg-white border border-slate-200/80 shadow-sm flex items-center justify-center p-2.5 transition-transform">
              <AlgoLogoMark className="w-full h-full" />
            </div>
            <div className="absolute -bottom-1 -right-1 bg-white rounded-full p-1 shadow-xs border border-slate-200">
              <Loader2 className="w-3.5 h-3.5 text-emerald-700 animate-spin" aria-hidden="true" />
            </div>
          </div>
        )}

        {!showLogo && (
          <div className="mb-4">
            <Loader2 className="w-8 h-8 text-emerald-700 animate-spin" aria-hidden="true" />
          </div>
        )}

        {/* Text Details */}
        <h3 className="text-base font-semibold text-slate-800 tracking-tight leading-snug">
          {message}
        </h3>

        {submessage && (
          <p className="mt-1.5 text-xs text-slate-500 leading-relaxed">
            {submessage}
          </p>
        )}

        {/* Subtle indeterminate progress bar */}
        <div
          className="mt-5 w-44 h-1 bg-slate-200/80 rounded-full overflow-hidden relative"
          aria-hidden="true"
        >
          <div className="absolute inset-y-0 bg-emerald-600 rounded-full animate-indeterminate-bar" />
        </div>
      </div>
    </div>
  );
};
