import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import LevelCanvasPage from "./LevelCanvasPage";
import { emptyGeometry, mockLevel } from "../test/fixtures";
import type { LevelGeometryResponse } from "../api/types";

vi.mock("../api/endpoints", () => ({
  LevelApi: { get: vi.fn(), getGeometry: vi.fn(), saveGeometry: vi.fn() },
}));

import { LevelApi } from "../api/endpoints";

function renderPage() {
  return render(
    <MemoryRouter initialEntries={[`/levels/${mockLevel.id}`]}>
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

describe("LevelCanvasPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(LevelApi.get).mockResolvedValue(mockLevel);
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





