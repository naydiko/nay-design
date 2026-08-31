import { type FormEvent, useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { LevelApi, ProjectApi } from "../api/endpoints";
import type { LevelResponse, ProjectResponse } from "../api/types";

export default function ProjectPage() {
  const { projectId } = useParams<{ projectId: string }>();
  const [project, setProject] = useState<ProjectResponse | null>(null);
  const [levels, setLevels] = useState<LevelResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [name, setName] = useState("");
  const [creating, setCreating] = useState(false);

  async function refresh() {
    if (!projectId) return;
    setLoading(true);
    setError(null);
    try {
      const [proj, lvls] = await Promise.all([
        ProjectApi.get(projectId),
        LevelApi.listByProject(projectId),
      ]);
      setProject(proj);
      setLevels(lvls.sort((a, b) => (a.orderIndex ?? 0) - (b.orderIndex ?? 0)));
    } catch (err) {
      setError((err as { message?: string }).message ?? "Failed to load project");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    refresh();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [projectId]);

  async function onCreate(e: FormEvent) {
    e.preventDefault();
    if (!projectId || !name.trim()) return;
    setCreating(true);
    setError(null);
    try {
      await LevelApi.create(projectId, { name: name.trim(), orderIndex: levels.length });
      setName("");
      await refresh();
    } catch (err) {
      setError((err as { message?: string }).message ?? "Failed to create level");
    } finally {
      setCreating(false);
    }
  }

  if (loading) return <p className="muted">Loading…</p>;
  if (!project) return <p className="error">Project not found.</p>;

  return (
    <div>
      <p>
        <Link to="/projects">← Back to projects</Link>
      </p>
      <h1>{project.name}</h1>
      <p className="muted">
        {project.projectType} · {project.status}
      </p>

      <h2>Levels</h2>
      <form className="inline-form" onSubmit={onCreate}>
        <input
          placeholder="New level name (e.g. Ground floor)"
          value={name}
          onChange={(e) => setName(e.target.value)}
        />
        <button type="submit" disabled={creating || !name.trim()}>
          {creating ? "Creating…" : "Create level"}
        </button>
      </form>

      {error && <div className="error">{error}</div>}

      {levels.length === 0 ? (
        <p className="muted">No levels yet. Create your first one above.</p>
      ) : (
        <ul className="card-list">
          {levels.map((l) => (
            <li key={l.id} className="card-list-item">
              <Link to={`/levels/${l.id}`}>
                <strong>{l.name}</strong>
                <span className="muted"> · elevation {l.elevationMm ?? 0} mm</span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

