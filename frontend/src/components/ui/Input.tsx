import React, { useState } from 'react';
import { cn } from '@/utils/cn';
import { Eye, EyeOff } from 'lucide-react';

export interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  error?: string;
  helperText?: string;
  leftIcon?: React.ReactNode;
  rightAction?: React.ReactNode;
  isPassword?: boolean;
}

export const Input = React.forwardRef<HTMLInputElement, InputProps>(
  (
    {
      className,
      type = 'text',
      label,
      error,
      helperText,
      leftIcon,
      rightAction,
      isPassword = false,
      id,
      required,
      ...props
    },
    ref
  ) => {
    const inputId = id || (label ? label.toLowerCase().replace(/\s+/g, '-') : undefined);
    const [showPassword, setShowPassword] = useState(false);

    const computedType = isPassword ? (showPassword ? 'text' : 'password') : type;

    return (
      <div className="w-full space-y-1.5 text-left">
        {label && (
          <label
            htmlFor={inputId}
            className="block text-xs font-semibold uppercase tracking-wider text-slate-300"
          >
            {label}
            {required && <span className="text-rose-400 ml-1" aria-hidden="true">*</span>}
          </label>
        )}

        <div className="relative flex items-center">
          {leftIcon && (
            <div className="absolute left-3.5 flex items-center pointer-events-none text-slate-400">
              {leftIcon}
            </div>
          )}

          <input
            ref={ref}
            id={inputId}
            type={computedType}
            required={required}
            aria-invalid={Boolean(error)}
            aria-describedby={
              error
                ? `${inputId}-error`
                : helperText
                ? `${inputId}-helper`
                : undefined
            }
            className={cn(
              'w-full bg-slate-900/90 text-slate-100 placeholder:text-slate-500 rounded-xl px-3.5 py-2.5 text-sm transition-all duration-150',
              'border border-slate-700/80 hover:border-slate-600',
              'focus:outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20',
              'disabled:opacity-50 disabled:bg-slate-900/40 disabled:cursor-not-allowed',
              leftIcon ? 'pl-10' : 'pl-3.5',
              (isPassword || rightAction) ? 'pr-11' : 'pr-3.5',
              error && 'border-rose-500 focus:border-rose-500 focus:ring-rose-500/20',
              className
            )}
            {...props}
          />

          {isPassword ? (
            <button
              type="button"
              onClick={() => setShowPassword((prev) => !prev)}
              aria-label={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
              className="absolute right-3.5 p-1 text-slate-400 hover:text-slate-200 transition-colors focus:outline-none focus:ring-1 focus:ring-indigo-400 rounded"
            >
              {showPassword ? (
                <EyeOff className="w-4 h-4" aria-hidden="true" />
              ) : (
                <Eye className="w-4 h-4" aria-hidden="true" />
              )}
            </button>
          ) : (
            rightAction && (
              <div className="absolute right-3 flex items-center">
                {rightAction}
              </div>
            )
          )}
        </div>

        {error && (
          <p
            id={`${inputId}-error`}
            role="alert"
            className="text-xs text-rose-400 font-medium flex items-center gap-1"
          >
            {error}
          </p>
        )}

        {!error && helperText && (
          <p id={`${inputId}-helper`} className="text-xs text-slate-400">
            {helperText}
          </p>
        )}
      </div>
    );
  }
);

Input.displayName = 'Input';
