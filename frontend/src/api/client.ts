// Thin fetch-based API client. Stores the JWT in localStorage and attaches
// it as a Bearer token on every request (Stage 1 - no refresh tokens).
import type { ApiError, ApiFieldError } from "./types";

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8090";

const TOKEN_KEY = "naydesign.token";
const USER_KEY = "naydesign.user";

// Paths that are intentionally public: a 401 from these means "bad
// credentials", not "your session expired" — never auto-redirect for them.
const PUBLIC_PATHS = ["/api/auth/login", "/api/auth/register"];

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string) {
  localStorage.setItem(TOKEN_KEY, token);
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
}

export function getStoredUser<T>(): T | null {
  const raw = localStorage.getItem(USER_KEY);
  return raw ? (JSON.parse(raw) as T) : null;
}

export function setStoredUser(user: unknown) {
  localStorage.setItem(USER_KEY, JSON.stringify(user));
}

function buildMessage(baseMessage: string, fieldErrors?: ApiFieldError[]): string {
  if (!fieldErrors || fieldErrors.length === 0) return baseMessage;
  const details = fieldErrors.map((f) => `${f.field}: ${f.message}`).join("; ");
  return `${baseMessage} — ${details}`;
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const { data } = await requestWithResponse<T>(path, options);
  return data;
}

/** Like {@link request}, but also returns the raw Response so callers can read headers. */
async function requestWithResponse<T>(
  path: string,
  options: RequestInit = {}
): Promise<{ data: T; response: Response }> {
  const token = getToken();
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    ...(options.headers as Record<string, string>),
  };
  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }

  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, { ...options, headers });
  } catch {
    throw { message: "Cannot reach the server. Is the backend running?" } satisfies ApiError;
  }

  if (response.status === 204) {
    return { data: undefined as T, response };
  }

  const text = await response.text();
  const data = text ? JSON.parse(text) : undefined;

  if (!response.ok) {
    const isPublicAuthPath = PUBLIC_PATHS.some((p) => path.startsWith(p));
    if (response.status === 401 && !isPublicAuthPath) {
      clearToken();
      if (typeof window !== "undefined" && !window.location.pathname.startsWith("/login")) {
        window.location.href = "/login";
      }
    }

    const fieldErrors: ApiFieldError[] | undefined = data?.fieldErrors;
    const baseMessage: string =
      (data && (data.message || data.error)) || `Request failed with status ${response.status}`;
    throw {
      message: buildMessage(baseMessage, fieldErrors),
      status: response.status,
      fieldErrors,
    } satisfies ApiError;
  }

  return { data: data as T, response };
}

export const api = {
  get: <T>(path: string) => request<T>(path, { method: "GET" }),
  post: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: "POST", body: body ? JSON.stringify(body) : undefined }),
  put: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: "PUT", body: body ? JSON.stringify(body) : undefined }),
  patch: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: "PATCH", body: body ? JSON.stringify(body) : undefined }),
  delete: <T>(path: string) => request<T>(path, { method: "DELETE" }),
  /** Like `put`, but also returns response headers (used to read X-Geometry-Warnings). */
  putWithHeaders: <T>(path: string, body?: unknown) =>
    requestWithResponse<T>(path, { method: "PUT", body: body ? JSON.stringify(body) : undefined }),
};

