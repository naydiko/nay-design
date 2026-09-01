# NayDesign — Stage 1 Completion Report

Date: 2026-09-01

## 1. Feature Checklist

| Area | Status | Notes |
|---|---|---|
| Register / Login (JWT) | DONE | `AuthController`/`AuthService`, 19 auth tests passing |
| Google Sign-In | DONE | Server-side ID-token verification via `GoogleIdTokenVerifierService`, never trusts client claims |
| Forgot / Reset password | DONE | Single-use, hashed, expiring tokens; response does not leak account existence |
| Email verification | DONE | Same token pattern as password reset |
| GET /api/me, change password | DONE | |
| Projects CRUD, owned-only | DONE | `ProjectService.findOwnedProjectOrThrow` |
| Levels CRUD | DONE | Ownership enforced via project chain (fixed this pass — see Known Bugs Fixed) |
| Rooms CRUD | DONE | Ownership enforced via level→project chain (fixed this pass) |
| Geometry save/load (walls/nodes/openings/rooms) | DONE | Upsert + reconciliation, ownership-checked, structural errors block save |
| Furniture placements save/load | DONE | Ownership-checked, Geometry Engine warnings returned non-blocking |
| Catalog (products/vendors) | DONE | 25 demo products, realistic dimensions/prices |
| 2D Canvas: walls/doors/windows/select/delete/undo/redo/save | DONE | `LevelCanvasPage.tsx`, pan/zoom/grid/snapping in `canvasView.ts` |
| Furniture placement/move/rotate/delete/lock | DONE | `RoomFurniturePage.tsx` |
| Geometry Engine (walls, openings, rooms, furniture, door clearance) | DONE | Backend authoritative; unit + integration tested |
| Validation UX (ERROR/WARNING display) | DONE | `ValidationPanel` component, structured `GeometryIssue` (code/severity/message/entityId) |
| Autosave / dirty-state | DONE | `useAutosave` hook: debounced, non-overlapping, stale-response-safe |
| Docker (Mode A full stack) | DONE | Verified end-to-end this pass (see below) |
| Docker (Mode B Postgres-only + IDE) | DONE | `backend/docker-compose.yml` |
| GitHub Actions CI | DONE | backend/frontend/docker jobs, no real secrets required |
| Frontend routing incl. protected routes | DONE | `App.tsx`, `RequireAuth` |

No features are NOT DONE for Stage 1 scope. Minor polish items are listed under Technical Debt.

## 2. Working User Flow

Register/Login → optional Google sign-in → Projects list (own projects only) → create/open Project → create/select Level → 2D canvas: draw walls, add doors/windows, save geometry (validated) → open Room → browse catalog, place furniture with real product dimensions, move/rotate/lock, save (non-blocking warnings shown) → reload → state restored exactly (IDs, rotation, lock, product references all preserved).

## 3. Backend Endpoints (all under `/api`, JWT except auth/public)

- `POST /auth/register`, `/auth/login`, `/auth/google`, `/auth/change-password`, `/auth/forgot-password`, `/auth/reset-password`, `/auth/verify-email`
- `GET/PATCH /users/{id}` (self-only)
- `POST/GET/PATCH/DELETE /projects`, `/projects/{id}`
- `POST/GET /projects/{projectId}/levels`, `GET/PATCH/DELETE /levels/{id}`
- `GET/PUT /levels/{levelId}/geometry`
- `POST/GET /levels/{levelId}/rooms`, `GET/PATCH/DELETE /rooms/{id}`
- `GET/PUT /rooms/{roomId}/placements`
- `GET /products`, `/products/{id}`, `/vendors`, `/vendors/{id}`

## 4. Frontend Routes

Public: `/login`, `/register`, `/forgot-password`, `/reset-password`, `/verify-email`.
Protected (redirect to `/login` if unauthenticated): `/projects`, `/projects/:projectId`, `/levels/:levelId`, `/levels/:levelId/rooms`, `/rooms/:roomId`, `/change-password`. Catch-all → `/projects`.

## 5. Geometry Engine Capabilities

Walls (length/thickness/height/distinct nodes), openings (offset/width/fit-within-wall), rooms (closure detection, bounding box), furniture (bounding box from product dims, room containment, wall/furniture intersection, rotation-aware), door clearance/swing blocking. Wall/opening structural errors block saves; furniture/room-closure findings are advisory warnings. Millimetre-consistent throughout; frontend does lightweight local checks only, backend is authoritative.

## 6. Security Functionality

- JWT stateless auth, 401 on missing/invalid/expired token.
- **Fixed this pass (CRITICAL):** Level/Room/Geometry/Furniture endpoints previously had zero ownership checks (IDOR) — any authenticated user could read/modify/delete any other user's levels, rooms, geometry, and furniture by UUID. Added `findOwnedLevelOrThrow`/`findOwnedRoomOrThrow` (walking `Room→Level→Project→owner`) to `LevelService`, `RoomService`, `LevelGeometryService`, and wired `@AuthenticationPrincipal` through both controllers. New regression tests confirm 403 on cross-user access. 108/108 backend tests pass.
- Passwords: BCrypt hashed, never exposed in responses.
- Reset/verification tokens: SHA-256 hashed at rest, single-use, time-limited.
- Forgot-password does not leak account existence.
- Google sign-in verifies ID tokens server-side.
- CORS origins configurable via `app.cors.allowed-origins` / `CORS_ALLOWED_ORIGINS` env var.
- Secrets (JWT secret, Google client ID, DB credentials) are environment-driven; `.env` is gitignored, `.env.example` documents safe placeholders.

## 7. Docker / Local Development

**Mode A (full stack):**
```
cp .env.example .env   # edit if needed
docker compose up --build
```
Verified this pass: `docker compose config`, `docker compose build`, full `up -d` → all three containers (`naydiko-postgres`, `naydiko-backend`, `naydiko-frontend`) reach **healthy**, registered a user, fetched JWT, called protected `/api/products` (25 items), confirmed SPA routing (`/projects` → 200), clean `down -v` teardown.

**Fixed this pass:** frontend Docker healthcheck used `wget http://localhost:80/`, which resolved `localhost` to `::1` (IPv6) inside the alpine container while nginx only binds IPv4 — causing a permanent false "unhealthy" status despite nginx working correctly. Changed to `http://127.0.0.1:80/`.

**Mode B (Postgres-only + IDE):** `backend/docker-compose.yml` starts only Postgres on port 5433 (`udesign-postgres`); run backend from IDE with `local` profile, frontend via `npm run dev`.

## 8. CI Status

`.github/workflows/ci.yml`: 3 parallel jobs — `backend` (`mvnw verify`), `frontend` (`npm ci && lint && test && build`), `docker` (`compose config/build/up` + health poll + smoke curl + `down -v`). No production secrets required (dev-safe fallback JWT secret, `DevAuthNotificationService` logs instead of emailing, Google client ID optional). Last known-good local dry-run: 108 backend tests, 57 frontend tests, Docker smoke test all pass.

## 9. Known Bugs Fixed This Pass

1. **CRITICAL — IDOR on Level/Room/Geometry/Furniture** (see Security section above).
2. **Docker frontend healthcheck false-negative** due to IPv6/IPv4 localhost resolution mismatch.

## 10. Known Technical Debt

- Furniture/room-closure Geometry Engine findings are advisory-only by design (Stage 1 doesn't block on spatial overlap) — acceptable but should be revisited if Stage 2 introduces stricter layout rules.
- No pagination on list endpoints (projects/levels/rooms/products) — fine at Stage 1 scale, will need it once catalog/project counts grow.
- Frontend lint has 5 `react(set-state-in-effect)` warnings (no errors) in a few pages — cosmetic, not functional bugs.

## 11. Manual / External Configuration Required

- **Google OAuth**: set `GOOGLE_CLIENT_ID` (backend) and `VITE_GOOGLE_CLIENT_ID` (frontend build arg) to enable Google sign-in; without it, Google sign-in is simply unavailable (local email/password still works).
- **Email provider**: Stage 1 ships `DevAuthNotificationService` (logs reset/verification links instead of emailing). A real provider (SES/SendGrid/etc.) must be wired in for production.
- **JWT_SECRET**: must be set in `.env` for Docker mode (compose fails fast with a clear error if missing) — no insecure default is baked into the image.

## 12. Stage 2 (Postponed, Not Implemented)

3D visualization, AI-assisted design generation, real vendor/e-commerce integrations, AR/VR, advanced CAD features (curved walls, multi-level structural analysis, etc.).

---

## Verdict

**STAGE 1 READY**

All backend/frontend builds pass cleanly, 108/108 backend and 57/57 frontend tests pass, Flyway migrations are consistent with entities, the previously-undiscovered critical IDOR vulnerability across Levels/Rooms/Geometry/Furniture has been fixed and is now covered by regression tests, Docker Mode A and Mode B both verified end-to-end (including a full register→JWT→protected-API→SPA-routing smoke test), and CI covers backend/frontend/Docker without requiring any real production secrets.

