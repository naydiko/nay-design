import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import ProjectPage from "./ProjectPage";
import { mockLevel, mockProject } from "../test/fixtures";

vi.mock("../api/endpoints", () => ({
  ProjectApi: { get: vi.fn(), update: vi.fn() },
  LevelApi: { listByProject: vi.fn(), create: vi.fn() },
}));

import { LevelApi, ProjectApi } from "../api/endpoints";

function renderPage() {
  return render(
    <MemoryRouter initialEntries={[`/projects/${mockProject.id}`]}>
      <Routes>
        <Route path="/projects/:projectId" element={<ProjectPage />} />
        <Route path="/levels/:levelId" element={<div>Level Canvas Page</div>} />
      </Routes>
    </MemoryRouter>
  );
}

describe("ProjectPage (level selection)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(ProjectApi.get).mockResolvedValue(mockProject);
  });

  it("lists the project's levels and links to the canvas for each", async () => {
    vi.mocked(LevelApi.listByProject).mockResolvedValue([mockLevel]);
    renderPage();

    expect(await screen.findByText("Ground floor")).toBeInTheDocument();
    const link = screen.getByRole("link", { name: /ground floor/i });
    expect(link).toHaveAttribute("href", `/levels/${mockLevel.id}`);
  });

  it("navigates to the level canvas when a level is selected", async () => {
    const user = userEvent.setup();
    vi.mocked(LevelApi.listByProject).mockResolvedValue([mockLevel]);
    renderPage();

    await user.click(await screen.findByRole("link", { name: /ground floor/i }));
    expect(await screen.findByText("Level Canvas Page")).toBeInTheDocument();
  });

  it("shows the empty state with no levels yet", async () => {
    vi.mocked(LevelApi.listByProject).mockResolvedValue([]);
    renderPage();
    expect(await screen.findByText(/no levels yet/i)).toBeInTheDocument();
  });

  it("creates a new level and refreshes the list", async () => {
    const user = userEvent.setup();
    vi.mocked(LevelApi.listByProject)
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce([mockLevel]);
    vi.mocked(LevelApi.create).mockResolvedValue(mockLevel);

    renderPage();
    await screen.findByText(/no levels yet/i);

    await user.type(screen.getByPlaceholderText(/new level name/i), "Ground floor");
    await user.click(screen.getByRole("button", { name: /create level/i }));

    await waitFor(() =>
      expect(LevelApi.create).toHaveBeenCalledWith(mockProject.id, { name: "Ground floor", orderIndex: 0 })
    );
    expect(await screen.findByText("Ground floor")).toBeInTheDocument();
  });

  it("shows an error message when the project fails to load", async () => {
    vi.mocked(ProjectApi.get).mockRejectedValue({ message: "Failed to load project" });
    vi.mocked(LevelApi.listByProject).mockResolvedValue([]);
    renderPage();
    expect(await screen.findByText("Project not found.")).toBeInTheDocument();
  });
});

