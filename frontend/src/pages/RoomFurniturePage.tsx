// Stage 1 furniture catalog + 2D placement canvas for a single Room.
// Catalog products come from GET /api/products (optionally filtered by
// category server-side; name search is done client-side). Furniture layout
// is edited locally (move/rotate/scale/lock/delete) and saved as a whole via
// PUT /api/rooms/{roomId}/placements.
import { useEffect, useMemo, useRef, useState, type PointerEvent as ReactPointerEvent } from "react";
import { Link, useParams } from "react-router-dom";
import { ProductApi, RoomApi, VendorApi } from "../api/endpoints";
import type {
  FurniturePlacementRequest,
  ProductResponse,
  RoomResponse,
  VendorResponse,
} from "../api/types";

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

const PX_PER_MM = 0.15;
const CANVAS_W = 900;
const CANVAS_H = 600;
const MARGIN = 30;
const DEFAULT_WIDTH_MM = 500;
const DEFAULT_DEPTH_MM = 500;

function toPx(xMm: number, yMm: number) {
  return { x: MARGIN + xMm * PX_PER_MM, y: MARGIN + yMm * PX_PER_MM };
}
function toMm(xPx: number, yPx: number) {
  return { xMm: (xPx - MARGIN) / PX_PER_MM, yMm: (yPx - MARGIN) / PX_PER_MM };
}
function newId() {
  return crypto.randomUUID();
}
function normalizeAngle(deg: number) {
  let a = deg % 360;
  if (a < 0) a += 360;
  return a;
}

/** Converts a point in mm-space into a placement's local (unrotated) frame,
 * so hit-testing can use a simple axis-aligned bounds check. */
function toLocalFrame(px: number, py: number, cx: number, cy: number, angleDeg: number) {
  const rad = (-angleDeg * Math.PI) / 180;
  const dx = px - cx;
  const dy = py - cy;
  return {
    lx: dx * Math.cos(rad) - dy * Math.sin(rad),
    ly: dx * Math.sin(rad) + dy * Math.cos(rad),
  };
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
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [status, setStatus] = useState<string | null>(null);
  const [warnings, setWarnings] = useState<string[]>([]);

  const dragRef = useRef<{
    kind: "move" | "rotate";
    clientId: string;
    before: LocalPlacement[];
  } | null>(null);

  const dirty = past.length > 0;

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
  }
  function undo() {
    setPast((p) => {
      if (p.length === 0) return p;
      const prev = p[p.length - 1];
      setFuture((f) => [placements, ...f]);
      setPlacements(prev);
      return p.slice(0, -1);
    });
  }
  function redo() {
    setFuture((f) => {
      if (f.length === 0) return f;
      const next = f[0];
      setPast((p) => [...p, placements]);
      setPlacements(next);
      return f.slice(1);
    });
  }

  // ---- Add from catalog ----
  function addToRoom(product: ProductResponse) {
    const placement: LocalPlacement = {
      clientId: newId(),
      serverId: null,
      product,
      xMm: (CANVAS_W - 2 * MARGIN) / 2 / PX_PER_MM,
      yMm: (CANVAS_H - 2 * MARGIN) / 2 / PX_PER_MM,
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
    const offset = (d / 2) * PX_PER_MM + 18;
    return { x: center.x + Math.sin(rad) * offset, y: center.y - Math.cos(rad) * offset };
  }
  function findAt(xPx: number, yPx: number): LocalPlacement | null {
    for (let i = placements.length - 1; i >= 0; i--) {
      const p = placements[i];
      const { w, d } = dims(p);
      const center = toPx(p.xMm, p.yMm);
      const { lx, ly } = toLocalFrame(xPx, yPx, center.x, center.y, p.rotationAngle);
      if (Math.abs(lx) <= (w * PX_PER_MM) / 2 && Math.abs(ly) <= (d * PX_PER_MM) / 2) {
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
    const drag = dragRef.current;
    if (!drag) return;
    const { x, y } = getCanvasPos(e);

    if (drag.kind === "move") {
      const { xMm, yMm } = toMm(x, y);
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
    const drag = dragRef.current;
    if (drag) {
      setPast((p) => [...p, drag.before]);
      setFuture([]);
      dragRef.current = null;
    }
  }

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
  async function save() {
    if (!roomId) return;
    setSaving(true);
    setError(null);
    setStatus(null);
    setWarnings([]);
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
      const { placements: response, warnings: geometryWarnings } = await RoomApi.savePlacements(
        roomId,
        body
      );
      const productById = new Map(products.map((prod) => [prod.id, prod]));
      // Re-fetch any products referenced by the response but missing from the
      // currently-filtered catalog list (e.g. filtered out by category).
      const missingIds = response.map((r) => r.productId).filter((id) => !productById.has(id));
      const missingProducts = await Promise.all(
        Array.from(new Set(missingIds)).map((id) => ProductApi.get(id))
      );
      missingProducts.forEach((p) => productById.set(p.id, p));

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
      setStatus("Saved");
      setWarnings(geometryWarnings);
    } catch (err) {
      setError((err as { message?: string }).message ?? "Failed to save furniture layout");
    } finally {
      setSaving(false);
    }
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
    ctx.strokeStyle = "#ddd";
    ctx.lineWidth = 2;
    ctx.strokeRect(MARGIN, MARGIN, CANVAS_W - 2 * MARGIN, CANVAS_H - 2 * MARGIN);

    for (const p of placements) {
      const { w, d } = dims(p);
      const center = toPx(p.xMm, p.yMm);
      const wPx = w * PX_PER_MM;
      const dPx = d * PX_PER_MM;
      const isSelected = selectedId === p.clientId;

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
  }, [placements, selectedId]);

  const selected = placements.find((p) => p.clientId === selectedId) ?? null;

  if (loading) return <p className="muted">Loading…</p>;

  return (
    <div>
      <p>{room && <Link to={`/levels/${room.levelId}/rooms`}>← Back to rooms</Link>}</p>
      <h1>{room?.name ?? "Room"} — Furniture</h1>

      <div className="toolbar">
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
      {warnings.length > 0 && (
        <div className="warning-banner">
          <strong>Layout saved with warnings:</strong>
          <ul>
            {warnings.map((w, i) => (
              <li key={i}>{w}</li>
            ))}
          </ul>
        </div>
      )}

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
          onPointerDown={onPointerDown}
          onPointerMove={onPointerMove}
          onPointerUp={onPointerUp}
        />

        <aside className="side-panel">
          <h3>Inspector</h3>
          {!selected && <p className="muted">Select a furniture item to edit it.</p>}
          {selected && (
            <div>
              <p>
                <strong>{selected.product.name}</strong>
              </p>
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
        unlocked.
      </p>
    </div>
  );
}


