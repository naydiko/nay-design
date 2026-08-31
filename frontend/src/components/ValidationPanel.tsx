// Simple, reusable panel for surfacing Geometry Engine validation results
// (see ../api/types.ts GeometryIssue). The backend Geometry Engine remains
// authoritative for all geometric calculations — this component only
// renders/labels/dispatches the issues it is given.
import type { GeometryIssue, GeometryIssueCode } from "../api/types";

const CODE_LABELS: Record<GeometryIssueCode, string> = {
  WALL_INVALID_NODES: "Invalid wall endpoints",
  WALL_ZERO_LENGTH: "Zero-length wall",
  WALL_NON_POSITIVE_THICKNESS: "Non-positive wall thickness",
  WALL_NON_POSITIVE_HEIGHT: "Non-positive wall height",
  OPENING_MISSING_WALL: "Opening references a missing wall",
  OPENING_NON_POSITIVE_WIDTH: "Non-positive opening width",
  OPENING_NON_POSITIVE_HEIGHT: "Non-positive opening height",
  OPENING_INVALID_OFFSET: "Invalid opening position",
  OPENING_OUT_OF_BOUNDS: "Opening out of bounds",
  ROOM_NOT_CLOSED: "Room boundary not closed",
  FURNITURE_MISSING_PRODUCT: "Furniture missing product",
  FURNITURE_MISSING_DIMENSIONS: "Furniture missing dimensions",
  FURNITURE_OUTSIDE_ROOM: "Furniture outside room",
  FURNITURE_INTERSECTS_WALL: "Furniture intersects wall",
  FURNITURE_INTERSECTS_FURNITURE: "Furniture overlaps furniture",
  DOOR_BLOCKED: "Door clearance blocked",
};

/** Human-readable label for an issue's code, falling back to the raw code. */
export function issueLabel(issue: GeometryIssue): string {
  return CODE_LABELS[issue.code] ?? issue.code;
}

export function hasErrors(issues: GeometryIssue[]): boolean {
  return issues.some((i) => i.severity === "ERROR");
}

interface ValidationPanelProps {
  title?: string;
  issues: GeometryIssue[];
  /** Called when the user clicks an issue; used to select/focus the related canvas object. */
  onSelect?: (issue: GeometryIssue) => void;
}

/** Renders a simple list of Geometry Engine validation issues, grouped
 * visually by severity. Clicking an issue (if `onSelect` is given) lets the
 * host page select/focus the related canvas object. */
export default function ValidationPanel({ title = "Validation", issues, onSelect }: ValidationPanelProps) {
  if (issues.length === 0) return null;
  const errorCount = issues.filter((i) => i.severity === "ERROR").length;
  const warningCount = issues.length - errorCount;

  return (
    <div className="validation-panel">
      <strong>
        {title}
        {errorCount > 0 && ` — ${errorCount} error${errorCount === 1 ? "" : "s"}`}
        {warningCount > 0 && ` — ${warningCount} warning${warningCount === 1 ? "" : "s"}`}
      </strong>
      <ul>
        {issues.map((issue, i) => (
          <li
            key={i}
            className={`validation-issue ${issue.severity === "ERROR" ? "validation-error" : "validation-warning"}`}
            onClick={onSelect ? () => onSelect(issue) : undefined}
          >
            <span className="validation-badge">{issue.severity}</span> {issueLabel(issue)}: {issue.message}
          </li>
        ))}
      </ul>
    </div>
  );
}

