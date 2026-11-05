import { describe, expect, it } from "vitest";
import {
  findSmallestClosedBoundary,
  orderClosedBoundary,
  type TopologyPoint,
  type TopologyWall,
} from "./roomTopology";

function adjacentRooms() {
  const nodes: TopologyPoint[] = [
    { id: "a", xMm: 0, yMm: 0 },
    { id: "b", xMm: 4000, yMm: 0 },
    { id: "c", xMm: 7000, yMm: 0 },
    { id: "d", xMm: 0, yMm: 2500 },
    { id: "e", xMm: 4000, yMm: 2500 },
    { id: "f", xMm: 7000, yMm: 2500 },
  ];
  const walls: TopologyWall[] = [
    { id: "ab", startNodeId: "a", endNodeId: "b" },
    { id: "bc", startNodeId: "b", endNodeId: "c" },
    { id: "cf", startNodeId: "c", endNodeId: "f" },
    { id: "fe", startNodeId: "f", endNodeId: "e" },
    { id: "ed", startNodeId: "e", endNodeId: "d" },
    { id: "da", startNodeId: "d", endNodeId: "a" },
    { id: "be", startNodeId: "b", endNodeId: "e" },
  ];
  return { nodes, walls };
}

describe("room topology", () => {
  it("finds the four walls of a non-square rectangle", () => {
    const { nodes, walls } = adjacentRooms();
    const boundary = findSmallestClosedBoundary(nodes, walls.slice(0, 1).concat(walls.slice(4, 7)), {
      xMm: 2000,
      yMm: 1000,
    });

    expect(boundary?.wallIds.sort()).toEqual(["ab", "be", "da", "ed"]);
    expect(boundary?.polygon).toHaveLength(4);
    expect(boundary?.areaMm2).toBe(10_000_000);
  });

  it("returns null when the point is outside every closed face", () => {
    const { nodes, walls } = adjacentRooms();
    expect(findSmallestClosedBoundary(nodes, walls, { xMm: 9000, yMm: 1000 })).toBeNull();
  });

  it("selects two adjacent rooms independently while reusing their shared wall", () => {
    const { nodes, walls } = adjacentRooms();
    const living = findSmallestClosedBoundary(nodes, walls, { xMm: 2000, yMm: 1000 });
    const kitchen = findSmallestClosedBoundary(nodes, walls, { xMm: 5500, yMm: 1000 });

    expect(living?.wallIds.sort()).toEqual(["ab", "be", "da", "ed"]);
    expect(kitchen?.wallIds.sort()).toEqual(["bc", "be", "cf", "fe"]);
    expect(living?.wallIds.filter((id) => kitchen?.wallIds.includes(id))).toEqual(["be"]);
  });

  it("chooses the smallest face in a multi-room level", () => {
    const { nodes, walls } = adjacentRooms();
    const boundary = findSmallestClosedBoundary(nodes, walls, { xMm: 5500, yMm: 1000 });
    expect(boundary?.areaMm2).toBe(7_500_000);
  });

  it("derives a moved room shape from the same wall and node ids", () => {
    const { nodes, walls } = adjacentRooms();
    const roomWalls = walls.filter((wall) => ["ab", "be", "ed", "da"].includes(wall.id));
    const before = orderClosedBoundary(nodes, roomWalls);
    const moved = nodes.map((node) => (node.id === "d" ? { ...node, xMm: -500 } : node));
    const after = orderClosedBoundary(moved, roomWalls);

    expect(before?.polygon.find((point) => point.id === "d")?.xMm).toBe(0);
    expect(after?.polygon.find((point) => point.id === "d")?.xMm).toBe(-500);
    expect(after?.wallIds.sort()).toEqual(before?.wallIds.sort());
  });
});
