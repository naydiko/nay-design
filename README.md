# NayDesign

[![CI](https://github.com/naydiko/universal-design/actions/workflows/ci.yml/badge.svg)](https://github.com/naydiko/universal-design/actions/workflows/ci.yml)

## Running NayDesign locally

### Prerequisites

- Docker Desktop (Mode A, and for PostgreSQL in Mode B)
- JDK 21 + an IDE with Maven support (Mode B backend)
- Node.js 22+ (Mode B frontend)

Copy the example environment file once, at the repo root:

```powershell
Copy-Item .env.example .env
```

Edit `.env` if you want non-default ports/credentials. Never commit `.env`.

---

### Option A — Full Docker environment

```powershell
docker compose up --build
```

Starts PostgreSQL, the backend and the frontend together. Flyway migrations
(including the Stage 1 demo catalog) run automatically on backend startup.

- Frontend: http://localhost:3000
- Backend API: http://localhost:8080 (Swagger UI at `/swagger-ui.html`)
- PostgreSQL: `localhost:5433` (see `POSTGRES_PORT` in `.env`)

Stop everything:

```powershell
docker compose down
```

Completely reset the development database:

```powershell
docker compose down -v
```

---

### Option B — IDE development

1. Start only PostgreSQL in Docker:

   ```powershell
   docker compose up postgres
   ```

   (equivalently `cd backend && docker compose up`, which uses the same port.)

2. Run the backend from your IDE (IntelliJ: run `BackendApplication` with the
   `local` Spring profile — this is the default). It connects to
   `localhost:5433` and Flyway applies migrations + demo catalog automatically.

   Backend: http://localhost:8090 (Swagger UI at `/swagger-ui.html`)

3. Run the frontend dev server:

   ```powershell
   cd frontend
   npm install
   npm run dev
   ```

   Frontend: http://localhost:5173 — talks to the backend via
   `VITE_API_BASE_URL` (defaults to `http://localhost:8090`, matching the
   `local` profile's port; see `frontend/.env` if you need to override it).

---

### Environment variables (`.env.example`)

| Variable | Purpose |
|---|---|
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` / `POSTGRES_PORT` | PostgreSQL container config |
| `BACKEND_PORT` | Host port the backend API is published on (Docker mode) |
| `JWT_SECRET` | JWT signing secret — set a real value beyond local dev |
| `FRONTEND_BASE_URL` | Used to build password-reset / email-verification links |
| `CORS_ALLOWED_ORIGINS` | Comma-separated origins allowed to call the API |
| `GOOGLE_CLIENT_ID` / `VITE_GOOGLE_CLIENT_ID` | Google OAuth Web Client ID (leave blank to disable Google sign-in) |
| `FRONTEND_PORT` | Host port the frontend is published on (Docker mode) |
| `VITE_API_BASE_URL` | Backend URL baked into the frontend bundle at build time |

### Notes

- Inside containers, services reach each other by **service name**
  (`postgres`, `backend`), never `localhost`. Only the browser and your host
  machine use `localhost:<published-port>`.
- The `docker` Spring profile (`application-docker.properties`) is used only
  inside the `backend` container; the `local` profile is used for IDE
  development.

---

## Continuous Integration

Every push and pull request targeting `main` automatically runs
[`.github/workflows/ci.yml`](.github/workflows/ci.yml), which verifies:

- **Backend** — `./mvnw verify` (unit tests, Spring context tests, and the
  full Testcontainers-backed PostgreSQL/Flyway integration suite).
- **Frontend** — `npm ci`, lint (`oxlint`), `npm test`, and `npm run build`
  (which also type-checks via `tsc -b`).
- **Docker** — `docker compose config`, `docker compose build`, and a smoke
  test that starts the full stack and checks `/actuator/health`.

If a workflow run fails, check the **Actions** tab on GitHub — each job
uploads its test reports as an artifact when it fails.




