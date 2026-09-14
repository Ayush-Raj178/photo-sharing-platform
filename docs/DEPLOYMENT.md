# Deployment Guide

Status: Vercel full-stack deployment configuration applied. Backend runs as a container; frontend runs as a Vite static build. Both use the same GitHub monorepo as separate Vercel projects.

## Architecture

```text
┌──────────────────────────┐     ┌──────────────────────────┐
│  Vercel Project 1        │     │  Vercel Project 2        │
│  (Container)             │     │  (Static / Vite)         │
│                          │     │                          │
│  Spring Boot backend     │     │  React / Vite frontend   │
│  Root Dir: backend       │     │  Root Dir: frontend      │
│  Dockerfile.vercel       │     │  npm run build → dist    │
└────────┬─────────────────┘     └──────────────────────────┘
         │
    ┌────┴────┐
    │         │
    v         v
  Aiven    Cloudinary
  MySQL
```

## Vercel Backend Project (Container)

### Import

1. Go to [vercel.com/new](https://vercel.com/new).
2. Import the GitHub repository: `Ayush-Raj178/photo-sharing-platform`.
3. Set **Root Directory** to `backend`.
4. Vercel detects `Dockerfile.vercel` and selects the Docker builder automatically.

### Environment Variables

Set these in the Vercel project **Settings → Environment Variables**:

| Variable | Example / Notes |
|---|---|
| `PORT` | `8080` (set explicitly unless Vercel injects its own) |
| `DB_JDBC_URL` | `jdbc:mysql://<aiven-host>:<port>/photoshare?sslMode=REQUIRED` |
| `DB_USERNAME` | Aiven application user |
| `DB_PASSWORD` | Aiven password (secret) |
| `STAFF_JWT_SECRET_BASE64` | Base64 of ≥ 32 random bytes |
| `GALLERY_JWT_SECRET_BASE64` | Different base64 signing key |
| `STORAGE_DRIVER` | `cloudinary` |
| `CLOUDINARY_CLOUD_NAME` | Your Cloudinary cloud name |
| `CLOUDINARY_API_KEY` | Cloudinary API key (secret) |
| `CLOUDINARY_API_SECRET` | Cloudinary API secret (secret) |
| `APP_ALLOWED_ORIGINS` | Frontend Vercel URL (set after frontend deploy) |
| `FRONTEND_ORIGIN` | Frontend Vercel URL (set after frontend deploy) |

> **Note:** `APP_ALLOWED_ORIGINS` and `FRONTEND_ORIGIN` require a circular bootstrap — see [Deployment Order](#deployment-order) below.

### Verify

After deployment, confirm:

```
GET https://<backend-vercel-domain>/api/health
→ {"status":"UP"}
```

### Dockerfile Strategy

- `backend/Dockerfile.vercel` is the Vercel-specific multi-stage build.
- `backend/Dockerfile` is preserved for other container hosts (Railway, Fly.io, etc.).
- Builder stage: Maven + Eclipse Temurin 17, dependency caching via `pom.xml` copy-first, production JAR built with tests skipped.
- Runtime stage: `eclipse-temurin:17-jre-alpine`, contains only the fat JAR.
- Startup: `java -jar /app/photoshare-backend.jar`
- PORT: read from `${PORT:8080}` in `application.yml`; Vercel may inject its own value.

---

## Vercel Frontend Project (Vite)

### Import

1. Go to [vercel.com/new](https://vercel.com/new).
2. Import the **same** GitHub repository: `Ayush-Raj178/photo-sharing-platform`.
3. Set **Root Directory** to `frontend`.
4. Vercel auto-detects the Vite framework.

### Build Settings

| Setting | Value |
|---|---|
| Framework | Vite |
| Build Command | `npm run build` |
| Output Directory | `dist` |

### Environment Variables

| Variable | Example |
|---|---|
| `VITE_API_BASE_URL` | `https://<backend-vercel-domain>/api/v1` |

### SPA Routing

`frontend/vercel.json` contains a catch-all rewrite that sends all non-asset paths to `/index.html`:

```json
{
  "rewrites": [
    { "source": "/(.*)", "destination": "/index.html" }
  ]
}
```

This supports direct navigation / browser refresh on all routes:
- `/login`, `/register`
- `/admin/*`, `/team/*`
- `/gallery/:publicId`

Vercel serves static assets (JS, CSS, images) before the rewrite fires, so they are unaffected.

---

## Deployment Order

Because the backend needs the frontend URL for CORS and the frontend needs the backend URL for API calls, follow this bootstrap sequence:

### Step 1 — Deploy Backend First

Deploy the backend with temporary placeholder values:

```
APP_ALLOWED_ORIGINS=http://localhost:5173
FRONTEND_ORIGIN=http://localhost:5173
```

All other variables (`DB_*`, `CLOUDINARY_*`, `*JWT*`, etc.) set to real production values.

Verify `GET /api/health` returns `{"status":"UP"}`.

### Step 2 — Note Backend URL

Copy the backend's Vercel URL, e.g. `https://photoshare-backend-xxx.vercel.app`.

### Step 3 — Deploy Frontend

Deploy the frontend with:

```
VITE_API_BASE_URL=https://photoshare-backend-xxx.vercel.app/api/v1
```

### Step 4 — Note Frontend URL

Copy the frontend's Vercel URL, e.g. `https://photoshare-xxx.vercel.app`.

### Step 5 — Update Backend CORS / Origin

In the backend Vercel project, update:

```
APP_ALLOWED_ORIGINS=https://photoshare-xxx.vercel.app
FRONTEND_ORIGIN=https://photoshare-xxx.vercel.app
```

### Step 6 — Redeploy Backend

Trigger a redeployment of the backend project so the new environment variables take effect.

### Step 7 — End-to-End Verification

1. Open the frontend URL in a browser.
2. Register / log in as Admin.
3. Create a gallery, upload photos, publish.
4. Open the generated public gallery link — it should use `FRONTEND_ORIGIN`, not `localhost`.
5. Verify PIN access works.
6. Confirm no CORS errors in the browser console.

---

## Gallery Link Behavior

The backend uses `FRONTEND_ORIGIN` to construct the customer-facing gallery URL. In production, this must be the actual frontend Vercel URL (HTTPS). The backend URL is never exposed as the customer gallery link.

## CORS

`APP_ALLOWED_ORIGINS` accepts comma-separated exact origins. Wildcards are never used. Only the frontend Vercel URL should be listed in production.

## Health Endpoint

`GET /api/health` is public, requires no authentication, and returns:

```json
{"status": "UP"}
```

It does not expose secrets, database state, or cloud provider details.

---

## Environment Contract Summary

### Backend (12 required variables)

| # | Variable | Secret? |
|---|---|---|
| 1 | `PORT` | No |
| 2 | `DB_JDBC_URL` | No |
| 3 | `DB_USERNAME` | Yes |
| 4 | `DB_PASSWORD` | Yes |
| 5 | `STAFF_JWT_SECRET_BASE64` | Yes |
| 6 | `GALLERY_JWT_SECRET_BASE64` | Yes |
| 7 | `STORAGE_DRIVER` | No |
| 8 | `CLOUDINARY_CLOUD_NAME` | No |
| 9 | `CLOUDINARY_API_KEY` | Yes |
| 10 | `CLOUDINARY_API_SECRET` | Yes |
| 11 | `APP_ALLOWED_ORIGINS` | No |
| 12 | `FRONTEND_ORIGIN` | No |

### Frontend (1 required variable)

| # | Variable | Secret? |
|---|---|---|
| 1 | `VITE_API_BASE_URL` | No |

---

## Production Build Reference

| Component | Command |
|---|---|
| Backend build | `mvn -B -DskipTests package` |
| Backend start | `java -jar target/photoshare-backend-0.0.1-SNAPSHOT.jar` |
| Frontend build | `npm run build` |
| Frontend output | `dist/` |

## Key Production Behaviors

- **MySQL Migration**: Flyway V1 and V2 migrations run automatically on startup and apply to an empty production DB. Hibernate validates the schema post-migration.
- **Cloudinary Storage**: Preserves authenticated delivery, backend-protected retrieval, and signed CDN read implementation. Does not expose secrets or signed URLs.
- **Known Limitations**: On a free-tier hosting platform, cold-starts might delay the initial backend response.

## Operations and Rollback

Persist uploaded originals independently of API restarts/releases. Back up MySQL and Cloudinary using compatible recovery points. Do not place dumps or customer photos in the repository.

For a failed release, restore the previous compatible application build and follow reviewed migration rollback/forward-repair steps. Never drop/recreate production schema or delete the storage bucket to roll back. Keep migrations backward-compatible where practical and back up before risky changes.
