import React from 'react';
import { Moon } from 'lucide-react';

export type PresenceStatus = 'ACTIVE' | 'OFFLINE';

interface UserAvatarProps {
  name: string;
  size?: 'xs' | 'sm' | 'md' | 'lg';
  presence?: PresenceStatus;
  ringColor?: string;
  className?: string;
}

export const UserAvatar: React.FC<UserAvatarProps> = ({
  name,
  size = 'md',
  presence,
  ringColor = 'ring-white',
  className = '',
}) => {
  const getInitials = (n: string) => {
    return (
      n
        .replace(/^(Prof\.|Dr\.|Mr\.|Ms\.|Mrs\.)\s*/i, '')
        .split(' ')
        .filter(Boolean)
        .map(part => part[0])
        .slice(0, 2)
        .join('')
        .toUpperCase() || 'U'
    );
  };

  const sizeStyles = {
    xs: {
      box: 'w-5 h-5 text-[9px] rounded-full',
      activeIndicator: 'w-1.5 h-1.5 ring-1 -bottom-0.5 -right-0.5',
      offlinePosition: '-bottom-0.5 -right-0.5',
      moonSize: 8,
    },
    sm: {
      box: 'w-7 h-7 text-[11px] rounded-lg',
      activeIndicator: 'w-2 h-2 ring-1.5 -bottom-0.5 -right-0.5',
      offlinePosition: '-bottom-0.5 -right-0.5',
      moonSize: 9.5,
    },
    md: {
      box: 'w-9 h-9 text-xs rounded-xl',
      activeIndicator: 'w-2.5 h-2.5 ring-2 -bottom-0.5 -right-0.5',
      offlinePosition: '-bottom-0.5 -right-0.5',
      moonSize: 11,
    },
    lg: {
      box: 'w-10 h-10 text-sm rounded-xl',
      activeIndicator: 'w-3 h-3 ring-2 -bottom-0.5 -right-0.5',
      offlinePosition: '-bottom-0.5 -right-0.5',
      moonSize: 12,
    },
  }[size];

  return (
    <div className={`relative inline-flex shrink-0 select-none ${className}`}>
      {/* Base Avatar Layer */}
      <div
        className={`${sizeStyles.box} bg-slate-100 border border-slate-200/90 text-slate-700 font-bold flex items-center justify-center`}
        aria-hidden="true"
      >
        {getInitials(name)}
      </div>

      {/* Floating Active Indicator Layer */}
      {presence === 'ACTIVE' && (
        <span
          className={`absolute rounded-full bg-emerald-500 shadow-xs ring-offset-0 ${ringColor} ${sizeStyles.activeIndicator}`}
          title="Active"
          aria-label="Active"
        />
      )}

      {/* Floating Offline Crescent Moon Indicator Layer */}
      {presence === 'OFFLINE' && (
        <span
          className={`absolute flex items-center justify-center leading-none ${sizeStyles.offlinePosition}`}
          title="Offline"
          aria-label="Offline"
        >
          <Moon
            size={sizeStyles.moonSize}
            className="text-amber-500 fill-amber-400 drop-shadow-[0_1px_1.5px_rgba(0,0,0,0.28)] -rotate-12"
            strokeWidth={2}
            aria-hidden="true"
          />
        </span>
      )}
    </div>
  );
};
