import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AuthProvider } from "../auth/AuthContext";
import LoginPage from "./LoginPage";
import { mockUser } from "../test/fixtures";

vi.mock("../api/endpoints", () => ({
  AuthApi: { login: vi.fn(), register: vi.fn(), me: vi.fn() },
}));

import { AuthApi } from "../api/endpoints";

function renderLogin() {
  return render(
    <AuthProvider>
      <MemoryRouter initialEntries={["/login"]}>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/projects" element={<div>Projects Page</div>} />
        </Routes>
      </MemoryRouter>
    </AuthProvider>
  );
}

describe("LoginPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("logs in with valid credentials, stores the token, and navigates to /projects", async () => {
    const user = userEvent.setup();
    vi.mocked(AuthApi.login).mockResolvedValue({
      token: "jwt-token-123",
      tokenType: "Bearer",
      user: mockUser,
    });

    renderLogin();

    await user.type(screen.getByLabelText(/email/i), "alice@example.com");
    await user.type(screen.getByLabelText(/password/i), "hunter22");
    await user.click(screen.getByRole("button", { name: /log in/i }));

    await waitFor(() => expect(screen.getByText("Projects Page")).toBeInTheDocument());
    expect(AuthApi.login).toHaveBeenCalledWith({ email: "alice@example.com", password: "hunter22" });
    expect(localStorage.getItem("naydesign.token")).toBe("jwt-token-123");
  });

  it("shows an error message and stays on the page on invalid credentials", async () => {
    const user = userEvent.setup();
    vi.mocked(AuthApi.login).mockRejectedValue({ message: "Invalid email or password", status: 401 });

    renderLogin();

    await user.type(screen.getByLabelText(/email/i), "alice@example.com");
    await user.type(screen.getByLabelText(/password/i), "wrong-password");
    await user.click(screen.getByRole("button", { name: /log in/i }));

    expect(await screen.findByText("Invalid email or password")).toBeInTheDocument();
    expect(screen.queryByText("Projects Page")).not.toBeInTheDocument();
    expect(localStorage.getItem("naydesign.token")).toBeNull();
  });

  it("surfaces a generic message on a network failure", async () => {
    const user = userEvent.setup();
    vi.mocked(AuthApi.login).mockRejectedValue({ message: "Cannot reach the server. Is the backend running?" });

    renderLogin();

    await user.type(screen.getByLabelText(/email/i), "alice@example.com");
    await user.type(screen.getByLabelText(/password/i), "hunter22");
    await user.click(screen.getByRole("button", { name: /log in/i }));

    expect(await screen.findByText(/cannot reach the server/i)).toBeInTheDocument();
  });
});

