// API endpoint helper functions, grouped by backend resource.
import { api } from "./client";
import type {
  AuthResponse,
  CreateLevelRequest,
  CreateProjectRequest,
  LevelGeometryRequest,
  LevelGeometryResponse,
  LevelResponse,
  LoginRequest,
  ProjectResponse,
  RegisterRequest,
  UpdateLevelRequest,
  UpdateProjectRequest,
  UserResponse,
  UUID,
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

