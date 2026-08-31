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

export type UserRole = "CLIENT" | "DESIGNER" | "ADMIN";
export type UserStatus = "ACTIVE" | "DISABLED" | "INVITED";

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
export type ProjectStatus = "DRAFT" | "ACTIVE" | "IN_REVIEW" | "COMPLETED" | "ARCHIVED";

export interface CreateProjectRequest {
  ownerId: UUID;
  name: string;
  description?: string;
  projectType: ProjectType;
  budgetMin?: number;
  budgetMax?: number;
  currency?: string;
}

// NOTE: unlike CreateProjectRequest, the backend's UpdateProjectRequest does
// not accept ownerId (ownership isn't reassignable via this endpoint) and
// requires an explicit status.
export interface UpdateProjectRequest {
  name: string;
  description?: string;
  projectType: ProjectType;
  status: ProjectStatus;
  budgetMin?: number;
  budgetMax?: number;
  currency?: string;
}

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

// NOTE: despite being PATCH, the backend's UpdateLevelRequest is a full
// replace: name/orderIndex/visible are required, only elevationMm is optional.
export interface UpdateLevelRequest {
  name: string;
  elevationMm?: number;
  orderIndex: number;
  visible: boolean;
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
export type OpeningDirection = "LEFT" | "RIGHT";
export type OpeningSwing = "IN" | "OUT" | "SLIDING" | "FIXED" | "NONE";
export type RoomType =
  | "LIVING_ROOM"
  | "BEDROOM"
  | "KITCHEN"
  | "DINING_ROOM"
  | "BATHROOM"
  | "OFFICE"
  | "CHILDREN_ROOM"
  | "HALLWAY"
  | "OUTDOOR"
  | "OTHER";
export type CeilingType = "FLAT" | "SUSPENDED" | "COFFERED" | "VAULTED" | "TRAY" | "EXPOSED" | "OTHER";

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
  /** Non-blocking Geometry Engine findings from the most recent save (e.g. a room not yet closed). Absent on plain reads. */
  warnings?: string[];
}

export interface ApiFieldError {
  field: string;
  message: string;
}

export interface ApiError {
  message: string;
  status?: number;
  /** Field-level details (bean validation errors, or Geometry Engine issues: field = issue code). */
  fieldErrors?: ApiFieldError[];
}

// ---- Rooms ----
export interface CreateRoomRequest {
  name: string;
  type: RoomType;
  floorFinish?: string;
  wallFinish?: string;
  ceilingFinish?: string;
  ceilingType?: CeilingType;
  ceilingHeightMm?: number;
}

export interface UpdateRoomRequest extends CreateRoomRequest {}

export interface RoomResponse {
  id: UUID;
  levelId: UUID;
  name: string;
  type: RoomType;
  floorFinish?: string;
  wallFinish?: string;
  ceilingFinish?: string;
  ceilingType?: CeilingType;
  ceilingHeightMm?: number;
  createdAt: string;
  updatedAt: string;
}

// ---- Vendors ----
export type VendorStatus = "ACTIVE" | "INACTIVE" | "PENDING_REVIEW";

export interface VendorResponse {
  id: UUID;
  name: string;
  country?: string;
  website?: string;
  logoUrl?: string;
  status: VendorStatus;
  createdAt: string;
  updatedAt: string;
}

// ---- Products ----
export type ProductStatus = "DRAFT" | "ACTIVE" | "DISCONTINUED" | "ARCHIVED";

export interface ProductResponse {
  id: UUID;
  vendorId: UUID;
  externalId?: string;
  name: string;
  sku?: string;
  category: string;
  collection?: string;
  style?: string;
  material?: string;
  color?: string;
  widthMm?: number;
  depthMm?: number;
  heightMm?: number;
  weightGrams?: number;
  priceAmount?: number;
  priceCurrency?: string;
  status: ProductStatus;
  primaryImageUrl?: string;
  createdAt: string;
  updatedAt: string;
}

// ---- Furniture placements ----
export interface FurniturePlacementRequest {
  id: UUID | null;
  productId: UUID;
  xMm: number;
  yMm: number;
  zMm: number;
  rotationAngle: number;
  scale: number;
  locked: boolean;
}

export interface FurniturePlacementResponse {
  id: UUID;
  roomId: UUID;
  productId: UUID;
  xMm: number;
  yMm: number;
  zMm: number;
  rotationAngle: number;
  scale: number;
  locked: boolean;
  createdAt: string;
  updatedAt: string;
}

