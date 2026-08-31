import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import ProjectsPage from "./ProjectsPage";
import { mockProject, mockUser } from "../test/fixtures";

vi.mock("../auth/AuthContext", () => ({
  useAuth: () => ({ user: mockUser, loading: false, login: vi.fn(), register: vi.fn(), logout: vi.fn() }),
}));

vi.mock("../api/endpoints", () => ({
  ProjectApi: { list: vi.fn(), create: vi.fn() },
}));

import { ProjectApi } from "../api/endpoints";

function renderPage() {
  return render(
    <MemoryRouter>
      <ProjectsPage />
    </MemoryRouter>
  );
}

describe("ProjectsPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("shows the empty state when the owner has no projects", async () => {
    vi.mocked(ProjectApi.list).mockResolvedValue([]);
    renderPage();
    expect(await screen.findByText(/no projects yet/i)).toBeInTheDocument();
    expect(ProjectApi.list).toHaveBeenCalledWith();
  });

  it("lists the owner's projects", async () => {
    vi.mocked(ProjectApi.list).mockResolvedValue([mockProject]);
    renderPage();
    expect(await screen.findByText("My House")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /my house/i })).toHaveAttribute(
      "href",
      `/projects/${mockProject.id}`
    );
  });

  it("creates a project and refreshes the list", async () => {
    const user = userEvent.setup();
    vi.mocked(ProjectApi.list).mockResolvedValueOnce([]).mockResolvedValueOnce([mockProject]);
    vi.mocked(ProjectApi.create).mockResolvedValue(mockProject);

    renderPage();
    await screen.findByText(/no projects yet/i);

    await user.type(screen.getByPlaceholderText(/new project name/i), "My House");
    await user.click(screen.getByRole("button", { name: /create project/i }));

    await waitFor(() =>
      expect(ProjectApi.create).toHaveBeenCalledWith({
        name: "My House",
        projectType: "RESIDENTIAL",
      })
    );
    expect(await screen.findByText("My House")).toBeInTheDocument();
  });

  it("shows an error message when loading projects fails", async () => {
    vi.mocked(ProjectApi.list).mockRejectedValue({ message: "Failed to load projects" });
    renderPage();
    expect(await screen.findByText("Failed to load projects")).toBeInTheDocument();
  });
});



