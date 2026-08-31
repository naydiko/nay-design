import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import RoomFurniturePage from "./RoomFurniturePage";
import { mockPlacement, mockProduct, mockRoom, mockVendor } from "../test/fixtures";
import type { RoomPlacementsSaveResponse } from "../api/types";

vi.mock("../api/endpoints", () => ({
  RoomApi: { get: vi.fn(), getPlacements: vi.fn(), savePlacements: vi.fn() },
  ProductApi: { list: vi.fn(), get: vi.fn() },
  VendorApi: { list: vi.fn() },
}));

import { ProductApi, RoomApi, VendorApi } from "../api/endpoints";

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
    vi.mocked(RoomApi.get).mockResolvedValue(mockRoom);
    vi.mocked(VendorApi.list).mockResolvedValue([mockVendor]);
    vi.mocked(ProductApi.list).mockResolvedValue([mockProduct]);
  });

  it("shows a loading state, then the room name and catalog once loaded", async () => {
    vi.mocked(RoomApi.getPlacements).mockResolvedValue([]);
    renderPage();
    expect(screen.getByText(/loading/i)).toBeInTheDocument();
    expect(await screen.findByRole("heading", { name: /living room — furniture/i })).toBeInTheDocument();
    expect(screen.getByText("Sofa")).toBeInTheDocument();
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
    // Placement center at (500,500)mm maps to (30 + 500*0.15, 30 + 500*0.15) = (105, 105)px.
    fireEvent.pointerDown(canvas, { clientX: 105, clientY: 105, button: 0 });
    fireEvent.pointerUp(canvas, { clientX: 105, clientY: 105, button: 0 });

    const xInput = () => inspector().getByLabelText(/x \(mm\)/i) as HTMLInputElement;
    expect(await inspector().findByText("Sofa")).toBeInTheDocument();
    const before = Number(xInput().value);

    drag(canvas, { x: 105, y: 105 }, { x: 205, y: 105 });

    await waitFor(() => expect(Number(xInput().value)).toBeGreaterThan(before));
  });

  it("selects a placed item and rotates it by dragging the rotate handle", async () => {
    vi.mocked(RoomApi.getPlacements).mockResolvedValue([mockPlacement({ xMm: 500, yMm: 500, rotationAngle: 0 })]);
    renderPage();
    await screen.findByText("Sofa");

    const canvas = document.querySelector("canvas.furniture-canvas") as HTMLElement;
    fireEvent.pointerDown(canvas, { clientX: 105, clientY: 105, button: 0 });
    fireEvent.pointerUp(canvas, { clientX: 105, clientY: 105, button: 0 });
    await inspector().findByText("Sofa");

    // Rotate handle sits above the (unrotated) item's center, at
    // center.y - ((depthMm/2)*scale + 18) = 105 - (67.5 + 18) = 19.5px.
    const rotationInput = () => inspector().getByLabelText(/rotation/i) as HTMLInputElement;
    expect(Number(rotationInput().value)).toBe(0);

    drag(canvas, { x: 105, y: 20 }, { x: 205, y: 105 });

    await waitFor(() => expect(Number(rotationInput().value)).not.toBe(0));
  });

  it("deletes the selected placement", async () => {
    const user = userEvent.setup();
    vi.mocked(RoomApi.getPlacements).mockResolvedValue([mockPlacement({ xMm: 500, yMm: 500 })]);
    renderPage();
    await screen.findByText("Sofa");

    const canvas = document.querySelector("canvas.furniture-canvas") as HTMLElement;
    fireEvent.pointerDown(canvas, { clientX: 105, clientY: 105, button: 0 });
    fireEvent.pointerUp(canvas, { clientX: 105, clientY: 105, button: 0 });
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

    await waitFor(() => expect(RoomApi.savePlacements).toHaveBeenCalled());
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
});



