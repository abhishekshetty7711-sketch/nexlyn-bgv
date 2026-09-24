# Nexlyn BGV Platform

Internal, admin-only platform for producing Nexlyn Background Verification (BGV)
reports. Replaces the single-file `nexlyn-bgv-report-v3.2.html` tool with a
Spring Modulith backend, a Postgres-backed dashboard, and server-side PDF
generation. See [`CLAUDE.md`](./CLAUDE.md) for the full spec — locked tech
decisions, architecture, data model, API, security model and the phased build
plan (§15). Work proceeds phase by phase from that file; this README only
covers running what exists today.

## Prerequisites

- Java 21 (JDK), Maven (or use the bundled `./backend/mvnw`)
- Node.js 20+ and npm
- Docker Desktop (Compose v2). **On Windows Home**, Docker Desktop requires
  the WSL2 backend (Hyper-V isn't available on Home editions) — run
  `wsl --install` in an elevated PowerShell, reboot, then start Docker
  Desktop before using `./scripts/local-up.sh`.

## Run locally

```sh
./scripts/local-up.sh
```

This copies `infra/local/.env.example` to `infra/local/.env` on first run,
then builds and starts Postgres, MinIO, the backend and the frontend via
Docker Compose. Backend health: http://localhost:8080/actuator/health.
Frontend: http://localhost:5173.

Stop everything with `./scripts/local-down.sh`.

## Build and test without Docker

```sh
./scripts/build-all.sh
```

Or individually:

```sh
cd backend && ./mvnw verify
cd frontend && npm install && npm run build && npm test
```

## Repository layout

- `backend/` — Maven multi-module Spring Boot app (`bgv-app`) built from
  `bgv-common` plus one module per bounded context (`auth`, `cases`,
  `verification`, `documents`, `reports`), enforced by Spring Modulith.
- `frontend/` — React + TypeScript + Vite admin dashboard.
- `infra/local/` — Docker Compose for local development.
- `infra/prod/` — production deployment (added in Phase 8).
- `docs/reference/` — the original HTML tool and its notes; report rendering
  is ported from here, not from memory (see CLAUDE.md §6).
- `scripts/` — local dev and CI helper scripts.
