import React, { useEffect, useRef } from 'react';

interface InteractiveDotGridProps {
  className?: string;
  dotGap?: number;
  baseRadius?: number;
  activeRadius?: number;
  baseColor?: string;
  activeColor?: string;
  interactionRadius?: number;
  repelStrength?: number;
  enableRipple?: boolean;
}

interface Dot {
  originX: number;
  originY: number;
  currentX: number;
  currentY: number;
  targetX: number;
  targetY: number;
  currentAlpha: number;
  targetAlpha: number;
  currentRadius: number;
  targetRadius: number;
}

interface Ripple {
  x: number;
  y: number;
  radius: number;
  maxRadius: number;
  strength: number;
}

export const InteractiveDotGrid: React.FC<InteractiveDotGridProps> = ({
  className = '',
  dotGap = 26,
  baseRadius = 1.25,
  activeRadius = 2.4,
  interactionRadius = 130,
  repelStrength = 7,
  enableRipple = true,
}) => {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const containerRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    const container = containerRef.current;
    if (!canvas || !container) return;

    const ctx = canvas.getContext('2d', { alpha: true });
    if (!ctx) return;

    let animationFrameId: number;
    let dots: Dot[] = [];
    const ripples: Ripple[] = [];

    let mouseX = -9999;
    let mouseY = -9999;
    let isMouseInside = false;

    // Check prefers-reduced-motion
    const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    const initDots = (width: number, height: number) => {
      dots = [];
      const cols = Math.floor(width / dotGap);
      const rows = Math.floor(height / dotGap);
      const offsetX = (width - (cols - 1) * dotGap) / 2;
      const offsetY = (height - (rows - 1) * dotGap) / 2;

      for (let r = 0; r < rows; r++) {
        for (let c = 0; c < cols; c++) {
          const x = offsetX + c * dotGap;
          const y = offsetY + r * dotGap;
          dots.push({
            originX: x,
            originY: y,
            currentX: x,
            currentY: y,
            targetX: x,
            targetY: y,
            currentAlpha: 0.1,
            targetAlpha: 0.1,
            currentRadius: baseRadius,
            targetRadius: baseRadius,
          });
        }
      }
    };

    const handleResize = () => {
      const rect = container.getBoundingClientRect();
      const dpr = Math.min(window.devicePixelRatio || 1, 2);
      const w = Math.floor(rect.width);
      const h = Math.floor(rect.height);

      canvas.width = w * dpr;
      canvas.height = h * dpr;
      canvas.style.width = `${w}px`;
      canvas.style.height = `${h}px`;

      ctx.setTransform(1, 0, 0, 1, 0, 0);
      ctx.scale(dpr, dpr);

      initDots(w, h);
    };

    handleResize();

    const resizeObserver = new ResizeObserver(() => {
      handleResize();
    });
    resizeObserver.observe(container);

    const onMouseMove = (e: MouseEvent) => {
      const rect = container.getBoundingClientRect();
      mouseX = e.clientX - rect.left;
      mouseY = e.clientY - rect.top;
      isMouseInside = true;
    };

    const onMouseEnter = () => {
      isMouseInside = true;
    };

    const onMouseLeave = () => {
      isMouseInside = false;
      mouseX = -9999;
      mouseY = -9999;
    };

    const onClick = (e: MouseEvent) => {
      if (!enableRipple || prefersReducedMotion) return;
      const rect = container.getBoundingClientRect();
      const clickX = e.clientX - rect.left;
      const clickY = e.clientY - rect.top;

      ripples.push({
        x: clickX,
        y: clickY,
        radius: 0,
        maxRadius: Math.max(rect.width, rect.height) * 0.8,
        strength: 1,
      });
    };

    // Listen on container (or parent if attached)
    const parent = container.parentElement;
    const targetElement = parent || container;

    targetElement.addEventListener('mousemove', onMouseMove as EventListener);
    targetElement.addEventListener('mouseenter', onMouseEnter);
    targetElement.addEventListener('mouseleave', onMouseLeave);
    targetElement.addEventListener('click', onClick as EventListener);

    // Animation Loop
    const render = () => {
      const rect = container.getBoundingClientRect();
      ctx.clearRect(0, 0, rect.width, rect.height);

      // Advance ripples
      for (let i = ripples.length - 1; i >= 0; i--) {
        const ripple = ripples[i];
        ripple.radius += 8;
        ripple.strength = Math.max(0, 1 - ripple.radius / ripple.maxRadius);
        if (ripple.radius >= ripple.maxRadius) {
          ripples.splice(i, 1);
        }
      }

      const baseAlpha = 0.11;
      const activeAlpha = 0.85;

      for (let i = 0; i < dots.length; i++) {
        const dot = dots[i];

        // 1. Cursor interaction
        let force = 0;
        let angle = 0;

        if (isMouseInside && !prefersReducedMotion) {
          const dx = mouseX - dot.originX;
          const dy = mouseY - dot.originY;
          const dist = Math.sqrt(dx * dx + dy * dy);

          if (dist < interactionRadius) {
            force = Math.pow(1 - dist / interactionRadius, 1.8);
            angle = Math.atan2(dy, dx);
          }
        }

        // 2. Ripple interaction
        let rippleBonus = 0;
        for (const ripple of ripples) {
          const rdx = dot.originX - ripple.x;
          const rdy = dot.originY - ripple.y;
          const rdist = Math.sqrt(rdx * rdx + rdy * rdy);
          const diff = Math.abs(rdist - ripple.radius);

          if (diff < 35) {
            const wave = (1 - diff / 35) * ripple.strength;
            if (wave > rippleBonus) rippleBonus = wave;
          }
        }

        // Target position (subtle repel effect)
        const repelX = -Math.cos(angle) * force * repelStrength;
        const repelY = -Math.sin(angle) * force * repelStrength;

        dot.targetX = dot.originX + repelX;
        dot.targetY = dot.originY + repelY;

        // Target alpha & radius
        const totalHighlight = Math.min(1, force + rippleBonus * 0.9);
        dot.targetAlpha = baseAlpha + totalHighlight * (activeAlpha - baseAlpha);
        dot.targetRadius = baseRadius + totalHighlight * (activeRadius - baseRadius);

        // Physics easing (Lerp)
        dot.currentX += (dot.targetX - dot.currentX) * 0.18;
        dot.currentY += (dot.targetY - dot.currentY) * 0.18;
        dot.currentAlpha += (dot.targetAlpha - dot.currentAlpha) * 0.15;
        dot.currentRadius += (dot.targetRadius - dot.currentRadius) * 0.15;

        // Render dot
        ctx.beginPath();
        ctx.arc(dot.currentX, dot.currentY, dot.currentRadius, 0, Math.PI * 2);
        ctx.fillStyle = `rgba(255, 255, 255, ${dot.currentAlpha.toFixed(3)})`;
        ctx.fill();
      }

      animationFrameId = requestAnimationFrame(render);
    };

    render();

    return () => {
      cancelAnimationFrame(animationFrameId);
      resizeObserver.disconnect();
      targetElement.removeEventListener('mousemove', onMouseMove as EventListener);
      targetElement.removeEventListener('mouseenter', onMouseEnter);
      targetElement.removeEventListener('mouseleave', onMouseLeave);
      targetElement.removeEventListener('click', onClick as EventListener);
    };
  }, [dotGap, baseRadius, activeRadius, interactionRadius, repelStrength, enableRipple]);

  return (
    <div
      ref={containerRef}
      className={`absolute inset-0 pointer-events-none overflow-hidden rounded-[inherit] ${className}`}
      aria-hidden="true"
    >
      <canvas ref={canvasRef} className="block w-full h-full" />
    </div>
  );
};
