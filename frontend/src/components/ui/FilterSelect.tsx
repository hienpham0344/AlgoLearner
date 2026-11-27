import { useEffect, useId, useLayoutEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { Check, ChevronDown } from 'lucide-react';
import './FilterSelect.css';

export interface FilterOption { value: string; label: string }

interface FilterSelectProps {
  label: string;
  value: string;
  options: FilterOption[];
  onValueChange: (value: string) => void;
  className?: string;
  tone?: 'light' | 'dark';
}

interface PopupPosition { top: number; left: number; width: number; maxHeight: number }

/** Small, single-value filter. The listbox is portaled to avoid clipping by workspaces. */
export function FilterSelect({ label, value, options, onValueChange, className = '', tone = 'light' }: FilterSelectProps) {
  const [open, setOpen] = useState(false);
  const [activeIndex, setActiveIndex] = useState(0);
  const [position, setPosition] = useState<PopupPosition>({ top: 0, left: 0, width: 200, maxHeight: 280 });
  const triggerRef = useRef<HTMLButtonElement>(null);
  const popupRef = useRef<HTMLDivElement>(null);
  const optionRefs = useRef<Array<HTMLButtonElement | null>>([]);
  const typeaheadRef = useRef('');
  const typeaheadTimerRef = useRef<number | null>(null);
  const listboxId = useId();
  const selectedIndex = Math.max(0, options.findIndex(option => option.value === value));
  const selectedLabel = options[selectedIndex]?.label ?? options[0]?.label ?? label;

  const updatePosition = () => {
    const rect = triggerRef.current?.getBoundingClientRect();
    if (!rect) return;
    const width = Math.min(Math.max(rect.width, 190), window.innerWidth - 16);
    const expectedHeight = Math.min(42 + options.length * 36, 280);
    const spaceBelow = window.innerHeight - rect.bottom - 8;
    const spaceAbove = rect.top - 8;
    const above = spaceBelow < expectedHeight && spaceAbove > spaceBelow;
    const maxHeight = Math.max(90, Math.min(expectedHeight, above ? spaceAbove : spaceBelow));
    setPosition({
      top: above ? Math.max(8, rect.top - maxHeight - 5) : rect.bottom + 5,
      left: Math.max(8, Math.min(rect.left, window.innerWidth - width - 8)),
      width,
      maxHeight,
    });
  };

  const dismiss = (restoreFocus: boolean) => {
    setOpen(false);
    typeaheadRef.current = '';
    if (restoreFocus) requestAnimationFrame(() => triggerRef.current?.focus());
  };

  const show = (index = selectedIndex) => {
    setActiveIndex(index);
    updatePosition();
    setOpen(true);
  };

  const choose = (index: number) => {
    const option = options[index];
    if (!option) return;
    onValueChange(option.value);
    dismiss(true);
  };

  useLayoutEffect(() => {
    if (!open) return;
    updatePosition();
    optionRefs.current[activeIndex]?.focus();
  }, [open]);

  useEffect(() => {
    if (!open) return;
    const outside = (event: PointerEvent) => {
      const target = event.target;
      if (!(target instanceof Node)) return;
      if (!triggerRef.current?.contains(target) && !popupRef.current?.contains(target)) dismiss(false);
    };
    const reposition = () => updatePosition();
    document.addEventListener('pointerdown', outside, true);
    window.addEventListener('resize', reposition);
    window.addEventListener('scroll', reposition, true);
    return () => {
      document.removeEventListener('pointerdown', outside, true);
      window.removeEventListener('resize', reposition);
      window.removeEventListener('scroll', reposition, true);
    };
  }, [open, options.length]);

  useEffect(() => () => { if (typeaheadTimerRef.current !== null) window.clearTimeout(typeaheadTimerRef.current); }, []);

  const onListKeyDown = (event: React.KeyboardEvent<HTMLDivElement>) => {
    if (event.key === 'Escape') { event.preventDefault(); event.stopPropagation(); dismiss(true); return; }
    if (event.key === 'Tab') { setOpen(false); return; }
    let next = activeIndex;
    if (event.key === 'ArrowDown') next = (activeIndex + 1) % options.length;
    else if (event.key === 'ArrowUp') next = (activeIndex - 1 + options.length) % options.length;
    else if (event.key === 'Home') next = 0;
    else if (event.key === 'End') next = options.length - 1;
    else if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); choose(activeIndex); return; }
    else if (event.key.length === 1 && !event.ctrlKey && !event.metaKey && !event.altKey) {
      typeaheadRef.current += event.key.toLocaleLowerCase();
      if (typeaheadTimerRef.current !== null) window.clearTimeout(typeaheadTimerRef.current);
      typeaheadTimerRef.current = window.setTimeout(() => { typeaheadRef.current = ''; }, 600);
      const match = options.findIndex(option => option.label.toLocaleLowerCase().startsWith(typeaheadRef.current));
      if (match >= 0) next = match;
    } else return;
    event.preventDefault();
    setActiveIndex(next);
    optionRefs.current[next]?.focus();
  };

  return <>
    <button
      ref={triggerRef}
      type="button"
      className={`filter-select-trigger ${className}`.trim()}
      data-tone={tone}
      aria-label={`${label}: ${selectedLabel}`}
      aria-haspopup="listbox"
      aria-controls={open ? listboxId : undefined}
      aria-expanded={open}
      onClick={() => open ? dismiss(false) : show()}
      onKeyDown={event => {
        if (event.key === 'Escape' && open) { event.preventDefault(); event.stopPropagation(); dismiss(true); }
        else if (!open && ['ArrowDown', 'ArrowUp', 'Enter', ' '].includes(event.key)) { event.preventDefault(); show(event.key === 'ArrowUp' ? Math.max(0, selectedIndex - 1) : selectedIndex); }
      }}
    ><span>{selectedLabel}</span><ChevronDown size={14} aria-hidden="true" /></button>
    {open && createPortal(<div
      ref={popupRef}
      id={listboxId}
      className="filter-select-popup"
      data-tone={tone}
      role="listbox"
      aria-label={label}
      style={{ top: position.top, left: position.left, width: position.width, maxHeight: position.maxHeight }}
      onKeyDown={onListKeyDown}
    ><div className="filter-select-heading">{label}</div>{options.map((option, index) => <button
      key={option.value}
      ref={element => { optionRefs.current[index] = element; }}
      type="button"
      role="option"
      tabIndex={-1}
      aria-selected={option.value === value}
      className="filter-select-option"
      onMouseEnter={() => setActiveIndex(index)}
      onClick={() => choose(index)}
    ><span>{option.label}</span>{option.value === value && <Check size={15} aria-hidden="true" />}</button>)}</div>, document.body)}
  </>;
}
