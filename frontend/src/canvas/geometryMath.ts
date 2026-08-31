// Pure, dependency-free geometry helpers shared by the level canvas
// (LevelCanvasPage) and the furniture canvas (RoomFurniturePage). Extracted
// so they can be unit tested directly without mounting either page.

/** Euclidean distance between two points. */
export function dist(ax: number, ay: number, bx: number, by: number): number {
  return Math.hypot(ax - bx, ay - by);
}

/** Projects point p onto segment ab, returning the clamped t in [0,1], the
 * distance from p to the segment, and the projected point. */
export function projectOntoSegment(
  px: number,
  py: number,
  ax: number,
  ay: number,
  bx: number,
  by: number
): { t: number; distance: number; x: number; y: number } {
  const dx = bx - ax;
  const dy = by - ay;
  const lenSq = dx * dx + dy * dy;
  let t = lenSq === 0 ? 0 : ((px - ax) * dx + (py - ay) * dy) / lenSq;
  t = Math.max(0, Math.min(1, t));
  const projX = ax + t * dx;
  const projY = ay + t * dy;
  return { t, distance: dist(px, py, projX, projY), x: projX, y: projY };
}

/** Normalizes an angle in degrees into [0, 360). */
export function normalizeAngle(deg: number): number {
  let a = deg % 360;
  if (a < 0) a += 360;
  return a;
}

/** Converts a point in world-space into a placement's local (unrotated)
 * frame, so hit-testing can use a simple axis-aligned bounds check. */
export function toLocalFrame(
  px: number,
  py: number,
  cx: number,
  cy: number,
  angleDeg: number
): { lx: number; ly: number } {
  const rad = (-angleDeg * Math.PI) / 180;
  const dx = px - cx;
  const dy = py - cy;
  return {
    lx: dx * Math.cos(rad) - dy * Math.sin(rad),
    ly: dx * Math.sin(rad) + dy * Math.cos(rad),
  };
}

