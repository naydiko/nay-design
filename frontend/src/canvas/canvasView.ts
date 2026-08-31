// Shared helpers for the Stage 1 2D canvases (level walls + room furniture).
//
// A ViewTransform describes pan/zoom state in *pixels* only; it never touches
// the underlying geometry, which always stays in millimetres. The mapping
// between world (mm) and screen (px) space is:
//
//   screenPx = originPx + worldMm * basePxPerMm * zoom + panPx
//
// so `zoom === 1` reproduces the original fixed-scale rendering, and pan/zoom
// are purely a viewport concern layered on top of unchanged mm coordinates.

export interface ViewTransform {
  zoom: number;
  panXPx: number;
  panYPx: number;
}

export const MIN_ZOOM = 0.15;
export const MAX_ZOOM = 8;

export const DEFAULT_VIEW: ViewTransform = { zoom: 1, panXPx: 0, panYPx: 0 };

export function clamp(value: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, value));
}

/** Picks a "nice" grid spacing (in mm) so that grid lines land roughly
 * `targetPx` apart on screen at the given effective scale. */
export function gridStepMm(scalePxPerMm: number, targetPx = 60): number {
  const rawMm = targetPx / scalePxPerMm;
  const steps = [10, 20, 25, 50, 100, 200, 250, 500, 1000, 2000, 2500, 5000, 10000, 20000, 25000, 50000];
  for (const s of steps) {
    if (s >= rawMm) return s;
  }
  return steps[steps.length - 1];
}

export function makeToPx(basePxPerMm: number, view: ViewTransform, originX: number, originY: number) {
  return (xMm: number, yMm: number) => {
    const scale = basePxPerMm * view.zoom;
    return { x: originX + xMm * scale + view.panXPx, y: originY + yMm * scale + view.panYPx };
  };
}

export function makeToMm(basePxPerMm: number, view: ViewTransform, originX: number, originY: number) {
  return (xPx: number, yPx: number) => {
    const scale = basePxPerMm * view.zoom;
    return { xMm: (xPx - originX - view.panXPx) / scale, yMm: (yPx - originY - view.panYPx) / scale };
  };
}

/** Returns a new view zoomed by `factor`, keeping the world point currently
 * under (cursorXPx, cursorYPx) fixed on screen. */
export function zoomAt(
  view: ViewTransform,
  basePxPerMm: number,
  originX: number,
  originY: number,
  cursorXPx: number,
  cursorYPx: number,
  factor: number,
  minZoom = MIN_ZOOM,
  maxZoom = MAX_ZOOM
): ViewTransform {
  const oldScale = basePxPerMm * view.zoom;
  const worldX = (cursorXPx - originX - view.panXPx) / oldScale;
  const worldY = (cursorYPx - originY - view.panYPx) / oldScale;
  const newZoom = clamp(view.zoom * factor, minZoom, maxZoom);
  const newScale = basePxPerMm * newZoom;
  return {
    zoom: newZoom,
    panXPx: cursorXPx - originX - worldX * newScale,
    panYPx: cursorYPx - originY - worldY * newScale,
  };
}

export interface Bounds {
  minX: number;
  maxX: number;
  minY: number;
  maxY: number;
}

/** Computes a view that fits `bounds` (in mm) inside a canvas of the given
 * pixel size, with padding, centering the content. */
export function fitView(
  basePxPerMm: number,
  bounds: Bounds | null,
  canvasW: number,
  canvasH: number,
  paddingPx = 60,
  minZoom = MIN_ZOOM,
  maxZoom = MAX_ZOOM
): ViewTransform {
  if (!bounds) return { ...DEFAULT_VIEW };
  const wMm = Math.max(bounds.maxX - bounds.minX, 200);
  const hMm = Math.max(bounds.maxY - bounds.minY, 200);
  const scaleX = (canvasW - paddingPx * 2) / wMm;
  const scaleY = (canvasH - paddingPx * 2) / hMm;
  const scale = Math.min(scaleX, scaleY);
  const zoom = clamp(scale / basePxPerMm, minZoom, maxZoom);
  const finalScale = basePxPerMm * zoom;
  const centerXMm = (bounds.minX + bounds.maxX) / 2;
  const centerYMm = (bounds.minY + bounds.maxY) / 2;
  // originX/originY are where mm-origin (0,0) sits on screen with no pan;
  // panPx is chosen so centerXMm/centerYMm land exactly on the screen origin.
  return { zoom, panXPx: -centerXMm * finalScale, panYPx: -centerYMm * finalScale };
}

export function formatLength(mm: number, unit: "mm" | "m"): string {
  if (unit === "m") return `${(mm / 1000).toFixed(2)} m`;
  return `${Math.round(mm)} mm`;
}

export function snapToGrid(value: number, stepMm: number): number {
  return Math.round(value / stepMm) * stepMm;
}




