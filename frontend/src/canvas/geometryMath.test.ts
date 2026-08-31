import { describe, expect, it } from "vitest";
import { dist, normalizeAngle, projectOntoSegment, toLocalFrame } from "./geometryMath";

describe("dist", () => {
  it("computes euclidean distance", () => {
    expect(dist(0, 0, 3, 4)).toBe(5);
    expect(dist(1, 1, 1, 1)).toBe(0);
  });
});

describe("projectOntoSegment", () => {
  it("projects a point onto the middle of a horizontal segment", () => {
    const r = projectOntoSegment(5, 3, 0, 0, 10, 0);
    expect(r.t).toBeCloseTo(0.5);
    expect(r.x).toBeCloseTo(5);
    expect(r.y).toBeCloseTo(0);
    expect(r.distance).toBeCloseTo(3);
  });

  it("clamps t to 0 when the point projects before the segment start", () => {
    const r = projectOntoSegment(-5, 0, 0, 0, 10, 0);
    expect(r.t).toBe(0);
    expect(r.x).toBe(0);
  });

  it("clamps t to 1 when the point projects after the segment end", () => {
    const r = projectOntoSegment(15, 0, 0, 0, 10, 0);
    expect(r.t).toBe(1);
    expect(r.x).toBe(10);
  });

  it("handles a zero-length segment without dividing by zero", () => {
    const r = projectOntoSegment(3, 4, 1, 1, 1, 1);
    expect(r.t).toBe(0);
    expect(r.distance).toBeCloseTo(dist(3, 4, 1, 1));
  });
});

describe("normalizeAngle", () => {
  it("leaves angles already in [0, 360) unchanged", () => {
    expect(normalizeAngle(0)).toBe(0);
    expect(normalizeAngle(90)).toBe(90);
    expect(normalizeAngle(359)).toBe(359);
  });

  it("wraps angles >= 360", () => {
    expect(normalizeAngle(360)).toBe(0);
    expect(normalizeAngle(450)).toBe(90);
    expect(normalizeAngle(720)).toBe(0);
  });

  it("wraps negative angles into [0, 360)", () => {
    expect(normalizeAngle(-90)).toBe(270);
    expect(normalizeAngle(-360)).toBeCloseTo(0);
  });
});

describe("toLocalFrame", () => {
  it("returns the offset from center unchanged when angle is 0", () => {
    const { lx, ly } = toLocalFrame(15, 25, 10, 20, 0);
    expect(lx).toBeCloseTo(5);
    expect(ly).toBeCloseTo(5);
  });

  it("rotates a point 90 degrees around the center", () => {
    // A point directly "east" of center, in a frame rotated 90deg, should
    // land on the local Y axis.
    const { lx, ly } = toLocalFrame(20, 10, 10, 10, 90);
    expect(lx).toBeCloseTo(0);
    expect(ly).toBeCloseTo(-10);
  });

  it("round-trips with the inverse rotation", () => {
    const angle = 37;
    const cx = 100;
    const cy = 200;
    const worldX = 130;
    const worldY = 180;
    const { lx, ly } = toLocalFrame(worldX, worldY, cx, cy, angle);
    // Rotate the local point back by -angle (i.e. toLocalFrame with -angle
    // applied to the local point, offset re-added) to recover world coords.
    const rad = (angle * Math.PI) / 180;
    const backX = cx + (lx * Math.cos(rad) - ly * Math.sin(rad));
    const backY = cy + (lx * Math.sin(rad) + ly * Math.cos(rad));
    expect(backX).toBeCloseTo(worldX);
    expect(backY).toBeCloseTo(worldY);
  });
});



