// Stage 1 2D floorplan canvas: draw/edit walls, doors and windows for a
// single Level, with local-only editing (undo/redo) and explicit Save/Load
// against PUT/GET /api/levels/{levelId}/geometry.
//
// Geometry is kept in millimetres (matching the backend model) and rendered
// at a fixed px-per-mm scale. Local entities use a stable `clientId` for
// internal references; `serverId` is null until the entity has been
// persisted at least once.
import { useEffect, useMemo, useRef, useState, type PointerEvent as ReactPointerEvent } from "react";
import { Link, useParams } from "react-router-dom";
import { LevelApi } from "../api/endpoints";
import type {
  LevelGeometryRequest,
  LevelGeometryResponse,
  LevelResponse,
  OpeningType,
  WallKind,
} from "../api/types";

// ---- Local geometry model ----
interface LocalNode {
  clientId: string;
  serverId: string | null;
  xMm: number;
  yMm: number;
  zMm: number;
}
interface LocalWall {
  clientId: string;
  serverId: string | null;
  startClientId: string;
  endClientId: string;
  thicknessMm: number;
  heightMm: number;
  kind: WallKind;
}
interface LocalOpening {
  clientId: string;
  serverId: string | null;
  wallClientId: string;
  type: OpeningType;
  offsetFromStartMm: number;
  widthMm: number;
  heightMm: number;
}
interface GeometryState {
  nodes: LocalNode[];
  walls: LocalWall[];
  openings: LocalOpening[];
}

type Tool = "select" | "wall" | "door" | "window" | "delete";
type Selection =
  | { type: "node"; clientId: string }
  | { type: "wall"; clientId: string }
  | { type: "opening"; clientId: string }
  | null;

const PX_PER_MM = 0.15;
const NODE_HIT_PX = 9;
const WALL_HIT_PX = 10;
const DEFAULT_WALL_THICKNESS_MM = 150;
const DEFAULT_WALL_HEIGHT_MM = 2700;
const DEFAULT_DOOR_WIDTH_MM = 900;
const DEFAULT_WINDOW_WIDTH_MM = 1200;
const DEFAULT_OPENING_HEIGHT_MM = 1200;

const CANVAS_W = 1100;
const CANVAS_H = 700;
const ORIGIN_X = CANVAS_W / 2;
const ORIGIN_Y = CANVAS_H / 2;

function toPx(xMm: number, yMm: number) {
  return { x: ORIGIN_X + xMm * PX_PER_MM, y: ORIGIN_Y + yMm * PX_PER_MM };
}
function toMm(xPx: number, yPx: number) {
  return { xMm: (xPx - ORIGIN_X) / PX_PER_MM, yMm: (yPx - ORIGIN_Y) / PX_PER_MM };
}
function dist(ax: number, ay: number, bx: number, by: number) {
  return Math.hypot(ax - bx, ay - by);
}
function newId() {
  return crypto.randomUUID();
}

/** Projects point p onto segment ab, returning the clamped t in [0,1], the
 * distance from p to the segment, and the projected point. */
function projectOntoSegment(px: number, py: number, ax: number, ay: number, bx: number, by: number) {
  const dx = bx - ax;
  const dy = by - ay;
  const lenSq = dx * dx + dy * dy;
  let t = lenSq === 0 ? 0 : ((px - ax) * dx + (py - ay) * dy) / lenSq;
  t = Math.max(0, Math.min(1, t));
  const projX = ax + t * dx;
  const projY = ay + t * dy;
  return { t, distance: dist(px, py, projX, projY), x: projX, y: projY };
}

function emptyState(): GeometryState {
  return { nodes: [], walls: [], openings: [] };
}

export default function LevelCanvasPage() {
  const { levelId } = useParams<{ levelId: string }>();
  const canvasRef = useRef<HTMLCanvasElement>(null);

  const [level, setLevel] = useState<LevelResponse | null>(null);
  const [state, setState] = useState<GeometryState>(emptyState());
  const [past, setPast] = useState<GeometryState[]>([]);
  const [future, setFuture] = useState<GeometryState[]>([]);
  const [tool, setTool] = useState<Tool>("select");
  const [selected, setSelected] = useState<Selection>(null);
  const [wallDrawStart, setWallDrawStart] = useState<string | null>(null); // clientId of pending start node
  const [previewMm, setPreviewMm] = useState<{ xMm: number; yMm: number } | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [status, setStatus] = useState<string | null>(null);

  const dragRef = useRef<{
    kind: "node" | "opening";
    clientId: string;
    beforeState: GeometryState;
  } | null>(null);

  const dirty = past.length > 0;

  // ---- Load geometry on mount ----
  useEffect(() => {
    if (!levelId) return;
    let cancelled = false;
    setLoading(true);
    setError(null);
    Promise.all([LevelApi.get(levelId), LevelApi.getGeometry(levelId)])
      .then(([lvl, geo]) => {
        if (cancelled) return;
        setLevel(lvl);
        setState(fromResponse(geo));
        setPast([]);
        setFuture([]);
      })
      .catch((err) => !cancelled && setError((err as { message?: string }).message ?? "Failed to load level"))
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
  }, [levelId]);

  function fromResponse(geo: LevelGeometryResponse): GeometryState {
    const nodes: LocalNode[] = geo.nodes.map((n) => ({
      clientId: newId(),
      serverId: n.id,
      xMm: n.xMm,
      yMm: n.yMm,
      zMm: n.zMm,
    }));
    const nodeByServerId = new Map(nodes.map((n) => [n.serverId as string, n]));
    const walls: LocalWall[] = geo.walls.map((w) => ({
      clientId: newId(),
      serverId: w.id,
      startClientId: nodeByServerId.get(w.startNodeId)!.clientId,
      endClientId: nodeByServerId.get(w.endNodeId)!.clientId,
      thicknessMm: w.thicknessMm,
      heightMm: w.heightMm,
      kind: w.kind,
    }));
    const wallByServerId = new Map(walls.map((w) => [w.serverId as string, w]));
    const openings: LocalOpening[] = geo.openings.map((o) => ({
      clientId: newId(),
      serverId: o.id,
      wallClientId: wallByServerId.get(o.wallId)!.clientId,
      type: o.type,
      offsetFromStartMm: o.offsetFromStartMm,
      widthMm: o.widthMm,
      heightMm: o.heightMm,
    }));
    return { nodes, walls, openings };
  }

  // ---- History helpers ----
  function commit(next: GeometryState) {
    setPast((p) => [...p, state]);
    setFuture([]);
    setState(next);
  }
  function undo() {
    setPast((p) => {
      if (p.length === 0) return p;
      const prev = p[p.length - 1];
      setFuture((f) => [state, ...f]);
      setState(prev);
      return p.slice(0, -1);
    });
    setSelected(null);
  }
  function redo() {
    setFuture((f) => {
      if (f.length === 0) return f;
      const next = f[0];
      setPast((p) => [...p, state]);
      setState(next);
      return f.slice(1);
    });
    setSelected(null);
  }

  // ---- Hit testing ----
  function findNodeAt(xPx: number, yPx: number): LocalNode | null {
    for (const n of state.nodes) {
      const p = toPx(n.xMm, n.yMm);
      if (dist(p.x, p.y, xPx, yPx) <= NODE_HIT_PX) return n;
    }
    return null;
  }
  function findOpeningAt(xPx: number, yPx: number): LocalOpening | null {
    for (const o of state.openings) {
      const wall = state.walls.find((w) => w.clientId === o.wallClientId);
      if (!wall) continue;
      const start = state.nodes.find((n) => n.clientId === wall.startClientId)!;
      const end = state.nodes.find((n) => n.clientId === wall.endClientId)!;
      const sp = toPx(start.xMm, start.yMm);
      const ep = toPx(end.xMm, end.yMm);
      const wallLenMm = dist(start.xMm, start.yMm, end.xMm, end.yMm) || 1;
      const t0 = o.offsetFromStartMm / wallLenMm;
      const t1 = (o.offsetFromStartMm + o.widthMm) / wallLenMm;
      const hx0 = sp.x + (ep.x - sp.x) * t0;
      const hy0 = sp.y + (ep.y - sp.y) * t0;
      const hx1 = sp.x + (ep.x - sp.x) * t1;
      const hy1 = sp.y + (ep.y - sp.y) * t1;
      const proj = projectOntoSegment(xPx, yPx, hx0, hy0, hx1, hy1);
      if (proj.distance <= WALL_HIT_PX) return o;
    }
    return null;
  }
  function findWallAt(xPx: number, yPx: number): LocalWall | null {
    let best: LocalWall | null = null;
    let bestDist = Infinity;
    for (const w of state.walls) {
      const start = state.nodes.find((n) => n.clientId === w.startClientId);
      const end = state.nodes.find((n) => n.clientId === w.endClientId);
      if (!start || !end) continue;
      const sp = toPx(start.xMm, start.yMm);
      const ep = toPx(end.xMm, end.yMm);
      const proj = projectOntoSegment(xPx, yPx, sp.x, sp.y, ep.x, ep.y);
      const threshold = Math.max(WALL_HIT_PX, (w.thicknessMm * PX_PER_MM) / 2 + 4);
      if (proj.distance <= threshold && proj.distance < bestDist) {
        best = w;
        bestDist = proj.distance;
      }
    }
    return best;
  }

  function nearestSnapNode(xPx: number, yPx: number): LocalNode | null {
    return findNodeAt(xPx, yPx);
  }

  // ---- Canvas pointer handlers ----
  function getCanvasPos(e: ReactPointerEvent<HTMLCanvasElement>) {
    const rect = canvasRef.current!.getBoundingClientRect();
    return { x: e.clientX - rect.left, y: e.clientY - rect.top };
  }

  function onPointerDown(e: ReactPointerEvent<HTMLCanvasElement>) {
    const { x, y } = getCanvasPos(e);

    if (tool === "wall") {
      const snapped = nearestSnapNode(x, y);
      if (!wallDrawStart) {
        let startClientId: string;
        if (snapped) {
          startClientId = snapped.clientId;
        } else {
          const { xMm, yMm } = toMm(x, y);
          const node: LocalNode = { clientId: newId(), serverId: null, xMm, yMm, zMm: 0 };
          commit({ ...state, nodes: [...state.nodes, node] });
          startClientId = node.clientId;
        }
        setWallDrawStart(startClientId);
        setPreviewMm(toMm(x, y));
      } else {
        let endClientId: string;
        let base = state;
        if (snapped && snapped.clientId !== wallDrawStart) {
          endClientId = snapped.clientId;
        } else if (snapped && snapped.clientId === wallDrawStart) {
          // clicked same node again: cancel
          setWallDrawStart(null);
          setPreviewMm(null);
          return;
        } else {
          const { xMm, yMm } = toMm(x, y);
          const node: LocalNode = { clientId: newId(), serverId: null, xMm, yMm, zMm: 0 };
          base = { ...state, nodes: [...state.nodes, node] };
          endClientId = node.clientId;
        }
        const wall: LocalWall = {
          clientId: newId(),
          serverId: null,
          startClientId: wallDrawStart,
          endClientId,
          thicknessMm: DEFAULT_WALL_THICKNESS_MM,
          heightMm: DEFAULT_WALL_HEIGHT_MM,
          kind: "INTERIOR",
        };
        commit({ ...base, walls: [...base.walls, wall] });
        setWallDrawStart(null);
        setPreviewMm(null);
      }
      return;
    }

    if (tool === "door" || tool === "window") {
      const wall = findWallAt(x, y);
      if (!wall) return;
      const start = state.nodes.find((n) => n.clientId === wall.startClientId)!;
      const end = state.nodes.find((n) => n.clientId === wall.endClientId)!;
      const sp = toPx(start.xMm, start.yMm);
      const ep = toPx(end.xMm, end.yMm);
      const proj = projectOntoSegment(x, y, sp.x, sp.y, ep.x, ep.y);
      const wallLenMm = dist(start.xMm, start.yMm, end.xMm, end.yMm) || 1;
      const width = tool === "door" ? DEFAULT_DOOR_WIDTH_MM : DEFAULT_WINDOW_WIDTH_MM;
      const clickOffsetMm = proj.t * wallLenMm;
      const offset = Math.max(0, Math.min(wallLenMm - width, clickOffsetMm - width / 2));
      const opening: LocalOpening = {
        clientId: newId(),
        serverId: null,
        wallClientId: wall.clientId,
        type: tool === "door" ? "DOOR" : "WINDOW",
        offsetFromStartMm: Math.max(0, offset),
        widthMm: Math.min(width, wallLenMm),
        heightMm: DEFAULT_OPENING_HEIGHT_MM,
      };
      commit({ ...state, openings: [...state.openings, opening] });
      setSelected({ type: "opening", clientId: opening.clientId });
      return;
    }

    if (tool === "delete") {
      const node = findNodeAt(x, y);
      if (node) {
        deleteNode(node.clientId);
        return;
      }
      const opening = findOpeningAt(x, y);
      if (opening) {
        deleteOpening(opening.clientId);
        return;
      }
      const wall = findWallAt(x, y);
      if (wall) {
        deleteWall(wall.clientId);
        return;
      }
      return;
    }

    // select tool
    const node = findNodeAt(x, y);
    if (node) {
      setSelected({ type: "node", clientId: node.clientId });
      dragRef.current = { kind: "node", clientId: node.clientId, beforeState: state };
      return;
    }
    const opening = findOpeningAt(x, y);
    if (opening) {
      setSelected({ type: "opening", clientId: opening.clientId });
      dragRef.current = { kind: "opening", clientId: opening.clientId, beforeState: state };
      return;
    }
    const wall = findWallAt(x, y);
    if (wall) {
      setSelected({ type: "wall", clientId: wall.clientId });
      return;
    }
    setSelected(null);
  }

  function onPointerMove(e: ReactPointerEvent<HTMLCanvasElement>) {
    const { x, y } = getCanvasPos(e);

    if (tool === "wall" && wallDrawStart) {
      const snapped = nearestSnapNode(x, y);
      setPreviewMm(snapped ? { xMm: snapped.xMm, yMm: snapped.yMm } : toMm(x, y));
      return;
    }

    const drag = dragRef.current;
    if (!drag) return;

    if (drag.kind === "node") {
      const { xMm, yMm } = toMm(x, y);
      setState((s) => ({
        ...s,
        nodes: s.nodes.map((n) => (n.clientId === drag.clientId ? { ...n, xMm, yMm } : n)),
      }));
    } else if (drag.kind === "opening") {
      setState((s) => {
        const opening = s.openings.find((o) => o.clientId === drag.clientId)!;
        const wall = s.walls.find((w) => w.clientId === opening.wallClientId)!;
        const start = s.nodes.find((n) => n.clientId === wall.startClientId)!;
        const end = s.nodes.find((n) => n.clientId === wall.endClientId)!;
        const sp = toPx(start.xMm, start.yMm);
        const ep = toPx(end.xMm, end.yMm);
        const proj = projectOntoSegment(x, y, sp.x, sp.y, ep.x, ep.y);
        const wallLenMm = dist(start.xMm, start.yMm, end.xMm, end.yMm) || 1;
        const centerMm = proj.t * wallLenMm;
        const offset = Math.max(0, Math.min(wallLenMm - opening.widthMm, centerMm - opening.widthMm / 2));
        return {
          ...s,
          openings: s.openings.map((o) =>
            o.clientId === drag.clientId ? { ...o, offsetFromStartMm: offset } : o
          ),
        };
      });
    }
  }

  function onPointerUp() {
    const drag = dragRef.current;
    if (drag) {
      setPast((p) => [...p, drag.beforeState]);
      setFuture([]);
      dragRef.current = null;
    }
  }

  // ---- Delete helpers (cascade) ----
  function deleteNode(clientId: string) {
    const wallsToRemove = state.walls.filter(
      (w) => w.startClientId === clientId || w.endClientId === clientId
    );
    const wallIds = new Set(wallsToRemove.map((w) => w.clientId));
    commit({
      nodes: state.nodes.filter((n) => n.clientId !== clientId),
      walls: state.walls.filter((w) => !wallIds.has(w.clientId)),
      openings: state.openings.filter((o) => !wallIds.has(o.wallClientId)),
    });
    setSelected(null);
  }
  function deleteWall(clientId: string) {
    commit({
      ...state,
      walls: state.walls.filter((w) => w.clientId !== clientId),
      openings: state.openings.filter((o) => o.wallClientId !== clientId),
    });
    setSelected(null);
  }
  function deleteOpening(clientId: string) {
    commit({ ...state, openings: state.openings.filter((o) => o.clientId !== clientId) });
    setSelected(null);
  }
  function deleteSelected() {
    if (!selected) return;
    if (selected.type === "node") deleteNode(selected.clientId);
    else if (selected.type === "wall") deleteWall(selected.clientId);
    else deleteOpening(selected.clientId);
  }

  // ---- Property edits (from side panel) ----
  function updateWallProp(clientId: string, patch: Partial<LocalWall>) {
    commit({
      ...state,
      walls: state.walls.map((w) => (w.clientId === clientId ? { ...w, ...patch } : w)),
    });
  }
  function updateOpeningProp(clientId: string, patch: Partial<LocalOpening>) {
    commit({
      ...state,
      openings: state.openings.map((o) => (o.clientId === clientId ? { ...o, ...patch } : o)),
    });
  }

  // ---- Save (two-phase, since new walls/openings can only reference
  // nodes that already have a server-assigned id) ----
  async function save() {
    if (!levelId) return;
    setSaving(true);
    setStatus(null);
    setError(null);
    try {
      let working = state;

      const hasNewNodes = working.nodes.some((n) => n.serverId === null);
      if (hasNewNodes) {
        // Phase 1: persist nodes + only already-persisted walls/openings, to
        // obtain real ids for brand-new nodes without touching new walls.
        const phase1Walls = working.walls.filter(
          (w) =>
            w.serverId !== null &&
            findNode(working, w.startClientId)?.serverId !== null &&
            findNode(working, w.endClientId)?.serverId !== null
        );
        const phase1WallIds = new Set(phase1Walls.map((w) => w.clientId));
        const phase1Openings = working.openings.filter(
          (o) => o.serverId !== null && phase1WallIds.has(o.wallClientId)
        );

        const phase1Request: LevelGeometryRequest = {
          nodes: working.nodes.map(toNodeDto),
          walls: phase1Walls.map((w) => toWallDto(w, working)),
          openings: phase1Openings.map((o) => toOpeningDto(o, working)),
          rooms: [],
          roomWalls: [],
        };
        const phase1Response = await LevelApi.saveGeometry(levelId, phase1Request);

        // Match brand-new nodes to the response by coordinates (safe here:
        // nothing else about them changed between request and response).
        const claimed = new Set<string>();
        working = {
          ...working,
          nodes: working.nodes.map((n) => {
            if (n.serverId !== null) return n;
            const match = phase1Response.nodes.find(
              (rn) =>
                !claimed.has(rn.id) &&
                Math.abs(rn.xMm - n.xMm) < 0.01 &&
                Math.abs(rn.yMm - n.yMm) < 0.01 &&
                Math.abs(rn.zMm - n.zMm) < 0.01
            );
            if (match) claimed.add(match.id);
            return match ? { ...n, serverId: match.id } : n;
          }),
        };
      }

      // Phase 2: send the complete, authoritative geometry now that every
      // node has a real id.
      const finalRequest: LevelGeometryRequest = {
        nodes: working.nodes.map(toNodeDto),
        walls: working.walls.map((w) => toWallDto(w, working)),
        openings: working.openings.map((o) => toOpeningDto(o, working)),
        rooms: [],
        roomWalls: [],
      };
      const finalResponse = await LevelApi.saveGeometry(levelId, finalRequest);
      setState(fromResponse(finalResponse));
      setPast([]);
      setFuture([]);
      setSelected(null);
      setStatus("Saved");
    } catch (err) {
      setError((err as { message?: string }).message ?? "Failed to save geometry");
    } finally {
      setSaving(false);
    }
  }

  function findNode(s: GeometryState, clientId: string) {
    return s.nodes.find((n) => n.clientId === clientId) ?? null;
  }
  function toNodeDto(n: LocalNode) {
    return { id: n.serverId, xMm: n.xMm, yMm: n.yMm, zMm: n.zMm };
  }
  function toWallDto(w: LocalWall, s: GeometryState) {
    const start = findNode(s, w.startClientId)!;
    const end = findNode(s, w.endClientId)!;
    return {
      id: w.serverId,
      startNodeId: start.serverId as string,
      endNodeId: end.serverId as string,
      thicknessMm: w.thicknessMm,
      heightMm: w.heightMm,
      kind: w.kind,
    };
  }
  function toOpeningDto(o: LocalOpening, s: GeometryState) {
    const wall = s.walls.find((w) => w.clientId === o.wallClientId)!;
    return {
      id: o.serverId,
      wallId: wall.serverId as string,
      type: o.type,
      offsetFromStartMm: o.offsetFromStartMm,
      widthMm: o.widthMm,
      heightMm: o.heightMm,
    };
  }

  // ---- Rendering ----
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    ctx.clearRect(0, 0, CANVAS_W, CANVAS_H);
    ctx.fillStyle = "#f7f7fa";
    ctx.fillRect(0, 0, CANVAS_W, CANVAS_H);

    // grid
    ctx.strokeStyle = "#e6e6ee";
    ctx.lineWidth = 1;
    for (let gx = ORIGIN_X % 100; gx < CANVAS_W; gx += 100) {
      ctx.beginPath();
      ctx.moveTo(gx, 0);
      ctx.lineTo(gx, CANVAS_H);
      ctx.stroke();
    }
    for (let gy = ORIGIN_Y % 100; gy < CANVAS_H; gy += 100) {
      ctx.beginPath();
      ctx.moveTo(0, gy);
      ctx.lineTo(CANVAS_W, gy);
      ctx.stroke();
    }

    // walls
    for (const w of state.walls) {
      const start = state.nodes.find((n) => n.clientId === w.startClientId);
      const end = state.nodes.find((n) => n.clientId === w.endClientId);
      if (!start || !end) continue;
      const sp = toPx(start.xMm, start.yMm);
      const ep = toPx(end.xMm, end.yMm);
      const isSelected = selected?.type === "wall" && selected.clientId === w.clientId;
      ctx.strokeStyle = isSelected ? "#2563eb" : "#333";
      ctx.lineWidth = Math.max(2, w.thicknessMm * PX_PER_MM);
      ctx.beginPath();
      ctx.moveTo(sp.x, sp.y);
      ctx.lineTo(ep.x, ep.y);
      ctx.stroke();

      const lengthMm = dist(start.xMm, start.yMm, end.xMm, end.yMm);
      ctx.fillStyle = "#555";
      ctx.font = "11px sans-serif";
      ctx.fillText(`${Math.round(lengthMm)} mm`, (sp.x + ep.x) / 2 + 6, (sp.y + ep.y) / 2 - 6);
    }

    // openings
    for (const o of state.openings) {
      const wall = state.walls.find((w) => w.clientId === o.wallClientId);
      if (!wall) continue;
      const start = state.nodes.find((n) => n.clientId === wall.startClientId);
      const end = state.nodes.find((n) => n.clientId === wall.endClientId);
      if (!start || !end) continue;
      const sp = toPx(start.xMm, start.yMm);
      const ep = toPx(end.xMm, end.yMm);
      const wallLenMm = dist(start.xMm, start.yMm, end.xMm, end.yMm) || 1;
      const t0 = o.offsetFromStartMm / wallLenMm;
      const t1 = (o.offsetFromStartMm + o.widthMm) / wallLenMm;
      const hx0 = sp.x + (ep.x - sp.x) * t0;
      const hy0 = sp.y + (ep.y - sp.y) * t0;
      const hx1 = sp.x + (ep.x - sp.x) * t1;
      const hy1 = sp.y + (ep.y - sp.y) * t1;
      const isSelected = selected?.type === "opening" && selected.clientId === o.clientId;
      ctx.strokeStyle = isSelected ? "#2563eb" : o.type === "DOOR" ? "#a0522d" : "#3b82f6";
      ctx.lineWidth = Math.max(4, wall.thicknessMm * PX_PER_MM + 2);
      ctx.beginPath();
      ctx.moveTo(hx0, hy0);
      ctx.lineTo(hx1, hy1);
      ctx.stroke();
    }

    // nodes
    for (const n of state.nodes) {
      const p = toPx(n.xMm, n.yMm);
      const isSelected = selected?.type === "node" && selected.clientId === n.clientId;
      ctx.fillStyle = isSelected ? "#2563eb" : "#111";
      ctx.beginPath();
      ctx.arc(p.x, p.y, 4, 0, Math.PI * 2);
      ctx.fill();
    }

    // wall-draw preview
    if (tool === "wall" && wallDrawStart && previewMm) {
      const start = state.nodes.find((n) => n.clientId === wallDrawStart);
      if (start) {
        const sp = toPx(start.xMm, start.yMm);
        const ep = toPx(previewMm.xMm, previewMm.yMm);
        ctx.strokeStyle = "#94a3b8";
        ctx.setLineDash([6, 4]);
        ctx.lineWidth = 2;
        ctx.beginPath();
        ctx.moveTo(sp.x, sp.y);
        ctx.lineTo(ep.x, ep.y);
        ctx.stroke();
        ctx.setLineDash([]);
      }
    }
  }, [state, selected, tool, wallDrawStart, previewMm]);

  const selectedWall = useMemo(
    () => (selected?.type === "wall" ? state.walls.find((w) => w.clientId === selected.clientId) : null),
    [selected, state.walls]
  );
  const selectedOpening = useMemo(
    () => (selected?.type === "opening" ? state.openings.find((o) => o.clientId === selected.clientId) : null),
    [selected, state.openings]
  );

  if (loading) return <p className="muted">Loading…</p>;

  return (
    <div>
      <p>
        {level && <Link to={`/projects/${level.projectId}`}>← Back to project</Link>}
      </p>
      <h1>{level?.name ?? "Level"} — Canvas</h1>

      <div className="toolbar">
        {(["select", "wall", "door", "window", "delete"] as Tool[]).map((t) => (
          <button
            key={t}
            className={tool === t ? "toolbar-btn active" : "toolbar-btn"}
            onClick={() => {
              setTool(t);
              setWallDrawStart(null);
              setPreviewMm(null);
            }}
          >
            {t[0].toUpperCase() + t.slice(1)}
          </button>
        ))}
        <span className="toolbar-sep" />
        <button className="toolbar-btn" onClick={undo} disabled={past.length === 0}>
          Undo
        </button>
        <button className="toolbar-btn" onClick={redo} disabled={future.length === 0}>
          Redo
        </button>
        <span className="toolbar-sep" />
        <button className="toolbar-btn primary" onClick={save} disabled={saving}>
          {saving ? "Saving…" : "Save"}
        </button>
        {dirty && <span className="muted">unsaved changes</span>}
        {status && <span className="muted">{status}</span>}
      </div>

      {error && <div className="error">{error}</div>}

      <div className="canvas-layout">
        <canvas
          ref={canvasRef}
          width={CANVAS_W}
          height={CANVAS_H}
          className="floor-canvas"
          onPointerDown={onPointerDown}
          onPointerMove={onPointerMove}
          onPointerUp={onPointerUp}
        />
        <aside className="side-panel">
          <h3>Inspector</h3>
          {!selected && <p className="muted">Select a wall, door or window to edit it.</p>}
          {selectedWall && (
            <div>
              <p>
                <strong>Wall</strong>
              </p>
              <label>
                Thickness (mm)
                <input
                  type="number"
                  value={selectedWall.thicknessMm}
                  onChange={(e) =>
                    updateWallProp(selectedWall.clientId, { thicknessMm: Number(e.target.value) })
                  }
                />
              </label>
              <label>
                Height (mm)
                <input
                  type="number"
                  value={selectedWall.heightMm}
                  onChange={(e) =>
                    updateWallProp(selectedWall.clientId, { heightMm: Number(e.target.value) })
                  }
                />
              </label>
              <label>
                Kind
                <select
                  value={selectedWall.kind}
                  onChange={(e) =>
                    updateWallProp(selectedWall.clientId, { kind: e.target.value as WallKind })
                  }
                >
                  <option value="INTERIOR">Interior</option>
                  <option value="EXTERIOR">Exterior</option>
                  <option value="LOAD_BEARING">Load bearing</option>
                  <option value="PARTITION">Partition</option>
                </select>
              </label>
              <button className="toolbar-btn danger" onClick={deleteSelected}>
                Delete wall
              </button>
            </div>
          )}
          {selectedOpening && (
            <div>
              <p>
                <strong>{selectedOpening.type === "DOOR" ? "Door" : "Window"}</strong>
              </p>
              <label>
                Position from wall start (mm)
                <input
                  type="number"
                  value={Math.round(selectedOpening.offsetFromStartMm)}
                  onChange={(e) =>
                    updateOpeningProp(selectedOpening.clientId, {
                      offsetFromStartMm: Number(e.target.value),
                    })
                  }
                />
              </label>
              <label>
                Width (mm)
                <input
                  type="number"
                  value={selectedOpening.widthMm}
                  onChange={(e) =>
                    updateOpeningProp(selectedOpening.clientId, { widthMm: Number(e.target.value) })
                  }
                />
              </label>
              <button className="toolbar-btn danger" onClick={deleteSelected}>
                Delete {selectedOpening.type === "DOOR" ? "door" : "window"}
              </button>
            </div>
          )}
        </aside>
      </div>
      <p className="muted hint">
        Wall tool: click to start a wall, click again to finish it (click near an existing point to
        connect). Door/Window tool: click on a wall to add one. Select tool: drag points or
        doors/windows to move them.
      </p>
    </div>
  );
}

