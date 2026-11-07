interface FolderTabShapeProps {
  active: boolean;
  isFirst?: boolean;
}

/** Paper tabs share a stable footprint; only their depth changes on selection. */
export function FolderTabShape({ active, isFirst = false }: FolderTabShapeProps) {
  const width = 134;
  const contour = isFirst && active
    ? 'M 0 44 C 5 34 12 21 20 9 C 25 3 31 0 39 0 L 108 0 C 117 0 123 4 126 13 L 134 44 Z'
    : isFirst
    ? 'M 10 44 C 13 43 15 38 16 32 L 21 13 C 25 4 30 0 38 0 L 104 0 C 113 0 120 5 123 14 L 134 44 Z'
    : 'M 0 44 C 6 44 11 39 14 32 L 21 13 C 25 4 30 0 38 0 L 104 0 C 113 0 120 5 123 14 L 134 44 Z';

  return (
    <svg
      className="folder-tab-svg"
      viewBox={`0 0 ${width} 44`}
      preserveAspectRatio="xMidYMid meet"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      aria-hidden="true"
    >
      <path d={contour} className="folder-tab-body" />
      <path d={contour.slice(0, -2)} className="folder-tab-stroke" />
      {active && (
        <line
          x1="0"
          y1="43"
          x2={width}
          y2="43"
          className="folder-tab-seam-eraser"
          stroke="#fbfaf8"
          strokeWidth="3"
        />
      )}
    </svg>
  );
}
