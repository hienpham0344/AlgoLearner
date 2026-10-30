import React, { useState, useRef, useEffect } from 'react';
import byteSvg from '@/assets/mascot-byte.svg';
import bugSvg from '@/assets/mascot-bug.svg';
import kitSvg from '@/assets/mascot-kit.svg';
import byteAngrySvg from '@/assets/mascot-byte-angry.svg';
import bugAngrySvg from '@/assets/mascot-bug-angry.svg';
import kitAngrySvg from '@/assets/mascot-kit-angry.svg';

interface MascotStatus {
  clicks: number;
  isAngry: boolean;
  message: string;
}

export const InteractiveHeroMascots: React.FC = () => {
  const [mascots, setMascots] = useState<Record<'byte' | 'bug' | 'kit', MascotStatus>>({
    byte: { clicks: 0, isAngry: false, message: '' },
    bug: { clicks: 0, isAngry: false, message: '' },
    kit: { clicks: 0, isAngry: false, message: '' },
  });

  const resetTimers = useRef<Record<'byte' | 'bug' | 'kit', NodeJS.Timeout | null>>({
    byte: null,
    bug: null,
    kit: null,
  });

  const angryMessages = {
    byte: 'Bíp bíp! Đừng chọc tui nữa mà! 💢',
    bug: 'Bzz! Cắn cho một phát giờ! 💢',
    kit: 'Meo gừ! Cào rách màn hình giờ! 💢',
  };

  const handleMascotClick = (type: 'byte' | 'bug' | 'kit') => {
    setMascots((prev) => {
      const current = prev[type];
      const nextClicks = current.clicks + 1;

      // Clear existing cooldown timer if any
      if (resetTimers.current[type]) {
        clearTimeout(resetTimers.current[type]!);
      }

      // Threshold: 4 clicks trigger ANGRY
      const becomeAngry = nextClicks >= 4 || current.isAngry;

      // Auto calm down after 5 seconds
      resetTimers.current[type] = setTimeout(() => {
        setMascots((m) => ({
          ...m,
          [type]: { clicks: 0, isAngry: false, message: '' },
        }));
      }, 5000);

      return {
        ...prev,
        [type]: {
          clicks: nextClicks,
          isAngry: becomeAngry,
          message: becomeAngry ? angryMessages[type] : '',
        },
      };
    });
  };

  useEffect(() => {
    return () => {
      // Cleanup all timers on unmount
      Object.values(resetTimers.current).forEach((t) => {
        if (t) clearTimeout(t);
      });
    };
  }, []);

  return (
    <div className="pt-10 pb-2 flex items-end justify-center gap-6 sm:gap-10 lg:gap-14 select-none relative z-10">
      {/* 1. Byte (Robot) */}
      <div className="relative flex flex-col items-center">
        {mascots.byte.isAngry && (
          <div className="absolute -top-12 z-20 bg-rose-500 text-white text-[11px] font-bold px-2.5 py-1 rounded-xl shadow-lg border border-rose-400 whitespace-nowrap animate-bounce flex items-center gap-1">
            <span>{mascots.byte.message}</span>
            <div className="absolute -bottom-1.5 left-1/2 -translate-x-1/2 w-3 h-3 bg-rose-500 rotate-45" />
          </div>
        )}
        <button
          type="button"
          onClick={() => handleMascotClick('byte')}
          className="group focus:outline-none cursor-pointer"
          aria-label="Byte mascot"
          title={mascots.byte.isAngry ? 'Byte đang quạu! (Angry)' : 'Byte - Robot học code (Click thử xem!)'}
        >
          <img
            key={mascots.byte.isAngry ? 'byte-angry' : 'byte-normal'}
            src={mascots.byte.isAngry ? byteAngrySvg : byteSvg}
            alt="Byte - Robot học code"
            className={`w-24 sm:w-28 lg:w-32 h-auto object-contain transition-all duration-300 drop-shadow-md ${
              mascots.byte.isAngry
                ? 'scale-110 rotate-3 filter drop-shadow-[0_4px_12px_rgba(244,63,94,0.4)]'
                : 'hover:-translate-y-1.5 hover:scale-105 active:scale-95'
            }`}
          />
        </button>
      </div>

      {/* 2. Bug (Bọ săn lỗi) */}
      <div className="relative flex flex-col items-center">
        {mascots.bug.isAngry && (
          <div className="absolute -top-12 z-20 bg-amber-500 text-white text-[11px] font-bold px-2.5 py-1 rounded-xl shadow-lg border border-amber-400 whitespace-nowrap animate-bounce flex items-center gap-1">
            <span>{mascots.bug.message}</span>
            <div className="absolute -bottom-1.5 left-1/2 -translate-x-1/2 w-3 h-3 bg-amber-500 rotate-45" />
          </div>
        )}
        <button
          type="button"
          onClick={() => handleMascotClick('bug')}
          className="group focus:outline-none cursor-pointer"
          aria-label="Bug mascot"
          title={mascots.bug.isAngry ? 'Bug đang quạu! (Angry)' : 'Bug - Bọ săn lỗi (Click thử xem!)'}
        >
          <img
            key={mascots.bug.isAngry ? 'bug-angry' : 'bug-normal'}
            src={mascots.bug.isAngry ? bugAngrySvg : bugSvg}
            alt="Bug - Bọ săn lỗi"
            className={`w-24 sm:w-28 lg:w-32 h-auto object-contain transition-all duration-300 drop-shadow-md ${
              mascots.bug.isAngry
                ? 'scale-110 -rotate-3 filter drop-shadow-[0_4px_12px_rgba(245,158,11,0.4)]'
                : 'hover:-translate-y-1.5 hover:scale-105 active:scale-95'
            }`}
          />
        </button>
      </div>

      {/* 3. Kit (Mèo lập trình) */}
      <div className="relative flex flex-col items-center">
        {mascots.kit.isAngry && (
          <div className="absolute -top-12 z-20 bg-rose-500 text-white text-[11px] font-bold px-2.5 py-1 rounded-xl shadow-lg border border-rose-400 whitespace-nowrap animate-bounce flex items-center gap-1">
            <span>{mascots.kit.message}</span>
            <div className="absolute -bottom-1.5 left-1/2 -translate-x-1/2 w-3 h-3 bg-rose-500 rotate-45" />
          </div>
        )}
        <button
          type="button"
          onClick={() => handleMascotClick('kit')}
          className="group focus:outline-none cursor-pointer"
          aria-label="Kit mascot"
          title={mascots.kit.isAngry ? 'Kit đang quạu! (Angry)' : 'Kit - Mèo lập trình (Click thử xem!)'}
        >
          <img
            key={mascots.kit.isAngry ? 'kit-angry' : 'kit-normal'}
            src={mascots.kit.isAngry ? kitAngrySvg : kitSvg}
            alt="Kit - Mèo lập trình"
            className={`w-24 sm:w-28 lg:w-32 h-auto object-contain transition-all duration-300 drop-shadow-md ${
              mascots.kit.isAngry
                ? 'scale-110 rotate-6 filter drop-shadow-[0_4px_12px_rgba(244,63,94,0.4)]'
                : 'hover:-translate-y-1.5 hover:scale-105 active:scale-95'
            }`}
          />
        </button>
      </div>
    </div>
  );
};
