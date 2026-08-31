import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { api, clearToken, getToken, setToken } from "./client";

describe("api client", () => {
  const originalFetch = globalThis.fetch;
  const originalLocation = window.location;

  beforeEach(() => {
    localStorage.clear();
    // jsdom's window.location can't be reassigned directly; delete + redefine.
    // @ts-expect-error - narrowing for test purposes only
    delete window.location;
    // @ts-expect-error - narrowing for test purposes only
    window.location = { ...originalLocation, href: "", pathname: "/levels/1" };
  });

  afterEach(() => {
    globalThis.fetch = originalFetch;
    // @ts-expect-error - restore original location
    window.location = originalLocation;
    vi.restoreAllMocks();
  });

  it("attaches the stored bearer token to every request", async () => {
    setToken("my-jwt-token");
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ ok: true }), { status: 200 }));
    globalThis.fetch = fetchMock;

    await api.get("/api/projects");

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [, options] = fetchMock.mock.calls[0];
    expect(options.headers.Authorization).toBe("Bearer my-jwt-token");
  });

  it("omits the Authorization header when there is no stored token", async () => {
    clearToken();
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({}), { status: 200 }));
    globalThis.fetch = fetchMock;

    await api.get("/api/products");

    const [, options] = fetchMock.mock.calls[0];
    expect(options.headers.Authorization).toBeUndefined();
  });

  it("clears the token and redirects to /login on a 401 from a protected endpoint", async () => {
    setToken("stale-token");
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ message: "Unauthorized" }), { status: 401 })
    );
    globalThis.fetch = fetchMock;

    await expect(api.get("/api/projects")).rejects.toMatchObject({ status: 401 });
    expect(getToken()).toBeNull();
    expect(window.location.href).toBe("/login");
  });

  it("does not redirect on a 401 from the login endpoint itself", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ message: "Invalid email or password" }), { status: 401 })
    );
    globalThis.fetch = fetchMock;

    await expect(api.post("/api/auth/login", { email: "a", password: "b" })).rejects.toMatchObject({
      message: "Invalid email or password",
    });
    expect(window.location.href).toBe("");
  });

  it("surfaces a friendly message when the network request itself fails", async () => {
    globalThis.fetch = vi.fn().mockRejectedValue(new TypeError("Failed to fetch"));
    await expect(api.get("/api/projects")).rejects.toMatchObject({
      message: "Cannot reach the server. Is the backend running?",
    });
  });

  it("includes field errors in the thrown message", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          message: "Validation failed",
          fieldErrors: [{ field: "name", message: "must not be blank" }],
        }),
        { status: 400 }
      )
    );
    globalThis.fetch = fetchMock;

    await expect(api.post("/api/projects", {})).rejects.toMatchObject({
      message: "Validation failed — name: must not be blank",
      status: 400,
    });
  });

  it("propagates structured geometry issues from an error response", async () => {
    const issues = [{ severity: "ERROR", code: "WALL_ZERO_LENGTH", message: "Wall has zero length" }];
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ message: "Invalid geometry", issues }), { status: 422 })
    );
    globalThis.fetch = fetchMock;

    await expect(api.put("/api/levels/1/geometry", {})).rejects.toMatchObject({ issues });
  });
});

