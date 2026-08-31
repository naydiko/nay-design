import { type FormEvent, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { ProjectApi } from "../api/endpoints";
import type { ProjectResponse, ProjectType } from "../api/types";

const PROJECT_TYPES: ProjectType[] = [
  "RESIDENTIAL",
  "COMMERCIAL",
  "RENOVATION",
  "NEW_BUILD",
  "OUTDOOR",
  "OTHER",
];

export default function ProjectsPage() {
  const { user } = useAuth();
  const [projects, setProjects] = useState<ProjectResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [name, setName] = useState("");
  const [projectType, setProjectType] = useState<ProjectType>("RESIDENTIAL");
  const [creating, setCreating] = useState(false);

  async function refresh() {
    if (!user) return;
    setLoading(true);
    setError(null);
    try {
      const list = await ProjectApi.list(user.id);
      setProjects(list);
    } catch (err) {
      setError((err as { message?: string }).message ?? "Failed to load projects");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    refresh();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user?.id]);

  async function onCreate(e: FormEvent) {
    e.preventDefault();
    if (!user || !name.trim()) return;
    setCreating(true);
    setError(null);
    try {
      await ProjectApi.create({ ownerId: user.id, name: name.trim(), projectType });
      setName("");
      await refresh();
    } catch (err) {
      setError((err as { message?: string }).message ?? "Failed to create project");
    } finally {
      setCreating(false);
    }
  }

  return (
    <div>
      <h1>Projects</h1>

      <form className="inline-form" onSubmit={onCreate}>
        <input
          placeholder="New project name"
          value={name}
          onChange={(e) => setName(e.target.value)}
        />
        <select value={projectType} onChange={(e) => setProjectType(e.target.value as ProjectType)}>
          {PROJECT_TYPES.map((t) => (
            <option key={t} value={t}>
              {t}
            </option>
          ))}
        </select>
        <button type="submit" disabled={creating || !name.trim()}>
          {creating ? "Creating…" : "Create project"}
        </button>
      </form>

      {error && <div className="error">{error}</div>}
      {loading ? (
        <p className="muted">Loading…</p>
      ) : projects.length === 0 ? (
        <p className="muted">No projects yet. Create your first one above.</p>
      ) : (
        <ul className="card-list">
          {projects.map((p) => (
            <li key={p.id} className="card-list-item">
              <Link to={`/projects/${p.id}`}>
                <strong>{p.name}</strong>
                <span className="muted"> · {p.projectType} · {p.status}</span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

