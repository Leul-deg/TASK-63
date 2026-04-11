# Residential Life Operations Portal

Local-only, on-prem portal for managing residential life operations: resident directory, housing agreements, room bookings, messaging, notifications, analytics, data collection, and local system integrations.

---

## Quick start (5 minutes)

### Prerequisites

| Tool | Version |
|------|---------|
| Docker + Docker Compose | any recent |
| Java | 21 |
| Maven | 3.9+ |
| Node.js | 20+ |

### 1 — Start PostgreSQL

```bash
export POSTGRES_DB=reslife
export POSTGRES_USER=reslife
export POSTGRES_PASSWORD=reslife
docker compose up -d
```

By default the compose stack publishes PostgreSQL on port `55432` so it does not collide with a locally running database on `5432`.
Override it if you prefer:

```bash
RESLIFE_DB_PORT=5432 docker compose up -d
```

### 2 — Start the backend

```bash
export SPRING_PROFILES_ACTIVE=local
export SPRING_DATASOURCE_USERNAME="$POSTGRES_USER"
export SPRING_DATASOURCE_PASSWORD="$POSTGRES_PASSWORD"
export RESLIFE_ENCRYPTION_KEY="$(openssl rand -base64 32)"
cd backend
./mvnw spring-boot:run
```

Health check: `curl http://localhost:8080/api/health` → `{"status":"ok"}`

Flyway runs all migrations automatically on startup. The dev seed data (V13, V14, V17) populates sample residents, agreements, messages, notifications, and the three login accounts below.

### 3 — Start the frontend

```bash
cd frontend
npm install
npm start
```

Opens at http://localhost:3000. The `proxy` field in `package.json` forwards all `/api/**` calls to the backend on port 8080.

---

## Dev login accounts

All passwords are `password` (BCrypt cost 12, matching the production encoder).

| Username | Role | Email |
|----------|------|-------|
| `admin` | Admin | admin@reslife.local |
| `staff` | Residence Staff | staff@reslife.local |
| `student` | Student | student@reslife.local |

> **Reseed note:** If you need to recreate dev passwords (e.g. after a schema reset),
> migration V17 re-hashes them at cost 12 automatically on next startup.

---

## Architecture overview

```
browser (React 18)
    │  HTTP + cookie session
    ▼
Spring Boot 3.2 / Java 21   ← port 8080
    │  JDBC (Spring Data JPA + JdbcTemplate)
    ▼
PostgreSQL 16               ← port 55432
```

### Backend layout

```
backend/src/main/java/com/reslife/api/
├── admin/           Admin-only REST controllers (users, analytics, crawler, integration keys, booking policy)
├── auth/            Login and session endpoints
├── common/          BaseEntity, SoftDeletableEntity
├── config/          SecurityConfig, JpaConfig, IntegrationConfig, CrawlerConfig
├── controller/      Health check endpoint
├── domain/
│   ├── analytics/   Snapshot compute service (runs every 15 min)
│   ├── crawler/     Multi-source data collection engine + scheduler
│   ├── housing/     Housing agreements, move-in records, attachments, booking policy enforcement
│   ├── integration/ HMAC signing, rate limiter, webhooks, audit log
│   ├── messaging/   Message threads
│   ├── notification/ Notification templates, delivery, acknowledgment
│   ├── resident/    Resident directory, bulk import/export
│   ├── system/      Audit log, configuration versioning
│   └── user/        User entity, roles, account status
├── encryption/      Field-level encryption converters for sensitive data
├── security/        Spring Security UserDetails impl, account status filter
├── storage/         File attachment storage
└── web/             Global exception handler, API error response type
```

### Database migrations (Flyway)

| Version | Description |
|---------|-------------|
| V1 | Initial schema (users, roles, sessions) |
| V2 | Auth schema refinements |
| V3 | Data protection fields |
| V4 | Resident directory |
| V5 | Resident form fields |
| V6 | Agreement attachments |
| V7 | Messaging enhancements |
| V8 | Notification templates |
| V9 | Booking policy seed |
| V10 | Integration schema (keys, webhooks, audit log) |
| V11 | Crawler schema (sources, jobs, pages) |
| V12 | Analytics snapshots table |
| V13 | Dev seed: buildings, residents, agreements, move-ins, notifications |
| V14 | Dev seed: login accounts (admin/staff/student), messages |
| V15 | Audit log actor snapshot + ON DELETE SET NULL FK |
| V16 | Message delivery receipts (DELIVERED status) |
| V17 | Re-hash dev seed passwords at BCrypt cost 12 |
| V18 | Rename analytics metric booking_conversion → agreement_signthrough |
| V19 | Fix onboarding.welcome notification template (strip unsupported Mustache section syntax) |
| V20 | Resident bookings schema + analytics metric key restored to booking_conversion |
| V21 | Dev seed: resident bookings for analytics and UI |

---

## Key features

### Analytics dashboard (`/admin/analytics`)

Four pre-computed metrics refreshed every 15 minutes (and on startup):

- **Booking conversion** — requested/confirmed/completed/cancelled/no-show booking status breakdown + 6-month converted trend
- **No-show rate** — move-in check-in status breakdown + monthly no-show trend
- **Slot utilization** — occupied vs. total rooms, by building
- **Settlement completion** — acknowledged-notification completion by category

Metrics are cached in `analytics_snapshots` and served without hitting aggregation queries on every page load. Use "Refresh now" to force an immediate recompute.

### Data collector (`/admin/crawler`)

Configurable multi-source crawl engine for intranet/local sites:

- Per-source cron or fixed-interval schedule
- Resumable checkpoints (survives restart mid-crawl)
- Concurrency cap (default 5 parallel fetchers)
- Per-source throttle delay and max-depth/max-pages limits
- Pause, resume, and cancel individual jobs via the UI

### Local integrations (`/admin/integration-keys`)

HMAC-SHA256 signed requests for on-prem devices. See [INTEGRATION.md](INTEGRATION.md) for the full signing protocol.

- Outgoing webhooks are restricted to private/local IP addresses (SSRF prevention)
- Rate limited: 60 requests per minute per integration key
- Replay protection: timestamps must be within 5 minutes of server clock

### Resident bookings (`/residents/:id/bookings`)

Staff can create, list, and update resident bookings from the resident directory. The backend enforces the active booking policy at booking-creation time, so requests outside the booking window, after the same-day cutoff, on blackout dates, or during a no-show restriction are rejected before a booking is persisted.

- Staff route: `GET/POST/PATCH /api/residents/{residentId}/bookings`
- Student self-service read-only view: `GET /api/students/me/bookings`
- Booking statuses: `REQUESTED`, `CONFIRMED`, `COMPLETED`, `CANCELLED`, `NO_SHOW`

---

## Running tests

### Backend (JUnit 5)

```bash
cd backend
./mvnw test
```

Backend tests cover auth service/controller behavior, integration HMAC/rate-limit/local-network validation, inbound integration filter/controller behavior, booking policy and booking creation boundaries, notification access control, import preview duplicate matching, and attachment validation/controller ownership checks.

### Frontend (React Testing Library)

```bash
cd frontend
npm test
```

Frontend tests cover the app shell/login page plus the resident booking page’s load/create flow.

---

## Offline constraints

This system is designed to operate **without internet access**:

- No CDN dependencies — all JS/CSS is bundled by `react-scripts`
- No external chart library — analytics uses pure SVG rendered in React
- No external fonts or icon sets
- Docker image `postgres:16-alpine` must be pulled once; thereafter fully offline
- Outgoing webhooks are hard-blocked from reaching public IPs by `LocalNetworkValidator`

---

## Database connection

Local dev connection values are explicit environment variables rather than committed runtime defaults:

| Field | Value |
|-------|-------|
| Host | localhost |
| Port | 55432 |
| Database | `$POSTGRES_DB` |
| User | `$SPRING_DATASOURCE_USERNAME` |
| Password | `$SPRING_DATASOURCE_PASSWORD` |

Required environment variables:

- `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` for the local compose database
- `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` for backend startup
- `RESLIFE_ENCRYPTION_KEY` for AES-256-GCM field encryption
- `SPRING_PROFILES_ACTIVE=local` for local non-TLS cookie settings
- Optional: `SPRING_DATASOURCE_URL`, `RESLIFE_DB_PORT`

---

## Known gaps and remaining work

| Area | Status | Notes |
|------|--------|-------|
| Rooms & maintenance | Placeholder routes | UI not built; backend schema exists in early migrations |
| Incidents | Placeholder route | Not yet implemented |
| Resident form | Implemented | Guided edit flow exists; bulk-import CSV works |
| Booking workflow | Implemented for staff / read-only for students | Staff can create and manage resident bookings; students can view their own bookings |
| Email / push notifications | Not implemented | Notifications are in-app only |
| Session store | JDBC-backed | Spring Session persists sessions in PostgreSQL |
| Production hardening | Partial | Local secrets are no longer committed in the default runtime path, but TLS and auth-endpoint rate limiting still need production rollout work |
| File uploads | Implemented for agreements | Staff can upload, list, download, and delete agreement attachments |
| Crawler content indexing | Raw HTML stored | No full-text search or NLP extraction layer |
