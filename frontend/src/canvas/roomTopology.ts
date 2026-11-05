export interface TopologyPoint {
  id: string;
  xMm: number;
  yMm: number;
}

export interface TopologyWall {
  id: string;
  startNodeId: string;
  endNodeId: string;
}

export interface OrderedBoundary {
  wallIds: string[];
  nodeIds: string[];
  polygon: TopologyPoint[];
  areaMm2: number;
}

interface HalfEdge {
  wallId: string;
  fromId: string;
  toId: string;
}

const EPSILON = 0.001;

function signedArea(points: TopologyPoint[]) {
  let area = 0;
  for (let index = 0; index < points.length; index += 1) {
    const current = points[index];
    const next = points[(index + 1) % points.length];
    area += current.xMm * next.yMm - next.xMm * current.yMm;
  }
  return area / 2;
}

export function pointInPolygon(
  point: { xMm: number; yMm: number },
  polygon: TopologyPoint[]
): boolean {
  let inside = false;
  for (let index = 0, previous = polygon.length - 1; index < polygon.length; previous = index, index += 1) {
    const current = polygon[index];
    const prior = polygon[previous];
    const cross =
      (point.yMm - current.yMm) * (prior.xMm - current.xMm) -
      (point.xMm - current.xMm) * (prior.yMm - current.yMm);
    const onEdge =
      Math.abs(cross) < EPSILON &&
      point.xMm >= Math.min(current.xMm, prior.xMm) - EPSILON &&
      point.xMm <= Math.max(current.xMm, prior.xMm) + EPSILON &&
      point.yMm >= Math.min(current.yMm, prior.yMm) - EPSILON &&
      point.yMm <= Math.max(current.yMm, prior.yMm) + EPSILON;
    if (onEdge) return true;

    const intersects =
      current.yMm > point.yMm !== prior.yMm > point.yMm &&
      point.xMm <
        ((prior.xMm - current.xMm) * (point.yMm - current.yMm)) /
          (prior.yMm - current.yMm) +
          current.xMm;
    if (intersects) inside = !inside;
  }
  return inside;
}

/**
 * Enumerates the bounded faces of a planar wall graph by walking directed
 * half-edges in angular order. Intersections are expected to already be
 * represented as shared Nodes, matching the persisted Level graph.
 */
export function findClosedFaces(nodes: TopologyPoint[], walls: TopologyWall[]): OrderedBoundary[] {
  const nodeById = new Map(nodes.map((node) => [node.id, node]));
  const outgoing = new Map<string, HalfEdge[]>();

  for (const wall of walls) {
    const start = nodeById.get(wall.startNodeId);
    const end = nodeById.get(wall.endNodeId);
    if (!start || !end || start.id === end.id) continue;
    const forward = { wallId: wall.id, fromId: start.id, toId: end.id };
    const reverse = { wallId: wall.id, fromId: end.id, toId: start.id };
    outgoing.set(start.id, [...(outgoing.get(start.id) ?? []), forward]);
    outgoing.set(end.id, [...(outgoing.get(end.id) ?? []), reverse]);
  }

  for (const [nodeId, edges] of outgoing) {
    const origin = nodeById.get(nodeId)!;
    edges.sort((left, right) => {
      const leftPoint = nodeById.get(left.toId)!;
      const rightPoint = nodeById.get(right.toId)!;
      return (
        Math.atan2(leftPoint.yMm - origin.yMm, leftPoint.xMm - origin.xMm) -
        Math.atan2(rightPoint.yMm - origin.yMm, rightPoint.xMm - origin.xMm)
      );
    });
  }

  const halfEdgeKey = (edge: HalfEdge) => `${edge.wallId}:${edge.fromId}:${edge.toId}`;
  const visited = new Set<string>();
  const faces: OrderedBoundary[] = [];
  const seenWallSets = new Set<string>();

  for (const edges of outgoing.values()) {
    for (const startEdge of edges) {
      if (visited.has(halfEdgeKey(startEdge))) continue;

      const traversed: HalfEdge[] = [];
      const localVisited = new Set<string>();
      let current = startEdge;
      let closed = false;

      while (traversed.length <= walls.length * 2) {
        const key = halfEdgeKey(current);
        if (localVisited.has(key)) {
          closed = key === halfEdgeKey(startEdge);
          break;
        }
        localVisited.add(key);
        visited.add(key);
        traversed.push(current);

        const nextEdges = outgoing.get(current.toId) ?? [];
        const reverseIndex = nextEdges.findIndex(
          (edge) => edge.wallId === current.wallId && edge.toId === current.fromId
        );
        if (reverseIndex < 0 || nextEdges.length < 2) break;
        current = nextEdges[(reverseIndex - 1 + nextEdges.length) % nextEdges.length];
      }

      if (!closed || traversed.length < 3) continue;
      const nodeIds = traversed.map((edge) => edge.fromId);
      if (new Set(nodeIds).size !== nodeIds.length) continue;
      const polygon = nodeIds.map((id) => nodeById.get(id)!);
      const areaMm2 = Math.abs(signedArea(polygon));
      if (areaMm2 < EPSILON) continue;

      const wallSetKey = traversed.map((edge) => edge.wallId).sort().join("|");
      if (seenWallSets.has(wallSetKey)) continue;
      seenWallSets.add(wallSetKey);
      faces.push({
        wallIds: traversed.map((edge) => edge.wallId),
        nodeIds,
        polygon,
        areaMm2,
      });
    }
  }

  return faces;
}

/** Returns the smallest closed face containing the clicked world-space point. */
export function findSmallestClosedBoundary(
  nodes: TopologyPoint[],
  walls: TopologyWall[],
  point: { xMm: number; yMm: number }
): OrderedBoundary | null {
  return (
    findClosedFaces(nodes, walls)
      .filter((face) => pointInPolygon(point, face.polygon))
      .sort((left, right) => left.areaMm2 - right.areaMm2)[0] ?? null
  );
}

/** Orders an already-associated RoomWall set, returning null when it is not one closed loop. */
export function orderClosedBoundary(
  nodes: TopologyPoint[],
  walls: TopologyWall[]
): OrderedBoundary | null {
  if (walls.length < 3) return null;
  const expectedIds = new Set(walls.map((wall) => wall.id));
  return (
    findClosedFaces(nodes, walls).find(
      (face) => face.wallIds.length === expectedIds.size && face.wallIds.every((id) => expectedIds.has(id))
    ) ?? null
  );
}
