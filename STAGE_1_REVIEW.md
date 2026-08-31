# NayDesign — Stage 1 Architecture & Code-Quality Review

_Senior-level review of the Stage 1 vertical slice (auth → projects → levels → rooms →
geometry → furniture → catalog). No redesign performed; CRITICAL/HIGH issues that could
be fixed safely were fixed in place. MEDIUM/LOW issues are recorded but not fixed._

## 1. Architecture overview

**Backend** — Spring Boot 4.1 / Java 21, layered architecture:
`controller → service (@Transactional) → repository (Spring Data JPA) → PostgreSQL`,
plus a framework-free `geometry` package (the "Geometry Engine": pure functions/records,
no Spring/JPA dependency) invoked from `LevelGeometryService` (structural wall/opening
validation, blocking) and `RoomService` (furniture spatial checks, advisory).
Package structure is conventional and consistent (`controller`, `service`, `domain.entity`,
`domain.repository`, `domain.enums`, `dto.request`, `dto.response`, `exception`,
`security`, `geometry`). DTOs are hand-written records (no MapStruct mappers exist despite
the dependency being declared — see Technical Debt). Auth is stateless JWT (HS256 via
`jjwt`), enforced by a single `JwtAuthenticationFilter` ahead of Spring Security's stack;
`GlobalExceptionHandler` centralizes all error → HTTP-status translation into one
`ErrorResponse` shape, including structured Geometry Engine `issues`.

**Frontend** — React 19 + TypeScript + Vite, no external state library. `AuthContext`
holds auth state (JWT in `localStorage`, validated against `/api/me` on load);
`RequireAuth` guards routes. Each editor page (`LevelCanvasPage`, `RoomFurniturePage`) is
a single large component owning its own local `useState` for document state, undo/redo
history (`past`/`future` stacks), viewport (`ViewTransform` — pan/zoom in px, decoupled
from mm-space geometry via `canvasView.ts`), and save state (`useAutosave` hook: debounced
autosave, generation-counter guard against stale responses, `beforeunload` warning). A
thin `fetch`-based `api/client.ts` attaches the bearer token and normalizes errors
(including Geometry Engine `issues`) into a typed `ApiError`. `ValidationPanel` renders
those issues generically, decoupled from the specific pages.

**Database** — Flyway-versioned PostgreSQL 16 schema (`V1__init_core_schema.sql`,
`V2__add_user_password_hash.sql`), one repeatable dev-only seed
(`R__seed_dev_catalog.sql`, loaded only under the `local` Spring profile). Consistent use
of `uuid` PKs (`gen_random_uuid()`), `timestamptz` with a shared `set_updated_at()`
trigger, `CHECK` constraints mirroring bean-validation rules, and deliberate FK delete
semantics (documented in `Level`'s Javadoc: walls/openings cascade, nodes are
`ON DELETE RESTRICT` at the DB level but Java-side cascade ordering guarantees walls are
gone first).

## 2. Review findings

Legend: 🔴 CRITICAL 🟠 HIGH 🟡 MEDIUM ⚪ LOW. **Fixed** items were changed in this pass;
others are recorded below and in §4/§5.

### Fixed in this pass

| # | Severity | Area | Issue | Fix |
|---|----------|------|-------|-----|
| 1 | 🟠 HIGH | Security / Backend | `UserController` (`/api/users/{id}`) let **any** authenticated user read or edit **any other user's** profile (PII: name, phone) — contradicted its own "self-service" Javadoc. | Added a self-only check (`principal.getId().equals(id)`, else `AccessDeniedException` → 403). Added `UserControllerIntegrationTest` (4 tests) proving self access works and cross-user access/mutation is rejected and has no effect. |
| 2 | 🟠 HIGH | Security config | `app.jwt.secret` had a hard-coded dev fallback (`change-this-dev-only-secret-key...`) in the **profile-agnostic** `application.properties`, so any future non-`local` profile that forgets to set `JWT_SECRET` would silently sign tokens with a known secret. | Removed the default from `application.properties` (now `${JWT_SECRET}`, no fallback — fails fast at startup if unset); the dev-only fallback now lives only in `application-local.properties`. |
| 3 | 🟠 HIGH | Backend / Observability | `GlobalExceptionHandler`'s catch-all handler swallowed unexpected exceptions with **no server-side log** — a real 500 in production would be completely invisible. | Added an SLF4J `log.error(...)` (with method/URI/stack trace) before returning the generic 500 body. |
| 4 | 🟠 HIGH | Backend / Performance (N+1) | `LevelGeometryService.buildGeometryResponse` iterated `room.getWalls()` (a lazy `@ManyToMany`) once per room to build `roomWalls` — one extra query per room on every geometry load/save. | Added `RoomRepository.findByLevelIdFetchingWalls` (`LEFT JOIN FETCH r.walls`, `DISTINCT`) and used it only on this path; `RoomService`'s plain room list is untouched. |
| 5 | 🟡 MEDIUM→fixed (cheap/safe) | Security config | CORS `exposedHeaders` still listed `X-Geometry-Warnings`, a header the backend no longer sends (superseded by structured `issues` in the JSON body in an earlier iteration) — stale config, needless exposure. | Removed the dead `exposedHeaders` entry. |

### Recorded, not fixed (see rationale)

| Severity | Area | Issue | Why not fixed now |
|---|------|-------|--------------------|
| 🔴 CRITICAL | Security / Backend | **No ownership/authorization checks on the Project→Level→Room→Geometry→Furniture chain.** JWT authentication proves *who* the caller is, but every service (`ProjectService`, `LevelService`, `RoomService`, `LevelGeometryService`) trusts the caller for *any* resource id — any authenticated user can read, edit, or delete **any other user's** projects/levels/rooms/geometry/furniture just by knowing (or brute-forcing) a UUID. | This is a real, systemic gap, but closing it safely requires: (a) deciding an authorization model (strict owner-only vs. future collaborators/sharing), (b) threading the authenticated principal through 5 services and ~6 controllers, and (c) rewriting large parts of the integration-test suite (which currently exercises cross-user calls on purpose to keep test setup simple). That is properly "Stage 2" work, not a safe, contained fix — see §5, item 1 (top priority). |
| 🟡 MEDIUM | Backend / Data integrity | Email uniqueness is case-sensitive at both the DB (`UNIQUE (email)`) and application level — `Foo@x.com` and `foo@x.com` can both register. | Low exploitability, but fixing well means a migration (`citext` or a lowercased generated column) plus normalizing on register/login; deferred to avoid a migration in this pass. |
| 🟡 MEDIUM | Backend / Build hygiene | `mapstruct` + `lombok-mapstruct-binding` are declared as dependencies/annotation-processors but **no `@Mapper` exists anywhere** — all DTO mapping is hand-written `toResponse` methods. | Dead dependency, not a correctness/security issue; removing it is safe but was deprioritized versus the fixes above. |
| 🟡 MEDIUM | Security | `/v3/api-docs/**` and `/swagger-ui/**` are `permitAll`, but no `springdoc-openapi` dependency is present — these routes almost certainly 404 today. Inert now, but if springdoc is added later without revisiting this, the full API surface becomes anonymously browsable. | Not exploitable today; flagged for when API docs are actually wired up. |
| 🟡 MEDIUM | Frontend / Security | JWT is stored in `localStorage` (readable by any injected script, i.e. XSS-amplifying) rather than an `httpOnly` cookie. | Standard SPA/bearer-token tradeoff; switching to cookies is an auth-transport redesign (CSRF handling, backend cookie issuance) — out of scope for a safe, contained fix. |
| ⚪ LOW | Backend | `UserService.createUser` / `listUsers` / `deleteUser` are dead code (no controller calls them). | No behavior risk; noted for cleanup. |
| ⚪ LOW | Backend | `RestAuthenticationEntryPoint` builds its own `ObjectMapper` per bean instantiation instead of reusing the app's configured one. | Cosmetic/perf-negligible. |
| ⚪ LOW | Database | No unique constraint on `(project_id, order_index)` for levels; duplicate order indices are allowed (UI would just show a tie). | Not affecting Stage 1 quality bar. |

### Verified as sound (no action needed)

- **Password storage**: BCrypt via Spring's `BCryptPasswordEncoder`, min length 8 enforced by bean validation. Good.
- **JWT validation**: signature + expiry + subject match all checked; invalid/expired tokens degrade to "unauthenticated" (never a 500); `UsernameNotFoundException` (deleted account) handled the same way. Good.
- **Transactions**: every service is `@Transactional(readOnly = true)` at the class level with explicit `@Transactional` on mutators — verified with a dedicated integration test that a partially-valid geometry update rolls back **entirely** on a Geometry Engine error.
- **Other suspected N+1s were false positives**: `wall.getStartNode().getId()`, `opening.getWall().getId()`, `placement.getProduct().getId()` etc. only ever call `.getId()` on a lazy proxy, which Hibernate resolves from the already-loaded FK column without a query. Only the `room.getWalls()` **collection** access above was a genuine N+1.
- **Geometry Engine**: pure, dependency-free, well-covered by both unit tests (per-validator) and new API-level integration tests (7 scenarios: valid room, invalid wall, opening-out-of-bounds, furniture-outside-room, wall/furniture collisions, blocked door).
- **CORS**: origin patterns scoped to localhost dev ports only; methods/headers reasonably scoped.
- **Exposed endpoints**: only `/api/auth/**` and `/actuator/health` are public; Actuator's web exposure isn't broadened beyond Spring Boot's secure defaults.

## 3. Completed functionality

- **Auth**: register, login, JWT issuance/validation, `/api/me`, self-service profile (now self-only).
- **Projects / Levels / Rooms**: full CRUD, correct cascade-delete semantics down to nodes/walls/openings/room-walls/furniture.
- **Level geometry**: single-document save/load (nodes, walls, openings, rooms, room-wall borders), two/three-phase save protocol for brand-new graphs, structural validation (blocking on error, e.g. zero-length walls, out-of-bounds openings, unknown references), transactional rollback verified end-to-end.
- **Furniture placements**: save/load/update/remove per room, unknown-product rejection, Geometry Engine spatial checks (room fit, wall/furniture collisions, door clearance) as non-blocking warnings.
- **Catalog**: read-only vendors/products with filtering, dev-seed data for local development.
- **Frontend editors**: 2D canvas for walls/openings (`LevelCanvasPage`) and furniture (`RoomFurniturePage`), undo/redo, pan/zoom with correct mm↔px conversion, snapping, a shared `ValidationPanel` rendering backend-computed Geometry Engine issues (severity-colored, click-to-select/highlight), and a Stage-1 save-state machine (debounced autosave, "Saved / Unsaved changes / Saving… / Save failed", `beforeunload` guard).
- **Test infrastructure**: 93 backend tests (unit + Testcontainers-backed Spring Boot integration tests covering every vertical-slice area listed in scope), all green; frontend `tsc -b && vite build` and `oxlint` clean.

## 4. Known limitations

- **No per-resource authorization** beyond "is this a valid JWT" — see CRITICAL finding above. Effectively single-tenant-per-database trust today.
- No password reset, email verification, or account lockout/rate-limiting on login.
- No optimistic concurrency control on geometry/furniture saves beyond the frontend's own generation-counter guard (a genuine backend `version`/ETag check does not exist — two clients saving the same level concurrently can silently overwrite each other).
- No pagination anywhere (`listProjects`, `listProducts`, etc. return full lists) — fine at Stage 1 data volumes, will not scale.
- Room polygon/area is not derived (only a bounding box + closure check); non-rectangular rooms are not truly modeled.
- No image/asset upload for products; media URLs are just strings.
- JWT stored in `localStorage` (XSS-sensitive); no refresh-token/rotation story.

## 5. Technical debt

1. **Authorization model is the single largest debt item** (see CRITICAL finding) — needs a deliberate design (owner-only vs. shared-project ACLs) before Stage 2 opens the app to multiple real users interacting with the same data.
2. Dead dependencies/code: `mapstruct` (+ its annotation processor path), `UserService.createUser/listUsers/deleteUser`, the now-inert Swagger route permits.
3. No API documentation is actually served (annotations exist via `swagger-annotations-jakarta`, but no `springdoc-openapi` runtime is wired up to render `/v3/api-docs`).
4. Email case-sensitivity (register/login) is a latent data-integrity bug.
5. Large page components (`LevelCanvasPage`, `RoomFurniturePage`) mix rendering, input handling, undo/redo, and persistence in one file each (~800–1000+ lines) — manageable at Stage 1 size but will need decomposition (e.g. extracting the canvas render loop and pointer-event handling into hooks) before more editor features land.
6. No database-level optimistic locking (`@Version`) on frequently-contested aggregates (`Level`, `Room`), relying entirely on last-write-wins.

## 6. Recommended next steps

1. **Design and implement resource-level authorization** for Project/Level/Room/Geometry/Furniture (owner-only to start), including updating the integration-test suite to exercise both "owner succeeds" and "non-owner is rejected (403)" paths — this is the top-priority item before any multi-user usage.
2. Add `@Version` optimistic locking to `Level` and `Room` (or at least `Level`, since geometry is the highest-contention aggregate) to make concurrent-edit conflicts explicit (409) instead of silent overwrites.
3. Wire up `springdoc-openapi` properly (or remove the now-inert permitted routes) so the existing Swagger annotations are actually useful.
4. Normalize email casing on register/login (lowercase before lookup/insert) and consider a follow-up migration for a case-insensitive unique constraint.
5. Remove the unused MapStruct dependency/processor and the dead `UserService` methods identified above.
6. Decompose `LevelCanvasPage`/`RoomFurniturePage` incrementally (extract render-loop and pointer-handling hooks) as new editor features are added, to keep them maintainable.
7. Add basic login rate-limiting/lockout and consider short-lived access tokens + refresh tokens as the auth story matures beyond Stage 1.
8. Add pagination to list endpoints before real data volumes make `findAll`-style queries a problem.

