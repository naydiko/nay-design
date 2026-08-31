// API endpoint helper functions, grouped by backend resource.
import { api } from "./client";
import type {
  AuthResponse,
  ChangePasswordRequest,
  CreateLevelRequest,
  CreateProjectRequest,
  CreateRoomRequest,
  ForgotPasswordRequest,
  FurniturePlacementRequest,
  FurniturePlacementResponse,
  GoogleLoginRequest,
  LevelGeometryRequest,
  LevelGeometryResponse,
  LevelResponse,
  LoginRequest,
  MessageResponse,
  ProductResponse,
  ProjectResponse,
  RegisterRequest,
  ResetPasswordRequest,
  RoomPlacementsSaveResponse,
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
  loginWithGoogle: (body: GoogleLoginRequest) => api.post<AuthResponse>("/api/auth/google", body),
  me: () => api.get<UserResponse>("/api/me"),
  changePassword: (body: ChangePasswordRequest) =>
    api.post<MessageResponse>("/api/auth/change-password", body),
  forgotPassword: (body: ForgotPasswordRequest) =>
    api.post<MessageResponse>("/api/auth/forgot-password", body),
  resetPassword: (body: ResetPasswordRequest) =>
    api.post<MessageResponse>("/api/auth/reset-password", body),
  verifyEmail: (token: string) =>
    api.get<MessageResponse>(`/api/auth/verify-email?token=${encodeURIComponent(token)}`),
};

export const ProjectApi = {
  list: () => api.get<ProjectResponse[]>("/api/projects"),
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
   * walls/furniture, blocked doors) via the response body's `issues` —
   * the save itself is never rejected for these.
   */
  savePlacements: (roomId: UUID, body: FurniturePlacementRequest[]) =>
    api.put<RoomPlacementsSaveResponse>(`/api/rooms/${roomId}/placements`, body),
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

