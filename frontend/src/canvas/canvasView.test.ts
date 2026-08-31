import { describe, expect, it } from "vitest";
import {
  DEFAULT_VIEW,
  fitView,
  formatLength,
  gridStepMm,
  makeToMm,
  makeToPx,
  snapToGrid,
  zoomAt,
  type ViewTransform,
} from "./canvasView";

describe("makeToPx / makeToMm", () => {
  it("round-trips mm -> px -> mm", () => {
    const view: ViewTransform = { zoom: 2, panXPx: 15, panYPx: -30 };
    const toPx = makeToPx(0.15, view, 500, 350);
    const toMm = makeToMm(0.15, view, 500, 350);
    const original = { xMm: 1234, yMm: -567 };
    const px = toPx(original.xMm, original.yMm);
    const back = toMm(px.x, px.y);
    expect(back.xMm).toBeCloseTo(original.xMm);
    expect(back.yMm).toBeCloseTo(original.yMm);
  });

  it("places the mm origin at the pixel origin with no pan/zoom", () => {
    const toPx = makeToPx(0.15, DEFAULT_VIEW, 500, 350);
    expect(toPx(0, 0)).toEqual({ x: 500, y: 350 });
  });
});

describe("zoomAt", () => {
  it("keeps the world point under the cursor fixed on screen", () => {
    const view: ViewTransform = { zoom: 1, panXPx: 0, panYPx: 0 };
    const basePxPerMm = 0.15;
    const originX = 500;
    const originY = 350;
    const cursorX = 620;
    const cursorY = 410;
    const toMmBefore = makeToMm(basePxPerMm, view, originX, originY);
    const worldBefore = toMmBefore(cursorX, cursorY);

    const zoomed = zoomAt(view, basePxPerMm, originX, originY, cursorX, cursorY, 2);
    const toMmAfter = makeToMm(basePxPerMm, zoomed, originX, originY);
    const worldAfter = toMmAfter(cursorX, cursorY);

    expect(worldAfter.xMm).toBeCloseTo(worldBefore.xMm);
    expect(worldAfter.yMm).toBeCloseTo(worldBefore.yMm);
    expect(zoomed.zoom).toBeCloseTo(2);
  });

  it("clamps zoom to the configured min/max", () => {
    const view: ViewTransform = { zoom: 1, panXPx: 0, panYPx: 0 };
    const zoomedOut = zoomAt(view, 0.15, 0, 0, 0, 0, 0.0001, 0.5, 4);
    expect(zoomedOut.zoom).toBe(0.5);
    const zoomedIn = zoomAt(view, 0.15, 0, 0, 0, 0, 10000, 0.5, 4);
    expect(zoomedIn.zoom).toBe(4);
  });
});

describe("fitView", () => {
  it("returns the default view for null bounds", () => {
    expect(fitView(0.15, null, 1100, 700)).toEqual(DEFAULT_VIEW);
  });

  it("centers the bounds on screen", () => {
    const bounds = { minX: 0, maxX: 4000, minY: 0, maxY: 3000 };
    const view = fitView(0.15, bounds, 1100, 700);
    // Center of bounds (2000, 1500) should map to the screen center
    // (canvasW/2, canvasH/2) when origin is placed at (canvasW/2, canvasH/2)
    // by the caller, since fitView's pan is chosen for that convention.
    const toPx = makeToPx(0.15, view, 550, 350);
    const center = toPx(2000, 1500);
    expect(center.x).toBeCloseTo(550);
    expect(center.y).toBeCloseTo(350);
  });

  it("clamps zoom within min/max bounds for degenerate/huge geometry", () => {
    const tiny = fitView(0.15, { minX: 0, maxX: 1, minY: 0, maxY: 1 }, 1100, 700);
    expect(tiny.zoom).toBeLessThanOrEqual(8);
    const huge = fitView(0.15, { minX: 0, maxX: 10_000_000, minY: 0, maxY: 10_000_000 }, 1100, 700);
    expect(huge.zoom).toBeGreaterThanOrEqual(0.15);
  });
});

describe("gridStepMm", () => {
  it("picks a nice step so grid lines land roughly targetPx apart", () => {
    expect(gridStepMm(1, 60)).toBe(100);
    expect(gridStepMm(0.15, 60)).toBe(500);
  });

  it("falls back to the largest step for extremely small scales", () => {
    expect(gridStepMm(0.0000001, 60)).toBe(50000);
  });
});

describe("snapToGrid", () => {
  it("rounds to the nearest grid step", () => {
    expect(snapToGrid(123, 50)).toBe(100);
    expect(snapToGrid(126, 50)).toBe(150);
    expect(snapToGrid(-30, 50)).toBe(-50);
  });
});

describe("formatLength", () => {
  it("formats mm as a rounded integer", () => {
    expect(formatLength(1234.6, "mm")).toBe("1235 mm");
  });

  it("formats meters with two decimal places", () => {
    expect(formatLength(1234, "m")).toBe("1.23 m");
  });
});

