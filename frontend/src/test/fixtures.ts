// Shared fixtures for page-level tests. Kept intentionally minimal: only
// the fields each page actually reads.
import type {
  FurniturePlacementResponse,
  LevelGeometryResponse,
  LevelResponse,
  ProductResponse,
  ProjectResponse,
  RoomResponse,
  UserResponse,
  VendorResponse,
} from "../api/types";

export const mockUser: UserResponse = {
  id: "user-1",
  email: "alice@example.com",
  displayName: "Alice",
  role: "CLIENT",
  status: "ACTIVE",
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

export const mockProject: ProjectResponse = {
  id: "project-1",
  ownerId: mockUser.id,
  name: "My House",
  projectType: "RESIDENTIAL",
  status: "DRAFT",
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

export const mockLevel: LevelResponse = {
  id: "level-1",
  projectId: mockProject.id,
  name: "Ground floor",
  visible: true,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

export const mockRoom: RoomResponse = {
  id: "room-1",
  levelId: mockLevel.id,
  name: "Living room",
  type: "LIVING_ROOM",
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

export const mockVendor: VendorResponse = {
  id: "vendor-1",
  name: "Acme Furniture",
  status: "ACTIVE",
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

export const mockProduct: ProductResponse = {
  id: "product-1",
  vendorId: mockVendor.id,
  name: "Sofa",
  category: "Seating",
  widthMm: 2000,
  depthMm: 900,
  heightMm: 800,
  priceAmount: 999,
  priceCurrency: "USD",
  status: "ACTIVE",
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

export function emptyGeometry(): LevelGeometryResponse {
  return { levelId: mockLevel.id, nodes: [], walls: [], openings: [], rooms: [], roomWalls: [] };
}

export function mockRoomGeometry(): LevelGeometryResponse {
  return {
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
    rooms: [{ id: mockRoom.id, name: mockRoom.name, type: mockRoom.type }],
    roomWalls: [
      { roomId: mockRoom.id, wallId: "wall-1" },
      { roomId: mockRoom.id, wallId: "wall-2" },
      { roomId: mockRoom.id, wallId: "wall-3" },
      { roomId: mockRoom.id, wallId: "wall-4" },
    ],
  };
}

export function mockPlacement(overrides: Partial<FurniturePlacementResponse> = {}): FurniturePlacementResponse {
  return {
    id: "placement-1",
    roomId: mockRoom.id,
    productId: mockProduct.id,
    xMm: 500,
    yMm: 500,
    zMm: 0,
    rotationAngle: 0,
    scale: 1,
    locked: false,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
    ...overrides,
  };
}

