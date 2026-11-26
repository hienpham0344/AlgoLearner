import React, { useEffect, useId, useLayoutEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { Check, ChevronDown } from 'lucide-react';
import type { WorkspaceMode } from '../../utils/practiceStorage';

interface LayoutOption {
  value: WorkspaceMode;
  label: string;
  triggerLabel: string;
  description: string;
}

const LAYOUT_OPTIONS: LayoutOption[] = [
  {
    value: 'split',
    label: 'Split',
    triggerLabel: 'Split view',
    description: 'Read and code side by side',
  },
  {
    value: 'focus',
    label: 'Focus',
    triggerLabel: 'Focus mode',
    description: 'Maximize the editor',
  },
  {
    value: 'console',
    label: 'Console',
    triggerLabel: 'Console view',
    description: 'Prioritize test results',
  },
];

function LayoutSilhouette({ mode }: { mode: WorkspaceMode }) {
  if (mode === 'split') {
    return (
      <svg width="16" height="14" viewBox="0 0 16 14" fill="none" aria-hidden="true">
        <rect x="0.75" y="0.75" width="14.5" height="12.5" rx="1.5" stroke="currentColor" strokeWidth="1.2" />
        <line x1="6" y1="1" x2="6" y2="13" stroke="currentColor" strokeWidth="1.2" />
      </svg>
    );
  }
  if (mode === 'focus') {
    return (
      <svg width="16" height="14" viewBox="0 0 16 14" fill="none" aria-hidden="true">
        <rect x="0.75" y="0.75" width="14.5" height="12.5" rx="1.5" stroke="currentColor" strokeWidth="1.2" />
        <line x1="3.5" y1="4.5" x2="10.5" y2="4.5" stroke="currentColor" strokeWidth="1.2" />
        <line x1="3.5" y1="7.5" x2="12.5" y2="7.5" stroke="currentColor" strokeWidth="1.2" />
      </svg>
    );
  }
  return (
    <svg width="16" height="14" viewBox="0 0 16 14" fill="none" aria-hidden="true">
      <rect x="0.75" y="0.75" width="14.5" height="12.5" rx="1.5" stroke="currentColor" strokeWidth="1.2" />
      <line x1="6" y1="1" x2="6" y2="13" stroke="currentColor" strokeWidth="1.2" />
      <line x1="6" y1="7" x2="15" y2="7" stroke="currentColor" strokeWidth="1.2" />
    </svg>
  );
}

interface WorkspaceLayoutSelectorProps {
  value: WorkspaceMode;
  onChange: (mode: WorkspaceMode) => void;
  tone: 'light' | 'dark';
}

export const WorkspaceLayoutSelector: React.FC<WorkspaceLayoutSelectorProps> = ({
  value,
  onChange,
  tone,
}) => {
  const [open, setOpen] = useState(false);
  const [activeIndex, setActiveIndex] = useState(0);
  const [pos, setPos] = useState({ top: 0, left: 0, width: 256 });
  const triggerRef = useRef<HTMLButtonElement>(null);
  const popupRef = useRef<HTMLDivElement>(null);
  const optionRefs = useRef<Array<HTMLButtonElement | null>>([]);
  const menuId = useId();

  const currentOption = LAYOUT_OPTIONS.find(o => o.value === value) ?? LAYOUT_OPTIONS[0];
  const selectedIndex = Math.max(0, LAYOUT_OPTIONS.findIndex(o => o.value === value));

  const updatePosition = () => {
    const rect = triggerRef.current?.getBoundingClientRect();
    if (!rect) return;
    const width = Math.min(264, window.innerWidth - 16);
    const left = Math.max(8, Math.min(rect.right - width, window.innerWidth - width - 8));
    setPos({ top: rect.bottom + 6, left, width });
  };

  const dismiss = (restoreFocus: boolean) => {
    setOpen(false);
    if (restoreFocus) window.requestAnimationFrame(() => triggerRef.current?.focus());
  };

  const show = (startIndex = selectedIndex) => {
    setActiveIndex(startIndex);
    updatePosition();
    setOpen(true);
  };

  const choose = (index: number) => {
    const option = LAYOUT_OPTIONS[index];
    if (!option) return;
    onChange(option.value);
    dismiss(true);
  };

  useLayoutEffect(() => {
    if (!open) return;
    updatePosition();
    optionRefs.current[activeIndex]?.focus();
  }, [open]);

  useEffect(() => {
    if (!open) return;
    const onPointerDown = (event: PointerEvent) => {
      const target = event.target;
      if (!(target instanceof Node)) return;
      if (!triggerRef.current?.contains(target) && !popupRef.current?.contains(target)) {
        dismiss(false);
      }
    };
    const onReposition = () => updatePosition();
    document.addEventListener('pointerdown', onPointerDown, true);
    window.addEventListener('resize', onReposition);
    window.addEventListener('scroll', onReposition, true);
    return () => {
      document.removeEventListener('pointerdown', onPointerDown, true);
      window.removeEventListener('resize', onReposition);
      window.removeEventListener('scroll', onReposition, true);
    };
  }, [open]);

  const onMenuKeyDown = (event: React.KeyboardEvent<HTMLDivElement>) => {
    if (event.key === 'Escape') {
      event.preventDefault();
      event.stopPropagation();
      dismiss(true);
      return;
    }
    if (event.key === 'Tab') {
      setOpen(false);
      return;
    }
    let next = activeIndex;
    if (event.key === 'ArrowDown') next = (activeIndex + 1) % LAYOUT_OPTIONS.length;
    else if (event.key === 'ArrowUp') next = (activeIndex - 1 + LAYOUT_OPTIONS.length) % LAYOUT_OPTIONS.length;
    else if (event.key === 'Home') next = 0;
    else if (event.key === 'End') next = LAYOUT_OPTIONS.length - 1;
    else if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      choose(activeIndex);
      return;
    } else return;

    event.preventDefault();
    setActiveIndex(next);
    optionRefs.current[next]?.focus();
  };

  return (
    <>
      <button
        ref={triggerRef}
        type="button"
        className="practice-layout-trigger"
        data-tone={tone}
        aria-label={`Workspace layout: ${currentOption.triggerLabel}`}
        aria-haspopup="listbox"
        aria-controls={open ? menuId : undefined}
        aria-expanded={open}
        onClick={() => (open ? dismiss(false) : show())}
        onKeyDown={event => {
          if (event.key === 'Escape' && open) {
            event.preventDefault();
            dismiss(true);
          } else if (!open && ['ArrowDown', 'ArrowUp', 'Enter', ' '].includes(event.key)) {
            event.preventDefault();
            show(selectedIndex);
          }
        }}
      >
        <LayoutSilhouette mode={value} />
        <span>{currentOption.triggerLabel}</span>
        <ChevronDown size={13} aria-hidden="true" />
      </button>

      {open &&
        createPortal(
          <div
            ref={popupRef}
            id={menuId}
            role="listbox"
            aria-label="Workspace layout"
            className="practice-layout-popup"
            data-tone={tone}
            style={{ top: pos.top, left: pos.left, width: pos.width }}
            onKeyDown={onMenuKeyDown}
          >
            <div className="practice-layout-heading">Workspace layout</div>
            {LAYOUT_OPTIONS.map((option, index) => {
              const isSelected = option.value === value;
              return (
                <button
                  key={option.value}
                  ref={el => {
                    optionRefs.current[index] = el;
                  }}
                  type="button"
                  role="option"
                  tabIndex={-1}
                  aria-selected={isSelected}
                  className="practice-layout-option"
                  onMouseEnter={() => setActiveIndex(index)}
                  onClick={() => choose(index)}
                >
                  <span className="practice-layout-option-icon">
                    <LayoutSilhouette mode={option.value} />
                  </span>
                  <span className="practice-layout-option-copy">
                    <strong>{option.label}</strong>
                    <small>{option.description}</small>
                  </span>
                  {isSelected && <Check size={14} className="shrink-0" aria-hidden="true" />}
                </button>
              );
            })}
          </div>,
          document.body,
        )}
    </>
  );
};

