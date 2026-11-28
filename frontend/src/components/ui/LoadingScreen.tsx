import type { CSSProperties, FC } from 'react';
import { cn } from '@/utils/cn';

export interface LoadingScreenProps {
  variant?: 'fullscreen' | 'page' | 'inline';
  /** Skeletons belong to the content component so they share its exact layout. */
  appearance?: 'line';
  message?: string;
  submessage?: string;
  className?: string;
}

export const Skeleton: FC<{ className?: string }> = ({ className }) => (
  <span className={cn('loading-bone', className)} aria-hidden="true" />
);

/** Replaces a value inside its existing text line without changing that line's height. */
export const LoadingValue: FC<{ width?: CSSProperties['width']; className?: string }> = ({ width = '8ch', className }) => (
  <span className={cn('loading-value', className)} style={{ width }} aria-hidden="true">
    <span className="loading-value-measure">0</span>
    <Skeleton className="loading-value-bone" />
  </span>
);

export const LoadingScreen: FC<LoadingScreenProps> = ({
  variant = 'fullscreen',
  message = 'Preparing your workspace…',
  submessage,
  className,
}) => (
  <div
    role="status"
    aria-live="polite"
    className={cn('loading-state', `loading-state--${variant}`, 'loading-state--line', className)}
  >
    <div className="loading-line-content">
      <div className="loading-line" aria-hidden="true" />
      <p className="loading-message">{message}</p>
      {submessage && <p className="loading-submessage">{submessage}</p>}
    </div>
  </div>
);
