import React, { useState, useRef, useEffect } from 'react';
import byteWrongSvg from '@/assets/mascot-byte-wrong.svg';
import bugWrongSvg from '@/assets/mascot-bug-wrong.svg';
import kitWrongSvg from '@/assets/mascot-kit-wrong.svg';
import byteAngrySvg from '@/assets/mascot-byte-angry.svg';
import bugAngrySvg from '@/assets/mascot-bug-angry.svg';
import kitAngrySvg from '@/assets/mascot-kit-angry.svg';

export interface WrongMascot {
  src: string;
  angrySrc: string;
  alt: string;
  name: 'Byte' | 'Bug' | 'Kit';
}

export const WRONG_MASCOTS: WrongMascot[] = [
  {
    src: byteWrongSvg,
    angrySrc: byteAngrySvg,
    alt: 'Byte - Robot học code',
    name: 'Byte',
  },
  {
    src: bugWrongSvg,
    angrySrc: bugAngrySvg,
    alt: 'Bug - Bọ săn lỗi',
    name: 'Bug',
  },
  {
    src: kitWrongSvg,
    angrySrc: kitAngrySvg,
    alt: 'Kit - Mèo lập trình',
    name: 'Kit',
  },
];

export const getRandomWrongMascot = (): WrongMascot => {
  const randomIndex = Math.floor(Math.random() * WRONG_MASCOTS.length);
  return WRONG_MASCOTS[randomIndex];
};

interface MascotAlertBubbleProps {
  title: string;
  description: string;
  className?: string;
  mascot?: WrongMascot;
}

export const MascotAlertBubble: React.FC<MascotAlertBubbleProps> = ({
  title,
  description,
  className = '',
  mascot,
}) => {
  const displayMascot = mascot || WRONG_MASCOTS[0];

  const [clicks, setClicks] = useState(0);
  const [isAngry, setIsAngry] = useState(false);
  const resetTimer = useRef<NodeJS.Timeout | null>(null);

  const handleMascotClick = () => {
    const next = clicks + 1;
    setClicks(next);

    if (resetTimer.current) {
      clearTimeout(resetTimer.current);
    }

    if (next >= 3 || isAngry) {
      setIsAngry(true);
    }

    resetTimer.current = setTimeout(() => {
      setIsAngry(false);
      setClicks(0);
    }, 4500);
  };

  useEffect(() => {
    return () => {
      if (resetTimer.current) clearTimeout(resetTimer.current);
    };
  }, []);

  // When angry, show angry dialog
  const activeTitle = isAngry ? 'Grrr! Đừng chọc tui nữa mà! 💢' : title;
  const activeDescription = isAngry
    ? `Tui là ${displayMascot.name}, tui đang quạu vì bạn bấm nhầm mật khẩu đấy!`
    : description;
  const activeImage = isAngry ? displayMascot.angrySrc : displayMascot.src;

  return (
    <div
      role="alert"
      className={`rounded-2xl bg-[#F4F4F6] p-4 sm:p-4.5 flex items-center gap-3 sm:gap-4 transition-all duration-200 select-none ${className}`}
    >
      {/* Animated Mascot on the Left (Clickable Easter Egg!) */}
      <button
        type="button"
        onClick={handleMascotClick}
        className="shrink-0 flex items-center justify-center focus:outline-none cursor-pointer group"
        title={isAngry ? `${displayMascot.name} đang quạu!` : `Click ${displayMascot.name} nhiều lần thử xem!`}
      >
        <img
          key={isAngry ? `${displayMascot.name}-angry` : displayMascot.src}
          src={activeImage}
          alt={displayMascot.alt}
          className={`w-13 sm:w-14 h-auto object-contain transition-all duration-300 drop-shadow-sm ${
            isAngry
              ? 'scale-115 rotate-6 filter drop-shadow-[0_4px_10px_rgba(244,63,94,0.35)]'
              : 'hover:scale-105 active:scale-95'
          }`}
        />
      </button>

      {/* Soft Pillowy Speech Bubble */}
      <div className="relative flex-1 bg-white rounded-2xl px-4 py-3 sm:py-3.5 shadow-[0_2px_8px_-2px_rgba(0,0,0,0.05),0_1px_3px_rgba(0,0,0,0.03)] text-left">
        {/* Soft Organic Curved Tail (Seamless with white card) */}
        <svg
          className="absolute -left-2 top-5 w-2.5 h-4.5 text-white fill-current filter drop-shadow-[-1px_1px_1px_rgba(0,0,0,0.02)]"
          viewBox="0 0 10 16"
          preserveAspectRatio="none"
          aria-hidden="true"
        >
          <path d="M10 0 C7 1.5, 3.5 4.5, 0.8 7 C0.2 7.5, 0.2 8.5, 0.8 9 C3.5 11.5, 7 14.5, 10 16 Z" />
        </svg>

        {/* Bubble Text Content */}
        <div className="relative z-10 space-y-0.5">
          <p
            className={`text-[13px] sm:text-[13.5px] font-bold tracking-tight leading-snug transition-colors ${
              isAngry ? 'text-rose-600' : 'text-slate-900'
            }`}
          >
            {activeTitle}
          </p>
          <p className="text-[11.5px] sm:text-xs text-slate-500 font-normal leading-relaxed">
            {activeDescription}
          </p>
        </div>
      </div>
    </div>
  );
};
