# NayDesign — Agent Instructions

## Purpose

NayDesign is a commercial interior-design application focused on real, measurable, editable room designs tied to real product dimensions and catalog data.

This file contains stable instructions for coding agents working in this repository.
Treat the current repository code as the source of truth for what is actually implemented.

For historical Stage 1 audit findings and technical debt, see `STAGE_1_REVIEW.md` if present.

## Product Direction

NayDesign should let a user:

1. Register / log in.
2. Create and open projects.
3. Create levels and rooms.
4. Draw and edit real room geometry.
5. Add doors and windows.
6. Browse furniture/products.
7. Place real-sized furniture into a room.
8. Validate physical/geometric constraints.
9. Save and reload the same design.

Stage 1 is a 2D editor backed by real geometry.

Do not turn Stage 1 into a generic AI image-generation application.

## Stage 1 Boundaries

Stage 1 includes:

- authentication
- projects
- levels
- rooms
- 2D walls
- doors/windows/openings
- read-only product catalog
- furniture placement
- geometry validation
- persistence
- autosave/save state
- Docker/local development
- automated tests and CI

Unless explicitly requested, do not introduce:

- 3D editing
- AR/VR
- AI room generation
- real vendor imports
- purchasing/shop integrations
- microservices
- advanced CAD/topology systems

## Technology

Backend:

- Java 21
- Spring Boot
- Spring Security
- Spring Data JPA / Hibernate
- PostgreSQL
- Flyway
- Maven
- Testcontainers for integration tests where available

Frontend:

- React
- TypeScript
- Vite
- Vitest / Testing Library
- HTML Canvas-based 2D editors

Infrastructure:

- Docker / Docker Compose
- GitHub Actions

Always inspect the repository before assuming exact dependency or framework versions.

## Architecture

Backend follows a conventional layered architecture:

`controller -> service -> repository -> PostgreSQL`

The Geometry Engine is intentionally kept separate from Spring/JPA concerns where practical.

The backend Geometry Engine is authoritative for persisted geometry validation.

Frontend geometry logic exists for fast interaction and preview only.

Do not move authoritative validation exclusively into the frontend.

## Geometry Rules

All domain/world coordinates and dimensions are stored in millimetres.

Never persist screen pixels as geometry.

Pixels belong only to the viewport/rendering transform.

The same world coordinate must mean the same thing everywhere:

- Floor Canvas
- Furniture mode
- backend Geometry Engine
- persistence
- future 3D views

Example:

`(4000, 2000)` always means 4000 mm x 2000 mm in world space, not pixels.

Pan, zoom, fit-to-view, and screen size must never mutate persisted world geometry.

## One Authoritative Room Geometry

Floor Canvas and Furniture mode are two views/editing modes over the SAME persisted room geometry.

Do not create an independent room rectangle or fallback geometry for Furniture mode.

Expected relationship:

`Room -> RoomWall -> Wall -> Node`

Level geometry owns nodes/walls/openings.

A Room references its boundary walls through RoomWall.

Furniture mode must reconstruct and render the room from persisted geometry.

If room geometry is missing or incomplete, show an explicit empty/error state.
Do not silently invent a fake rectangular room.

## Level Geometry Save Semantics

Level geometry is handled as a document/aggregate.

Be especially careful with destructive replacement semantics.

Saving edited walls/openings must not accidentally delete:

- existing Rooms
- RoomWall relationships that should remain
- FurniturePlacements

Never reintroduce the previous class of data-loss bug where omitted room data caused rooms to be deleted.

When changing geometry persistence, add regression coverage for preservation of unrelated persisted data.

## Openings

Doors/windows are represented as openings attached to walls.

Opening placement must be based on wall geometry and real millimetre offsets/dimensions.

Do not create a separate coordinate system for openings.

## Furniture

Furniture placements reference real Product dimensions.

Furniture should be rendered proportionally to room geometry.

Example: a 2100 mm sofa should occupy about 52.5% of a 4000 mm wall span before viewport transformation.

Furniture mode may edit placements, but room walls/openings remain read-only there.
Structural geometry editing belongs to Floor Canvas.

## Validation

Backend validation should cover physical/geometric constraints such as:

- zero-length/invalid walls
- invalid wall references
- openings outside their wall
- incomplete/invalid room boundaries
- furniture outside the room
- furniture intersecting walls
- furniture intersecting furniture
- blocked door clearance/swing

Avoid subjective interior-design rules unless explicitly requested.

Prefer structured validation issues with a stable code, severity, message, and related entity identifier where applicable.

## Database Principles

Use Flyway for schema changes.

Java enums are the application source of truth where currently designed that way; persist them as strings unless the existing implementation says otherwise.

Respect existing foreign-key delete semantics.

Do not duplicate media binaries in the database; product media records contain metadata/URLs.

Do not make schema changes casually for a frontend-only problem.

## API Principles

Prefer task-oriented/domain aggregate endpoints over CRUD endpoints for every internal entity.

Existing high-level concepts include:

- auth/me
- projects
- levels
- rooms
- level geometry
- room furniture placements
- vendors/products

Before adding an endpoint, inspect whether the current API already exposes the needed aggregate.

## Security

Authentication is JWT-based in the current Stage 1 implementation.

Never commit:

- JWT secrets
- API keys
- OAuth client secrets
- passwords
- private credentials

Use environment variables and `.env.example` for documented configuration.

Do not weaken authorization/security merely to make a test pass.

## Frontend Editing Rules

Preserve established editor behaviour unless the task explicitly changes it:

- pan/zoom
- fit-to-view
- grid
- snapping
- selection
- undo/redo
- save/autosave state

Transient drawing state must not be persisted until the intended geometry operation is completed.

Closing a wall loop should finish that transient chain; the tool itself may remain active for starting a new chain.

## Working Method

Before modifying code:

1. Read this file.
2. Inspect the relevant implementation.
3. Inspect `git status`.
4. Inspect relevant uncommitted diffs.
5. Trace the real data flow instead of guessing.
6. Preserve unfinished or completed work already present in the worktree.

The repository is the source of truth when chat history, old reviews, or prompts disagree with current code.

Do not blindly implement old instructions if the repository has evolved.

## Change Discipline

For each task:

- make the smallest coherent change
- avoid unrelated refactors
- preserve existing architecture unless there is a clear reason to change it
- do not redesign the application opportunistically
- add a regression test for fixed bugs where practical
- update documentation only when the change makes it inaccurate

If a larger architectural change is genuinely required, explain why before spreading the change across unrelated areas.

## Verification

For frontend changes, normally run the relevant subset and, before completion when practical:

```bash
npm test
npm run lint
npm run build
```

Run these from the frontend directory unless repository scripts provide another entry point.

For backend changes, run relevant tests and then, where the local environment permits:

```bash
./mvnw test
./mvnw package
```

or the repository's stronger verification command if appropriate.

Testcontainers integration tests require a usable Docker environment.
If Docker is unavailable, report that clearly instead of claiming the integration suite passed.

## Definition of Done

A task is not complete merely because code was changed.

Before reporting completion:

- inspect the final diff
- ensure no unrelated files were changed
- run relevant tests/build/lint
- report anything that could not be verified
- do not claim a user flow works unless the relevant data path was actually verified

For geometry/furniture bugs, verify the persisted end-to-end flow, not only rendering.

## Documentation Hierarchy

Use this priority when information conflicts:

1. Current repository code and schema for actual implementation state.
2. This `AGENTS.md` for stable project intent and working rules.
3. Current architecture/product documentation.
4. `STAGE_1_REVIEW.md` and other dated audits for historical findings.
5. Old chat history or old prompts.

Keep `AGENTS.md` stable and concise.
Do not add temporary bug notes, one-off test output, or chat history here.
