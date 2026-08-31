// Stage 1 furniture catalog + 2D placement canvas for a single Room.
// Catalog products come from GET /api/products (optionally filtered by
// category server-side; name search is done client-side). Furniture layout
// is edited locally (move/rotate/scale/lock/delete) and saved as a whole via
// PUT /api/rooms/{roomId}/placements.
//
// Geometry stays in millimetres; screen pixels are derived via a pan/zoom
// ViewTransform (see ../canvas/canvasView.ts), matching the level canvas.
import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type PointerEvent as ReactPointerEvent,
} from "react";
import { Link, useParams } from "react-router-dom";
import { ProductApi, RoomApi, VendorApi } from "../api/endpoints";
import type {
  FurniturePlacementRequest,
  GeometryIssue,
  ProductResponse,
  RoomResponse,
  VendorResponse,
} from "../api/types";
import ValidationPanel from "../components/ValidationPanel";
import { SAVE_STATE_LABELS, useAutosave } from "../hooks/useAutosave";
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
import { normalizeAngle, toLocalFrame } from "../canvas/geometryMath";

interface LocalPlacement {
  clientId: string;
  serverId: string | null;
  product: ProductResponse;
  xMm: number;
  yMm: number;
  zMm: number;
  rotationAngle: number;
  scale: number;
  locked: boolean;
}

const BASE_PX_PER_MM = 0.15;
const CANVAS_W = 900;
const CANVAS_H = 600;
const MARGIN = 30;
const ORIGIN_X = MARGIN;
const ORIGIN_Y = MARGIN;
const DEFAULT_WIDTH_MM = 500;
const DEFAULT_DEPTH_MM = 500;
const SNAP_GRID_MM = 50;
// Static mm-space room boundary matching the original fixed-scale rectangle,
// so it pans/zooms together with the placements it outlines.
const ROOM_BOUNDS_MM = {
  minX: 0,
  minY: 0,
  maxX: (CANVAS_W - 2 * MARGIN) / BASE_PX_PER_MM,
  maxY: (CANVAS_H - 2 * MARGIN) / BASE_PX_PER_MM,
};

function newId() {
  return crypto.randomUUID();
}
function isTypingTarget(target: EventTarget | null) {
  const el = target as HTMLElement | null;
  return !!el && (el.tagName === "INPUT" || el.tagName === "TEXTAREA" || el.isContentEditable);
}

export default function RoomFurniturePage() {
  const { roomId } = useParams<{ roomId: string }>();
  const canvasRef = useRef<HTMLCanvasElement>(null);

  const [room, setRoom] = useState<RoomResponse | null>(null);
  const [products, setProducts] = useState<ProductResponse[]>([]);
  const [vendors, setVendors] = useState<Map<string, VendorResponse>>(new Map());
  const [categories, setCategories] = useState<string[]>([]);
  const [search, setSearch] = useState("");
  const [categoryFilter, setCategoryFilter] = useState<string>("");

  const [placements, setPlacements] = useState<LocalPlacement[]>([]);
  const [past, setPast] = useState<LocalPlacement[][]>([]);
  const [future, setFuture] = useState<LocalPlacement[][]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [warnings, setWarnings] = useState<GeometryIssue[]>([]);

  const [view, setView] = useState<ViewTransform>({ zoom: 1, panXPx: 0, panYPx: 0 });
  const [snapEnabled, setSnapEnabled] = useState(true);
  const [unit, setUnit] = useState<"mm" | "m">("mm");
  const [spacePanning, setSpacePanning] = useState(false);

  const dragRef = useRef<{
    kind: "move" | "rotate";
    clientId: string;
    before: LocalPlacement[];
  } | null>(null);
  const panRef = useRef<{ startXPx: number; startYPx: number; startPan: ViewTransform } | null>(null);

  // Indirection so the autosave hook can be wired up before `persistLayout`
  // (which needs the hook's own getGeneration/isStale) is defined below.
  const persistRef = useRef<() => Promise<void>>(async () => {});
  const { saveState, markDirty, saveNow, getGeneration, isStale } = useAutosave({
    save: () => persistRef.current(),
  });

  const toPx = useCallback(
    (xMm: number, yMm: number) => makeToPx(BASE_PX_PER_MM, view, ORIGIN_X, ORIGIN_Y)(xMm, yMm),
    [view]
  );
  const toMm = useCallback(
    (xPx: number, yPx: number) => makeToMm(BASE_PX_PER_MM, view, ORIGIN_X, ORIGIN_Y)(xPx, yPx),
    [view]
  );
  const scale = BASE_PX_PER_MM * view.zoom;

  // ---- Initial load ----
  useEffect(() => {
    if (!roomId) return;
    let cancelled = false;
    setLoading(true);
    setError(null);
    Promise.all([RoomApi.get(roomId), RoomApi.getPlacements(roomId), VendorApi.list(), ProductApi.list()])
      .then(([r, placementsRes, vendorList, productList]) => {
        if (cancelled) return;
        setRoom(r);
        setVendors(new Map(vendorList.map((v) => [v.id, v])));
        setProducts(productList);
        setCategories(Array.from(new Set(productList.map((p) => p.category))).sort());

        const productById = new Map(productList.map((p) => [p.id, p]));
        const local: LocalPlacement[] = placementsRes
          .map((pl): LocalPlacement | null => {
            const product = productById.get(pl.productId);
            if (!product) return null;
            return {
              clientId: newId(),
              serverId: pl.id,
              product,
              xMm: pl.xMm,
              yMm: pl.yMm,
              zMm: pl.zMm,
              rotationAngle: pl.rotationAngle,
              scale: pl.scale,
              locked: pl.locked,
            };
          })
          .filter((p): p is LocalPlacement => p !== null);
        setPlacements(local);
        setPast([]);
        setFuture([]);
      })
      .catch((err) => !cancelled && setError((err as { message?: string }).message ?? "Failed to load room"))
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
  }, [roomId]);

  // Re-fetch products when the category filter changes (server-side filter).
  useEffect(() => {
    ProductApi.list({ category: categoryFilter || undefined })
      .then(setProducts)
      .catch(() => {
        /* keep previous list on failure */
      });
  }, [categoryFilter]);

  const filteredProducts = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return products;
    return products.filter((p) => p.name.toLowerCase().includes(q));
  }, [products, search]);

  // ---- History ----
  function commit(next: LocalPlacement[]) {
    setPast((p) => [...p, placements]);
    setFuture([]);
    setPlacements(next);
    markDirty();
  }
  const undo = useCallback(() => {
    setPast((p) => {
      if (p.length === 0) return p;
      const prev = p[p.length - 1];
      setFuture((f) => [placements, ...f]);
      setPlacements(prev);
      return p.slice(0, -1);
    });
  }, [placements]);
  const redo = useCallback(() => {
    setFuture((f) => {
      if (f.length === 0) return f;
      const next = f[0];
      setPast((p) => [...p, placements]);
      setPlacements(next);
      return f.slice(1);
    });
  }, [placements]);

  // ---- Add from catalog ----
  function addToRoom(product: ProductResponse) {
    const placement: LocalPlacement = {
      clientId: newId(),
      serverId: null,
      product,
      xMm: (ROOM_BOUNDS_MM.minX + ROOM_BOUNDS_MM.maxX) / 2,
      yMm: (ROOM_BOUNDS_MM.minY + ROOM_BOUNDS_MM.maxY) / 2,
      zMm: 0,
      rotationAngle: 0,
      scale: 1,
      locked: false,
    };
    commit([...placements, placement]);
    setSelectedId(placement.clientId);
  }

  // ---- Hit testing ----
  function dims(p: LocalPlacement) {
    const w = (p.product.widthMm ?? DEFAULT_WIDTH_MM) * p.scale;
    const d = (p.product.depthMm ?? DEFAULT_DEPTH_MM) * p.scale;
    return { w, d };
  }
  function rotateHandlePx(p: LocalPlacement) {
    const { d } = dims(p);
    const center = toPx(p.xMm, p.yMm);
    const rad = (p.rotationAngle * Math.PI) / 180;
    const offset = (d / 2) * scale + 18;
    return { x: center.x + Math.sin(rad) * offset, y: center.y - Math.cos(rad) * offset };
  }
  function findAt(xPx: number, yPx: number): LocalPlacement | null {
    for (let i = placements.length - 1; i >= 0; i--) {
      const p = placements[i];
      const { w, d } = dims(p);
      const center = toPx(p.xMm, p.yMm);
      const { lx, ly } = toLocalFrame(xPx, yPx, center.x, center.y, p.rotationAngle);
      if (Math.abs(lx) <= (w * scale) / 2 && Math.abs(ly) <= (d * scale) / 2) {
        return p;
      }
    }
    return null;
  }

  function getCanvasPos(e: ReactPointerEvent<HTMLCanvasElement>) {
    const rect = canvasRef.current!.getBoundingClientRect();
    return { x: e.clientX - rect.left, y: e.clientY - rect.top };
  }

  function onPointerDown(e: ReactPointerEvent<HTMLCanvasElement>) {
    const { x, y } = getCanvasPos(e);

    if (e.button === 1 || spacePanning) {
      e.preventDefault();
      panRef.current = { startXPx: x, startYPx: y, startPan: view };
      return;
    }

    if (selectedId) {
      const selected = placements.find((p) => p.clientId === selectedId);
      if (selected && !selected.locked) {
        const handle = rotateHandlePx(selected);
        if (Math.hypot(handle.x - x, handle.y - y) <= 8) {
          dragRef.current = { kind: "rotate", clientId: selectedId, before: placements };
          return;
        }
      }
    }

    const hit = findAt(x, y);
    if (hit) {
      setSelectedId(hit.clientId);
      if (!hit.locked) {
        dragRef.current = { kind: "move", clientId: hit.clientId, before: placements };
      }
      return;
    }
    setSelectedId(null);
  }

  function onPointerMove(e: ReactPointerEvent<HTMLCanvasElement>) {
    const { x, y } = getCanvasPos(e);

    if (panRef.current) {
      const { startXPx, startYPx, startPan } = panRef.current;
      setView({ ...startPan, panXPx: startPan.panXPx + (x - startXPx), panYPx: startPan.panYPx + (y - startYPx) });
      return;
    }

    const drag = dragRef.current;
    if (!drag) return;

    if (drag.kind === "move") {
      const raw = toMm(x, y);
      const xMm = snapEnabled ? snapToGrid(raw.xMm, SNAP_GRID_MM) : raw.xMm;
      const yMm = snapEnabled ? snapToGrid(raw.yMm, SNAP_GRID_MM) : raw.yMm;
      setPlacements((ps) => ps.map((p) => (p.clientId === drag.clientId ? { ...p, xMm, yMm } : p)));
    } else {
      setPlacements((ps) =>
        ps.map((p) => {
          if (p.clientId !== drag.clientId) return p;
          const center = toPx(p.xMm, p.yMm);
          const dx = x - center.x;
          const dy = y - center.y;
          const angle = normalizeAngle((Math.atan2(dx, -dy) * 180) / Math.PI);
          return { ...p, rotationAngle: angle };
        })
      );
    }
  }

  function onPointerUp() {
    if (panRef.current) {
      panRef.current = null;
      return;
    }
    const drag = dragRef.current;
    if (drag) {
      setPast((p) => [...p, drag.before]);
      setFuture([]);
      dragRef.current = null;
      markDirty();
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
    setView((v) => zoomAt(v, BASE_PX_PER_MM, ORIGIN_X, ORIGIN_Y, CANVAS_W / 2, CANVAS_H / 2, factor));
  }

  function fitToViewport() {
    if (placements.length === 0) {
      setView(fitView(BASE_PX_PER_MM, ROOM_BOUNDS_MM, CANVAS_W, CANVAS_H));
      return;
    }
    let minX = Infinity;
    let maxX = -Infinity;
    let minY = Infinity;
    let maxY = -Infinity;
    for (const p of placements) {
      const { w, d } = dims(p);
      minX = Math.min(minX, p.xMm - w / 2);
      maxX = Math.max(maxX, p.xMm + w / 2);
      minY = Math.min(minY, p.yMm - d / 2);
      maxY = Math.max(maxY, p.yMm + d / 2);
    }
    minX = Math.min(minX, ROOM_BOUNDS_MM.minX);
    minY = Math.min(minY, ROOM_BOUNDS_MM.minY);
    maxX = Math.max(maxX, ROOM_BOUNDS_MM.maxX);
    maxY = Math.max(maxY, ROOM_BOUNDS_MM.maxY);
    setView(fitView(BASE_PX_PER_MM, { minX, maxX, minY, maxY }, CANVAS_W, CANVAS_H));
  }

  // ---- Keyboard shortcuts ----
  useEffect(() => {
    function onKeyDown(e: KeyboardEvent) {
      if (e.key === "Escape") {
        if (panRef.current) panRef.current = null;
        else if (selectedId) setSelectedId(null);
        return;
      }
      if (isTypingTarget(e.target)) return;
      if (e.key === "Delete" || e.key === "Backspace") {
        if (selectedId) {
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
      if (e.code === "Space") setSpacePanning(true);
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
  }, [selectedId, undo, redo]);

  // ---- Inspector actions ----
  function updateSelected(patch: Partial<LocalPlacement>) {
    if (!selectedId) return;
    commit(placements.map((p) => (p.clientId === selectedId ? { ...p, ...patch } : p)));
  }
  function deleteSelected() {
    if (!selectedId) return;
    commit(placements.filter((p) => p.clientId !== selectedId));
    setSelectedId(null);
  }
  function toggleLockSelected() {
    if (!selectedId) return;
    const p = placements.find((pl) => pl.clientId === selectedId);
    if (!p) return;
    updateSelected({ locked: !p.locked });
  }

  // ---- Save ----
  // Used by both the explicit Save button and the debounced autosave (see
  // ../hooks/useAutosave.ts); always reads `placements` fresh via the
  // closure recreated on every change (captured through persistRef), and
  // never overwrites local editor state with a server response if newer
  // edits happened meanwhile (see `isStale`).
  const persistLayout = useCallback(async () => {
    if (!roomId) return;
    setError(null);
    const token = getGeneration();
    try {
      const body: FurniturePlacementRequest[] = placements.map((p) => ({
        id: p.serverId,
        productId: p.product.id,
        xMm: p.xMm,
        yMm: p.yMm,
        zMm: p.zMm,
        rotationAngle: p.rotationAngle,
        scale: p.scale,
        locked: p.locked,
      }));
      const { placements: response, issues } = await RoomApi.savePlacements(roomId, body);
      const productById = new Map(products.map((prod) => [prod.id, prod]));
      // Re-fetch any products referenced by the response but missing from the
      // currently-filtered catalog list (e.g. filtered out by category).
      const missingIds = response.map((r) => r.productId).filter((id) => !productById.has(id));
      const missingProducts = await Promise.all(
        Array.from(new Set(missingIds)).map((id) => ProductApi.get(id))
      );
      missingProducts.forEach((p) => productById.set(p.id, p));

      // Only replace local editor state with the server's response if
      // nothing changed locally while this request was in flight — otherwise
      // we'd silently discard newer edits. The autosave hook detects the
      // same generation mismatch and keeps the document marked "unsaved" so
      // those newer edits get (re)saved shortly after.
      if (!isStale(token)) {
        setPlacements(
          response.map((r) => ({
            clientId: newId(),
            serverId: r.id,
            product: productById.get(r.productId)!,
            xMm: r.xMm,
            yMm: r.yMm,
            zMm: r.zMm,
            rotationAngle: r.rotationAngle,
            scale: r.scale,
            locked: r.locked,
          }))
        );
        setPast([]);
        setFuture([]);
        setSelectedId(null);
      }
      setWarnings(issues);
    } catch (err) {
      const apiErr = err as { message?: string; issues?: GeometryIssue[] };
      setError(apiErr.message ?? "Failed to save furniture layout");
      setWarnings(apiErr.issues ?? []);
      throw err;
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [roomId, placements, products, getGeneration, isStale]);

  useEffect(() => {
    persistRef.current = persistLayout;
  }, [persistLayout]);

  // ---- Validation issue highlighting/selection ----
  const issuesByPlacementId = useMemo(() => {
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
    const placement = placements.find((p) => p.serverId === issue.relatedEntityId);
    if (placement) setSelectedId(placement.clientId);
  }

  // ---- Rendering ----
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    ctx.clearRect(0, 0, CANVAS_W, CANVAS_H);
    ctx.fillStyle = "#fafaf7";
    ctx.fillRect(0, 0, CANVAS_W, CANVAS_H);

    // grid: adaptive spacing so lines stay ~60px apart at any zoom level.
    const stepMm = gridStepMm(scale);
    const stepPx = stepMm * scale;
    const originPx = toPx(0, 0);
    ctx.strokeStyle = "#eee";
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

    // room boundary (a fixed mm-space rectangle, panned/zoomed with content)
    const boundTL = toPx(ROOM_BOUNDS_MM.minX, ROOM_BOUNDS_MM.minY);
    const boundBR = toPx(ROOM_BOUNDS_MM.maxX, ROOM_BOUNDS_MM.maxY);
    ctx.strokeStyle = "#ddd";
    ctx.lineWidth = 2;
    ctx.strokeRect(boundTL.x, boundTL.y, boundBR.x - boundTL.x, boundBR.y - boundTL.y);

    for (const p of placements) {
      const { w, d } = dims(p);
      const center = toPx(p.xMm, p.yMm);
      const wPx = w * scale;
      const dPx = d * scale;
      const isSelected = selectedId === p.clientId;
      const issue = p.serverId ? issuesByPlacementId.get(p.serverId) : undefined;

      if (isSelected) {
        ctx.save();
        ctx.translate(center.x, center.y);
        ctx.rotate((p.rotationAngle * Math.PI) / 180);
        ctx.strokeStyle = "#2563eb";
        ctx.lineWidth = 2;
        ctx.setLineDash([4, 3]);
        ctx.strokeRect(-wPx / 2 - 4, -dPx / 2 - 4, wPx + 8, dPx + 8);
        ctx.setLineDash([]);
        ctx.restore();
      } else if (issue) {
        ctx.save();
        ctx.translate(center.x, center.y);
        ctx.rotate((p.rotationAngle * Math.PI) / 180);
        ctx.strokeStyle = issue.severity === "ERROR" ? "#dc2626" : "#d97706";
        ctx.lineWidth = 2;
        ctx.setLineDash([4, 3]);
        ctx.strokeRect(-wPx / 2 - 4, -dPx / 2 - 4, wPx + 8, dPx + 8);
        ctx.setLineDash([]);
        ctx.restore();
      }

      ctx.save();
      ctx.translate(center.x, center.y);
      ctx.rotate((p.rotationAngle * Math.PI) / 180);

      ctx.fillStyle = isSelected ? "#fde68a" : "#fbbf7a";
      ctx.strokeStyle = p.locked ? "#991b1b" : isSelected ? "#2563eb" : "#92400e";
      ctx.lineWidth = 2;
      ctx.fillRect(-wPx / 2, -dPx / 2, wPx, dPx);
      ctx.strokeRect(-wPx / 2, -dPx / 2, wPx, dPx);

      ctx.fillStyle = "#3f2d17";
      ctx.font = "10px sans-serif";
      ctx.textAlign = "center";
      ctx.fillText(p.product.name.slice(0, 16), 0, 4);
      ctx.restore();

      if (isSelected && !p.locked) {
        const handle = rotateHandlePx(p);
        ctx.beginPath();
        ctx.moveTo(center.x, center.y);
        ctx.lineTo(handle.x, handle.y);
        ctx.strokeStyle = "#2563eb";
        ctx.lineWidth = 1;
        ctx.stroke();
        ctx.fillStyle = "#2563eb";
        ctx.beginPath();
        ctx.arc(handle.x, handle.y, 6, 0, Math.PI * 2);
        ctx.fill();
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [placements, selectedId, view, scale, toPx, issuesByPlacementId]);

  const selected = placements.find((p) => p.clientId === selectedId) ?? null;
  const selectedDims = useMemo(() => (selected ? dims(selected) : null), [selected]);

  if (loading) return <p className="muted">Loading…</p>;

  return (
    <div>
      <p>{room && <Link to={`/levels/${room.levelId}/rooms`}>← Back to rooms</Link>}</p>
      <h1>{room?.name ?? "Room"} — Furniture</h1>

      <div className="toolbar">
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
        <button className="toolbar-btn primary" onClick={() => void saveNow()} disabled={saveState === "saving"}>
          {saveState === "saving" ? "Saving…" : "Save"}
        </button>
        <span className={`save-status save-status-${saveState}`}>{SAVE_STATE_LABELS[saveState]}</span>
      </div>

      {error && <div className="error">{error}</div>}
      <ValidationPanel title="Layout validation" issues={warnings} onSelect={selectIssue} />

      <div className="room-layout">
        <aside className="catalog-panel">
          <h3>Catalog</h3>
          <input
            className="catalog-search"
            placeholder="Search by name…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
          <select value={categoryFilter} onChange={(e) => setCategoryFilter(e.target.value)}>
            <option value="">All categories</option>
            {categories.map((c) => (
              <option key={c} value={c}>
                {c}
              </option>
            ))}
          </select>
          <div className="catalog-list">
            {filteredProducts.map((p) => (
              <div key={p.id} className="catalog-item">
                <div className="catalog-thumb">
                  {p.primaryImageUrl ? (
                    <img src={p.primaryImageUrl} alt={p.name} />
                  ) : (
                    <div className="catalog-thumb-placeholder">{p.category?.[0] ?? "?"}</div>
                  )}
                </div>
                <div className="catalog-info">
                  <div className="catalog-name">{p.name}</div>
                  <div className="muted">{vendors.get(p.vendorId)?.name ?? "Unknown vendor"}</div>
                  <div className="muted">
                    {p.widthMm ?? "?"}×{p.depthMm ?? "?"}×{p.heightMm ?? "?"} mm
                  </div>
                  <div className="catalog-price">
                    {p.priceAmount != null ? `${p.priceAmount} ${p.priceCurrency ?? ""}` : "—"}
                  </div>
                </div>
                <button className="toolbar-btn" onClick={() => addToRoom(p)}>
                  Add
                </button>
              </div>
            ))}
            {filteredProducts.length === 0 && <p className="muted">No products found.</p>}
          </div>
        </aside>

        <canvas
          ref={canvasRef}
          width={CANVAS_W}
          height={CANVAS_H}
          className="floor-canvas furniture-canvas"
          style={{ cursor: spacePanning ? "grab" : "default" }}
          onPointerDown={onPointerDown}
          onPointerMove={onPointerMove}
          onPointerUp={onPointerUp}
          onPointerLeave={onPointerUp}
          onContextMenu={(e) => e.preventDefault()}
        />

        <aside className="side-panel">
          <h3>Inspector</h3>
          {!selected && <p className="muted">Select a furniture item to edit it.</p>}
          {selected && (
            <div>
              <p>
                <strong>{selected.product.name}</strong>
              </p>
              {selectedDims && (
                <p className="muted">
                  {formatLength(selectedDims.w, unit)} × {formatLength(selectedDims.d, unit)}
                </p>
              )}
              <label>
                X (mm)
                <input
                  type="number"
                  value={Math.round(selected.xMm)}
                  disabled={selected.locked}
                  onChange={(e) => updateSelected({ xMm: Number(e.target.value) })}
                />
              </label>
              <label>
                Y (mm)
                <input
                  type="number"
                  value={Math.round(selected.yMm)}
                  disabled={selected.locked}
                  onChange={(e) => updateSelected({ yMm: Number(e.target.value) })}
                />
              </label>
              <label>
                Rotation (°)
                <input
                  type="number"
                  value={Math.round(selected.rotationAngle)}
                  disabled={selected.locked}
                  onChange={(e) => updateSelected({ rotationAngle: normalizeAngle(Number(e.target.value)) })}
                />
              </label>
              <label>
                Scale
                <input
                  type="number"
                  step="0.1"
                  min="0.1"
                  value={selected.scale}
                  disabled={selected.locked}
                  onChange={(e) => updateSelected({ scale: Math.max(0.1, Number(e.target.value)) })}
                />
              </label>
              <button className="toolbar-btn" onClick={toggleLockSelected}>
                {selected.locked ? "Unlock" : "Lock"}
              </button>
              <button className="toolbar-btn danger" onClick={deleteSelected} disabled={selected.locked}>
                Delete
              </button>
            </div>
          )}
        </aside>
      </div>
      <p className="muted hint">
        Click “Add” on a catalog item to place it in the room. Drag furniture to move it, drag the
        blue handle to rotate. Locked items can’t be moved, rotated, scaled or deleted until
        unlocked. Scroll to zoom (centered on cursor); hold Space or middle-click drag to pan. Esc
        deselects, Delete/Backspace removes the selection, Ctrl/Cmd+Z undoes, Ctrl/Cmd+Shift+Z or
        Ctrl/Cmd+Y redoes.
      </p>
    </div>
  );
}


