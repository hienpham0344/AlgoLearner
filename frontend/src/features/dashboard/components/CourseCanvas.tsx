import { useEffect, useRef } from 'react';

type Particle = {
  anchorX: number;
  anchorY: number;
  x: number;
  y: number;
  vx: number;
  vy: number;
  u: number;
  v: number;
  z: number;
  baseRadius: number;
  rightWeight: number;
  phase: number;
  highlight: number;
  isHub: boolean;
  neighbors: number[];
};

const DESKTOP_COLUMNS = 9;
const DESKTOP_ROWS = 7;
const COMPACT_COLUMNS = 6;
const COMPACT_ROWS = 5;

const clamp = (value: number, min: number, max: number) => Math.min(max, Math.max(min, value));
const smoothstep = (edge0: number, edge1: number, x: number) => {
  const t = clamp((x - edge0) / (edge1 - edge0), 0, 1);
  return t * t * (3 - 2 * t);
};

export function CourseCanvas() {
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    const card = canvas?.parentElement;
    const context = canvas?.getContext('2d', { alpha: true });
    if (!canvas || !card || !context) return;

    const motionPreference = window.matchMedia('(prefers-reduced-motion: reduce)');
    if (!motionPreference.matches && !card.dataset.entered) {
      card.dataset.entered = 'false';
    } else {
      card.dataset.entered = 'true';
    }

    const particles: Particle[] = [];
    const pointer = {
      x: 0,
      y: 0,
      targetX: 0,
      targetY: 0,
      strength: 0,
      targetStrength: 0,
      parallaxX: 0,
      parallaxY: 0,
      active: false,
    };

    let width = 0;
    let height = 0;
    let pixelRatio = 0;
    let activeColumns = DESKTOP_COLUMNS;
    let activeRows = DESKTOP_ROWS;
    let maxEdgeDistance = 120;
    let visible = false;
    let frame = 0;
    let previousTime = 0;
    let elapsed = 0;

    const draw = () => {
      context.clearRect(0, 0, width, height);
      if (width === 0 || height === 0) return;

      // 1. Render delicate topological connecting edges
      for (let i = 0; i < particles.length; i += 1) {
        const origin = particles[i];
        if (origin.rightWeight <= 0.01) continue;

        for (const neighborIndex of origin.neighbors) {
          const neighbor = particles[neighborIndex];
          const distance = Math.hypot(origin.x - neighbor.x, origin.y - neighbor.y);
          if (distance >= maxEdgeDistance) continue;

          const distFactor = 1 - Math.pow(distance / maxEdgeDistance, 1.75);
          const rightEnv = Math.min(origin.rightWeight, neighbor.rightWeight) * 0.6
            + ((origin.rightWeight + neighbor.rightWeight) * 0.5) * 0.4;
          const depthAvg = (origin.z + neighbor.z) * 0.5;
          const flowWave = 0.86 + 0.14 * Math.sin(
            elapsed * 1.05 - (origin.u + neighbor.u) * 2.4 + (origin.v + neighbor.v) * 1.2,
          );
          const cursorBoost = (origin.highlight + neighbor.highlight) * 0.5;
          const opacity = rightEnv * distFactor * (0.048 + depthAvg * 0.115) * flowWave
            + rightEnv * distFactor * cursorBoost * 0.16;

          if (opacity <= 0.004) continue;

          context.lineWidth = 0.65 + cursorBoost * 0.3;
          context.strokeStyle = `rgba(224, 228, 226, ${opacity.toFixed(3)})`;
          context.beginPath();
          context.moveTo(origin.x, origin.y);
          context.lineTo(neighbor.x, neighbor.y);
          context.stroke();
        }
      }

      // 2. Render particles across depth layers (ambient -> structural -> hub nodes)
      for (let i = 0; i < particles.length; i += 1) {
        const particle = particles[i];
        if (particle.rightWeight <= 0.01) continue;

        const pulse = 0.9 + 0.1 * Math.sin(elapsed * 0.9 + particle.phase);
        const alpha = clamp(
          particle.rightWeight * (0.13 + particle.z * 0.37) * pulse
            + particle.rightWeight * particle.highlight * 0.36,
          0,
          0.88,
        );
        const radius = particle.baseRadius * (1 + particle.highlight * 0.3);

        if (particle.isHub) {
          const ringAlpha = particle.rightWeight * (0.068 + particle.highlight * 0.17);
          const ringRadius = 3.9 + particle.highlight * 1.5;
          context.lineWidth = 0.65;
          context.strokeStyle = `rgba(232, 236, 234, ${ringAlpha.toFixed(3)})`;
          context.beginPath();
          context.arc(particle.x, particle.y, ringRadius, 0, Math.PI * 2);
          context.stroke();
        }

        context.fillStyle = `rgba(238, 241, 239, ${alpha.toFixed(3)})`;
        context.beginPath();
        context.arc(particle.x, particle.y, radius, 0, Math.PI * 2);
        context.fill();
      }
    };

    const buildGraph = () => {
      const previous = [...particles];
      const previousWidth = width;
      const previousHeight = height;
      const previousColumns = activeColumns;
      const previousRows = activeRows;

      const compact = width < 560;
      activeColumns = compact ? COMPACT_COLUMNS : DESKTOP_COLUMNS;
      activeRows = compact ? COMPACT_ROWS : DESKTOP_ROWS;

      const startX = compact ? 0.52 : 0.39;
      const endX = 0.955;
      const fadeStart = compact ? 0.50 : 0.37;
      const fadeFull = compact ? 0.85 : 0.72;

      const cellW = (width * (endX - startX)) / Math.max(1, activeColumns - 1);
      const cellH = (height * 0.78) / Math.max(1, activeRows - 1);
      maxEdgeDistance = Math.hypot(cellW, cellH) * 1.48;

      particles.length = 0;

      for (let row = 0; row < activeRows; row += 1) {
        for (let column = 0; column < activeColumns; column += 1) {
          const u = column / (activeColumns - 1);
          const v = row / (activeRows - 1);
          const seed = row * 7.13 + column * 11.47;
          const phase = row * 2.17 + column * 1.63;

          // Concentrate nodes toward the right visual stage with harmonic stream curvature
          const xNorm = startX + Math.pow(u, 0.86) * (endX - startX);
          const envelope = 0.74 + Math.sin(u * Math.PI * 1.1 - 0.25) * 0.12;
          const streamShift = Math.sin(u * Math.PI * 1.65 + (v - 0.5) * 1.35) * 0.036;
          const yNorm = 0.5 + (v - 0.5) * envelope + streamShift;

          const jitterX = Math.sin(seed * 1.93) * (width * 0.012);
          const jitterY = Math.cos(seed * 2.41) * (height * 0.024);

          const anchorX = clamp(width * xNorm + jitterX, width * 0.34, width - 10);
          const anchorY = clamp(height * yNorm + jitterY, 14, height - 14);

          // Stratified depth layers: hub (1.0), structural (0.72), ambient (0.42)
          const isHub = u > 0.32 && (row * 3 + column * 2) % 6 === 0 && row > 0 && row < activeRows - 1;
          const isAmbient = !isHub && (row + column * 2) % 3 === 0;
          const z = isHub ? 1 : isAmbient ? 0.42 : 0.72;
          const baseRadius = isHub ? 1.45 : isAmbient ? 0.8 : 1.08;

          const horizontalFade = smoothstep(fadeStart, fadeFull, anchorX / width);
          const verticalVignette = smoothstep(0.03, 0.14, anchorY / height)
            * smoothstep(0.03, 0.14, 1 - anchorY / height);
          const rightWeight = horizontalFade * verticalVignette * (compact ? 0.78 : 1);

          // Directed acyclic graph / lattice neighbors without crossing diagonals
          const neighbors: number[] = [];
          if (column < activeColumns - 1) {
            neighbors.push(row * activeColumns + (column + 1));
          }
          if (row < activeRows - 1) {
            neighbors.push((row + 1) * activeColumns + column);
          }
          if (row < activeRows - 1 && column < activeColumns - 1 && (row + column) % 2 === 0) {
            neighbors.push((row + 1) * activeColumns + (column + 1));
          } else if (row > 0 && column < activeColumns - 1 && (row + column) % 3 === 1) {
            neighbors.push((row - 1) * activeColumns + (column + 1));
          }

          const sameGrid = previousColumns === activeColumns && previousRows === activeRows;
          const old = sameGrid ? previous[row * activeColumns + column] : undefined;

          particles.push({
            anchorX,
            anchorY,
            x: old && previousWidth ? anchorX + ((old.x - old.anchorX) * width) / previousWidth : anchorX,
            y: old && previousHeight ? anchorY + ((old.y - old.anchorY) * height) / previousHeight : anchorY,
            vx: old?.vx ?? 0,
            vy: old?.vy ?? 0,
            u,
            v,
            z,
            baseRadius,
            rightWeight,
            phase,
            highlight: old?.highlight ?? 0,
            isHub,
            neighbors,
          });
        }
      }
    };

    const resize = () => {
      const bounds = card.getBoundingClientRect();
      const ratio = Math.min(window.devicePixelRatio || 1, 2);
      const nextWidth = Math.round(bounds.width);
      const nextHeight = Math.round(bounds.height);
      if (nextWidth === width && nextHeight === height && ratio === pixelRatio) return;

      width = nextWidth;
      height = nextHeight;
      pixelRatio = ratio;
      canvas.width = Math.round(width * ratio);
      canvas.height = Math.round(height * ratio);
      context.setTransform(ratio, 0, 0, ratio, 0, 0);

      buildGraph();
      draw();
    };

    const animate = (time: number) => {
      frame = 0;
      if (!visible || document.hidden || motionPreference.matches) return;
      frame = window.requestAnimationFrame(animate);

      const rawDelta = previousTime ? time - previousTime : 16.67;
      previousTime = time;
      const dt = clamp(rawDelta / 16.67, 0.25, 2.2);
      elapsed += dt * 0.014;

      const pointerLerp = 1 - Math.pow(1 - 0.16, dt);
      const strengthLerp = 1 - Math.pow(1 - (pointer.active ? 0.11 : 0.065), dt);
      pointer.x += (pointer.targetX - pointer.x) * pointerLerp;
      pointer.y += (pointer.targetY - pointer.y) * pointerLerp;
      pointer.strength += (pointer.targetStrength - pointer.strength) * strengthLerp;

      const targetParallaxX = width > 0 ? ((pointer.x / width) - 0.65) * pointer.strength : 0;
      const targetParallaxY = height > 0 ? ((pointer.y / height) - 0.5) * pointer.strength : 0;
      pointer.parallaxX += (targetParallaxX - pointer.parallaxX) * 0.08 * dt;
      pointer.parallaxY += (targetParallaxY - pointer.parallaxY) * 0.08 * dt;

      const repelRadius = width < 560 ? 105 : 138;
      const springStiffness = 0.036;
      const damping = Math.pow(0.86, dt);

      for (let i = 0; i < particles.length; i += 1) {
        const particle = particles[i];
        const depthScale = 0.55 + particle.z * 0.45;

        // Multi-frequency organic harmonic drift
        const driftX = (
          Math.sin(elapsed + particle.phase) * 3.1
          + Math.cos(elapsed * 0.53 - particle.phase * 1.4) * 1.5
        ) * depthScale;
        const driftY = (
          Math.cos(elapsed * 0.82 + particle.phase * 1.25) * 2.7
          + Math.sin(elapsed * 0.47 + particle.phase * 0.85) * 1.3
        ) * depthScale;

        // Subtle multi-plane depth parallax
        const parallaxX = pointer.parallaxX * (particle.z - 0.58) * 11;
        const parallaxY = pointer.parallaxY * (particle.z - 0.58) * 8.5;

        const targetX = particle.anchorX + driftX + parallaxX;
        const targetY = particle.anchorY + driftY + parallaxY;

        const dx = particle.x - pointer.x;
        const dy = particle.y - pointer.y;
        const distance = Math.hypot(dx, dy) || 1;

        let repelX = 0;
        let repelY = 0;
        let targetHighlight = 0;

        if (pointer.strength > 0.002 && distance < repelRadius) {
          const proximity = 1 - distance / repelRadius;
          const smoothProx = proximity * proximity * (3 - 2 * proximity);
          const force = smoothProx * pointer.strength * (1.25 * depthScale);
          const nx = dx / distance;
          const ny = dy / distance;

          // Gentle radial repulsion with a subtle tangential flow deflection
          repelX = (nx * force - ny * force * 0.14) * dt;
          repelY = (ny * force + nx * force * 0.14) * dt;
          targetHighlight = smoothProx * pointer.strength;
        }

        particle.highlight += (targetHighlight - particle.highlight) * 0.14 * dt;
        particle.vx = (particle.vx + (targetX - particle.x) * springStiffness * dt + repelX) * damping;
        particle.vy = (particle.vy + (targetY - particle.y) * springStiffness * dt + repelY) * damping;
        particle.x += particle.vx * dt;
        particle.y += particle.vy * dt;
      }

      draw();
    };

    const start = () => {
      if (frame || !visible || document.hidden || motionPreference.matches) return;
      previousTime = performance.now();
      frame = window.requestAnimationFrame(animate);
    };

    const stop = () => {
      if (!frame) return;
      window.cancelAnimationFrame(frame);
      frame = 0;
    };

    const onPointerMove = (event: PointerEvent) => {
      if (event.pointerType === 'touch') return;
      const bounds = card.getBoundingClientRect();
      const nextX = event.clientX - bounds.left;
      const nextY = event.clientY - bounds.top;

      // Snap pointer position when entering from idle to avoid sweeping across the card
      if (!pointer.active && pointer.strength < 0.02) {
        pointer.x = nextX;
        pointer.y = nextY;
      }
      pointer.targetX = nextX;
      pointer.targetY = nextY;
      pointer.targetStrength = 1;
      pointer.active = true;
    };

    const onPointerLeave = () => {
      pointer.targetStrength = 0;
      pointer.active = false;
    };

    const onVisibilityChange = () => {
      if (document.hidden) stop();
      else start();
    };

    const onMotionChange = () => {
      if (motionPreference.matches) {
        stop();
        card.dataset.entered = 'true';
        pointer.targetStrength = 0;
        pointer.strength = 0;
        pointer.active = false;
        for (const particle of particles) {
          particle.x = particle.anchorX;
          particle.y = particle.anchorY;
          particle.vx = 0;
          particle.vy = 0;
          particle.highlight = 0;
        }
        draw();
      } else {
        start();
      }
    };

    const resizeObserver = new ResizeObserver(resize);
    const intersectionObserver = new IntersectionObserver(
      ([entry]) => {
        visible = Boolean(entry?.isIntersecting);
        if (visible) {
          if (card.dataset.entered === 'false') {
            window.requestAnimationFrame(() => {
              card.dataset.entered = 'true';
            });
          }
          start();
        } else {
          stop();
        }
      },
      { threshold: 0.08 },
    );

    resizeObserver.observe(card);
    intersectionObserver.observe(card);
    card.addEventListener('pointermove', onPointerMove, { passive: true });
    card.addEventListener('pointerleave', onPointerLeave, { passive: true });
    card.addEventListener('pointercancel', onPointerLeave, { passive: true });
    document.addEventListener('visibilitychange', onVisibilityChange);
    motionPreference.addEventListener('change', onMotionChange);
    resize();

    return () => {
      stop();
      resizeObserver.disconnect();
      intersectionObserver.disconnect();
      card.removeEventListener('pointermove', onPointerMove);
      card.removeEventListener('pointerleave', onPointerLeave);
      card.removeEventListener('pointercancel', onPointerLeave);
      document.removeEventListener('visibilitychange', onVisibilityChange);
      motionPreference.removeEventListener('change', onMotionChange);
    };
  }, []);

  return <canvas className="dashboard-course-canvas" ref={canvasRef} aria-hidden="true" />;
}
