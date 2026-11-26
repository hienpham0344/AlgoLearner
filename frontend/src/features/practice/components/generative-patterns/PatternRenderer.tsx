import { memo, useId, useMemo } from 'react';
import { generatePatternGeometry } from './patternGeometry';
import { clampPatternConfig, patternRegistry, type PatternConfig } from './patternCore';

interface Props { config: PatternConfig; className?: string }

export const PatternRenderer = memo(function PatternRenderer({ config, className = '' }: Props) {
  const safe = clampPatternConfig(config);
  const primitive = patternRegistry[safe.family].primitive;
  const geometry = useMemo(() => generatePatternGeometry(safe), [safe.seed, safe.family, safe.variant, safe.density, safe.complexity]);
  const gridId = `agps-grid-${useId().replace(/:/g, '')}`;

  return <svg
    className={`agps-pattern agps-${primitive} ${className}`.trim()}
    data-family={safe.family}
    data-surface={safe.surface}
    data-variant={safe.variant}
    data-motion={safe.motionEnabled}
    viewBox="0 0 520 170"
    preserveAspectRatio="xMidYMid meet"
    fill="none"
    aria-hidden="true"
    focusable="false"
  >
    <defs><pattern id={gridId} width="22" height="22" patternUnits="userSpaceOnUse"><path d="M22 0H0V22" stroke="currentColor" strokeWidth=".6" /></pattern></defs>
    <rect className="agps-grid" width="520" height="170" fill={`url(#${gridId})`} />
    <g className="agps-construction" stroke="currentColor" strokeWidth=".7" strokeDasharray="3 6" opacity=".32"><path d="M40 85H480M260 12V158" /></g>
    <g className="agps-geometry">
      {geometry.lines.map((line, index) => <line key={`l-${index}`} className={line.emphasis ? 'agps-emphasis' : 'agps-secondary'} x1={line.x1} y1={line.y1} x2={line.x2} y2={line.y2} stroke="currentColor" strokeWidth={line.emphasis ? 1.35 : .95} />)}
      {geometry.paths.map((path, index) => <path key={`p-${index}`} className={path.emphasis ? 'agps-emphasis' : 'agps-secondary'} d={path.d} stroke="currentColor" strokeWidth={path.emphasis ? 1.35 : 1} />)}
      {geometry.rects.map((rect, index) => <rect key={`r-${index}`} className={rect.emphasis ? 'agps-emphasis' : 'agps-secondary'} x={rect.x} y={rect.y} width={rect.width} height={rect.height} stroke="currentColor" strokeWidth={rect.emphasis ? 1.2 : .9} />)}
      {geometry.circles.map((circle, index) => <circle key={`c-${index}`} className={circle.emphasis ? 'agps-emphasis' : 'agps-secondary'} cx={circle.cx} cy={circle.cy} r={circle.r} stroke="currentColor" strokeWidth={circle.emphasis ? 1.3 : 1} fill="var(--pattern-background, var(--atelier-bg, #1b1c1e))" />)}
    </g>
  </svg>;
});
