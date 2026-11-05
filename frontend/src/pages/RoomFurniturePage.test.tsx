import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import RoomFurniturePage, { buildRoomBoundaryFromGeometry } from "./RoomFurniturePage";
import { emptyGeometry, mockPlacement, mockProduct, mockRoom, mockRoomGeometry, mockVendor } from "../test/fixtures";
import type { LevelGeometryResponse, RoomPlacementsSaveResponse } from "../api/types";
import { fitView, gridStepMm, makeToMm, makeToPx, snapToGrid } from "../canvas/canvasView";

vi.mock("../api/endpoints", () => ({
  LevelApi: { getGeometry: vi.fn() },
  RoomApi: { get: vi.fn(), getPlacements: vi.fn(), savePlacements: vi.fn() },
  ProductApi: { list: vi.fn(), get: vi.fn() },
  VendorApi: { list: vi.fn() },
}));

import { LevelApi, ProductApi, RoomApi, VendorApi } from "../api/endpoints";

type DrawCommand = { name: string; args: unknown[] };

const BASE_PX_PER_MM = 0.15;
const CANVAS_W = 900;
const CANVAS_H = 600;
const ORIGIN_X = CANVAS_W / 2;
const ORIGIN_Y = CANVAS_H / 2;
const ROOM_BOUNDS = { minX: 0, minY: 0, maxX: 4000, maxY: 2000 };

let drawCommands: DrawCommand[] = [];
let getContextSpy: ReturnType<typeof vi.spyOn>;

function makeCanvasContext() {
  const record = (name: string) =>
    vi.fn((...args: unknown[]) => {
      drawCommands.push({ name, args });
    });
  return {
    clearRect: record("clearRect"),
    fillRect: record("fillRect"),
    strokeRect: record("strokeRect"),
    beginPath: record("beginPath"),
    closePath: record("closePath"),
    moveTo: record("moveTo"),
    lineTo: record("lineTo"),
    stroke: record("stroke"),
    fill: record("fill"),
    arc: record("arc"),
    fillText: record("fillText"),
    setLineDash: record("setLineDash"),
    save: record("save"),
    restore: record("restore"),
    translate: record("translate"),
    rotate: record("rotate"),
    strokeStyle: "",
    fillStyle: "",
    lineWidth: 1,
    font: "",
    textAlign: "start",
  } as unknown as CanvasRenderingContext2D;
}

function fittedPoint(xMm: number, yMm: number) {
  return makeToPx(
    BASE_PX_PER_MM,
    fitView(BASE_PX_PER_MM, ROOM_BOUNDS, CANVAS_W, CANVAS_H),
    ORIGIN_X,
    ORIGIN_Y
  )(xMm, yMm);
}

function approx(value: unknown, expected: number) {
  return typeof value === "number" && Math.abs(value - expected) < 0.1;
}

function hasSegment(from: { x: number; y: number }, to: { x: number; y: number }) {
  let lastMove: unknown[] | null = null;
  for (const command of drawCommands) {
    if (command.name === "moveTo") {
      lastMove = command.args;
      continue;
    }
    if (
      command.name === "lineTo" &&
      lastMove &&
      approx(lastMove[0], from.x) &&
      approx(lastMove[1], from.y) &&
      approx(command.args[0], to.x) &&
      approx(command.args[1], to.y)
    ) {
      return true;
    }
  }
  return false;
}

function fillTextIncludes(text: string) {
  return drawCommands.some((command) => command.name === "fillText" && String(command.args[0]).includes(text));
}

function latestFillRectWithSize(widthPx: number, depthPx: number) {
  return drawCommands.find(
    (command) =>
      command.name === "fillRect" &&
      approx(command.args[2], widthPx) &&
      approx(command.args[3], depthPx)
  );
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={[`/rooms/${mockRoom.id}`]}>
      <Routes>
        <Route path="/rooms/:roomId" element={<RoomFurniturePage />} />
      </Routes>
    </MemoryRouter>
  );
}

function drag(canvas: HTMLElement, from: { x: number; y: number }, to: { x: number; y: number }) {
  fireEvent.pointerDown(canvas, { clientX: from.x, clientY: from.y, button: 0 });
  fireEvent.pointerMove(canvas, { clientX: to.x, clientY: to.y });
  fireEvent.pointerUp(canvas, { clientX: to.x, clientY: to.y, button: 0 });
}

function inspector() {
  return within(document.querySelector(".side-panel") as HTMLElement);
}

describe("RoomFurniturePage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    drawCommands = [];
    getContextSpy = vi
      .spyOn(HTMLCanvasElement.prototype, "getContext")
      .mockReturnValue(makeCanvasContext());
    vi.mocked(RoomApi.get).mockResolvedValue(mockRoom);
    vi.mocked(LevelApi.getGeometry).mockResolvedValue(mockRoomGeometry());
    vi.mocked(VendorApi.list).mockResolvedValue([mockVendor]);
    vi.mocked(ProductApi.list).mockResolvedValue([mockProduct]);
  });

  afterEach(() => {
    getContextSpy.mockRestore();
  });

  it("reconstructs the room boundary from level room-wall geometry", () => {
    const boundary = buildRoomBoundaryFromGeometry(mockRoomGeometry(), mockRoom.id);

    expect(boundary?.segments).toHaveLength(4);
    expect(boundary?.polygon).toHaveLength(4);
    expect(boundary?.bounds).toEqual(ROOM_BOUNDS);
    expect(boundary?.segments.map((segment) => segment.lengthMm).sort((a, b) => a - b)).toEqual([
      2000, 2000, 4000, 4000,
    ]);
  });

  it("reconstructs each adjacent room from its own links while sharing one wall", () => {
    const geometry: LevelGeometryResponse = {
      levelId: mockRoom.levelId,
      nodes: [
        { id: "a", xMm: 0, yMm: 0, zMm: 0 },
        { id: "b", xMm: 4000, yMm: 0, zMm: 0 },
        { id: "c", xMm: 7000, yMm: 0, zMm: 0 },
        { id: "d", xMm: 0, yMm: 2500, zMm: 0 },
        { id: "e", xMm: 4000, yMm: 2500, zMm: 0 },
        { id: "f", xMm: 7000, yMm: 2500, zMm: 0 },
      ],
      walls: [
        { id: "ab", startNodeId: "a", endNodeId: "b", thicknessMm: 150, heightMm: 2700, kind: "INTERIOR" },
        { id: "bc", startNodeId: "b", endNodeId: "c", thicknessMm: 150, heightMm: 2700, kind: "INTERIOR" },
        { id: "cf", startNodeId: "c", endNodeId: "f", thicknessMm: 150, heightMm: 2700, kind: "INTERIOR" },
        { id: "fe", startNodeId: "f", endNodeId: "e", thicknessMm: 150, heightMm: 2700, kind: "INTERIOR" },
        { id: "ed", startNodeId: "e", endNodeId: "d", thicknessMm: 150, heightMm: 2700, kind: "INTERIOR" },
        { id: "da", startNodeId: "d", endNodeId: "a", thicknessMm: 150, heightMm: 2700, kind: "INTERIOR" },
        { id: "be", startNodeId: "b", endNodeId: "e", thicknessMm: 150, heightMm: 2700, kind: "INTERIOR" },
      ],
      openings: [],
      rooms: [
        { id: "living", name: "Living", type: "LIVING_ROOM" },
        { id: "kitchen", name: "Kitchen", type: "KITCHEN" },
      ],
      roomWalls: [
        { roomId: "living", wallId: "ab" },
        { roomId: "living", wallId: "be" },
        { roomId: "living", wallId: "ed" },
        { roomId: "living", wallId: "da" },
        { roomId: "kitchen", wallId: "bc" },
        { roomId: "kitchen", wallId: "cf" },
        { roomId: "kitchen", wallId: "fe" },
        { roomId: "kitchen", wallId: "be" },
      ],
    };

    const living = buildRoomBoundaryFromGeometry(geometry, "living");
    const kitchen = buildRoomBoundaryFromGeometry(geometry, "kitchen");

    expect(living?.segments.map((segment) => segment.wallId).sort()).toEqual(["ab", "be", "da", "ed"]);
    expect(kitchen?.segments.map((segment) => segment.wallId).sort()).toEqual(["bc", "be", "cf", "fe"]);
    expect(living?.bounds).toEqual({ minX: 0, minY: 0, maxX: 4000, maxY: 2500 });
    expect(kitchen?.bounds).toEqual({ minX: 4000, minY: 0, maxX: 7000, maxY: 2500 });
  });

  it("uses the same millimetre world coordinates through fit and zoom transforms", () => {
    const view = fitView(BASE_PX_PER_MM, ROOM_BOUNDS, CANVAS_W, CANVAS_H);
    const toPx = makeToPx(BASE_PX_PER_MM, view, ORIGIN_X, ORIGIN_Y);
    const toMm = makeToMm(BASE_PX_PER_MM, view, ORIGIN_X, ORIGIN_Y);
    const b = toPx(4000, 0);
    const world = toMm(b.x, b.y);

    expect(Math.round(world.xMm)).toBe(4000);
    expect(Math.round(world.yMm)).toBe(0);

    const zoomedView = { ...view, zoom: view.zoom * 1.25 };
    expect(toMm(b.x, b.y)).toEqual(world);
    expect(makeToMm(BASE_PX_PER_MM, zoomedView, ORIGIN_X, ORIGIN_Y)(b.x, b.y)).not.toEqual(world);
    expect(snapToGrid(124, 50)).toBe(100);
    expect(gridStepMm(BASE_PX_PER_MM * view.zoom)).toBeGreaterThanOrEqual(50);
  });

  it("shows a loading state, then the room name and catalog once loaded", async () => {
    vi.mocked(RoomApi.getPlacements).mockResolvedValue([]);
    renderPage();
    expect(screen.getByText(/loading/i)).toBeInTheDocument();
    expect(await screen.findByRole("heading", { name: /living room — furniture/i })).toBeInTheDocument();
    expect(screen.getByText("Sofa")).toBeInTheDocument();
    expect(LevelApi.getGeometry).toHaveBeenCalledWith(mockRoom.levelId);
  });

  it("shows an error message when the room fails to load", async () => {
    vi.mocked(RoomApi.get).mockRejectedValue({ message: "Failed to load room" });
    vi.mocked(RoomApi.getPlacements).mockResolvedValue([]);
    renderPage();
    expect(await screen.findByText("Failed to load room")).toBeInTheDocument();
  });

  it("adds a product from the catalog to the room", async () => {
    const user = userEvent.setup();
    vi.mocked(RoomApi.getPlacements).mockResolvedValue([]);
    renderPage();
    await screen.findByText("Sofa");

    expect(inspector().getByText(/select a furniture item/i)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /add/i }));

    expect(await inspector().findByText("Sofa")).toBeInTheDocument();
    expect(inspector().getByRole("button", { name: /delete/i })).toBeInTheDocument();
  });

  it("selects a placed item and moves it by dragging", async () => {
    vi.mocked(RoomApi.getPlacements).mockResolvedValue([mockPlacement({ xMm: 500, yMm: 500 })]);
    renderPage();
    await screen.findByText("Sofa");

    const canvas = document.querySelector("canvas.furniture-canvas") as HTMLElement;
    const start = fittedPoint(500, 500);
    const end = fittedPoint(1150, 500);
    fireEvent.pointerDown(canvas, { clientX: start.x, clientY: start.y, button: 0 });
    fireEvent.pointerUp(canvas, { clientX: start.x, clientY: start.y, button: 0 });

    const xInput = () => inspector().getByLabelText(/x \(mm\)/i) as HTMLInputElement;
    expect(await inspector().findByText("Sofa")).toBeInTheDocument();
    const before = Number(xInput().value);

    drag(canvas, start, end);

    await waitFor(() => expect(Number(xInput().value)).toBeGreaterThan(before));
  });

  it("selects a placed item and rotates it by dragging the rotate handle", async () => {
    vi.mocked(RoomApi.getPlacements).mockResolvedValue([mockPlacement({ xMm: 500, yMm: 500, rotationAngle: 0 })]);
    renderPage();
    await screen.findByText("Sofa");

    const canvas = document.querySelector("canvas.furniture-canvas") as HTMLElement;
    const center = fittedPoint(500, 500);
    fireEvent.pointerDown(canvas, { clientX: center.x, clientY: center.y, button: 0 });
    fireEvent.pointerUp(canvas, { clientX: center.x, clientY: center.y, button: 0 });
    await inspector().findByText("Sofa");

    const scale = BASE_PX_PER_MM * fitView(BASE_PX_PER_MM, ROOM_BOUNDS, CANVAS_W, CANVAS_H).zoom;
    const handle = { x: center.x, y: center.y - (mockProduct.depthMm! / 2) * scale - 18 };
    const rotationInput = () => inspector().getByLabelText(/rotation/i) as HTMLInputElement;
    expect(Number(rotationInput().value)).toBe(0);

    drag(canvas, handle, { x: center.x + 100, y: center.y });

    await waitFor(() => expect(Number(rotationInput().value)).not.toBe(0));
  });

  it("deletes the selected placement", async () => {
    const user = userEvent.setup();
    vi.mocked(RoomApi.getPlacements).mockResolvedValue([mockPlacement({ xMm: 500, yMm: 500 })]);
    renderPage();
    await screen.findByText("Sofa");

    const canvas = document.querySelector("canvas.furniture-canvas") as HTMLElement;
    const start = fittedPoint(500, 500);
    fireEvent.pointerDown(canvas, { clientX: start.x, clientY: start.y, button: 0 });
    fireEvent.pointerUp(canvas, { clientX: start.x, clientY: start.y, button: 0 });
    await inspector().findByText("Sofa");

    await user.click(inspector().getByRole("button", { name: /^delete$/i }));
    expect(inspector().getByText(/select a furniture item/i)).toBeInTheDocument();
  });

  it("supports undo/redo of adding a product", async () => {
    const user = userEvent.setup();
    vi.mocked(RoomApi.getPlacements).mockResolvedValue([]);
    renderPage();
    await screen.findByText("Sofa");

    expect(screen.getByRole("button", { name: "Undo" })).toBeDisabled();
    await user.click(screen.getByRole("button", { name: /add/i }));
    expect(await inspector().findByText("Sofa")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Undo" }));
    expect(inspector().getByText(/select a furniture item/i)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Redo" }));
    expect(await inspector().findByText("Sofa")).toBeInTheDocument();
  });

  it("saves the furniture layout via the Save button", async () => {
    const user = userEvent.setup();
    vi.mocked(RoomApi.getPlacements).mockResolvedValue([]);
    const response: RoomPlacementsSaveResponse = {
      placements: [mockPlacement()],
      issues: [],
    };
    vi.mocked(RoomApi.savePlacements).mockResolvedValue(response);

    renderPage();
    await screen.findByText("Sofa");
    await user.click(screen.getByRole("button", { name: /add/i }));
    await inspector().findByText("Sofa");

    await user.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() =>
      expect(RoomApi.savePlacements).toHaveBeenCalledWith(
        mockRoom.id,
        expect.arrayContaining([
          expect.objectContaining({
            productId: mockProduct.id,
            xMm: 2000,
            yMm: 1000,
          }),
        ])
      )
    );
    expect(await screen.findByText("Saved")).toBeInTheDocument();
  });

  it("shows save-failed status and validation issues when saving is rejected", async () => {
    const user = userEvent.setup();
    vi.mocked(RoomApi.getPlacements).mockResolvedValue([]);
    vi.mocked(RoomApi.savePlacements).mockRejectedValue({
      message: "Invalid layout",
      issues: [{ severity: "WARNING", code: "FURNITURE_OUTSIDE_ROOM", message: "Sofa is outside the room" }],
    });

    renderPage();
    await screen.findByText("Sofa");
    await user.click(screen.getByRole("button", { name: /add/i }));
    await inspector().findByText("Sofa");

    await user.click(screen.getByRole("button", { name: "Save" }));

    expect(await screen.findByText("Save failed")).toBeInTheDocument();
    expect(screen.getByText(/layout validation/i)).toBeInTheDocument();
    expect(screen.getByText(/sofa is outside the room/i)).toBeInTheDocument();
  });

  it("renders actual room walls, dimensions, and proportional furniture sizes", async () => {
    vi.mocked(RoomApi.getPlacements).mockResolvedValue([mockPlacement({ xMm: 2000, yMm: 1000 })]);
    renderPage();
    await screen.findByText("Sofa");

    const a = fittedPoint(0, 0);
    const b = fittedPoint(4000, 0);
    const c = fittedPoint(4000, 2000);

    await waitFor(() => expect(hasSegment(a, b)).toBe(true));
    expect(hasSegment(b, c)).toBe(true);
    expect(fillTextIncludes("4000 mm")).toBe(true);
    expect(fillTextIncludes("2000 mm")).toBe(true);

    const wallWidthPx = b.x - a.x;
    const furnitureWidthPx = mockProduct.widthMm! * BASE_PX_PER_MM * fitView(BASE_PX_PER_MM, ROOM_BOUNDS, CANVAS_W, CANVAS_H).zoom;
    expect(furnitureWidthPx / wallWidthPx).toBeCloseTo(0.5);
    expect(latestFillRectWithSize(furnitureWidthPx, mockProduct.depthMm! * BASE_PX_PER_MM * fitView(BASE_PX_PER_MM, ROOM_BOUNDS, CANVAS_W, CANVAS_H).zoom)).toBeTruthy();
  });

  it("does not silently create a fake room when geometry is missing", async () => {
    vi.mocked(LevelApi.getGeometry).mockResolvedValue(emptyGeometry());
    vi.mocked(RoomApi.getPlacements).mockResolvedValue([]);

    renderPage();

    expect(await screen.findByText(/room geometry is not available/i)).toBeInTheDocument();
    expect(screen.getByText(/open floor canvas/i)).toBeInTheDocument();
    expect(document.querySelector("canvas.furniture-canvas")).toBeNull();
    expect(screen.queryByRole("button", { name: /add/i })).not.toBeInTheDocument();
  });
});



