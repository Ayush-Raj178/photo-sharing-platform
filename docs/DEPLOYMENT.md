# Deployment Guide

Status: Vercel Services deployment configuration applied. Frontend and backend deploy together as ONE Vercel project using Vercel Services. Both services share a single public domain.

## Architecture

```text
┌─────────────────────────────────────────────────┐
│             ONE Vercel Project                  │
│             (Vercel Services)                   │
│                                                 │
│  ┌──────────────────┐  ┌──────────────────────┐ │
│  │ frontend service │  │ backend service      │ │
│  │ React / Vite     │  │ Spring Boot (Docker) │ │
│  │ root: frontend/  │  │ root: backend/       │ │
│  │ Static → dist/   │  │ Dockerfile.vercel    │ │
│  └──────────────────┘  └──────────┬───────────┘ │
│                                   │             │
│        shared public domain       │             │
│        https://<project>.vercel.app             │
└───────────────────────────────────┼─────────────┘
                               ┌────┴────┐
                               │         │
                               v         v
                             Aiven    Cloudinary
                             MySQL
```

## Root vercel.json (Vercel Services)

The root [`vercel.json`](../vercel.json) defines two services and path-based routing:

```json
{
  "services": {
    "frontend": {
      "root": "frontend/"
    },
    "backend": {
      "root": "backend/",
      "runtime": "container",
      "entrypoint": "Dockerfile.vercel"
    }
  },
  "rewrites": [
    { "source": "/api/(.*)", "destination": { "service": "backend" } },
    { "source": "/(.*)", "destination": { "service": "frontend" } }
  ]
}
```

### Routing Rules

| Request Path | Routed To |
|---|---|
| `/api/health` | backend service |
| `/api/v1/*` | backend service |
| `/login`, `/register` | frontend service |
| `/admin/*`, `/team/*` | frontend service |
| `/gallery/:publicId` | frontend service |
| `/assets/*`, `*.js`, `*.css` | frontend service (static) |
| Everything else | frontend service |

The `/api/(.*)` rewrite is evaluated first. All API traffic reaches the Spring Boot container. Everything else falls through to the Vite static build.

---

## Frontend Service

### Configuration

- **Root**: `frontend/`
- **Framework**: Vite (auto-detected)
- **Build Command**: `npm run build`
- **Output Directory**: `dist`

### SPA Routing

The frontend service has its own [`frontend/vercel.json`](../frontend/vercel.json) with a catch-all rewrite:

```json
{
  "rewrites": [
    { "source": "/(.*)", "destination": "/index.html" }
  ]
}
```

This ensures direct browser navigation and refresh work on all client-side routes (`/login`, `/register`, `/admin/*`, `/team/*`, `/gallery/:publicId`). Static assets (JS, CSS, images) in `dist/assets/` are served directly before the rewrite fires.

### API Base URL

Since both services share the same public origin, the frontend uses a same-origin relative base:

```
VITE_API_BASE_URL=/api/v1
```

No cross-origin requests in production. Local development falls back to `http://localhost:8080/api/v1` automatically when `VITE_API_BASE_URL` is not set.

---

## Backend Service

### Configuration

- **Root**: `backend/`
- **Runtime**: `container`
- **Entrypoint**: `Dockerfile.vercel`

### Dockerfile Strategy

- [`backend/Dockerfile.vercel`](../backend/Dockerfile.vercel) is the Vercel-specific multi-stage build.
- [`backend/Dockerfile`](../backend/Dockerfile) is preserved for other container hosts (Railway, Fly.io, etc.).
- Builder stage: `maven:3.9.4-eclipse-temurin-17`, dependency caching via `pom.xml` copy-first, production JAR built with tests skipped.
- Runtime stage: `eclipse-temurin:17-jre-alpine`, contains only the fat JAR.
- Startup: `java -jar /app/photoshare-backend.jar`
- PORT: read from `${PORT:8080}` in `application.yml`; Vercel injects `$PORT` automatically.

---

## Environment Variables (ONE Vercel Project)

All environment variables are set in a single Vercel project under **Settings → Environment Variables**.

### Backend Variables (11 required)

| # | Variable | Value / Notes | Secret? |
|---|---|---|---|
| 1 | `DB_JDBC_URL` | `jdbc:mysql://<aiven-host>:<port>/photoshare?sslMode=REQUIRED` | No |
| 2 | `DB_USERNAME` | Aiven application user | Yes |
| 3 | `DB_PASSWORD` | Aiven password | Yes |
| 4 | `STAFF_JWT_SECRET_BASE64` | Base64 of ≥ 32 random bytes | Yes |
| 5 | `GALLERY_JWT_SECRET_BASE64` | Different base64 signing key | Yes |
| 6 | `STORAGE_DRIVER` | `cloudinary` | No |
| 7 | `CLOUDINARY_CLOUD_NAME` | Your Cloudinary cloud name | No |
| 8 | `CLOUDINARY_API_KEY` | Cloudinary API key | Yes |
| 9 | `CLOUDINARY_API_SECRET` | Cloudinary API secret | Yes |
| 10 | `APP_ALLOWED_ORIGINS` | `https://<project>.vercel.app` | No |
| 11 | `FRONTEND_ORIGIN` | `https://<project>.vercel.app` | No |

> **Note:** `APP_ALLOWED_ORIGINS` and `FRONTEND_ORIGIN` should be set to the project's public Vercel URL. Since both services share the same origin, this is straightforward — no circular bootstrap required. Set a temporary value on the first deploy, then update once you know the final URL.

### Frontend Variables (1 required)

| # | Variable | Value | Secret? |
|---|---|---|---|
| 1 | `VITE_API_BASE_URL` | `/api/v1` | No |

---

## Deployment Steps

### Step 1 — Import Repository

1. Go to [vercel.com/new](https://vercel.com/new)
2. Import the GitHub repository: `Ayush-Raj178/photo-sharing-platform`
3. Do NOT set a Root Directory — leave it at the repository root so Vercel discovers the root `vercel.json`
4. Vercel detects the Services configuration automatically

### Step 2 — Set Environment Variables

In the Vercel project **Settings → Environment Variables**, add all variables from both tables above.

For the first deployment, you can use a placeholder for `APP_ALLOWED_ORIGINS` and `FRONTEND_ORIGIN` (e.g., `https://placeholder.vercel.app`).

Set `VITE_API_BASE_URL` to `/api/v1`.

### Step 3 — Deploy

Click **Deploy**. Vercel builds both services:
- Frontend: runs `npm run build` in `frontend/`, outputs to `dist/`
- Backend: builds Docker image from `backend/Dockerfile.vercel`

### Step 4 — Verify Health

```
GET https://<project>.vercel.app/api/health
→ {"status":"UP"}
```

### Step 5 — Update Origin Variables

Once you know the final project URL:

1. Update `APP_ALLOWED_ORIGINS` → `https://<project>.vercel.app`
2. Update `FRONTEND_ORIGIN` → `https://<project>.vercel.app`
3. Redeploy

### Step 6 — End-to-End Verification

1. Open `https://<project>.vercel.app` in a browser
2. Register / log in as Admin
3. Create a gallery, upload photos, publish
4. Open the generated public gallery link — it should use the project URL
5. Verify PIN access works
6. Confirm no CORS errors in the browser console
7. Test direct URL navigation: `/login`, `/gallery/<id>`, etc.

---

## CORS

With Vercel Services, browser requests from the frontend to `/api/*` are same-origin — no CORS preflight is triggered.

`APP_ALLOWED_ORIGINS` is still respected by the backend for safety. Set it to the project's public Vercel URL. Wildcards are never used.

## Public Gallery URL

The backend uses `FRONTEND_ORIGIN` to construct customer-facing gallery links. In production, this is the shared Vercel project URL:

```
https://<project>.vercel.app/gallery/<publicId>
```

The backend container's internal URL is never exposed to customers.

## Health Endpoint

`GET /api/health` is public, requires no authentication, and returns:

```json
{"status": "UP"}
```

It does not expose secrets, database state, or cloud provider details.

---

## Production Build Reference

| Component | Command |
|---|---|
| Backend build | Dockerfile.vercel: `mvn -B -DskipTests package` |
| Backend start | `java -jar /app/photoshare-backend.jar` |
| Frontend build | `npm run build` |
| Frontend output | `dist/` |

## Key Production Behaviors

- **MySQL Migration**: Flyway V1 and V2 migrations run automatically on startup. Hibernate validates the schema post-migration.
- **Cloudinary Storage**: Preserves authenticated delivery, backend-protected retrieval, and signed CDN read. Does not expose secrets.
- **Same-Origin**: Frontend and backend share a domain — no CORS complexity.
- **Known Limitations**: Cold-starts may delay the first backend response on free-tier.

## Operations and Rollback

Persist uploaded originals independently of deployments. Back up MySQL and Cloudinary using compatible recovery points. Do not place dumps or customer photos in the repository.

For a failed release, restore the previous deployment via Vercel's deployment history. Keep Flyway migrations backward-compatible and back up before risky changes.
