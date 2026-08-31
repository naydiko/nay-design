import { type FormEvent, useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { LevelApi, RoomApi } from "../api/endpoints";
import type { LevelResponse, RoomResponse, RoomType } from "../api/types";

const ROOM_TYPES: RoomType[] = [
  "LIVING_ROOM",
  "BEDROOM",
  "KITCHEN",
  "DINING_ROOM",
  "BATHROOM",
  "OFFICE",
  "CHILDREN_ROOM",
  "HALLWAY",
  "OUTDOOR",
  "OTHER",
];

export default function RoomsPage() {
  const { levelId } = useParams<{ levelId: string }>();
  const [level, setLevel] = useState<LevelResponse | null>(null);
  const [rooms, setRooms] = useState<RoomResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [name, setName] = useState("");
  const [type, setType] = useState<RoomType>("LIVING_ROOM");
  const [creating, setCreating] = useState(false);

  async function refresh() {
    if (!levelId) return;
    setLoading(true);
    setError(null);
    try {
      const [lvl, list] = await Promise.all([LevelApi.get(levelId), RoomApi.listByLevel(levelId)]);
      setLevel(lvl);
      setRooms(list);
    } catch (err) {
      setError((err as { message?: string }).message ?? "Failed to load rooms");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    refresh();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [levelId]);

  async function onCreate(e: FormEvent) {
    e.preventDefault();
    if (!levelId || !name.trim()) return;
    setCreating(true);
    setError(null);
    try {
      await RoomApi.create(levelId, { name: name.trim(), type });
      setName("");
      await refresh();
    } catch (err) {
      setError((err as { message?: string }).message ?? "Failed to create room");
    } finally {
      setCreating(false);
    }
  }

  if (loading) return <p className="muted">Loading…</p>;

  return (
    <div>
      <p>{level && <Link to={`/levels/${level.id}`}>← Back to level canvas</Link>}</p>
      <h1>{level?.name ?? "Level"} — Rooms</h1>

      <form className="inline-form" onSubmit={onCreate}>
        <input
          placeholder="New room name (e.g. Living room)"
          value={name}
          onChange={(e) => setName(e.target.value)}
        />
        <select value={type} onChange={(e) => setType(e.target.value as RoomType)}>
          {ROOM_TYPES.map((t) => (
            <option key={t} value={t}>
              {t.replace(/_/g, " ")}
            </option>
          ))}
        </select>
        <button type="submit" disabled={creating || !name.trim()}>
          {creating ? "Creating…" : "Create room"}
        </button>
      </form>

      {error && <div className="error">{error}</div>}

      {rooms.length === 0 ? (
        <p className="muted">No rooms yet. Create your first one above.</p>
      ) : (
        <ul className="card-list">
          {rooms.map((r) => (
            <li key={r.id} className="card-list-item">
              <Link to={`/rooms/${r.id}`}>
                <strong>{r.name}</strong>
                <span className="muted"> · {r.type.replace(/_/g, " ")}</span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

