// Stage 1 2D floorplan canvas: draw/edit walls, doors and windows for a
// single Level, with local-only editing (undo/redo) and explicit Save/Load
// against PUT/GET /api/levels/{levelId}/geometry.
//
// Geometry is kept in millimetres (matching the backend model). Screen
// pixels are derived from mm via a pan/zoom ViewTransform (see
// ../canvas/canvasView.ts) — the geometry itself never stores pixels.
// Local entities use a stable `clientId` for internal references;
// `serverId` is null until the entity has been persisted at least once.
import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type PointerEvent as ReactPointerEvent,
} from "react";
import { Link, useParams } from "react-router-dom";
import { LevelApi } from "../api/endpoints";
import type {
  GeometryIssue,
  LevelGeometryRequest,
  LevelGeometryResponse,
  LevelResponse,
  OpeningType,
  WallKind,
} from "../api/types";
import ValidationPanel from "../components/ValidationPanel";
import {
  fitView,
  formatLength,
  gridStepMm,
  makeToMm,
  makeToPx,
  snapToGrid,
  zoomAt,
  type ViewTransform,
} from "../canvas/canvasView";

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

type Tool = "select" | "pan" | "wall" | "door" | "window" | "delete";
type Selection =
  | { type: "node"; clientId: string }
  | { type: "wall"; clientId: string }
  | { type: "opening"; clientId: string }
  | null;
type LengthUnit = "mm" | "m";

const BASE_PX_PER_MM = 0.15;
const NODE_HIT_PX = 9;
const WALL_HIT_PX = 10;
const SNAP_GRID_MM = 50;
const DEFAULT_WALL_THICKNESS_MM = 150;
const DEFAULT_WALL_HEIGHT_MM = 2700;
const DEFAULT_DOOR_WIDTH_MM = 900;
const DEFAULT_WINDOW_WIDTH_MM = 1200;
const DEFAULT_OPENING_HEIGHT_MM = 1200;

const CANVAS_W = 1100;
const CANVAS_H = 700;
const ORIGIN_X = CANVAS_W / 2;
const ORIGIN_Y = CANVAS_H / 2;

function dist(ax: number, ay: number, bx: number, by: number) {
  return Math.hypot(ax - bx, ay - by);
}
function newId() {
  return crypto.randomUUID();
}
function isTypingTarget(target: EventTarget | null) {
  const el = target as HTMLElement | null;
  return !!el && (el.tagName === "INPUT" || el.tagName === "TEXTAREA" || el.isContentEditable);
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
  const [warnings, setWarnings] = useState<GeometryIssue[]>([]);

  const [view, setView] = useState<ViewTransform>({ zoom: 1, panXPx: 0, panYPx: 0 });
  const [snapEnabled, setSnapEnabled] = useState(true);
  const [unit, setUnit] = useState<LengthUnit>("mm");
  const [spacePanning, setSpacePanning] = useState(false);

  const dragRef = useRef<{
    kind: "node" | "opening";
    clientId: string;
    beforeState: GeometryState;
  } | null>(null);
  const panRef = useRef<{ startXPx: number; startYPx: number; startPan: ViewTransform } | null>(null);

  const dirty = past.length > 0;

  const toPx = useCallback(
    (xMm: number, yMm: number) => makeToPx(BASE_PX_PER_MM, view, ORIGIN_X, ORIGIN_Y)(xMm, yMm),
    [view]
  );
  const toMm = useCallback(
    (xPx: number, yPx: number) => makeToMm(BASE_PX_PER_MM, view, ORIGIN_X, ORIGIN_Y)(xPx, yPx),
    [view]
  );
  const scale = BASE_PX_PER_MM * view.zoom;

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
  const undo = useCallback(() => {
    setPast((p) => {
      if (p.length === 0) return p;
      const prev = p[p.length - 1];
      setFuture((f) => [state, ...f]);
      setState(prev);
      return p.slice(0, -1);
    });
    setSelected(null);
  }, [state]);
  const redo = useCallback(() => {
    setFuture((f) => {
      if (f.length === 0) return f;
      const next = f[0];
      setPast((p) => [...p, state]);
      setState(next);
      return f.slice(1);
    });
    setSelected(null);
  }, [state]);

  // ---- Hit testing ----
  function findNodeAt(xPx: number, yPx: number, excludeClientId?: string): LocalNode | null {
    for (const n of state.nodes) {
      if (n.clientId === excludeClientId) continue;
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
      const threshold = Math.max(WALL_HIT_PX, (w.thicknessMm * scale) / 2 + 4);
      if (proj.distance <= threshold && proj.distance < bestDist) {
        best = w;
        bestDist = proj.distance;
      }
    }
    return best;
  }

  function nearestSnapNode(xPx: number, yPx: number, excludeClientId?: string): LocalNode | null {
    return findNodeAt(xPx, yPx, excludeClientId);
  }

  /** Snaps a world-space point to nearby wall endpoints first, then to the
   * snap grid (if enabled), then leaves it as-is. */
  function resolveDropPoint(xPx: number, yPx: number, excludeClientId?: string) {
    const snapped = nearestSnapNode(xPx, yPx, excludeClientId);
    if (snapped) return { xMm: snapped.xMm, yMm: snapped.yMm, snappedToNode: true as const };
    const raw = toMm(xPx, yPx);
    if (snapEnabled) {
      return {
        xMm: snapToGrid(raw.xMm, SNAP_GRID_MM),
        yMm: snapToGrid(raw.yMm, SNAP_GRID_MM),
        snappedToNode: false as const,
      };
    }
    return { ...raw, snappedToNode: false as const };
  }

  // ---- Canvas pointer handlers ----
  function getCanvasPos(e: ReactPointerEvent<HTMLCanvasElement>) {
    const rect = canvasRef.current!.getBoundingClientRect();
    return { x: e.clientX - rect.left, y: e.clientY - rect.top };
  }

  function beginPan(x: number, y: number) {
    panRef.current = { startXPx: x, startYPx: y, startPan: view };
  }

  function onPointerDown(e: ReactPointerEvent<HTMLCanvasElement>) {
    const { x, y } = getCanvasPos(e);

    // Middle-click, or space+left-click, or the Pan tool: pan the viewport
    // regardless of the active drawing tool.
    if (e.button === 1 || spacePanning || tool === "pan") {
      e.preventDefault();
      beginPan(x, y);
      return;
    }

    if (tool === "wall") {
      const dropped = resolveDropPoint(x, y);
      if (!wallDrawStart) {
        let startClientId: string;
        if (dropped.snappedToNode) {
          startClientId = nearestSnapNode(x, y)!.clientId;
        } else {
          const node: LocalNode = { clientId: newId(), serverId: null, xMm: dropped.xMm, yMm: dropped.yMm, zMm: 0 };
          commit({ ...state, nodes: [...state.nodes, node] });
          startClientId = node.clientId;
        }
        setWallDrawStart(startClientId);
        setPreviewMm({ xMm: dropped.xMm, yMm: dropped.yMm });
      } else {
        let endClientId: string;
        let base = state;
        const snappedNode = nearestSnapNode(x, y);
        if (snappedNode && snappedNode.clientId !== wallDrawStart) {
          endClientId = snappedNode.clientId;
        } else if (snappedNode && snappedNode.clientId === wallDrawStart) {
          // clicked same node again: cancel
          setWallDrawStart(null);
          setPreviewMm(null);
          return;
        } else {
          const node: LocalNode = { clientId: newId(), serverId: null, xMm: dropped.xMm, yMm: dropped.yMm, zMm: 0 };
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

    if (panRef.current) {
      const { startXPx, startYPx, startPan } = panRef.current;
      setView({ ...startPan, panXPx: startPan.panXPx + (x - startXPx), panYPx: startPan.panYPx + (y - startYPx) });
      return;
    }

    if (tool === "wall" && wallDrawStart) {
      const dropped = resolveDropPoint(x, y, wallDrawStart);
      setPreviewMm({ xMm: dropped.xMm, yMm: dropped.yMm });
      return;
    }

    const drag = dragRef.current;
    if (!drag) return;

    if (drag.kind === "node") {
      const dropped = resolveDropPoint(x, y, drag.clientId);
      setState((s) => ({
        ...s,
        nodes: s.nodes.map((n) => (n.clientId === drag.clientId ? { ...n, xMm: dropped.xMm, yMm: dropped.yMm } : n)),
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
    if (panRef.current) {
      panRef.current = null;
      return;
    }
    const drag = dragRef.current;
    if (drag) {
      setPast((p) => [...p, drag.beforeState]);
      setFuture([]);
      dragRef.current = null;
    }
  }

  // ---- Wheel zoom (centered on cursor) ----
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const handler = (e: WheelEvent) => {
      e.preventDefault();
      const rect = canvas.getBoundingClientRect();
      const cursorX = e.clientX - rect.left;
      const cursorY = e.clientY - rect.top;
      const factor = Math.exp(-e.deltaY * 0.0015);
      setView((v) => zoomAt(v, BASE_PX_PER_MM, ORIGIN_X, ORIGIN_Y, cursorX, cursorY, factor));
    };
    canvas.addEventListener("wheel", handler, { passive: false });
    return () => canvas.removeEventListener("wheel", handler);
  }, []);

  function zoomButton(factor: number) {
    setView((v) => zoomAt(v, BASE_PX_PER_MM, ORIGIN_X, ORIGIN_Y, ORIGIN_X, ORIGIN_Y, factor));
  }

  function fitToViewport() {
    if (state.nodes.length === 0) {
      setView({ zoom: 1, panXPx: 0, panYPx: 0 });
      return;
    }
    let minX = Infinity;
    let maxX = -Infinity;
    let minY = Infinity;
    let maxY = -Infinity;
    for (const n of state.nodes) {
      minX = Math.min(minX, n.xMm);
      maxX = Math.max(maxX, n.xMm);
      minY = Math.min(minY, n.yMm);
      maxY = Math.max(maxY, n.yMm);
    }
    setView(fitView(BASE_PX_PER_MM, { minX, maxX, minY, maxY }, CANVAS_W, CANVAS_H));
  }

  // ---- Keyboard shortcuts ----
  useEffect(() => {
    function onKeyDown(e: KeyboardEvent) {
      if (e.key === "Escape") {
        if (wallDrawStart) {
          setWallDrawStart(null);
          setPreviewMm(null);
        } else if (panRef.current) {
          panRef.current = null;
        } else if (tool !== "select") {
          setTool("select");
        } else if (selected) {
          setSelected(null);
        }
        return;
      }
      if (isTypingTarget(e.target)) return;
      if (e.key === "Delete" || e.key === "Backspace") {
        if (selected) {
          e.preventDefault();
          deleteSelected();
        }
        return;
      }
      const mod = e.ctrlKey || e.metaKey;
      if (mod && !e.altKey && (e.key === "z" || e.key === "Z")) {
        e.preventDefault();
        if (e.shiftKey) redo();
        else undo();
        return;
      }
      if (mod && !e.altKey && (e.key === "y" || e.key === "Y")) {
        e.preventDefault();
        redo();
        return;
      }
      if (e.code === "Space") {
        setSpacePanning(true);
      }
    }
    function onKeyUp(e: KeyboardEvent) {
      if (e.code === "Space") setSpacePanning(false);
    }
    window.addEventListener("keydown", onKeyDown);
    window.addEventListener("keyup", onKeyUp);
    return () => {
      window.removeEventListener("keydown", onKeyDown);
      window.removeEventListener("keyup", onKeyUp);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selected, wallDrawStart, tool, undo, redo]);

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
  function updateNodeProp(clientId: string, patch: Partial<LocalNode>) {
    commit({
      ...state,
      nodes: state.nodes.map((n) => (n.clientId === clientId ? { ...n, ...patch } : n)),
    });
  }
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
    setWarnings([]);
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
      setWarnings(finalResponse.issues ?? []);
    } catch (err) {
      const apiErr = err as { message?: string; issues?: GeometryIssue[] };
      setError(apiErr.message ?? "Failed to save geometry");
      // Structural (ERROR-severity) findings that made the backend reject
      // the save — surfaced the same way as post-save warnings so they can
      // be inspected/highlighted, even though nothing was persisted.
      setWarnings(apiErr.issues ?? []);
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

  // ---- Validation issue highlighting/selection ----
  // Maps each entity's server id to the worst-severity issue that concerns
  // it, so the canvas can highlight offending nodes/walls/openings without
  // re-deriving anything the Geometry Engine already computed.
  const issuesByEntityId = useMemo(() => {
    const map = new Map<string, GeometryIssue>();
    for (const issue of warnings) {
      if (!issue.relatedEntityId) continue;
      const existing = map.get(issue.relatedEntityId);
      if (!existing || (existing.severity !== "ERROR" && issue.severity === "ERROR")) {
        map.set(issue.relatedEntityId, issue);
      }
    }
    return map;
  }, [warnings]);

  function selectIssue(issue: GeometryIssue) {
    if (!issue.relatedEntityId) return;
    const node = state.nodes.find((n) => n.serverId === issue.relatedEntityId);
    if (node) {
      setSelected({ type: "node", clientId: node.clientId });
      return;
    }
    const wall = state.walls.find((w) => w.serverId === issue.relatedEntityId);
    if (wall) {
      setSelected({ type: "wall", clientId: wall.clientId });
      return;
    }
    const opening = state.openings.find((o) => o.serverId === issue.relatedEntityId);
    if (opening) {
      setSelected({ type: "opening", clientId: opening.clientId });
    }
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

    // grid: adaptive spacing so lines stay ~60px apart at any zoom level.
    const stepMm = gridStepMm(scale);
    const originPx = toPx(0, 0);
    const stepPx = stepMm * scale;
    ctx.strokeStyle = "#e6e6ee";
    ctx.lineWidth = 1;
    const firstGx = ((originPx.x % stepPx) + stepPx) % stepPx;
    for (let gx = firstGx; gx < CANVAS_W; gx += stepPx) {
      ctx.beginPath();
      ctx.moveTo(gx, 0);
      ctx.lineTo(gx, CANVAS_H);
      ctx.stroke();
    }
    const firstGy = ((originPx.y % stepPx) + stepPx) % stepPx;
    for (let gy = firstGy; gy < CANVAS_H; gy += stepPx) {
      ctx.beginPath();
      ctx.moveTo(0, gy);
      ctx.lineTo(CANVAS_W, gy);
      ctx.stroke();
    }
    // origin axes, slightly darker, to help orient panning/zooming.
    ctx.strokeStyle = "#d3d3e0";
    ctx.beginPath();
    ctx.moveTo(originPx.x, 0);
    ctx.lineTo(originPx.x, CANVAS_H);
    ctx.moveTo(0, originPx.y);
    ctx.lineTo(CANVAS_W, originPx.y);
    ctx.stroke();

    // walls
    for (const w of state.walls) {
      const start = state.nodes.find((n) => n.clientId === w.startClientId);
      const end = state.nodes.find((n) => n.clientId === w.endClientId);
      if (!start || !end) continue;
      const sp = toPx(start.xMm, start.yMm);
      const ep = toPx(end.xMm, end.yMm);
      const isSelected = selected?.type === "wall" && selected.clientId === w.clientId;
      const issue = w.serverId ? issuesByEntityId.get(w.serverId) : undefined;
      ctx.strokeStyle = isSelected
        ? "#2563eb"
        : issue?.severity === "ERROR"
          ? "#dc2626"
          : issue
            ? "#d97706"
            : "#333";
      ctx.lineWidth = Math.max(2, w.thicknessMm * scale);
      ctx.beginPath();
      ctx.moveTo(sp.x, sp.y);
      ctx.lineTo(ep.x, ep.y);
      ctx.stroke();

      const lengthMm = dist(start.xMm, start.yMm, end.xMm, end.yMm);
      ctx.fillStyle = isSelected ? "#2563eb" : "#555";
      ctx.font = "11px sans-serif";
      ctx.fillText(formatLength(lengthMm, unit), (sp.x + ep.x) / 2 + 6, (sp.y + ep.y) / 2 - 6);
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
      const issue = o.serverId ? issuesByEntityId.get(o.serverId) : undefined;
      ctx.strokeStyle = isSelected
        ? "#2563eb"
        : issue?.severity === "ERROR"
          ? "#dc2626"
          : issue
            ? "#d97706"
            : o.type === "DOOR"
              ? "#a0522d"
              : "#3b82f6";
      ctx.lineWidth = Math.max(4, wall.thicknessMm * scale + 2);
      ctx.beginPath();
      ctx.moveTo(hx0, hy0);
      ctx.lineTo(hx1, hy1);
      ctx.stroke();
    }

    // nodes
    for (const n of state.nodes) {
      const p = toPx(n.xMm, n.yMm);
      const isSelected = selected?.type === "node" && selected.clientId === n.clientId;
      const issue = n.serverId ? issuesByEntityId.get(n.serverId) : undefined;
      if (isSelected || issue) {
        ctx.strokeStyle = isSelected ? "#2563eb" : issue!.severity === "ERROR" ? "#dc2626" : "#d97706";
        ctx.lineWidth = 2;
        ctx.beginPath();
        ctx.arc(p.x, p.y, 8, 0, Math.PI * 2);
        ctx.stroke();
      }
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

        const previewLenMm = dist(start.xMm, start.yMm, previewMm.xMm, previewMm.yMm);
        ctx.fillStyle = "#475569";
        ctx.fillText(formatLength(previewLenMm, unit), (sp.x + ep.x) / 2 + 6, (sp.y + ep.y) / 2 - 6);
      }
    }
  }, [state, selected, tool, wallDrawStart, previewMm, view, unit, scale, toPx, issuesByEntityId]);

  const selectedNode = useMemo(
    () => (selected?.type === "node" ? state.nodes.find((n) => n.clientId === selected.clientId) : null),
    [selected, state.nodes]
  );
  const selectedWall = useMemo(
    () => (selected?.type === "wall" ? state.walls.find((w) => w.clientId === selected.clientId) : null),
    [selected, state.walls]
  );
  const selectedOpening = useMemo(
    () => (selected?.type === "opening" ? state.openings.find((o) => o.clientId === selected.clientId) : null),
    [selected, state.openings]
  );
  const selectedWallLengthMm = useMemo(() => {
    if (!selectedWall) return null;
    const start = state.nodes.find((n) => n.clientId === selectedWall.startClientId);
    const end = state.nodes.find((n) => n.clientId === selectedWall.endClientId);
    if (!start || !end) return null;
    return dist(start.xMm, start.yMm, end.xMm, end.yMm);
  }, [selectedWall, state.nodes]);

  if (loading) return <p className="muted">Loading…</p>;

  const cursorStyle = tool === "pan" || spacePanning ? "grab" : tool === "select" ? "default" : "crosshair";

  return (
    <div>
      <p>
        {level && <Link to={`/projects/${level.projectId}`}>← Back to project</Link>}
        {level && (
          <>
            {" · "}
            <Link to={`/levels/${level.id}/rooms`}>Rooms & furniture →</Link>
          </>
        )}
      </p>
      <h1>{level?.name ?? "Level"} — Canvas</h1>

      <div className="toolbar">
        {(["select", "wall", "door", "window", "delete", "pan"] as Tool[]).map((t) => (
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
        <button className="toolbar-btn" onClick={() => zoomButton(1 / 1.25)} title="Zoom out">
          −
        </button>
        <span className="muted" style={{ minWidth: 44, textAlign: "center", display: "inline-block" }}>
          {Math.round(view.zoom * 100)}%
        </span>
        <button className="toolbar-btn" onClick={() => zoomButton(1.25)} title="Zoom in">
          +
        </button>
        <button className="toolbar-btn" onClick={fitToViewport} title="Fit design to viewport">
          Fit
        </button>
        <span className="toolbar-sep" />
        <label className="muted" style={{ display: "inline-flex", alignItems: "center", gap: 4 }}>
          <input type="checkbox" checked={snapEnabled} onChange={(e) => setSnapEnabled(e.target.checked)} />
          Snap to grid ({SNAP_GRID_MM} mm)
        </label>
        <button className="toolbar-btn" onClick={() => setUnit(unit === "mm" ? "m" : "mm")} title="Toggle length unit">
          Units: {unit}
        </button>
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
      <ValidationPanel title="Geometry validation" issues={warnings} onSelect={selectIssue} />

      <div className="canvas-layout">
        <canvas
          ref={canvasRef}
          width={CANVAS_W}
          height={CANVAS_H}
          className="floor-canvas"
          style={{ cursor: cursorStyle }}
          onPointerDown={onPointerDown}
          onPointerMove={onPointerMove}
          onPointerUp={onPointerUp}
          onPointerLeave={onPointerUp}
          onContextMenu={(e) => e.preventDefault()}
        />
        <aside className="side-panel">
          <h3>Inspector</h3>
          {!selected && <p className="muted">Select a point, wall, door or window to edit it.</p>}
          {selectedNode && (
            <div>
              <p>
                <strong>Point</strong>
              </p>
              <label>
                X (mm)
                <input
                  type="number"
                  value={Math.round(selectedNode.xMm)}
                  onChange={(e) => updateNodeProp(selectedNode.clientId, { xMm: Number(e.target.value) })}
                />
              </label>
              <label>
                Y (mm)
                <input
                  type="number"
                  value={Math.round(selectedNode.yMm)}
                  onChange={(e) => updateNodeProp(selectedNode.clientId, { yMm: Number(e.target.value) })}
                />
              </label>
              <button className="toolbar-btn danger" onClick={deleteSelected}>
                Delete point
              </button>
            </div>
          )}
          {selectedWall && (
            <div>
              <p>
                <strong>Wall</strong>
              </p>
              {selectedWallLengthMm != null && (
                <p className="muted">Length: {formatLength(selectedWallLengthMm, unit)}</p>
              )}
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
        doors/windows to move them. Scroll to zoom (centered on cursor); hold Space, use the Pan
        tool, or middle-click drag to pan. Esc cancels the current tool/action, Delete/Backspace
        removes the selection, Ctrl/Cmd+Z undoes, Ctrl/Cmd+Shift+Z or Ctrl/Cmd+Y redoes.
      </p>
    </div>
  );
}

