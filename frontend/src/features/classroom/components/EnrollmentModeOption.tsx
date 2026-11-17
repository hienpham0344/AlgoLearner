import React, { useState, useRef, useEffect, useCallback } from 'react';
import { createPortal } from 'react-dom';

interface EnrollmentModeOptionProps {
  id: string;
  label: string;
  description: string;
  isSelected: boolean;
  disabled?: boolean;
  onClick: () => void;
}

export const EnrollmentModeOption: React.FC<EnrollmentModeOptionProps> = ({
  id,
  label,
  description,
  isSelected,
  disabled = false,
  onClick,
}) => {
  const [isVisible, setIsVisible] = useState(false);
  const [coords, setCoords] = useState<{
    top: number;
    left: number;
    caretOffset: number;
    placeBelow: boolean;
  } | null>(null);

  const buttonRef = useRef<HTMLButtonElement>(null);
  const timerRef = useRef<number | null>(null);

  const updateCoords = useCallback(() => {
    if (!buttonRef.current) return;
    const rect = buttonRef.current.getBoundingClientRect();

    // Flip below if too close to viewport top
    const placeBelow = rect.top < 64;
    const targetY = placeBelow ? rect.bottom + 8 : rect.top - 8;

    // Horizontal center aligned with button
    const buttonCenterX = rect.left + rect.width / 2;

    // Viewport bounds awareness (prevent clipping on viewport edges or near sidebar)
    const viewportWidth = window.innerWidth;
    const estimatedHalfWidth = 140; // Approx half width of tooltip
    const minPadding = 16;

    const minCenter = minPadding + estimatedHalfWidth;
    const maxCenter = viewportWidth - minPadding - estimatedHalfWidth;

    const clampedCenterX = Math.max(minCenter, Math.min(buttonCenterX, maxCenter));
    const caretOffset = buttonCenterX - clampedCenterX;

    setCoords({
      top: targetY,
      left: clampedCenterX,
      caretOffset,
      placeBelow,
    });
  }, []);

  const showTooltip = () => {
    if (timerRef.current) clearTimeout(timerRef.current);
    timerRef.current = window.setTimeout(() => {
      updateCoords();
      setIsVisible(true);
    }, 200); // 200ms delay to prevent accidental popups
  };

  const hideTooltip = () => {
    if (timerRef.current) {
      clearTimeout(timerRef.current);
      timerRef.current = null;
    }
    setIsVisible(false);
  };

  // Keep tooltip aligned during viewport changes or scrolls
  useEffect(() => {
    if (!isVisible) return;
    updateCoords();

    const handleWindowChange = () => {
      updateCoords();
    };

    window.addEventListener('scroll', handleWindowChange, true);
    window.addEventListener('resize', handleWindowChange);
    return () => {
      window.removeEventListener('scroll', handleWindowChange, true);
      window.removeEventListener('resize', handleWindowChange);
    };
  }, [isVisible, updateCoords]);

  useEffect(() => {
    return () => {
      if (timerRef.current) clearTimeout(timerRef.current);
    };
  }, []);

  return (
    <div className="relative inline-flex">
      <button
        ref={buttonRef}
        id={id}
        type="button"
        role="radio"
        aria-checked={isSelected}
        aria-describedby={`${id}-tooltip`}
        disabled={disabled}
        onClick={onClick}
        onMouseEnter={showTooltip}
        onMouseLeave={hideTooltip}
        onFocus={showTooltip}
        onBlur={hideTooltip}
        onTouchStart={() => {
          if (!isVisible) {
            showTooltip();
          } else {
            hideTooltip();
          }
        }}
        className={`h-9 px-4 rounded-lg text-xs font-semibold transition-all border select-none ${
          isSelected
            ? 'bg-slate-900 text-white border-slate-900 shadow-xs ring-1 ring-slate-900 font-bold'
            : 'bg-white/80 hover:bg-white text-slate-700 border-slate-200/90 hover:border-slate-300 shadow-2xs font-medium'
        } ${disabled ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer'}`}
      >
        <span>{label}</span>
      </button>

      {/* Render tooltip in portal (document.body) to escape any parent stacking contexts & overflow clipping */}
      {isVisible &&
        coords &&
        typeof document !== 'undefined' &&
        createPortal(
          <div
            id={`${id}-tooltip`}
            role="tooltip"
            style={{
              position: 'fixed',
              top: `${coords.top}px`,
              left: `${coords.left}px`,
              transform: coords.placeBelow ? 'translate(-50%, 0)' : 'translate(-50%, -100%)',
              zIndex: 9999,
            }}
            className="px-3 py-1.5 rounded-lg text-[11.5px] font-medium leading-snug text-slate-800 bg-slate-100/90 backdrop-blur-md border border-slate-300/70 shadow-[0_4px_16px_rgba(0,0,0,0.06),0_1px_3px_rgba(0,0,0,0.04)] pointer-events-none whitespace-nowrap select-none transition-opacity duration-150"
          >
            {description}
            {/* Subtle Caret Indicator */}
            {coords.placeBelow ? (
              <div
                className="absolute bottom-full left-1/2 -mb-px border-4 border-transparent border-b-slate-300/70"
                style={{ transform: `translateX(calc(-50% + ${coords.caretOffset}px))` }}
                aria-hidden="true"
              />
            ) : (
              <div
                className="absolute top-full left-1/2 -mt-px border-4 border-transparent border-t-slate-300/70"
                style={{ transform: `translateX(calc(-50% + ${coords.caretOffset}px))` }}
                aria-hidden="true"
              />
            )}
          </div>,
          document.body,
        )}
    </div>
  );
};
