import { clampPatternConfig, patternRegistry, seededRandom, type PatternConfig } from './patternCore';

export interface PatternLine { x1: number; y1: number; x2: number; y2: number; emphasis?: boolean }
export interface PatternCircle { cx: number; cy: number; r: number; emphasis?: boolean }
export interface PatternRect { x: number; y: number; width: number; height: number; emphasis?: boolean }
export interface PatternPath { d: string; emphasis?: boolean }
export interface PatternGeometry { lines: PatternLine[]; circles: PatternCircle[]; rects: PatternRect[]; paths: PatternPath[] }

const round = (number: number) => Math.round(number * 10) / 10;

/** Bounded geometry in a 520 × 170 viewBox. No DOM, time, or uncontrolled randomness. */
export function generatePatternGeometry(rawConfig: PatternConfig): PatternGeometry {
  const config = clampPatternConfig(rawConfig);
  const random = seededRandom(config.seed ^ Math.imul(config.variant + 1, 0x9e3779b9));
  const lines: PatternLine[] = [];
  const circles: PatternCircle[] = [];
  const rects: PatternRect[] = [];
  const paths: PatternPath[] = [];
  const primitive = patternRegistry[config.family].primitive;
  const jitter = (range: number) => round((random() - .5) * range);

  if (primitive === 'tree') {
    const levels = config.complexity > .56 ? 4 : 3;
    const count = (1 << levels) - 1;
    const points: Array<[number, number]> = [];
    for (let index = 0; index < count; index++) {
      const level = Math.floor(Math.log2(index + 1));
      const within = index - ((1 << level) - 1);
      const slots = 1 << level;
      const x = round(260 + (within - (slots - 1) / 2) * (levels === 4 ? 430 / slots : 330 / slots) + (level ? jitter(12 + config.variant * 2) : 0));
      const y = round(24 + level * (levels === 4 ? 40 : 54) + jitter(level ? 4 : 0));
      points.push([x, y]);
      if (index) lines.push({ x1: points[Math.floor((index - 1) / 2)][0], y1: points[Math.floor((index - 1) / 2)][1], x2: x, y2: y, emphasis: index % 3 === config.variant % 3 });
      circles.push({ cx: x, cy: y, r: index ? 4.5 + (config.variant % 2) : 6.5, emphasis: index === 0 || index % 4 === config.variant % 4 });
    }
    for (let level = 0; level < levels; level++) lines.push({ x1: 52, y1: 24 + level * (levels === 4 ? 40 : 54), x2: 468, y2: 24 + level * (levels === 4 ? 40 : 54) });
  } else if (primitive === 'architecture') {
    const layers = 3 + (config.complexity > .58 ? 1 : 0);
    for (let index = 0; index < layers; index++) {
      const y = 22 + index * (layers === 4 ? 30 : 45);
      const halfWidth = 105 + config.variant * 6 + jitter(9);
      paths.push({ d: `M ${round(260 - halfWidth)} ${y + 20} L 260 ${y - 4} L ${round(260 + halfWidth)} ${y + 20} L 260 ${y + 44} Z`, emphasis: index === config.variant % layers });
      paths.push({ d: `M ${round(260 - halfWidth)} ${y + 20} v 6 L 260 ${y + 50} L ${round(260 + halfWidth)} ${y + 26} v -6` });
    }
    [140, 260, 380].forEach(x => lines.push({ x1: x, y1: 15, x2: x, y2: 155 }));
  } else if (primitive === 'graph') {
    const count = 8 + Math.floor(config.density * 8);
    const points = Array.from({ length: count }, (_, index) => {
      const angle = 2 * Math.PI * index / count + config.variant * .12;
      return [round(260 + Math.cos(angle) * (165 + jitter(24))), round(84 + Math.sin(angle) * (58 + jitter(10)))] as const;
    });
    points.forEach(([x, y], index) => {
      const next = points[(index + 1) % count];
      lines.push({ x1: x, y1: y, x2: next[0], y2: next[1], emphasis: index % 4 === config.variant % 4 });
      if (index % 2 === 0) { const across = points[(index + 3 + config.variant) % count]; lines.push({ x1: x, y1: y, x2: across[0], y2: across[1] }); }
      circles.push({ cx: x, cy: y, r: 4 + random() * 2, emphasis: index % 3 === config.variant % 3 });
    });
  } else if (primitive === 'matrix') {
    const columns = 9 + Math.floor(config.density * 4);
    const rows = 4 + Math.floor(config.complexity * 2);
    const cellWidth = 360 / columns;
    const cellHeight = 123 / rows;
    for (let row = 0; row < rows; row++) for (let col = 0; col < columns; col++) {
      rects.push({ x: round(80 + col * cellWidth), y: round(24 + row * cellHeight), width: round(cellWidth - 6), height: round(cellHeight - 5), emphasis: random() < config.density * .48 });
    }
    if (config.family === 'relational-matrix') for (let row = 0; row < rows - 1; row++) lines.push({ x1: 450, y1: round(36 + row * cellHeight), x2: 465, y2: round(36 + (row + 1) * cellHeight) });
  } else if (primitive === 'network') {
    const layers = config.family === 'neural-network' ? 4 : 3;
    const count = config.complexity > .56 ? 4 : 3;
    const nodes: Array<Array<[number, number]>> = Array.from({ length: layers }, (_, layer) => Array.from({ length: count }, (_, row) => [round(90 + layer * (340 / (layers - 1)) + jitter(7)), round(28 + row * (110 / (count - 1)) + jitter(6))]));
    nodes.forEach((layer, layerIndex) => layer.forEach(([x, y], rowIndex) => {
      circles.push({ cx: x, cy: y, r: 5, emphasis: (layerIndex + rowIndex) % 4 === config.variant % 4 });
      if (layerIndex < layers - 1) nodes[layerIndex + 1].forEach(([nextX, nextY], nextIndex) => {
        if (config.family === 'neural-network' || nextIndex === rowIndex || random() < config.density * .42) lines.push({ x1: x, y1: y, x2: nextX, y2: nextY, emphasis: rowIndex === config.variant % count && nextIndex === rowIndex });
      });
    }));
  } else if (primitive === 'timeline') {
    const tracks = 4 + Math.floor(config.complexity * 3);
    for (let row = 0; row < tracks; row++) {
      const y = 30 + row * (115 / (tracks - 1));
      lines.push({ x1: 65, y1: round(y + 8), x2: 465, y2: round(y + 8) });
      let x = 78 + random() * 28;
      while (x < 430) {
        const width = Math.min(40 + random() * 58, 445 - x);
        rects.push({ x: round(x), y: round(y), width: round(width), height: 15, emphasis: random() < .34 });
        x += width + 13 + random() * 28;
      }
    }
  } else if (primitive === 'modules') {
    const columns = config.family === 'viewport' ? 4 : 6;
    const rows = config.family === 'viewport' ? 2 : 3;
    const width = columns === 4 ? 79 : 54;
    const height = rows === 2 ? 54 : 33;
    for (let row = 0; row < rows; row++) for (let col = 0; col < columns; col++) {
      const x = 82 + col * (width + 12);
      const y = 21 + row * (height + 13);
      rects.push({ x, y, width, height, emphasis: (row + col + config.variant) % 4 === 0 });
      if (config.family === 'viewport') rects.push({ x: x + 8, y: y + 8, width: width - 16, height: height - 16 });
    }
  } else {
    const count = 3 + Math.floor(config.density * 5);
    lines.push({ x1: 55, y1: 86, x2: 465, y2: 86 }, { x1: 260, y1: 18, x2: 260, y2: 152 });
    for (let index = 0; index < count; index++) {
      const radiusX = 45 + index * (25 + config.variant * 2);
      const radiusY = 14 + index * 9;
      paths.push({ d: `M ${260 - radiusX} 86 A ${radiusX} ${radiusY} 0 1 0 ${260 + radiusX} 86 A ${radiusX} ${radiusY} 0 1 0 ${260 - radiusX} 86`, emphasis: index === config.variant % count });
    }
    if (config.family === 'decision-boundary') for (let index = 0; index < 17; index++) circles.push({ cx: round(100 + random() * 320), cy: round(25 + random() * 120), r: 2.5, emphasis: index % 3 === 0 });
  }

  return { lines: lines.slice(0, 100), circles: circles.slice(0, 50), rects: rects.slice(0, 90), paths: paths.slice(0, 35) };
}
