# Planned deployment and submission

Status: Production-ready configuration applied. No hosting service, production database, domain, repository, or deployment has been created yet. Supports DEPLOY-01 through DEPLOY-07, DOC-04, DOC-05, and SEC-08.

## Topology and provider decision

Plan an HTTPS frontend origin serving the React/Vite static build, with /api routed to one Spring Boot instance. MySQL is reachable only by the backend over a private or tightly restricted network. Original photos reside in a private persistent object storage service; only the backend's storage identity can access them. Production must not rely on ephemeral application disk for stored originals.

Prefer same-origin frontend/API access to simplify browser configuration. If separate origins are later required, configure an exact CORS allowlist and HTTPS on both. Do not let a reverse proxy cache protected JSON/image responses.

Cloudinary is selected for production-capable photo storage. Application/database hosting, paid tier, region, and resource sizing remain undecided. Verify current limits and pricing before deployment. A hosting provider's reverse-proxy request cap must support the approved multipart envelope; if it cannot, revise the upload design/limits together before deployment.

Use one backend instance initially because proposed rate-limit counters are in memory. Configure known proxy forwarding and bounded upload/hash concurrency. Before adding instances, provide shared rate limiting or equivalent edge enforcement and verify cross-instance behavior. Storage/DB remain shared durable dependencies.

## Environment contract

The root [.env.example](../.env.example) is a placeholder inventory. Spring binds the documented backend variables, but does not automatically load the root `.env.example`; values must be provided by the current shell, an ignored local mechanism, or the host secret facility.

Only variables prefixed VITE_ may be exposed to the frontend build; never put secrets in them. Local values go in ignored files. Production secrets come from the host's secret facility rather than committed environment files. The numeric values below are proposed defaults documented for planning; the example file itself contains placeholders only.

### Application and browser

- APP_PROFILE: local, test, or production configuration profile; bind explicitly.
- SERVER_PORT: backend listener chosen by host (local proposal 8080); do not expose MySQL publicly.
- FRONTEND_ORIGIN: canonical frontend origin used to construct share URLs; HTTPS in production.
- CORS_ALLOWED_ORIGINS: comma-separated exact permitted development/alternate frontend origins; no wildcard.
- VITE_API_BASE_URL: frontend-visible API base; proposal /api/v1 under same-origin routing. A frontend development proxy may supply local routing later.

### MySQL

- DB_JDBC_URL: provider-appropriate JDBC connection URL including database name and required TLS settings; no password embedded.
- DB_USERNAME: dedicated least-privilege application account.
- DB_PASSWORD: secret password from local ignored configuration or production secret facility.

Use separate databases/accounts for local, test, and production. Run versioned migrations with appropriately scoped migration permissions; the runtime account should not require global administrative privileges. Pin and document the chosen database version later.

### Authentication and access

- JWT_ISSUER: stable identifier verified by both token validators.
- STAFF_JWT_SECRET_BASE64: unique random staff signing key, at least 32 random bytes before base64 encoding.
- GALLERY_JWT_SECRET_BASE64: independent random gallery signing key with the same strength; never reuse the staff key.
- STAFF_JWT_TTL_SECONDS: proposed 900.
- GALLERY_GRANT_TTL_SECONDS: proposed 900.
- RATE_LIMIT_WINDOW_SECONDS: proposed 300.
- LOGIN_RATE_LIMIT_ATTEMPTS: proposed 10 per account + client IP per window.
- LOGIN_RATE_LIMIT_IP_ATTEMPTS: proposed 50 per client IP across accounts per window.
- PIN_RATE_LIMIT_ATTEMPTS: proposed 5 per share token + client IP per window.
- PIN_RATE_LIMIT_IP_ATTEMPTS: proposed 50 per client IP across tokens per window.
- REGISTRATION_RATE_LIMIT_ATTEMPTS: proposed 5 per client IP per window.
- MEMBER_PROVISION_RATE_LIMIT_ATTEMPTS: proposed 20 per authenticated Admin per window.

Audiences/token types and proposed Argon2id parameters are specified in [security-model.md](security-model.md). Validate all configuration at startup; production must refuse placeholder, empty, reused, or weak keys.

### Private photo storage

- STORAGE_DRIVER: `local` for development/tests or `cloudinary` for production-capable storage.
- CLOUDINARY_CLOUD_NAME: Cloudinary product-environment name, required only for Cloudinary mode.
- CLOUDINARY_API_KEY: backend-only Cloudinary API key, required only for Cloudinary mode.
- CLOUDINARY_API_SECRET: backend-only Cloudinary API secret, required only for Cloudinary mode; never expose or print it.
- LOCAL_STORAGE_ROOT: local-only absolute folder outside the webroot; never the production persistence plan.
- MAX_FILE_SIZE_BYTES: proposed 10485760 (10 MiB).
- MAX_REQUEST_SIZE_BYTES: proposed 220200960 (210 MiB including multipart overhead).
- MAX_FILES_PER_UPLOAD: proposed 20.
- MAX_IMAGE_PIXELS: proposed 40000000.

Cloudinary credentials are explicitly bound only when Cloudinary mode is selected; all three are validated at startup. Configure matching proxy/server/app limits. Storage key structure, reconciliation grace periods, and cleanup are in [storage-design.md](storage-design.md).

## Production Build and Execution

**Architecture**:
```text
Vercel frontend
      |
      v
Spring Boot backend
   |          |
   v          v
MySQL      Cloudinary
```

**Required Environment Variables**:
- **Backend**: `DB_JDBC_URL`, `DB_USERNAME`, `DB_PASSWORD`, `STAFF_JWT_SECRET_BASE64`, `GALLERY_JWT_SECRET_BASE64`, `STORAGE_DRIVER` (cloudinary), `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`, `APP_ALLOWED_ORIGINS`, `PORT`.
- **Frontend**: `VITE_API_BASE_URL`.

**Build and Start Commands**:
- **Backend Build**: `mvn -B -DskipTests package`
- **Backend Start**: `java -jar target/photoshare-backend-0.0.1-SNAPSHOT.jar`
- **Frontend Build**: `npm run build`

**Key Production Behaviors**:
- **MySQL Migration**: Flyway V1 and V2 migrations run automatically on startup and apply to an empty production DB. Hibernate validates the schema post-migration.
- **CORS Setup**: `APP_ALLOWED_ORIGINS` configures allowed CORS origins without using wildcard `*` with authenticated requests.
- **Cloudinary Storage**: Preserves authenticated delivery, backend-protected retrieval, and signed CDN read implementation. Does not expose secrets or signed URLs.
- **Health Check Path**: `GET /api/health` returns `{"status": "UP"}` publicly.
- **Known Limitations**: On a free-tier hosting platform, cold-starts might delay the initial backend response, and Vercel requires a `vercel.json` rewrite to route SPA paths properly (which has been added).

## Planned deployment sequence after implementation approval

1. Choose application/database hosting and the Cloudinary service tier; review costs, upload limits, durability, and access policies.
2. Provision isolated production resources with TLS and restricted networking; inject real environment values securely.
3. Run reviewed database migrations and verify schema constraints. Do not use destructive schema auto-update as a release strategy.
4. Build and deploy the API and frontend using the build commands provided above. Set canonical share-link origin and same-origin /api routing.
5. Confirm anonymous storage reads/listing are blocked; test backend write/read/cleanup and persistence after application restart.
6. Provision synthetic demo Admin/Team Member accounts and a selected published gallery using approved means. Keep their credentials/PIN out of version control, screenshots, and build output.
7. Run the complete live workflow and negative tests (wrong PIN, cross-event, member publish attempt, unpublished/unselected photo access), then record truthful evidence.
8. Complete the final README with real setup/test/deployment instructions and known limitations; audit source and dependency/configuration choices.
9. Only after completion/audit and explicit user direction, handle Git/repository creation, commits/pushes, and final submission. None occurs in this documentation phase.

## Operations and rollback considerations

Persist uploaded originals independently of API restarts/releases. Back up MySQL and storage using compatible recovery points; choose retention/recovery targets with the provider later and test restoration before claiming recoverability. Do not place dumps or customer photos in the repository.

Emit sanitized request/failure logs. Check application availability, DB/storage connectivity, failed uploads, and stale PENDING records using host capabilities; a monitoring platform is not mandated. Do not expose secrets or detailed dependency health publicly.

For a later failed release, restore the previous compatible application build and follow reviewed migration rollback/forward-repair steps. Never drop/recreate production schema or delete the storage bucket to roll back. Keep migrations backward-compatible where practical and back up before risky changes.

## Submission checklist (all NOT STARTED)

- DEPLOY-01: cloud application deployed and accessible online.
- DEPLOY-02: working live application URL.
- DEPLOY-03: source code and evaluator-accessible source repository, created only after user authorization.
- DEPLOY-04: working demo Admin credentials supplied privately.
- DEPLOY-05: working demo Team Member credentials supplied privately.
- DEPLOY-06: working demo Gallery URL and PIN supplied privately.
- DOC-02 through DOC-05: full final README with overview/stack, architecture/DB explanation, local setup/environment variables, deployment steps, and known limitations.
- TEST-01 through TEST-04: implemented tests and truthful verification evidence.
- DOC-06: readiness to explain code/architecture and make a small change.
- DEPLOY-07: all required deliverables accessible and functional at submission to talent@trizen-ai.com by September 20, 2026, 11:59 PM IST.

Do not copy SRD example credentials into a live system or commit actual demo credentials. The user has not authorized sending submission email. Hosting choice, final repository visibility, demo credential delivery channel, backup retention, and final deployment commands remain open.
