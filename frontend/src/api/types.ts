// API DTO types matching the backend's request/response DTOs.
// Kept intentionally close to the backend shape so the geometry document
// can be sent/received without transformation.

export type UUID = string;

// ---- Auth ----
export interface RegisterRequest {
  email: string;
  displayName: string;
  firstName?: string;
  lastName?: string;
  phoneNumber?: string;
  password: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export type UserRole = "CLIENT" | "DESIGNER" | "VENDOR" | "ADMIN";
export type UserStatus = "ACTIVE" | "INACTIVE" | "SUSPENDED";

export interface UserResponse {
  id: UUID;
  email: string;
  displayName: string;
  firstName?: string;
  lastName?: string;
  phoneNumber?: string;
  role: UserRole;
  status: UserStatus;
  createdAt: string;
  updatedAt: string;
}

export interface AuthResponse {
  token: string;
  tokenType: string;
  user: UserResponse;
}

// ---- Projects ----
export type ProjectType =
  | "RESIDENTIAL"
  | "COMMERCIAL"
  | "RENOVATION"
  | "NEW_BUILD"
  | "OUTDOOR"
  | "OTHER";
export type ProjectStatus = "DRAFT" | "ACTIVE" | "ARCHIVED" | "COMPLETED";

export interface CreateProjectRequest {
  ownerId: UUID;
  name: string;
  description?: string;
  projectType: ProjectType;
  budgetMin?: number;
  budgetMax?: number;
  currency?: string;
}

export interface UpdateProjectRequest extends CreateProjectRequest {}

export interface ProjectResponse {
  id: UUID;
  ownerId: UUID;
  name: string;
  description?: string;
  projectType: ProjectType;
  status: ProjectStatus;
  budgetMin?: number;
  budgetMax?: number;
  currency?: string;
  createdAt: string;
  updatedAt: string;
}

// ---- Levels ----
export interface CreateLevelRequest {
  name: string;
  elevationMm?: number;
  orderIndex?: number;
}

export interface UpdateLevelRequest {
  name?: string;
  elevationMm?: number;
  orderIndex?: number;
  visible?: boolean;
}

export interface LevelResponse {
  id: UUID;
  projectId: UUID;
  name: string;
  elevationMm?: number;
  orderIndex?: number;
  visible: boolean;
  minXMm?: number;
  minYMm?: number;
  maxXMm?: number;
  maxYMm?: number;
  createdAt: string;
  updatedAt: string;
}

// ---- Geometry ----
export type WallKind = "INTERIOR" | "EXTERIOR" | "LOAD_BEARING" | "PARTITION";
export type OpeningType = "DOOR" | "WINDOW" | "ARCHWAY";
export type OpeningDirection = "IN" | "OUT";
export type OpeningSwing = "LEFT" | "RIGHT";
export type RoomType =
  | "LIVING_ROOM"
  | "BEDROOM"
  | "KITCHEN"
  | "BATHROOM"
  | "HALLWAY"
  | "OFFICE"
  | "OTHER";

export interface NodeDto {
  id: UUID | null;
  xMm: number;
  yMm: number;
  zMm: number;
}

export interface WallDto {
  id: UUID | null;
  startNodeId: UUID;
  endNodeId: UUID;
  thicknessMm: number;
  heightMm: number;
  kind: WallKind;
}

export interface OpeningDto {
  id: UUID | null;
  wallId: UUID;
  type: OpeningType;
  offsetFromStartMm: number;
  widthMm: number;
  heightMm: number;
  sillHeightMm?: number;
  direction?: OpeningDirection;
  swing?: OpeningSwing;
}

export interface RoomGeometryDto {
  id: UUID | null;
  name: string;
  type: RoomType;
}

export interface RoomWallDto {
  roomId: UUID;
  wallId: UUID;
}

export interface LevelGeometryRequest {
  nodes: NodeDto[];
  walls: WallDto[];
  openings: OpeningDto[];
  rooms: RoomGeometryDto[];
  roomWalls: RoomWallDto[];
}

export interface LevelGeometryResponse {
  levelId: UUID;
  nodes: (NodeDto & { id: UUID })[];
  walls: (WallDto & { id: UUID })[];
  openings: (OpeningDto & { id: UUID })[];
  rooms: (RoomGeometryDto & { id: UUID })[];
  roomWalls: RoomWallDto[];
}

export interface ApiError {
  message: string;
  status?: number;
}

