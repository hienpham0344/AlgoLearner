import { useEffect, useMemo, useRef, useState, type KeyboardEvent } from 'react';
import type { DailyPracticeActivity, PracticeStats } from '../types';

const DISPLAY_TIME_ZONE = 'Asia/Bangkok';
const BANGKOK_OFFSET_MS = 7 * 60 * 60 * 1000;
const DAY_MS = 24 * 60 * 60 * 1000;
const DAYS_PER_WEEK = 7;

const cellDateFormatter = new Intl.DateTimeFormat('en', {
  timeZone: DISPLAY_TIME_ZONE,
  weekday: 'short',
  month: 'short',
  day: 'numeric',
});

const monthShortFormatter = new Intl.DateTimeFormat('en', {
  timeZone: DISPLAY_TIME_ZONE,
  month: 'short',
});

const weekdayShortFormatter = new Intl.DateTimeFormat('en', {
  timeZone: DISPLAY_TIME_ZONE,
  weekday: 'short',
});

type DifficultyTier = 'easy' | 'medium' | 'hard';

interface ActivityCell {
  dateKey: string;
  dateLabel: string;
  monthLabel: string;
  weekdayShort: string;
  solvedCount: number;
  level: 0 | 1 | 2 | 3;
  colIndex: number;
  rowIndex: number;
  flatIndex: number;
}

const getIntensityLevel = (solvedCount: number): 0 | 1 | 2 | 3 => {
  if (solvedCount <= 0) return 0;
  if (solvedCount === 1) return 1;
  if (solvedCount === 2) return 2;
  return 3;
};

const parseBangkokDateKey = (dateKey: string): Date => {
  const [year, month, day] = dateKey.split('-').map(Number);
  return new Date(Date.UTC(year, (month || 1) - 1, day || 1, 5, 0, 0));
};

const toBangkokDateKey = (timestampMs: number) =>
  new Date(timestampMs + BANGKOK_OFFSET_MS).toISOString().slice(0, 10);

const formatStreakNumber = (streak: number | null): string => {
  if (streak === null) return '—';
  if (streak > 0 && streak < 10) return `0${streak}`;
  return String(streak);
};

const formatPercentage = (count: number, total: number): string => {
  if (total <= 0) return '0%';
  const raw = (count / total) * 100;
  return Number.isInteger(raw) ? `${raw}%` : `${raw.toFixed(1)}%`;
};

const buildChronologicalGrid = (history: DailyPracticeActivity[], weekCount: number) => {
  const totalDays = weekCount * DAYS_PER_WEEK;
  const sorted = [...history].sort((a, b) => a.date.localeCompare(b.date));
  const byDate = new Map<string, number>();

  for (const entry of sorted) {
    byDate.set(entry.date, Math.max(0, entry.solvedCount));
  }

  const latestDateKey = sorted[sorted.length - 1]?.date ?? toBangkokDateKey(Date.now());
  const latestAnchorMs = parseBangkokDateKey(latestDateKey).getTime();

  const columns: ActivityCell[][] = [];
  const flatCells: ActivityCell[] = [];

  for (let col = 0; col < weekCount; col += 1) {
    const columnCells: ActivityCell[] = [];
    for (let row = 0; row < DAYS_PER_WEEK; row += 1) {
      const flatIndex = col * DAYS_PER_WEEK + row;
      const daysBeforeLatest = totalDays - 1 - flatIndex;
      const cellTimeMs = latestAnchorMs - daysBeforeLatest * DAY_MS;
      const dateObj = new Date(cellTimeMs);
      const dateKey = toBangkokDateKey(cellTimeMs);
      const solvedCount = byDate.get(dateKey) ?? 0;

      const cell: ActivityCell = {
        dateKey,
        dateLabel: cellDateFormatter.format(dateObj),
        monthLabel: monthShortFormatter.format(dateObj).toUpperCase(),
        weekdayShort: weekdayShortFormatter.format(dateObj).slice(0, 1).toUpperCase(),
        solvedCount,
        level: getIntensityLevel(solvedCount),
        colIndex: col,
        rowIndex: row,
        flatIndex,
      };

      columnCells.push(cell);
      flatCells.push(cell);
    }
    columns.push(columnCells);
  }

  // Compute non-overlapping month headers across week columns
  const columnMonths: (string | null)[] = columns.map(() => null);
  let lastMonth = '';
  let lastPlacedCol = -3;

  for (let col = 0; col < columns.length; col += 1) {
    const firstCellMonth = columns[col][0].monthLabel;
    const lastCellMonth = columns[col][DAYS_PER_WEEK - 1].monthLabel;
    const monthToConsider = col === 0 ? firstCellMonth : lastCellMonth;

    if (monthToConsider !== lastMonth) {
      if (col - lastPlacedCol >= 2) {
        columnMonths[col] = monthToConsider;
        lastPlacedCol = col;
      }
      lastMonth = monthToConsider;
    }
  }

  const rowLabels = columns[0]?.map((cell, idx) => (idx % 2 === 0 ? cell.weekdayShort : '')) ?? [];

  return { columns, flatCells, columnMonths, rowLabels };
};

interface PracticePulseProps {
  practice: PracticeStats;
  loading: boolean;
}

export function PracticePulse({ practice, loading }: PracticePulseProps) {
  const gridContainerRef = useRef<HTMLDivElement>(null);
  const cellRefs = useRef<(HTMLButtonElement | null)[]>([]);

  const [weekCount, setWeekCount] = useState<number>(12);
  const [activeCellIndex, setActiveCellIndex] = useState<number | null>(null);
  const [activeTier, setActiveTier] = useState<DifficultyTier | null>(null);

  const hasActivityHistory = Boolean(practice.activityHistory && practice.activityHistory.length > 0);

  useEffect(() => {
    const element = gridContainerRef.current;
    if (!element) return;

    const updateWeeksFromWidth = () => {
      const width = element.getBoundingClientRect().width;
      if (width <= 0) return;
      if (width >= 295) {
        setWeekCount(12);
      } else if (width >= 245) {
        setWeekCount(10);
      } else {
        setWeekCount(8);
      }
    };

    const observer = new ResizeObserver(updateWeeksFromWidth);
    observer.observe(element);
    updateWeeksFromWidth();

    return () => observer.disconnect();
  }, [hasActivityHistory, loading]);

  const gridData = useMemo(() => {
    if (!practice.activityHistory || practice.activityHistory.length === 0) return null;
    return buildChronologicalGrid(practice.activityHistory, weekCount);
  }, [practice.activityHistory, weekCount]);

  const activeCell =
    gridData && activeCellIndex !== null ? (gridData.flatCells[activeCellIndex] ?? null) : null;

  const handleCellKeyDown = (event: KeyboardEvent<HTMLButtonElement>, flatIndex: number) => {
    if (!gridData) return;
    const total = gridData.flatCells.length;
    let nextIndex: number | null = null;

    if (event.key === 'ArrowDown') nextIndex = Math.min(total - 1, flatIndex + 1);
    else if (event.key === 'ArrowUp') nextIndex = Math.max(0, flatIndex - 1);
    else if (event.key === 'ArrowRight') nextIndex = Math.min(total - 1, flatIndex + DAYS_PER_WEEK);
    else if (event.key === 'ArrowLeft') nextIndex = Math.max(0, flatIndex - DAYS_PER_WEEK);

    if (nextIndex !== null && nextIndex !== flatIndex) {
      event.preventDefault();
      setActiveCellIndex(nextIndex);
      cellRefs.current[nextIndex]?.focus();
    }
  };

  const hasDifficultyData =
    practice.easy !== null && practice.medium !== null && practice.hard !== null;

  const difficultyTiers = useMemo(() => {
    if (practice.easy === null || practice.medium === null || practice.hard === null) {
      return null;
    }
    const easy = Math.max(0, practice.easy);
    const medium = Math.max(0, practice.medium);
    const hard = Math.max(0, practice.hard);
    const total = easy + medium + hard;

    return {
      total,
      items: [
        {
          id: 'easy' as const,
          label: 'Easy',
          count: easy,
          percentLabel: formatPercentage(easy, total),
          share: total > 0 ? (easy / total) * 100 : 33.33,
        },
        {
          id: 'medium' as const,
          label: 'Medium',
          count: medium,
          percentLabel: formatPercentage(medium, total),
          share: total > 0 ? (medium / total) * 100 : 33.33,
        },
        {
          id: 'hard' as const,
          label: 'Hard',
          count: hard,
          percentLabel: formatPercentage(hard, total),
          share: total > 0 ? (hard / total) * 100 : 33.34,
        },
      ],
    };
  }, [practice.easy, practice.medium, practice.hard]);

  const activeDifficultyItem = difficultyTiers?.items.find(item => item.id === activeTier) ?? null;

  if (loading) {
    return (
      <div className="dashboard-practice-loading" aria-label="Loading practice statistics">
        <span />
        <span />
        <span />
      </div>
    );
  }

  const streakLabel =
    practice.currentStreak === null
      ? 'streak unavailable'
      : practice.currentStreak === 1
        ? 'day streak'
        : 'day streak';

  return (
    <div className="dashboard-practice-studio">
      {/* 1. Current Streak */}
      <div className="dashboard-streak-block">
        <strong className="dashboard-streak-number">
          {formatStreakNumber(practice.currentStreak)}
        </strong>
        <span className="dashboard-streak-label">{streakLabel}</span>
      </div>

      {/* 2. Coding Activity History */}
      {gridData && (
        <div
          className="dashboard-activity-block"
          ref={gridContainerRef}
          onMouseLeave={() => setActiveCellIndex(null)}
        >
          <div className="dashboard-activity-header">
            <span className="dashboard-activity-title">CODING ACTIVITY</span>
            <span className="dashboard-activity-window" aria-live="polite">
              {activeCell
                ? `${activeCell.dateLabel} · ${activeCell.solvedCount} ${activeCell.solvedCount === 1 ? 'problem' : 'problems'}`
                : `Last ${weekCount} weeks`}
            </span>
          </div>

          <div className="dashboard-activity-frame">
            <div className="dashboard-activity-months" aria-hidden="true">
              <span className="dashboard-activity-axis-spacer" />
              <div
                className="dashboard-activity-months-track"
                style={{ gridTemplateColumns: `repeat(${weekCount}, minmax(0, 1fr))` }}
              >
                {gridData.columnMonths.map((month, colIdx) => (
                  <span key={colIdx}>{month ?? ''}</span>
                ))}
              </div>
            </div>

            <div className="dashboard-activity-matrix-wrap">
              <div className="dashboard-activity-weekdays" aria-hidden="true">
                {gridData.rowLabels.map((label, rowIdx) => (
                  <span key={rowIdx}>{label}</span>
                ))}
              </div>

              <div
                className="dashboard-activity-matrix"
                role="grid"
                aria-label={`Daily coding activity over the last ${weekCount} weeks`}
                style={{ gridTemplateColumns: `repeat(${weekCount}, minmax(0, 1fr))` }}
              >
                {gridData.columns.map((column, colIdx) => (
                  <div className="dashboard-activity-week" role="row" key={colIdx}>
                    {column.map(cell => {
                      const isSelected = activeCellIndex === cell.flatIndex;
                      const solvedText =
                        cell.solvedCount === 0
                          ? 'No problems solved'
                          : `${cell.solvedCount} ${cell.solvedCount === 1 ? 'problem' : 'problems'} solved`;

                      return (
                        <button
                          key={cell.dateKey}
                          ref={el => {
                            cellRefs.current[cell.flatIndex] = el;
                          }}
                          type="button"
                          role="gridcell"
                          className="dashboard-activity-cell"
                          data-level={cell.level}
                          data-active={isSelected}
                          aria-label={`${cell.dateLabel}: ${solvedText}`}
                          onMouseEnter={() => setActiveCellIndex(cell.flatIndex)}
                          onFocus={() => setActiveCellIndex(cell.flatIndex)}
                          onBlur={() => setActiveCellIndex(current => (current === cell.flatIndex ? null : current))}
                          onClick={() =>
                            setActiveCellIndex(current => (current === cell.flatIndex ? null : cell.flatIndex))
                          }
                          onKeyDown={event => handleCellKeyDown(event, cell.flatIndex)}
                        />
                      );
                    })}
                  </div>
                ))}

                {activeCell && (
                  <div
                    className="dashboard-activity-tooltip"
                    role="tooltip"
                    style={{
                      left: `${((activeCell.colIndex + 0.5) / weekCount) * 100}%`,
                    }}
                    data-align={
                      activeCell.colIndex <= 1
                        ? 'start'
                        : activeCell.colIndex >= weekCount - 2
                          ? 'end'
                          : 'center'
                    }
                  >
                    <strong>
                      {activeCell.solvedCount === 0
                        ? '0 solved'
                        : `${activeCell.solvedCount} solved`}
                    </strong>
                    <span>{activeCell.dateLabel}</span>
                  </div>
                )}
              </div>
            </div>

            <div className="dashboard-activity-footer" aria-hidden="true">
              <span>Daily log · UTC+7</span>
              <div className="dashboard-activity-scale">
                <span>Less</span>
                <i data-level="0" />
                <i data-level="1" />
                <i data-level="2" />
                <i data-level="3" />
                <span>More</span>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* 3. Total Problems Solved */}
      <div className="dashboard-solved-row">
        <span className="dashboard-solved-label">Total solved</span>
        <strong className="dashboard-solved-value">
          {practice.totalSolved !== null ? practice.totalSolved : '—'}
        </strong>
      </div>

      {/* 4. Difficulty Distribution */}
      <div
        className="dashboard-difficulty-block"
        onMouseLeave={() => setActiveTier(null)}
      >
        <div className="dashboard-difficulty-header">
          <span className="dashboard-difficulty-title">DIFFICULTY</span>
          <span className="dashboard-difficulty-readout" aria-live="polite">
            {activeDifficultyItem
              ? `${activeDifficultyItem.label} · ${activeDifficultyItem.count} (${activeDifficultyItem.percentLabel})`
              : hasDifficultyData
                ? 'By tier'
                : 'Unavailable'}
          </span>
        </div>

        {difficultyTiers ? (
          <>
            <div
              className="dashboard-difficulty-bar"
              role="group"
              aria-label="Solved problem distribution by difficulty"
              data-has-active={activeTier !== null}
            >
              {difficultyTiers.items.map(tier => {
                if (difficultyTiers.total > 0 && tier.count === 0) return null;
                const isActive = activeTier === tier.id;

                return (
                  <button
                    key={tier.id}
                    type="button"
                    className="dashboard-difficulty-segment"
                    data-tier={tier.id}
                    data-active={isActive}
                    style={{ width: `${tier.share}%` }}
                    aria-label={`${tier.label}: ${tier.count} solved (${tier.percentLabel})`}
                    onMouseEnter={() => setActiveTier(tier.id)}
                    onFocus={() => setActiveTier(tier.id)}
                    onBlur={() => setActiveTier(current => (current === tier.id ? null : current))}
                    onClick={() => setActiveTier(current => (current === tier.id ? null : tier.id))}
                  />
                );
              })}
            </div>

            <div className="dashboard-difficulty-legend" data-has-active={activeTier !== null}>
              {difficultyTiers.items.map(tier => {
                const isActive = activeTier === tier.id;
                return (
                  <button
                    key={tier.id}
                    type="button"
                    className="dashboard-difficulty-tier"
                    data-tier={tier.id}
                    data-active={isActive}
                    onMouseEnter={() => setActiveTier(tier.id)}
                    onFocus={() => setActiveTier(tier.id)}
                    onBlur={() => setActiveTier(current => (current === tier.id ? null : current))}
                    onClick={() => setActiveTier(current => (current === tier.id ? null : tier.id))}
                  >
                    <span className="dashboard-difficulty-swatch" data-tier={tier.id} aria-hidden="true" />
                    <span className="dashboard-difficulty-name">{tier.label}</span>
                    <strong className="dashboard-difficulty-count">{tier.count}</strong>
                    <span className="dashboard-difficulty-pct">{tier.percentLabel}</span>
                  </button>
                );
              })}
            </div>
          </>
        ) : (
          <p className="dashboard-practice-note">
            Difficulty distribution will appear when category counts are available.
          </p>
        )}
      </div>
    </div>
  );
}

