import { useState } from 'react';
import { PatternRenderer } from './PatternRenderer';
import { patternRegistry, resolvePattern, type PatternConfig, type PatternFamily, type PatternSurface } from './patternCore';
import '../../styles/practice.css';
import './patternLab.css';

const families = Object.keys(patternRegistry) as PatternFamily[];

export function PatternLab() {
  const [family, setFamily] = useState<PatternFamily>('tree');
  const [seed, setSeed] = useState(2026);
  const [surface, setSurface] = useState<PatternSurface>('dark');
  const [density, setDensity] = useState(.55);
  const [complexity, setComplexity] = useState(.55);
  const [variant, setVariant] = useState(0);
  const [width, setWidth] = useState(640);
  const [count, setCount] = useState(2);
  const [motionEnabled, setMotionEnabled] = useState(true);
  const config: PatternConfig = { family, seed, surface, density, complexity, variant, motionEnabled };

  return <main className="pattern-lab">
    <header><span>ALGOLEARN / INTERNAL DESIGN TOOL</span><h1>Pattern Lab</h1><p>Inspect deterministic classroom covers. Hover or focus a preview to see its interaction.</p></header>
    <div className="pattern-lab-controls">
      <label>Family<select value={family} onChange={event => setFamily(event.target.value as PatternFamily)}>{families.map(value => <option value={value} key={value}>{patternRegistry[value].label}</option>)}</select></label>
      <label>Seed<input type="number" value={seed} onChange={event => setSeed(Number(event.target.value) || 0)} /></label>
      <label>Surface<select value={surface} onChange={event => setSurface(event.target.value as PatternSurface)}><option value="dark">Dark</option><option value="light">Light</option></select></label>
      <label>Width<select value={width} onChange={event => setWidth(Number(event.target.value))}><option value={320}>320 px</option><option value={480}>480 px</option><option value={640}>640 px</option><option value={960}>960 px</option></select></label>
      <label>Dataset size<select value={count} onChange={event => setCount(Number(event.target.value))}>{[2, 10, 30, 100].map(value => <option key={value} value={value}>{value} classrooms</option>)}</select></label>
      <label>Density <output>{density.toFixed(2)}</output><input type="range" min="0.2" max="0.85" step="0.05" value={density} onChange={event => setDensity(Number(event.target.value))} /></label>
      <label>Complexity <output>{complexity.toFixed(2)}</output><input type="range" min="0.2" max="0.85" step="0.05" value={complexity} onChange={event => setComplexity(Number(event.target.value))} /></label>
      <label className="pattern-lab-check"><input type="checkbox" checked={motionEnabled} onChange={event => setMotionEnabled(event.target.checked)} /> Motion on interaction</label>
    </div>
    <section aria-label="Selected pattern" className="pattern-lab-stage"><div className="pattern-lab-cover atelier-cover" data-tone={surface} style={{ maxWidth: width }} tabIndex={0}><PatternRenderer config={config} /><div><span>{patternRegistry[family].label.toUpperCase()} / {String(variant + 1).padStart(2, '0')}</span><strong>Classroom visual identity</strong></div></div></section>
    <section aria-label="Pattern variants"><h2>Variants</h2><div className="pattern-lab-variants">{[0, 1, 2, 3, 4].map(value => <button key={value} type="button" className="pattern-lab-variant atelier-cover" data-tone={surface} aria-pressed={variant === value} onClick={() => setVariant(value)}><PatternRenderer config={{ ...config, variant: value }} /><span>Variant {value + 1}</span></button>)}</div></section>
    <section aria-label="Dataset preview"><h2>Dataset preview · {count} classrooms</h2><div className="pattern-lab-dataset">{Array.from({ length: count }, (_, index) => { const item = resolvePattern({ id: `pattern-lab-class-${index}`, name: patternRegistry[family].label, visualFamily: family }); return <div className="pattern-lab-swatch atelier-cover" data-tone={surface} key={index}><PatternRenderer config={{ ...item, surface, motionEnabled }} /><span>Classroom {index + 1}</span></div>; })}</div></section>
  </main>;
}
