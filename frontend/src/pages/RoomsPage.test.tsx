import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { LevelApi, ProductApi, RoomApi, VendorApi } from "../api/endpoints";
import { emptyGeometry, mockLevel, mockProduct, mockRoom, mockRoomGeometry, mockVendor } from "../test/fixtures";
import type { LevelGeometryResponse, RoomResponse } from "../api/types";
import LevelCanvasPage from "./LevelCanvasPage";
import RoomFurniturePage from "./RoomFurniturePage";
import RoomsPage from "./RoomsPage";

vi.mock("../api/endpoints", () => ({
  LevelApi: { get: vi.fn(), getGeometry: vi.fn(), saveGeometry: vi.fn() },
  RoomApi: {
    listByLevel: vi.fn(),
    get: vi.fn(),
    getPlacements: vi.fn(),
    savePlacements: vi.fn(),
    createFromBoundary: vi.fn(),
    remove: vi.fn(),
  },
  ProductApi: { list: vi.fn(), get: vi.fn() },
  VendorApi: { list: vi.fn() },
}));

const roomTwo: RoomResponse = {
  ...mockRoom,
  id: "room-2",
  name: "Room 2",
};

function makeCanvasContext() {
  const fn = vi.fn();
  return {
    clearRect: fn,
    fillRect: fn,
    strokeRect: fn,
    beginPath: fn,
    closePath: fn,
    moveTo: fn,
    lineTo: fn,
    stroke: fn,
    fill: fn,
    arc: fn,
    fillText: fn,
    setLineDash: fn,
    save: fn,
    restore: fn,
    translate: fn,
    rotate: fn,
    strokeStyle: "",
    fillStyle: "",
    lineWidth: 1,
    font: "",
    textAlign: "start",
  } as unknown as CanvasRenderingContext2D;
}

function RouteProbe() {
  const location = useLocation();
  return <div>Current route: {location.pathname + location.search}</div>;
}

function renderRoutes(initialPath = `/levels/${mockLevel.id}/rooms`) {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <Routes>
        <Route path="/levels/:levelId/rooms" element={<><RouteProbe /><RoomsPage /></>} />
        <Route path="/levels/:levelId" element={<><RouteProbe /><LevelCanvasPage /></>} />
        <Route path="/rooms/:roomId" element={<RoomFurniturePage />} />
      </Routes>
    </MemoryRouter>
  );
}

function click(canvas: HTMLElement, x: number, y: number) {
  fireEvent.pointerDown(canvas, { clientX: x, clientY: y, button: 0 });
  fireEvent.pointerUp(canvas, { clientX: x, clientY: y, button: 0 });
}

describe("RoomsPage routing", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockReturnValue(makeCanvasContext());
    vi.mocked(LevelApi.get).mockResolvedValue(mockLevel);
    vi.mocked(LevelApi.getGeometry).mockResolvedValue(mockRoomGeometry());
    vi.mocked(RoomApi.listByLevel).mockResolvedValue([mockRoom]);
    vi.mocked(RoomApi.get).mockResolvedValue(mockRoom);
    vi.mocked(RoomApi.getPlacements).mockResolvedValue([]);
    vi.mocked(RoomApi.createFromBoundary).mockResolvedValue({ ...mockRoom, id: "new-room", name: "New room" });
    vi.mocked(RoomApi.remove).mockResolvedValue(undefined);
    vi.mocked(VendorApi.list).mockResolvedValue([mockVendor]);
    vi.mocked(ProductApi.list).mockResolvedValue([mockProduct]);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("renders Locate/Edit and Furniture actions without an empty-room creation form", async () => {
    renderRoutes();

    expect(await screen.findByText(mockRoom.name)).toBeInTheDocument();
    expect(screen.getByText("Boundary: Defined")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Locate/Edit on canvas" })).toHaveAttribute(
      "href",
      `/levels/${mockLevel.id}?roomId=${mockRoom.id}`
    );
    expect(screen.getByRole("link", { name: "Furniture" })).toHaveAttribute("href", `/rooms/${mockRoom.id}`);
    expect(screen.queryByRole("button", { name: "Create room" })).not.toBeInTheDocument();
  });

  it("shows Furniture unavailable when a room has no usable persisted boundary", async () => {
    vi.mocked(LevelApi.getGeometry).mockResolvedValue({
      ...mockRoomGeometry(),
      roomWalls: [],
    });

    renderRoutes();

    expect(await screen.findByText(mockRoom.name)).toBeInTheDocument();
    expect(screen.getByText("Boundary: Not defined")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Furniture" })).not.toBeInTheDocument();
    expect(screen.getByText("Furniture unavailable")).toBeInTheDocument();
  });

  it("deletes a room after confirmation and refreshes the list", async () => {
    const user = userEvent.setup();
    vi.spyOn(window, "confirm").mockReturnValue(true);
    vi.mocked(RoomApi.listByLevel).mockResolvedValueOnce([mockRoom]).mockResolvedValueOnce([]);

    renderRoutes();
    await screen.findByText(mockRoom.name);

    await user.click(screen.getByRole("button", { name: "Delete" }));

    await waitFor(() => expect(RoomApi.remove).toHaveBeenCalledWith(mockRoom.id));
    expect(await screen.findByText(/no rooms have been defined yet/i)).toBeInTheDocument();
  });

  it("navigates Locate/Edit to the one Level Canvas with room context", async () => {
    const user = userEvent.setup();
    renderRoutes();

    await user.click(await screen.findByRole("link", { name: "Locate/Edit on canvas" }));

    expect(await screen.findByText(`Current route: /levels/${mockLevel.id}?roomId=${mockRoom.id}`)).toBeInTheDocument();
    expect(await screen.findByText(`Located room: ${mockRoom.name}`)).toBeInTheDocument();
  });

  it("navigates Furniture to RoomFurniturePage and renders catalog UI", async () => {
    const user = userEvent.setup();
    renderRoutes();

    await user.click(await screen.findByRole("link", { name: "Furniture" }));

    expect(await screen.findByRole("heading", { name: /living room.*furniture/i })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Catalog" })).toBeInTheDocument();
    expect(screen.getByText("Sofa")).toBeInTheDocument();
  });

  it("supports direct navigation to RoomFurniturePage", async () => {
    renderRoutes(`/rooms/${mockRoom.id}`);

    expect(await screen.findByRole("heading", { name: /living room.*furniture/i })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Catalog" })).toBeInTheDocument();
  });

  it("keeps the rooms route valid when no rooms exist", async () => {
    vi.mocked(LevelApi.getGeometry).mockResolvedValue(emptyGeometry());
    vi.mocked(RoomApi.listByLevel).mockResolvedValue([]);

    renderRoutes();

    await waitFor(() => expect(LevelApi.get).toHaveBeenCalledWith(mockLevel.id));
    expect(await screen.findByText(/no rooms have been defined yet/i)).toBeInTheDocument();
  });

  it("shows two adjacent shared-wall rooms as immediately usable by Furniture", async () => {
    const initialGeometry: LevelGeometryResponse = {
      levelId: mockLevel.id,
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
        { id: mockRoom.id, name: mockRoom.name, type: mockRoom.type },
        { id: roomTwo.id, name: roomTwo.name, type: roomTwo.type },
      ],
      roomWalls: [
        { roomId: mockRoom.id, wallId: "ab" },
        { roomId: mockRoom.id, wallId: "be" },
        { roomId: mockRoom.id, wallId: "ed" },
        { roomId: mockRoom.id, wallId: "da" },
        { roomId: roomTwo.id, wallId: "bc" },
        { roomId: roomTwo.id, wallId: "cf" },
        { roomId: roomTwo.id, wallId: "fe" },
        { roomId: roomTwo.id, wallId: "be" },
      ],
    };
    vi.mocked(LevelApi.getGeometry).mockResolvedValue(initialGeometry);
    vi.mocked(RoomApi.listByLevel).mockResolvedValue([mockRoom, roomTwo]);

    renderRoutes();
    expect(await screen.findByText(mockRoom.name)).toBeInTheDocument();
    expect(screen.getByText(roomTwo.name)).toBeInTheDocument();
    expect(screen.getAllByText("Boundary: Defined")).toHaveLength(2);
    expect(screen.getAllByRole("link", { name: "Furniture" })).toHaveLength(2);
    expect(initialGeometry.roomWalls.filter((roomWall) => roomWall.wallId === "be")).toHaveLength(2);
  });

  it("defines two rooms in one canvas session and opens each room in Furniture", async () => {
    const user = userEvent.setup();
    let geometry: LevelGeometryResponse = {
      levelId: mockLevel.id,
      nodes: [
        { id: "a", xMm: -3000, yMm: -1000, zMm: 0 },
        { id: "b", xMm: 0, yMm: -1000, zMm: 0 },
        { id: "c", xMm: 3000, yMm: -1000, zMm: 0 },
        { id: "d", xMm: -3000, yMm: 1000, zMm: 0 },
        { id: "e", xMm: 0, yMm: 1000, zMm: 0 },
        { id: "f", xMm: 3000, yMm: 1000, zMm: 0 },
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
      rooms: [],
      roomWalls: [],
    };
    const rooms: RoomResponse[] = [];
    vi.mocked(LevelApi.getGeometry).mockImplementation(async () => geometry);
    vi.mocked(LevelApi.saveGeometry).mockImplementation(async (_levelId, request) => {
      geometry = {
        ...geometry,
        nodes: request.nodes.map((node) => ({ ...node, id: node.id! })),
        walls: request.walls.map((wall) => ({ ...wall, id: wall.id! })),
        openings: request.openings.map((opening) => ({ ...opening, id: opening.id! })),
        issues: [],
      };
      return geometry;
    });
    vi.mocked(RoomApi.listByLevel).mockImplementation(async () => rooms);
    vi.mocked(RoomApi.createFromBoundary).mockImplementation(async (_levelId, request) => {
      const room: RoomResponse = {
        ...mockRoom,
        id: request.name === "Living" ? "living" : "kitchen",
        name: request.name,
        type: request.type,
      };
      rooms.push(room);
      geometry = {
        ...geometry,
        rooms: [...geometry.rooms, { id: room.id, name: room.name, type: room.type }],
        roomWalls: [
          ...geometry.roomWalls,
          ...request.wallIds.map((wallId) => ({ roomId: room.id, wallId })),
        ],
      };
      return room;
    });
    vi.mocked(RoomApi.get).mockImplementation(async (roomId) => rooms.find((room) => room.id === roomId)!);

    const rendered = renderRoutes(`/levels/${mockLevel.id}`);
    await screen.findByRole("heading", { name: /ground floor.*canvas/i });
    await user.click(screen.getByRole("button", { name: "Define room" }));
    const canvas = document.querySelector("canvas.floor-canvas") as HTMLElement;

    click(canvas, 300, 350);
    let dialog = await screen.findByRole("dialog", { name: "Define room" });
    await user.type(within(dialog).getByLabelText("Name"), "Living");
    await user.click(within(dialog).getByRole("button", { name: "Create" }));
    await waitFor(() => expect(RoomApi.createFromBoundary).toHaveBeenCalledTimes(1));

    click(canvas, 800, 350);
    dialog = await screen.findByRole("dialog", { name: "Define room" });
    await user.type(within(dialog).getByLabelText("Name"), "Kitchen");
    await user.selectOptions(within(dialog).getByLabelText("Type"), "KITCHEN");
    await user.click(within(dialog).getByRole("button", { name: "Create" }));
    await waitFor(() => expect(RoomApi.createFromBoundary).toHaveBeenCalledTimes(2));
    expect(screen.getByRole("button", { name: "Define room" })).toHaveClass("active");

    await user.click(screen.getByRole("link", { name: /rooms & furniture/i }));
    expect(await screen.findByText("Living")).toBeInTheDocument();
    expect(screen.getByText("Kitchen")).toBeInTheDocument();
    expect(screen.getAllByText("Boundary: Defined")).toHaveLength(2);

    const sharedLinks = geometry.roomWalls.filter((roomWall) => roomWall.wallId === "be");
    expect(sharedLinks.map((roomWall) => roomWall.roomId).sort()).toEqual(["kitchen", "living"]);

    rendered.unmount();
    const livingRendered = renderRoutes("/rooms/living");
    expect(await screen.findByRole("heading", { name: /living.*furniture/i })).toBeInTheDocument();
    expect(screen.queryByText(/room geometry is not available/i)).not.toBeInTheDocument();

    livingRendered.unmount();
    renderRoutes("/rooms/kitchen");
    expect(await screen.findByRole("heading", { name: /kitchen.*furniture/i })).toBeInTheDocument();
    expect(screen.queryByText(/room geometry is not available/i)).not.toBeInTheDocument();
  });
});
