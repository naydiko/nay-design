import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import LevelCanvasPage from "./LevelCanvasPage";
import { emptyGeometry, mockLevel, mockRoom } from "../test/fixtures";
import type { LevelGeometryRequest, LevelGeometryResponse } from "../api/types";

vi.mock("../api/endpoints", () => ({
  LevelApi: { get: vi.fn(), getGeometry: vi.fn(), saveGeometry: vi.fn() },
  RoomApi: { createFromBoundary: vi.fn() },
}));

import { LevelApi, RoomApi } from "../api/endpoints";

type DrawCommand = { name: string; args: unknown[] };

let drawCommands: DrawCommand[] = [];
let canvasContext: CanvasRenderingContext2D;
let getContextSpy: ReturnType<typeof vi.spyOn>;

function makeCanvasContext() {
  const record = (name: string) =>
    vi.fn((...args: unknown[]) => {
      drawCommands.push({ name, args });
    });
  return {
    clearRect: record("clearRect"),
    fillRect: record("fillRect"),
    beginPath: record("beginPath"),
    closePath: record("closePath"),
    moveTo: record("moveTo"),
    lineTo: record("lineTo"),
    stroke: record("stroke"),
    arc: record("arc"),
    fill: record("fill"),
    fillText: record("fillText"),
    setLineDash: record("setLineDash"),
    strokeStyle: "",
    fillStyle: "",
    lineWidth: 1,
    font: "",
  } as unknown as CanvasRenderingContext2D;
}

function renderPage(path = `/levels/${mockLevel.id}`) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/levels/:levelId" element={<LevelCanvasPage />} />
      </Routes>
    </MemoryRouter>
  );
}

/** Fires a full click (pointerdown+pointerup) on the canvas at the given
 * canvas-relative coordinates. jsdom's getBoundingClientRect() is all-zero
 * by default, so clientX/clientY map 1:1 to canvas-local pixels. */
function click(canvas: HTMLElement, x: number, y: number) {
  fireEvent.pointerDown(canvas, { clientX: x, clientY: y, button: 0 });
  fireEvent.pointerUp(canvas, { clientX: x, clientY: y, button: 0 });
}

function inspector() {
  return within(document.querySelector(".side-panel") as HTMLElement);
}

function approx(value: unknown, expected: number) {
  return typeof value === "number" && Math.abs(value - expected) < 0.1;
}

function isDashCommand(command: DrawCommand, dashed: boolean) {
  if (command.name !== "setLineDash") return false;
  const segments = command.args[0];
  return Array.isArray(segments) && (dashed ? segments.length > 0 : segments.length === 0);
}

function hasDashedSegment(fromX: number, fromY: number, toX: number, toY: number) {
  let dashed = false;
  let lastMove: unknown[] | null = null;
  for (const command of drawCommands) {
    if (isDashCommand(command, true)) {
      dashed = true;
      lastMove = null;
      continue;
    }
    if (isDashCommand(command, false)) {
      dashed = false;
      lastMove = null;
      continue;
    }
    if (!dashed) continue;
    if (command.name === "moveTo") {
      lastMove = command.args;
      continue;
    }
    if (
      command.name === "lineTo" &&
      lastMove &&
      approx(lastMove[0], fromX) &&
      approx(lastMove[1], fromY) &&
      approx(command.args[0], toX) &&
      approx(command.args[1], toY)
    ) {
      return true;
    }
  }
  return false;
}

function hasSegment(fromX: number, fromY: number, toX: number, toY: number) {
  let lastMove: unknown[] | null = null;
  for (const command of drawCommands) {
    if (command.name === "moveTo") {
      lastMove = command.args;
      continue;
    }
    if (
      command.name === "lineTo" &&
      lastMove &&
      approx(lastMove[0], fromX) &&
      approx(lastMove[1], fromY) &&
      approx(command.args[0], toX) &&
      approx(command.args[1], toY)
    ) {
      return true;
    }
  }
  return false;
}

function hasArcAt(x: number, y: number) {
  return drawCommands.some(
    (command) => command.name === "arc" && approx(command.args[0], x) && approx(command.args[1], y)
  );
}

function hasAnyDashedLine() {
  return drawCommands.some((command) => isDashCommand(command, true));
}

function mockSaveEcho() {
  vi.mocked(LevelApi.saveGeometry).mockImplementation(async (_levelId, request: LevelGeometryRequest) => ({
    levelId: mockLevel.id,
    nodes: request.nodes.map((node, index) => ({
      id: node.id ?? `node-${index + 1}`,
      xMm: node.xMm,
      yMm: node.yMm,
      zMm: node.zMm,
    })),
    walls: request.walls.map((wall, index) => ({
      id: wall.id ?? `wall-${index + 1}`,
      startNodeId: wall.startNodeId,
      endNodeId: wall.endNodeId,
      thicknessMm: wall.thicknessMm,
      heightMm: wall.heightMm,
      kind: wall.kind,
    })),
    openings: [],
    rooms: request.rooms.map((room, index) => ({
      id: room.id ?? `room-${index + 1}`,
      name: room.name,
      type: room.type,
    })),
    roomWalls: request.roomWalls,
    issues: [],
  }));
}

describe("LevelCanvasPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    drawCommands = [];
    canvasContext = makeCanvasContext();
    getContextSpy = vi
      .spyOn(HTMLCanvasElement.prototype, "getContext")
      .mockReturnValue(canvasContext);
    vi.mocked(LevelApi.get).mockResolvedValue(mockLevel);
    vi.mocked(RoomApi.createFromBoundary).mockResolvedValue(mockRoom);
  });

  afterEach(() => {
    getContextSpy.mockRestore();
  });

  it("shows a loading state, then the level name once geometry has loaded", async () => {
    vi.mocked(LevelApi.getGeometry).mockResolvedValue(emptyGeometry());
    renderPage();
    expect(screen.getByText(/loading/i)).toBeInTheDocument();
    expect(await screen.findByRole("heading", { name: /ground floor/i })).toBeInTheDocument();
    expect(LevelApi.getGeometry).toHaveBeenCalledWith(mockLevel.id);
  });

  it("shows an error message when the level fails to load", async () => {
    vi.mocked(LevelApi.get).mockRejectedValue({ message: "Failed to load level" });
    vi.mocked(LevelApi.getGeometry).mockResolvedValue(emptyGeometry());
    renderPage();
    expect(await screen.findByText("Failed to load level")).toBeInTheDocument();
  });

  it("draws a wall between two clicks with the wall tool, then selects and deletes it", async () => {
    const user = userEvent.setup();
    vi.mocked(LevelApi.getGeometry).mockResolvedValue(emptyGeometry());
    renderPage();
    await screen.findByRole("heading", { name: /ground floor/i });

    await user.click(screen.getByRole("button", { name: "Wall" }));
    const canvas = document.querySelector("canvas.floor-canvas") as HTMLElement;
    expect(canvas).toBeTruthy();

    click(canvas, 100, 100);
    click(canvas, 300, 100);

    // Switch to select and click the wall's midpoint to select it.
    await user.click(screen.getByRole("button", { name: "Select" }));
    click(canvas, 200, 100);

    expect(await inspector().findByText("Wall")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /delete wall/i })).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /delete wall/i }));
    expect(screen.queryByRole("button", { name: /delete wall/i })).not.toBeInTheDocument();
  });

  it("stores the first wall point and renders a snapped live preview while moving", async () => {
    const user = userEvent.setup();
    vi.mocked(LevelApi.getGeometry).mockResolvedValue(emptyGeometry());
    renderPage();
    await screen.findByRole("heading", { name: /ground floor/i });

    await user.click(screen.getByRole("button", { name: "Wall" }));
    const canvas = document.querySelector("canvas.floor-canvas") as HTMLElement;

    click(canvas, 550, 350);
    await waitFor(() => expect(hasArcAt(550, 350)).toBe(true));

    fireEvent.pointerMove(canvas, { clientX: 702, clientY: 351 });

    await waitFor(() => expect(hasDashedSegment(550, 350, 700, 350)).toBe(true));
    expect(LevelApi.saveGeometry).not.toHaveBeenCalled();
  });

  it("finishes a wall, clears the preview line, and continues from the previous endpoint", async () => {
    const user = userEvent.setup();
    vi.mocked(LevelApi.getGeometry).mockResolvedValue(emptyGeometry());
    renderPage();
    await screen.findByRole("heading", { name: /ground floor/i });

    await user.click(screen.getByRole("button", { name: "Wall" }));
    const canvas = document.querySelector("canvas.floor-canvas") as HTMLElement;

    click(canvas, 550, 350);
    fireEvent.pointerMove(canvas, { clientX: 702, clientY: 351 });
    await waitFor(() => expect(hasDashedSegment(550, 350, 700, 350)).toBe(true));

    drawCommands = [];
    click(canvas, 702, 351);

    await waitFor(() => expect(hasSegment(550, 350, 700, 350)).toBe(true));
    expect(hasAnyDashedLine()).toBe(false);

    drawCommands = [];
    fireEvent.pointerMove(canvas, { clientX: 700, clientY: 500 });

    await waitFor(() => expect(hasDashedSegment(700, 350, 700, 500)).toBe(true));
  });

  it("clears the wall draft after snapping the final segment back to the chain start", async () => {
    const user = userEvent.setup();
    vi.mocked(LevelApi.getGeometry).mockResolvedValue(emptyGeometry());
    mockSaveEcho();
    renderPage();
    await screen.findByRole("heading", { name: /ground floor/i });

    await user.click(screen.getByRole("button", { name: "Wall" }));
    const canvas = document.querySelector("canvas.floor-canvas") as HTMLElement;

    click(canvas, 550, 350);
    click(canvas, 700, 350);
    click(canvas, 700, 500);
    click(canvas, 550, 500);

    drawCommands = [];
    click(canvas, 552, 351);
    await waitFor(() => expect(hasSegment(550, 500, 550, 350)).toBe(true));
    expect(hasAnyDashedLine()).toBe(false);

    drawCommands = [];
    fireEvent.pointerMove(canvas, { clientX: 600, clientY: 400 });
    expect(hasAnyDashedLine()).toBe(false);

    await user.click(screen.getByRole("button", { name: "Save" }));
    await waitFor(() => expect(LevelApi.saveGeometry).toHaveBeenCalled());
    const finalRequest = vi.mocked(LevelApi.saveGeometry).mock.calls.at(-1)![1];
    expect(finalRequest.nodes).toHaveLength(4);
    expect(finalRequest.walls).toHaveLength(4);

    drawCommands = [];
    click(canvas, 850, 350);
    fireEvent.pointerMove(canvas, { clientX: 850, clientY: 500 });
    await waitFor(() => expect(hasDashedSegment(850, 350, 850, 500)).toBe(true));
  });

  it("closing a wall loop stays a normal geometry operation and does not open room creation", async () => {
    const user = userEvent.setup();
    vi.mocked(LevelApi.getGeometry).mockResolvedValue(emptyGeometry());
    mockSaveEcho();
    renderPage();
    await screen.findByRole("heading", { name: /ground floor/i });

    await user.click(screen.getByRole("button", { name: "Wall" }));
    const canvas = document.querySelector("canvas.floor-canvas") as HTMLElement;

    click(canvas, 550, 350);
    click(canvas, 1150, 350);
    click(canvas, 1150, 650);
    click(canvas, 550, 650);
    click(canvas, 552, 351);

    expect(screen.queryByRole("dialog", { name: "Define room" })).not.toBeInTheDocument();
    expect(RoomApi.createFromBoundary).not.toHaveBeenCalled();
  });

  it("Define room click inside a rectangle creates the room with the detected four walls", async () => {
    const user = userEvent.setup();
    vi.mocked(LevelApi.getGeometry).mockResolvedValue({
      levelId: mockLevel.id,
      nodes: [
        { id: "node-1", xMm: 0, yMm: 0, zMm: 0 },
        { id: "node-2", xMm: 4000, yMm: 0, zMm: 0 },
        { id: "node-3", xMm: 4000, yMm: 2000, zMm: 0 },
        { id: "node-4", xMm: 0, yMm: 2000, zMm: 0 },
      ],
      walls: [
        { id: "wall-1", startNodeId: "node-1", endNodeId: "node-2", thicknessMm: 150, heightMm: 2700, kind: "INTERIOR" },
        { id: "wall-2", startNodeId: "node-2", endNodeId: "node-3", thicknessMm: 150, heightMm: 2700, kind: "INTERIOR" },
        { id: "wall-3", startNodeId: "node-3", endNodeId: "node-4", thicknessMm: 150, heightMm: 2700, kind: "INTERIOR" },
        { id: "wall-4", startNodeId: "node-4", endNodeId: "node-1", thicknessMm: 150, heightMm: 2700, kind: "INTERIOR" },
      ],
      openings: [],
      rooms: [],
      roomWalls: [],
    });
    mockSaveEcho();
    renderPage();
    await screen.findByRole("heading", { name: /ground floor/i });
    await user.click(screen.getByRole("button", { name: "Define room" }));
    const canvas = document.querySelector("canvas.floor-canvas") as HTMLElement;
    click(canvas, 800, 500);

    const dialog = await screen.findByRole("dialog", { name: "Define room" });
    await user.type(within(dialog).getByLabelText("Name"), "Living Room");
    await user.click(within(dialog).getByRole("button", { name: "Create" }));

    await waitFor(() => expect(RoomApi.createFromBoundary).toHaveBeenCalledWith(mockLevel.id, {
      name: "Living Room",
      type: "LIVING_ROOM",
      wallIds: ["wall-1", "wall-2", "wall-3", "wall-4"],
    }));
    expect(screen.queryByRole("dialog", { name: "Define room" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Define room" })).toHaveClass("active");
  });

  it("clicking outside closed regions shows a non-destructive error and creates no room", async () => {
    const user = userEvent.setup();
    vi.mocked(LevelApi.getGeometry).mockResolvedValue(emptyGeometry());
    renderPage();
    await screen.findByRole("heading", { name: /ground floor/i });
    await user.click(screen.getByRole("button", { name: "Define room" }));
    click(document.querySelector("canvas.floor-canvas") as HTMLElement, 800, 500);

    expect(await screen.findByText("No closed room boundary found here.")).toBeInTheDocument();
    expect(RoomApi.createFromBoundary).not.toHaveBeenCalled();
    expect(screen.queryByRole("dialog", { name: "Define room" })).not.toBeInTheDocument();
  });

  it("cancels an unfinished continuous wall segment with Escape without deleting completed walls", async () => {
    const user = userEvent.setup();
    vi.mocked(LevelApi.getGeometry).mockResolvedValue(emptyGeometry());
    mockSaveEcho();
    renderPage();
    await screen.findByRole("heading", { name: /ground floor/i });

    await user.click(screen.getByRole("button", { name: "Wall" }));
    const canvas = document.querySelector("canvas.floor-canvas") as HTMLElement;

    click(canvas, 550, 350);
    click(canvas, 700, 350);
    fireEvent.pointerMove(canvas, { clientX: 700, clientY: 500 });
    await waitFor(() => expect(hasDashedSegment(700, 350, 700, 500)).toBe(true));

    fireEvent.keyDown(window, { key: "Escape" });
    await user.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => expect(LevelApi.saveGeometry).toHaveBeenCalled());
    const finalRequest = vi.mocked(LevelApi.saveGeometry).mock.calls.at(-1)![1];
    expect(finalRequest.nodes).toHaveLength(2);
    expect(finalRequest.walls).toHaveLength(1);
  });

  it("does not include an unfinished wall preview in the save payload", async () => {
    const user = userEvent.setup();
    vi.mocked(LevelApi.getGeometry).mockResolvedValue(emptyGeometry());
    vi.mocked(LevelApi.saveGeometry).mockResolvedValue(emptyGeometry());
    renderPage();
    await screen.findByRole("heading", { name: /ground floor/i });

    await user.click(screen.getByRole("button", { name: "Wall" }));
    const canvas = document.querySelector("canvas.floor-canvas") as HTMLElement;

    click(canvas, 550, 350);
    fireEvent.pointerMove(canvas, { clientX: 700, clientY: 350 });
    await waitFor(() => expect(hasDashedSegment(550, 350, 700, 350)).toBe(true));

    await user.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => expect(LevelApi.saveGeometry).toHaveBeenCalled());
    const request = vi.mocked(LevelApi.saveGeometry).mock.calls.at(-1)![1];
    expect(request.nodes).toHaveLength(0);
    expect(request.walls).toHaveLength(0);
    expect(request.openings).toHaveLength(0);
  });

  it("supports undo/redo of a wall-drawing action", async () => {
    const user = userEvent.setup();
    vi.mocked(LevelApi.getGeometry).mockResolvedValue(emptyGeometry());
    renderPage();
    await screen.findByRole("heading", { name: /ground floor/i });

    const undoBtn = () => screen.getByRole("button", { name: "Undo" });
    const redoBtn = () => screen.getByRole("button", { name: "Redo" });
    expect(undoBtn()).toBeDisabled();

    await user.click(screen.getByRole("button", { name: "Wall" }));
    const canvas = document.querySelector("canvas.floor-canvas") as HTMLElement;
    click(canvas, 100, 100);
    click(canvas, 300, 100);

    await waitFor(() => expect(undoBtn()).toBeEnabled());

    await user.click(screen.getByRole("button", { name: "Select" }));
    click(canvas, 200, 100);
    expect(await inspector().findByText("Wall")).toBeInTheDocument();

    await user.click(undoBtn());
    // The wall (and its endpoint node) are gone again, so nothing is selected.
    expect(inspector().getByText(/select a point, wall, door or window/i)).toBeInTheDocument();
    expect(redoBtn()).toBeEnabled();

    await user.click(redoBtn());
    click(canvas, 200, 100);
    expect(await inspector().findByText("Wall")).toBeInTheDocument();
  });

  it("saves geometry via the Save button and shows the saved status", async () => {
    const user = userEvent.setup();
    vi.mocked(LevelApi.getGeometry).mockResolvedValue(emptyGeometry());
    const saved: LevelGeometryResponse = {
      levelId: mockLevel.id,
      nodes: [
        { id: "n1", xMm: 0, yMm: 0, zMm: 0 },
        { id: "n2", xMm: 1000, yMm: 0, zMm: 0 },
      ],
      walls: [{ id: "w1", startNodeId: "n1", endNodeId: "n2", thicknessMm: 150, heightMm: 2700, kind: "INTERIOR" }],
      openings: [],
      rooms: [],
      roomWalls: [],
      issues: [],
    };
    vi.mocked(LevelApi.saveGeometry).mockResolvedValue(saved);

    renderPage();
    await screen.findByRole("heading", { name: /ground floor/i });

    await user.click(screen.getByRole("button", { name: "Wall" }));
    const canvas = document.querySelector("canvas.floor-canvas") as HTMLElement;
    click(canvas, 100, 100);
    click(canvas, 300, 100);

    await user.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => expect(LevelApi.saveGeometry).toHaveBeenCalled());
    expect(await screen.findByText("Saved")).toBeInTheDocument();
  });

  it("shows a save-failed status and geometry validation issues when saving is rejected", async () => {
    const user = userEvent.setup();
    vi.mocked(LevelApi.getGeometry).mockResolvedValue(emptyGeometry());
    vi.mocked(LevelApi.saveGeometry).mockRejectedValue({
      message: "Invalid geometry",
      issues: [{ severity: "ERROR", code: "WALL_ZERO_LENGTH", message: "Wall has zero length" }],
    });

    renderPage();
    await screen.findByRole("heading", { name: /ground floor/i });

    await user.click(screen.getByRole("button", { name: "Wall" }));
    const canvas = document.querySelector("canvas.floor-canvas") as HTMLElement;
    click(canvas, 100, 100);
    click(canvas, 300, 100);

    await user.click(screen.getByRole("button", { name: "Save" }));

    expect(await screen.findByText("Save failed")).toBeInTheDocument();
    expect(screen.getByText(/geometry validation/i)).toBeInTheDocument();
    expect(screen.getByText(/wall has zero length/i)).toBeInTheDocument();
  });
});





