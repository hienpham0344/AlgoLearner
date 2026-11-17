export function FolderBackingArtwork() {
  return (
    <div className="folder-backing-layer" aria-hidden="true">
      <svg className="folder-orbit folder-orbit-top" viewBox="0 0 430 160" fill="none">
        <path d="M8 151C95 83 235 11 333 13c53 1 85 35 88 104" />
        <path d="M42 151C133 103 261 39 341 43c48 3 69 26 79 74" />
        <circle cx="362" cy="41" r="4" />
      </svg>
      <svg className="folder-orbit folder-orbit-left" viewBox="0 0 480 250" fill="none">
        <path d="M7 222C-5 187 149 75 332 29c64-16 116-19 142-12" />
        <path d="M89 248C85 201 208 128 340 94c63-16 112-17 134-13" />
        <circle cx="102" cy="231" r="4" />
      </svg>
      <div className="folder-charcoal-flap" />
      <div className="folder-charcoal-backing" />
      <div className="folder-paper-underlay sheet-underlay-2" />
      <div className="folder-paper-underlay sheet-underlay-1" />
    </div>
  );
}
