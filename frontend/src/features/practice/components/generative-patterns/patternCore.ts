export type PatternFamily =
  | 'tree' | 'graph' | 'architecture' | 'relational-matrix' | 'neural-network'
  | 'decision-boundary' | 'topology' | 'encrypted-mesh' | 'process-timeline'
  | 'coordinate-geometry' | 'modular-composition' | 'viewport'
  | 'distributed-system' | 'wireframe' | 'spatial-grid' | 'sequence' | 'universal';

export type PatternPrimitive = 'tree' | 'graph' | 'architecture' | 'matrix' | 'network' | 'timeline' | 'geometry' | 'modules';
export type PatternSurface = 'dark' | 'light';

export interface PatternConfig {
  seed: number;
  family: PatternFamily;
  variant: number;
  surface: PatternSurface;
  density: number;
  complexity: number;
  motionEnabled: boolean;
}

export interface PatternSource {
  id: string;
  name: string;
  description?: string | null;
  subject?: string | null;
  category?: string | null;
  visualFamily?: PatternFamily | null;
}

export const patternRegistry: Record<PatternFamily, { label: string; primitive: PatternPrimitive }> = {
  tree: { label: 'Tree', primitive: 'tree' },
  graph: { label: 'Graph', primitive: 'graph' },
  architecture: { label: 'Architecture', primitive: 'architecture' },
  'relational-matrix': { label: 'Relational Matrix', primitive: 'matrix' },
  'neural-network': { label: 'Neural Network', primitive: 'network' },
  'decision-boundary': { label: 'Decision Boundary', primitive: 'geometry' },
  topology: { label: 'Topology', primitive: 'network' },
  'encrypted-mesh': { label: 'Encrypted Mesh', primitive: 'matrix' },
  'process-timeline': { label: 'Process Timeline', primitive: 'timeline' },
  'coordinate-geometry': { label: 'Coordinate Geometry', primitive: 'geometry' },
  'modular-composition': { label: 'Modular Composition', primitive: 'modules' },
  viewport: { label: 'Viewport System', primitive: 'modules' },
  'distributed-system': { label: 'Distributed Systems', primitive: 'network' },
  wireframe: { label: 'Wireframe', primitive: 'geometry' },
  'spatial-grid': { label: 'Spatial Grid', primitive: 'matrix' },
  sequence: { label: 'Sequence', primitive: 'timeline' },
  universal: { label: 'Universal Geometry', primitive: 'geometry' },
};

/** FNV-1a over UTF-16 code units; stable across browser sessions and devices. */
export function hashClassroomId(id: string): number {
  let hash = 0x811c9dc5;
  for (let index = 0; index < id.length; index++) {
    hash ^= id.charCodeAt(index);
    hash = Math.imul(hash, 0x01000193);
  }
  return hash >>> 0;
}

/** Mulberry32. The returned sequence depends only on the supplied seed. */
export function seededRandom(seed: number): () => number {
  let state = seed >>> 0;
  return () => {
    state = (state + 0x6d2b79f5) >>> 0;
    let value = state;
    value = Math.imul(value ^ (value >>> 15), value | 1);
    value ^= value + Math.imul(value ^ (value >>> 7), value | 61);
    return ((value ^ (value >>> 14)) >>> 0) / 4294967296;
  };
}

const normalize = (text: string) => text.normalize('NFD').toLowerCase().replace(/[\u0300-\u036f]/g, '').replace(/đ/g, 'd');
const has = (text: string, words: string) => new RegExp(`(?:^|[^a-z])(?:${words})(?=$|[^a-z])`, 'i').test(text);

/** Conservative title/subject matching. Ambiguous titles use universal geometry. */
export function resolvePatternFamily(source: PatternSource): PatternFamily {
  if (source.visualFamily && source.visualFamily in patternRegistry) return source.visualFamily;
  const structured = normalize(`${source.subject ?? ''} ${source.category ?? ''}`);
  const title = normalize(`${source.name} ${source.description ?? ''}`);
  const classify = (text: string): PatternFamily | null => {
    if (has(text, 'data structures?|cau truc du lieu|binary tree|cay nhi phan')) return 'tree';
    if (has(text, 'machine learning|hoc may')) return 'decision-boundary';
    if (has(text, 'artificial intelligence|tri tue nhan tao|neural networks?')) return 'neural-network';
    if (has(text, 'database systems?|databases?|co so du lieu|sql')) return 'relational-matrix';
    if (has(text, 'cybersecurity|an toan thong tin|bao mat')) return 'encrypted-mesh';
    if (has(text, 'operating systems?|he dieu hanh')) return 'process-timeline';
    if (has(text, 'computer networks?|mang may tinh')) return 'topology';
    if (has(text, 'cloud computing|dien toan dam may')) return 'distributed-system';
    if (has(text, 'computer graphics|do hoa may tinh')) return 'wireframe';
    if (has(text, 'game development|phat trien game')) return 'spatial-grid';
    if (has(text, 'mobile development|lap trinh di dong|android|ios')) return 'viewport';
    if (has(text, 'software engineering|ky thuat phan mem')) return 'architecture';
    if (has(text, 'web development|lap trinh web|full.stack')) return 'modular-composition';
    if (has(text, 'mathematics|toan hoc|hinh hoc')) return 'coordinate-geometry';
    if (has(text, 'algorithms?|thuat toan|graph algorithms?')) return 'graph';
    if (has(text, 'programming|lap trinh')) return 'sequence';
    return null;
  };
  return classify(structured) ?? classify(title) ?? 'universal';
}

export function resolvePattern(source: PatternSource): PatternConfig {
  const seed = hashClassroomId(source.id);
  const random = seededRandom(seed);
  return {
    seed,
    family: resolvePatternFamily(source),
    variant: Math.floor(random() * 5),
    surface: seed % 2 === 0 ? 'dark' : 'light',
    density: .38 + random() * .32,
    complexity: .38 + random() * .32,
    motionEnabled: true,
  };
}

export function clampPatternConfig(config: PatternConfig): PatternConfig {
  return {
    ...config,
    variant: Math.max(0, Math.min(4, Math.floor(config.variant))),
    density: Math.max(.2, Math.min(.85, config.density)),
    complexity: Math.max(.2, Math.min(.85, config.complexity)),
  };
}
