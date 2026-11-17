import { useEffect, useRef } from 'react';

type Particle = {
  anchorX: number;
  anchorY: number;
  x: number;
  y: number;
  vx: number;
  vy: number;
  z: number;
  radius: number;
  baseAlpha: number;
  phaseX: number;
  phaseY: number;
  driftAmp: number;
  canConnect: boolean;
  highlight: number;
};

type ParticleConfig = {
  anchorX: number;
  anchorY: number;
  z: number;
  radius: number;
  baseAlpha: number;
  driftAmp: number;
  canConnect: boolean;
};

// Sculpted constellation waypoints (normalized x, y) forming the organic branching
// plexus silhouette on the right half of the card, paired with local satellite nodes.
const FILAMENT_WAYPOINTS: readonly [number, number][] = [
  // Upper-mid arch
  [0.485, 0.265], [0.515, 0.195], [0.538, 0.145], [0.556, 0.088], [0.592, 0.155], [0.625, 0.215],
  // Central descending spine
  [0.618, 0.285], [0.648, 0.345], [0.622, 0.415], [0.665, 0.455], [0.695, 0.385],
  // Dense center-right triangular nexus
  [0.622, 0.555], [0.652, 0.525], [0.684, 0.575], [0.718, 0.515], [0.742, 0.475],
  [0.758, 0.535], [0.785, 0.505], [0.808, 0.538], [0.735, 0.595], [0.768, 0.635],
  // Lower-mid branch
  [0.605, 0.655], [0.638, 0.685], [0.575, 0.725], [0.522, 0.815], [0.468, 0.765],
  [0.445, 0.865], [0.515, 0.905], [0.635, 0.925], [0.672, 0.955],
  // Lower-right branch
  [0.748, 0.715], [0.778, 0.755], [0.818, 0.715], [0.805, 0.815], [0.848, 0.775],
  [0.858, 0.885], [0.888, 0.935], [0.912, 0.895],
  // Upper-right cluster
  [0.784, 0.152], [0.795, 0.128], [0.822, 0.095], [0.845, 0.145], [0.885, 0.092],
  // Far-right mid cluster
  [0.878, 0.455], [0.896, 0.335], [0.918, 0.395], [0.935, 0.465], [0.955, 0.515],
];

const AMBIENT_DUST_DESKTOP = 64;
const AMBIENT_DUST_MOBILE = 32;

const clamp = (value: number, min: number, max: number) => Math.min(max, Math.max(min, value));

export function InteractiveCourseBackground() {
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
    let maxConnectDist = 78;
    let visible = false;
    let frame = 0;
    let previousTime = 0;
    let elapsed = 0;

    const buildScene = () => {
      const previous = [...particles];
      const previousWidth = width;
      const previousHeight = height;

      let state = 0x5f3759df;
      const nextRand = () => {
        state = Math.imul(state ^ (state >>> 15), state | 1);
        state ^= state + Math.imul(state ^ (state >>> 7), state | 61);
        return ((state ^ (state >>> 14)) >>> 0) / 4294967296;
      };

      const compact = width < 560;
      maxConnectDist = clamp(Math.min(width * 0.096, 84), 50, 84);

      particles.length = 0;
      let index = 0;

      const appendParticle = (config: ParticleConfig) => {
        const old = previous[index];
        index += 1;

        particles.push({
          anchorX: config.anchorX,
          anchorY: config.anchorY,
          x: old && previousWidth ? config.anchorX + ((old.x - old.anchorX) * width) / previousWidth : config.anchorX,
          y: old && previousHeight ? config.anchorY + ((old.y - old.anchorY) * height) / previousHeight : config.anchorY,
          vx: old?.vx ?? 0,
          vy: old?.vy ?? 0,
          z: config.z,
          radius: config.radius,
          baseAlpha: config.baseAlpha,
          phaseX: nextRand() * Math.PI * 2,
          phaseY: nextRand() * Math.PI * 2,
          driftAmp: config.driftAmp,
          canConnect: config.canConnect,
          highlight: old?.highlight ?? 0,
        });
      };

      // 1. Primary constellation waypoints + subtle local satellites on the right half
      const step = compact ? 2 : 1;
      for (let i = 0; i < FILAMENT_WAYPOINTS.length; i += step) {
        const [wx, wy] = FILAMENT_WAYPOINTS[i];
        const normX = compact ? clamp(wx * 0.9 + 0.09, 0.48, 0.97) : wx;
        const normY = wy;
        const z = 0.5 + nextRand() * 0.5;

        appendParticle({
          anchorX: normX * width,
          anchorY: normY * height,
          z,
          radius: 0.95 + Math.pow(z, 1.2) * 0.75,
          baseAlpha: 0.26 + z * 0.34,
          driftAmp: 2.0 + nextRand() * 2.4,
          canConnect: true,
        });

        // Add a close satellite node for every second waypoint on desktop to enrich micro-triangles
        if (!compact && i % 2 === 0) {
          const angle = nextRand() * Math.PI * 2;
          const distX = (0.018 + nextRand() * 0.028) * Math.cos(angle);
          const distY = (0.035 + nextRand() * 0.055) * Math.sin(angle);
          const satZ = 0.4 + nextRand() * 0.45;

          appendParticle({
            anchorX: clamp(normX + distX, 0.44, 0.97) * width,
            anchorY: clamp(normY + distY, 0.05, 0.95) * height,
            z: satZ,
            radius: 0.8 + satZ * 0.65,
            baseAlpha: 0.2 + satZ * 0.28,
            driftAmp: 2.2 + nextRand() * 2.5,
            canConnect: true,
          });
        }
      }

      // 2. Fine standalone ambient starfield/dust dots scattered across the entire card
      const dustCount = compact ? AMBIENT_DUST_MOBILE : AMBIENT_DUST_DESKTOP;
      for (let i = 0; i < dustCount; i += 1) {
        const z = 0.25 + nextRand() * 0.7;

        appendParticle({
          anchorX: (0.02 + nextRand() * 0.96) * width,
          anchorY: (0.04 + nextRand() * 0.92) * height,
          z,
          radius: 0.65 + nextRand() * 0.85,
          baseAlpha: 0.14 + z * 0.28,
          driftAmp: 1.4 + nextRand() * 2.0,
          canConnect: false,
        });
      }
    };

    const draw = () => {
      context.clearRect(0, 0, width, height);
      if (width <= 0 || height <= 0) return;

      // 1. Draw delicate constellation / plexus lines between nearby connectable nodes
      const maxDistSq = maxConnectDist * maxConnectDist;
      context.lineWidth = 0.6;

      for (let i = 0; i < particles.length; i += 1) {
        const a = particles[i];
        if (!a.canConnect) continue;

        for (let j = i + 1; j < particles.length; j += 1) {
          const b = particles[j];
          if (!b.canConnect) continue;

          const dx = a.x - b.x;
          const dy = a.y - b.y;
          const distSq = dx * dx + dy * dy;
          if (distSq >= maxDistSq) continue;

          const dist = Math.sqrt(distSq);
          const proximity = 1 - dist / maxConnectDist;
          const smoothProx = proximity * proximity;
          const cursorBoost = (a.highlight + b.highlight) * 0.5;
          const alpha = smoothProx * (0.19 + ((a.z + b.z) * 0.5) * 0.11) + smoothProx * cursorBoost * 0.18;

          if (alpha <= 0.005) continue;

          context.strokeStyle = `rgba(214, 220, 228, ${alpha.toFixed(3)})`;
          context.beginPath();
          context.moveTo(a.x, a.y);
          context.lineTo(b.x, b.y);
          context.stroke();
        }
      }

      // 2. Draw fine monochrome constellation vertices & ambient starfield dots
      for (let i = 0; i < particles.length; i += 1) {
        const p = particles[i];
        const twinkle = 0.9 + 0.1 * Math.sin(elapsed * 1.15 + p.phaseX);
        const alpha = clamp(p.baseAlpha * twinkle + p.highlight * 0.28, 0.06, 0.86);
        const radius = p.radius * (1 + p.highlight * 0.25);

        context.fillStyle = `rgba(226, 231, 238, ${alpha.toFixed(3)})`;
        context.beginPath();
        context.arc(p.x, p.y, radius, 0, Math.PI * 2);
        context.fill();
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

      buildScene();
      draw();
    };

    const animate = (time: number) => {
      frame = 0;
      if (!visible || document.hidden || motionPreference.matches) return;
      frame = window.requestAnimationFrame(animate);

      const rawDelta = previousTime ? time - previousTime : 16.67;
      previousTime = time;
      const dt = clamp(rawDelta / 16.67, 0.25, 2.2);
      elapsed += dt * 0.015;

      const pointerLerp = 1 - Math.pow(1 - 0.16, dt);
      const strengthLerp = 1 - Math.pow(1 - (pointer.active ? 0.1 : 0.06), dt);
      pointer.x += (pointer.targetX - pointer.x) * pointerLerp;
      pointer.y += (pointer.targetY - pointer.y) * pointerLerp;
      pointer.strength += (pointer.targetStrength - pointer.strength) * strengthLerp;

      const targetParallaxX = width > 0 ? ((pointer.x / width) - 0.5) * pointer.strength : 0;
      const targetParallaxY = height > 0 ? ((pointer.y / height) - 0.5) * pointer.strength : 0;
      pointer.parallaxX += (targetParallaxX - pointer.parallaxX) * 0.08 * dt;
      pointer.parallaxY += (targetParallaxY - pointer.parallaxY) * 0.08 * dt;

      const repelRadius = width < 560 ? 105 : 138;
      const springStiffness = 0.032;
      const damping = Math.pow(0.87, dt);

      for (let i = 0; i < particles.length; i += 1) {
        const p = particles[i];
        const depthScale = 0.5 + p.z * 0.5;

        const driftX = (
          Math.sin(elapsed + p.phaseX) * p.driftAmp
          + Math.cos(elapsed * 0.52 - p.phaseY) * (p.driftAmp * 0.45)
        ) * depthScale;
        const driftY = (
          Math.cos(elapsed * 0.84 + p.phaseY) * p.driftAmp
          + Math.sin(elapsed * 0.46 + p.phaseX) * (p.driftAmp * 0.45)
        ) * depthScale;

        const parallaxX = pointer.parallaxX * (p.z - 0.5) * 10;
        const parallaxY = pointer.parallaxY * (p.z - 0.5) * 8;

        const targetX = p.anchorX + driftX + parallaxX;
        const targetY = p.anchorY + driftY + parallaxY;

        const dx = p.x - pointer.x;
        const dy = p.y - pointer.y;
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

          repelX = (nx * force - ny * force * 0.15) * dt;
          repelY = (ny * force + nx * force * 0.15) * dt;
          targetHighlight = smoothProx * pointer.strength;
        }

        p.highlight += (targetHighlight - p.highlight) * 0.14 * dt;
        p.vx = (p.vx + (targetX - p.x) * springStiffness * dt + repelX) * damping;
        p.vy = (p.vy + (targetY - p.y) * springStiffness * dt + repelY) * damping;
        p.x += p.vx * dt;
        p.y += p.vy * dt;
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
        for (const p of particles) {
          p.x = p.anchorX;
          p.y = p.anchorY;
          p.vx = 0;
          p.vy = 0;
          p.highlight = 0;
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
      { threshold: 0.06 },
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
