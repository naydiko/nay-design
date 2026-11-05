import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { LevelApi, RoomApi } from "../api/endpoints";
import type { LevelGeometryResponse, LevelResponse, RoomResponse } from "../api/types";
import { orderClosedBoundary } from "../canvas/roomTopology";

function roomHasUsableBoundary(geometry: LevelGeometryResponse | null, roomId: string) {
  if (!geometry) return false;

  const roomWallIds = new Set(geometry.roomWalls.filter((rw) => rw.roomId === roomId).map((rw) => rw.wallId));
  if (roomWallIds.size < 3) return false;

  const walls = geometry.walls.filter((wall) => roomWallIds.has(wall.id));
  if (walls.length !== roomWallIds.size) return false;
  return orderClosedBoundary(geometry.nodes, walls) !== null;
}

export default function RoomsPage() {
  const { levelId } = useParams<{ levelId: string }>();
  const [level, setLevel] = useState<LevelResponse | null>(null);
  const [rooms, setRooms] = useState<RoomResponse[]>([]);
  const [geometry, setGeometry] = useState<LevelGeometryResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  async function refresh() {
    if (!levelId) return;
    setLoading(true);
    setError(null);
    try {
      const [lvl, list, geo] = await Promise.all([
        LevelApi.get(levelId),
        RoomApi.listByLevel(levelId),
        LevelApi.getGeometry(levelId),
      ]);
      setLevel(lvl);
      setRooms(list);
      setGeometry(geo);
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

  async function onDelete(room: RoomResponse) {
    if (!window.confirm(`Delete room "${room.name}"?`)) return;
    setError(null);
    try {
      await RoomApi.remove(room.id);
      await refresh();
    } catch (err) {
      setError((err as { message?: string }).message ?? "Failed to delete room");
    }
  }

  if (loading) return <p className="muted">Loading…</p>;

  return (
    <div>
      <p>{level && <Link to={`/levels/${level.id}`}>← Back to level canvas</Link>}</p>
      <h1>{level?.name ?? "Level"} — Rooms</h1>

      <p className="muted">
        Rooms are defined from closed wall regions on the <Link to={`/levels/${levelId}`}>Level Canvas</Link>.
      </p>

      {error && <div className="error">{error}</div>}

      {rooms.length === 0 ? (
        <p className="muted">No rooms have been defined yet. Use Define room on the Level Canvas.</p>
      ) : (
        <ul className="card-list">
          {rooms.map((r) => (
            <li key={r.id} className="card-list-item room-row">
              <div className="room-row-main">
                <strong>{r.name}</strong>
                <span className="muted"> · {r.type.replace(/_/g, " ")}</span>
                <div className="muted">
                  Boundary: {roomHasUsableBoundary(geometry, r.id) ? "Defined" : "Not defined"}
                </div>
              </div>
              <span className="card-actions">
                <Link to={`/levels/${levelId}?roomId=${r.id}`}>Locate/Edit on canvas</Link>
                {roomHasUsableBoundary(geometry, r.id) ? (
                  <Link to={`/rooms/${r.id}`}>Furniture</Link>
                ) : (
                  <span className="muted" title="Define this room boundary before placing furniture">
                    Furniture unavailable
                  </span>
                )}
                <button type="button" className="link-button" onClick={() => onDelete(r)}>
                  Delete
                </button>
              </span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

