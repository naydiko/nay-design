// API endpoint helper functions, grouped by backend resource.
import { api } from "./client";
import type {
  AuthResponse,
  CreateLevelRequest,
  CreateProjectRequest,
  CreateRoomRequest,
  FurniturePlacementRequest,
  FurniturePlacementResponse,
  LevelGeometryRequest,
  LevelGeometryResponse,
  LevelResponse,
  LoginRequest,
  ProductResponse,
  ProjectResponse,
  RegisterRequest,
  RoomResponse,
  UpdateLevelRequest,
  UpdateProjectRequest,
  UpdateRoomRequest,
  UserResponse,
  UUID,
  VendorResponse,
} from "./types";

export const AuthApi = {
  register: (body: RegisterRequest) => api.post<AuthResponse>("/api/auth/register", body),
  login: (body: LoginRequest) => api.post<AuthResponse>("/api/auth/login", body),
  me: () => api.get<UserResponse>("/api/me"),
};

export const ProjectApi = {
  list: (ownerId: UUID) =>
    api.get<ProjectResponse[]>(`/api/projects?ownerId=${encodeURIComponent(ownerId)}`),
  get: (id: UUID) => api.get<ProjectResponse>(`/api/projects/${id}`),
  create: (body: CreateProjectRequest) => api.post<ProjectResponse>("/api/projects", body),
  update: (id: UUID, body: UpdateProjectRequest) =>
    api.put<ProjectResponse>(`/api/projects/${id}`, body),
  remove: (id: UUID) => api.delete<void>(`/api/projects/${id}`),
};

export const LevelApi = {
  listByProject: (projectId: UUID) =>
    api.get<LevelResponse[]>(`/api/projects/${projectId}/levels`),
  get: (id: UUID) => api.get<LevelResponse>(`/api/levels/${id}`),
  create: (projectId: UUID, body: CreateLevelRequest) =>
    api.post<LevelResponse>(`/api/projects/${projectId}/levels`, body),
  update: (id: UUID, body: UpdateLevelRequest) =>
    api.patch<LevelResponse>(`/api/levels/${id}`, body),
  remove: (id: UUID) => api.delete<void>(`/api/levels/${id}`),
  getGeometry: (levelId: UUID) =>
    api.get<LevelGeometryResponse>(`/api/levels/${levelId}/geometry`),
  saveGeometry: (levelId: UUID, body: LevelGeometryRequest) =>
    api.put<LevelGeometryResponse>(`/api/levels/${levelId}/geometry`, body),
};

export const RoomApi = {
  listByLevel: (levelId: UUID) => api.get<RoomResponse[]>(`/api/levels/${levelId}/rooms`),
  get: (id: UUID) => api.get<RoomResponse>(`/api/rooms/${id}`),
  create: (levelId: UUID, body: CreateRoomRequest) =>
    api.post<RoomResponse>(`/api/levels/${levelId}/rooms`, body),
  update: (id: UUID, body: UpdateRoomRequest) => api.patch<RoomResponse>(`/api/rooms/${id}`, body),
  remove: (id: UUID) => api.delete<void>(`/api/rooms/${id}`),
  getPlacements: (roomId: UUID) =>
    api.get<FurniturePlacementResponse[]>(`/api/rooms/${roomId}/placements`),
  /**
   * Saves the complete furniture layout. Also surfaces any non-blocking
   * Geometry Engine findings (furniture outside the room, overlapping
   * walls/furniture, blocked doors) returned via the X-Geometry-Warnings
   * response header — the save itself is never rejected for these.
   */
  savePlacements: async (
    roomId: UUID,
    body: FurniturePlacementRequest[]
  ): Promise<{ placements: FurniturePlacementResponse[]; warnings: string[] }> => {
    const { data, response } = await api.putWithHeaders<FurniturePlacementResponse[]>(
      `/api/rooms/${roomId}/placements`,
      body
    );
    const header = response.headers.get("X-Geometry-Warnings");
    const warnings = header ? header.split(" | ").filter(Boolean) : [];
    return { placements: data, warnings };
  },
};

export const ProductApi = {
  list: (params?: { vendorId?: UUID; category?: string }) => {
    const search = new URLSearchParams();
    if (params?.vendorId) search.set("vendorId", params.vendorId);
    if (params?.category) search.set("category", params.category);
    const qs = search.toString();
    return api.get<ProductResponse[]>(`/api/products${qs ? `?${qs}` : ""}`);
  },
  get: (id: UUID) => api.get<ProductResponse>(`/api/products/${id}`),
};

export const VendorApi = {
  list: () => api.get<VendorResponse[]>("/api/vendors"),
  get: (id: UUID) => api.get<VendorResponse>(`/api/vendors/${id}`),
};

