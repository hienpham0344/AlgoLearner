import React from 'react';
import { cn } from '@/utils/cn';

export interface CardProps extends React.HTMLAttributes<HTMLDivElement> {
  variant?: 'default' | 'glass' | 'interactive';
}

export const Card: React.FC<CardProps> = ({
  className,
  variant = 'default',
  children,
  ...props
}) => {
  const base = 'rounded-2xl transition-all duration-200';

  const variants = {
    default: 'bg-slate-900 border border-slate-800 shadow-xl shadow-slate-950/50',
    glass: 'bg-slate-900/80 backdrop-blur-xl border border-slate-800/80 shadow-2xl shadow-black/60',
    interactive: 'bg-slate-900/90 border border-slate-800 hover:border-slate-700/80 hover:shadow-xl hover:shadow-indigo-500/5 cursor-pointer',
  };

  return (
    <div className={cn(base, variants[variant], className)} {...props}>
      {children}
    </div>
  );
};
