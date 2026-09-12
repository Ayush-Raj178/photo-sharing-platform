# Planned testing and verification

Status: backend integration, Cloudinary boundary, frontend unit/build, and live browser journey verification pass locally. The user's real standalone Cloudinary upload/cleanup smoke succeeded on 2026-09-10. Normal Cloudinary/MySQL workflow checks, expanded failure injection, final README checks, and release/submission checks remain outstanding. Case statuses below distinguish verified coverage from partial and planned coverage.

## Scope and fixtures

Use unit tests for pure rules/provider mapping, integration/API tests for persistence/security/transaction behavior, and end-to-end browser verification for the SRD workflow. The implemented local suites use JUnit/Spring Boot/MockMvc with H2, synthetic Cloudinary boundary clients, real-SDK loopback signing tests, Vitest/Testing Library with jsdom, and Playwright with installed Chrome. Real MySQL metadata and Cloudinary protected application flows still require separate verification beyond the successful standalone storage smoke.

Fixtures: Admin A and Admin B; A's members A1 and A2; B's member B1; two events owned by A and one by B; assigned/unassigned member combinations; READY/PENDING/FAILED photos; an A draft and a published gallery on another event. Include an unselected photo in a published gallery's event and a published gallery for B. Keep synthetic photos and temporary credentials isolated from production. Public tests start without staff sessions.

Each case below states current coverage. Tests of policy choices (PIN length, limits, one-gallery lifecycle, token storage) are design verification, not invented SRD requirements.

## Unit tests

### U-01: Credential and token rules

- Requirement IDs: AUTH-01, AUTH-02, SEC-01, SEC-06, TEST-01.
- Status: NOT STARTED.
- Planned verification: Verify hashing the same password/PIN with fresh random salts produces different encoded hashes while both verify correctly; verify correct/wrong credentials; reject expired, bad-signature, wrong-issuer, wrong-audience, and wrong-type tokens. Preserve leading-zero PIN strings. Hash cost and token policies are project decisions.

### U-02: Role and scope predicates

- Requirement IDs: USER-02, EVENT-02, PHOTO-02, PHOTO-03, SEC-01, SEC-02, SEC-03, SEC-07, TEST-01, TEST-02.
- Status: PARTIAL — frontend route-role redirection is covered; detailed backend scope predicates are primarily exercised through integration tests.
- Planned verification: Check owner, assigned uploader, different member, unassigned member, different Admin, and public grant cases. Test nested resource/event mismatch and no trusted client ownership fields.

### U-03: Upload validation and compensation

- Requirement IDs: PHOTO-04, PHOTO-05, PHOTO-06, PHOTO-07, SEC-04, SEC-05.
- Status: PARTIAL — frontend JPEG/PNG prevalidation is covered; compensation/failure-injection cases remain planned.
- Planned verification: Check empty/oversize files, too many files, aggregate limits, MIME/extension/signature mismatch, corrupt/unsupported image, excessive decoded pixels, path-like filenames, unique keys, partial writes, uncertain finalization, and safe cleanup decisions.

### U-04: Gallery state transitions

- Requirement IDs: GALLERY-01, GALLERY-02, GALLERY-03, GALLERY-04, PUBLIC-02, PUBLIC-03, SEC-07, TEST-03, TEST-04.
- Status: PARTIAL — the public PIN gate and protected-data behavior after a wrong PIN are covered at component level; backend lifecycle rules are primarily integration-tested.
- Planned verification: Verify draft selection replacement/order, duplicate rejection, same-event/READY checks, nonempty selection and PIN prerequisites, first publication, atomic re-publication, stable share-token format, optional PIN rotation/versioning, and grant scoping. Values such as PIN format remain design-policy tests.

## Integration/API tests

### I-01: Registration and login

- Requirement IDs: AUTH-01, AUTH-02, SEC-01, SEC-05, SEC-06, TEST-01.
- Status: PARTIAL — Admin registration, Team Member login, unauthenticated denial, and role separation pass; the full invalid/expired/substituted-token matrix remains planned.
- Planned verification: Register an Admin, reject role/owner injection, normalize duplicate email, log in both roles, reject unknown/wrong credentials uniformly, and reject invalid/expired/substituted tokens on protected endpoints. Verify database hashes and no secret fields in responses.

### I-02: Event ownership and member provisioning

- Requirement IDs: USER-01, EVENT-01, EVENT-02, SEC-02, SEC-05, TEST-01.
- Status: PARTIAL — two-Admin ownership, new/existing member assignment, assigned lists, and foreign-event denial pass; rollback and injection permutations remain planned.
- Planned verification: Create events for two Admins; provision a member atomically with assignment; assign an existing own-roster member; reject other-owner roster IDs, mixed request forms, duplicate assignment/email, and role spoofing. Verify owned/assigned event lists and rollback on failed membership insertion.

### I-03: Authorization across all staff resource routes

- Requirement IDs: USER-02, PHOTO-02, PHOTO-03, SEC-01, SEC-02, SEC-03, SEC-07, TEST-01, TEST-02.
- Status: PARTIAL — key event, member, photo, gallery, and publish denials pass; exhaustive route-by-route token/resource permutation coverage remains planned.
- Planned verification: Attempt direct forbidden requests as a Team Member to event/member/gallery creation, selection, PIN setting, and publish. Check every metadata/content route with foreign event/photo/gallery IDs, including own photo under the wrong event path. Owner sees all own-event uploads; members see/read only their own while assigned. Reject inaccessible/missing resources without data leaks.

### I-04: Multiple uploads and real metadata/storage

- Requirement IDs: PHOTO-01, PHOTO-04, PHOTO-05, PHOTO-06, SEC-04, ARCH-01, TEST-02.
- Status: PARTIAL — two-file multipart upload, metadata persistence, role scope, local storage, Cloudinary mapping, and an explicit no-binary-column schema check pass. The user's standalone production-adapter smoke verifies real authenticated Cloudinary upload and cleanup; normal multi-photo Cloudinary/MySQL metadata verification remains pending.
- Planned verification: Repeat the normal upload against real Cloudinary and MySQL. Confirm Cloudinary holds the bytes while MySQL retains only metadata/storage identifiers, and confirm ADMIN/unassigned upload is denied before storage.

### I-05: Upload failure boundaries and recovery

- Requirement IDs: PHOTO-07, SEC-04, SEC-05, TEST-02.
- Status: PARTIAL — invalid image content and an injected provider failure in a mixed batch produce per-file failures; the successful file remains READY and cleanup is attempted. Commit/timeout/reconciliation failures remain planned.
- Planned verification: Inject preflight outage, write timeout, metadata commit failure/uncertainty, cleanup failure, crash/stale PENDING record, and lost response. Verify reconciliation never deletes READY/selected objects. Test server/proxy aggregate request rejection as well as file validation.

### I-06: Draft selection and publication/re-publication transactions

- Requirement IDs: GALLERY-01, GALLERY-02, GALLERY-03, GALLERY-04, SEC-02, SEC-03, TEST-03.
- Status: PARTIAL — selection, cross-event rejection, PIN setup, initial publication, stable-link re-publication, PIN rotation, and grant invalidation pass; concurrency and failure-injection cases remain planned.
- Planned verification: Create one draft per event, set PIN, replace ordered selection, reject foreign/non-READY/duplicate photos atomically, reject empty/missing-PIN publication, and publish only the selected set. Re-publish a complete replacement selection and optionally a new PIN while keeping the share URL stable. Race draft mutation/re-publication to verify locking; failures retain the previous complete public state. An unchanged re-publish is idempotent. PIN rotation invalidates old gallery grants, while a selection-only re-publication keeps valid grants. Second gallery creation fails under A-05.

### I-07: PIN verification and gallery grants

- Requirement IDs: PUBLIC-01, PUBLIC-02, PUBLIC-03, SEC-06, TEST-04.
- Status: PARTIAL — unknown/draft link, wrong/correct/rotated PIN, wrong-gallery grant, and stale-grant cases pass; expiry/signature/rate-limit boundaries remain planned.
- Planned verification: Without any staff account/session, wrong PIN returns 401 and no grant; correct PIN returns a scoped grant. Check no metadata before PIN, leading zeros, expired/forged/wrong-gallery grants, unknown/draft link behavior, separate signing keys, rate-limit/Retry-After behavior, and gallery grant rejection on staff routes. After PIN rotation, reject the old PIN and every grant issued under its PIN version; accept the replacement PIN.

### I-08: Published-only image access

- Requirement IDs: PUBLIC-02, PHOTO-04, SEC-07, TEST-02, TEST-04.
- Status: PARTIAL — selected content succeeds and unselected/cross-gallery/draft exposure is denied with `nosniff`; Cloudinary missing/read failures map to controlled storage failures in boundary tests, while real-provider access remains pending.
- Planned verification: With a valid gallery grant, read selected READY bytes only. Reject unselected same-event photos, draft photos, foreign-event photos, wrong gallery grants, staff tokens on public routes, and direct anonymous object reads/listing. Verify no-store/nosniff, no object URL leakage, and controlled missing-object failure. Authorized owner/uploader may still review unpublished photos.

### I-09: Schema and input/error contract

- Requirement IDs: ARCH-01, SEC-05, SEC-06.
- Status: PARTIAL — Flyway migration, Hibernate validation, core H2 constraints, API envelopes, and representative input failures pass; real-MySQL constraint and exhaustive contract checks remain planned.
- Planned verification: Use real MySQL to test unique email/membership/gallery/selection constraints, same-event composite FKs, required metadata, and query scoping. Verify API statuses/envelopes, unknown-field rejection, filename escaping, safe logs, CORS allowlist, no query-token authentication, and no secret/hash leaks. Storage failures may require an adapter test double plus a later cloud smoke check.

## End-to-end tests

### E-01: Complete SRD five-step workflow

- Requirement IDs: AUTH-01, AUTH-02, USER-01, EVENT-01, EVENT-02, PHOTO-01, PHOTO-02, PHOTO-03, PHOTO-06, GALLERY-01, GALLERY-02, GALLERY-03, GALLERY-04, PUBLIC-01, PUBLIC-02, TEST-01, TEST-02, TEST-03, TEST-04.
- Status: VERIFIED — Playwright completes the real Admin registration/event/member flow, Team Member login and two-photo upload, Admin review/selection/PIN/publication, and account-free customer PIN/gallery/lightbox flow against the live Spring backend.
- Planned verification: Use isolated Admin, member, and account-free customer browser contexts. Register/login Admin, create event/add member, log in member and upload multiple images, review/select/publish as Admin, copy link, then open it as customer and enter correct PIN. Confirm gallery contains exactly the selection.

### E-02: Denied actions and recoverable failures

- Requirement IDs: USER-02, PHOTO-07, PUBLIC-03, SEC-02, SEC-03, SEC-07, TEST-02, TEST-04.
- Status: PARTIAL — component and backend suites verify wrong PIN, role/event isolation, unpublished content, and invalid upload handling; the complete browser-level negative/recovery matrix remains planned.
- Planned verification: Try foreign event navigation and direct forbidden API calls as member. Show mixed-upload results and uncertain-outcome recovery. Submit wrong PIN, access a draft link, manipulate photo ID to an unselected photo, and let a gallery grant expire; verify safe errors and no protected display.

### E-03: Responsive and usable screens

- Requirement IDs: UI-01.
- Status: VERIFIED for the mandatory baseline — live Admin, Team Member, PIN gate, public grid, and lightbox screens were captured and inspected at 1487/1536 px desktop, 800 px tablet, and 390 px mobile sizes; the journey also asserts zero browser console errors.
- Planned verification: Verify Admin, Team Member, and customer flows at 360/768/1280 px, keyboard focus/labels, long names, loading/empty/error states, multiple-file results, and gallery preview. Do not claim accessibility certification or performance SLAs.

## Documentary/release tests and checks

### D-01: Stack, overview, architecture and schema explanation

- Requirement IDs: DOC-01, DOC-02, DOC-03.
- Status: NOT STARTED.
- Planned verification: After implementation, compare actual stack/versions, architecture, schema/migrations, and final README to the documents. Confirm linked explanations/diagrams are accurate. The current short README intentionally does not complete these final obligations.

### D-02: Setup, environment, deployment and limitations

- Requirement IDs: DOC-04, DOC-05.
- Status: NOT STARTED.
- Planned verification: Follow the final README from a clean environment with placeholder configuration replaced locally; verify actual startup/test/migration steps, environment documentation, deployment steps, and truthful known limitations. No run commands exist to test in this phase.

### D-03: Secret and repository audit

- Requirement IDs: SEC-08, DEPLOY-03.
- Status: NOT STARTED.
- Planned verification: Before any later user-approved Git activity, inspect tracked candidates and ignored local files for real secrets, credentials, images, dumps, logs, and private data. After authorized repository creation, verify source and tests are accessible to evaluators; audit history as well as current files. No repository actions are authorized now.

### D-04: Cloud/live submission smoke verification

- Requirement IDs: DEPLOY-01, DEPLOY-02, DEPLOY-04, DEPLOY-05, DEPLOY-06, DEPLOY-07.
- Status: NOT STARTED.
- Planned verification: Against the eventual live URL, verify HTTPS, private persistent storage, both demo logins, demo gallery URL plus correct/wrong PIN, and the complete workflow. Validate all submitted links/deliverables are accessible and functional before September 20, 2026, 11:59 PM IST. Deliver credentials and submission only through a user-approved channel; no email is sent by this test plan.

### D-05: Explainability

- Requirement IDs: DOC-06.
- Status: NOT STARTED.
- Planned verification: Prepare to explain authorization, storage compensation, publication transactions, schema ownership, and stack choices; perform a small local change during evaluation preparation. Do not substitute generated documentation for understanding actual code.

## Requirement-to-case index

Every mandatory requirement has at least one planned automated test or documentary/delivery check. A mapping alone does not verify the requirement.

- AUTH-01: U-01, I-01, E-01.
- AUTH-02: U-01, I-01, E-01.
- USER-01: I-02, E-01.
- USER-02: U-02, I-03, E-02.
- EVENT-01: I-02, E-01.
- EVENT-02: U-02, I-02, E-01.
- PHOTO-01: I-04, E-01.
- PHOTO-02: U-02, I-03, E-01.
- PHOTO-03: U-02, I-03, E-01.
- PHOTO-04: U-03, I-04, I-08.
- PHOTO-05: U-03, I-04.
- PHOTO-06: U-03, I-04, E-01.
- PHOTO-07: U-03, I-05, E-02.
- GALLERY-01: U-04, I-06, E-01.
- GALLERY-02: U-04, I-06, E-01.
- GALLERY-03: U-04, I-06, E-01.
- GALLERY-04: U-04, I-06, E-01.
- PUBLIC-01: I-07, E-01.
- PUBLIC-02: U-04, I-07, I-08, E-01.
- PUBLIC-03: U-04, I-07, E-02.
- SEC-01: U-01, U-02, I-01, I-03.
- SEC-02: U-02, I-02, I-03, I-06, E-02.
- SEC-03: U-02, I-03, I-06, E-02.
- SEC-04: U-03, I-04, I-05.
- SEC-05: U-03, I-01, I-02, I-05, I-09.
- SEC-06: U-01, I-01, I-07, I-09.
- SEC-07: U-02, U-04, I-03, I-08, E-02.
- SEC-08: D-03.
- UI-01: E-03.
- ARCH-01: I-04, I-09.
- DOC-01: D-01.
- DOC-02: D-01.
- DOC-03: D-01.
- DOC-04: D-02.
- DOC-05: D-02.
- DOC-06: D-05.
- TEST-01: U-01, U-02, I-01, I-02, I-03, E-01.
- TEST-02: U-02, I-03, I-04, I-05, I-08, E-01, E-02.
- TEST-03: U-04, I-06, E-01.
- TEST-04: U-04, I-07, I-08, E-01, E-02.
- DEPLOY-01: D-04.
- DEPLOY-02: D-04.
- DEPLOY-03: D-03.
- DEPLOY-04: D-04.
- DEPLOY-05: D-04.
- DEPLOY-06: D-04.
- DEPLOY-07: D-04.

## Acceptance and evidence policy

### Evidence recorded on 2026-09-08

- Backend: `mvn -B -ntp -Dmaven.repo.local=<workspace cache> verify` — 11 tests across 3 classes passed, 0 failures/errors/skips. Coverage includes H2 API/security integration, local/Cloudinary provider selection, Cloudinary configuration and authenticated-upload mapping, safe provider failures, mixed-batch partial failure, and no binary photo column.
- Frontend unit: `npm test` — 1 test file, 7 tests passed, including restored-session verification and the modal input-focus regression test.
- Frontend production bundle: `npm run build` — Vite production build passed (1,924 modules transformed).
- Full-stack browser: `npm run e2e` while the Spring backend and Vite server were running — 1 complete mandatory journey passed in installed Chrome with no captured console errors.
- Responsive visual evidence was generated outside the source tree and manually compared with all three retained design concepts. It contains synthetic test-only accounts and generated image fixtures.

For each approved implementation slice, add focused tests alongside the code and record actual command, environment/version, date, outcome, and relevant requirement IDs. Store safe screenshots under docs/screenshots only after the UI is real. Redact tokens, passwords, PINs, and personal data; do not commit private upload fixtures.

Completion requires core end-to-end behavior and negative security paths, not only unit tests. Verify real MySQL constraints and live private storage access; mocked storage success does not prove deployed bucket privacy. Re-run impacted cases when an approved contract/assumption changes.

No deployed application URL, full real-provider security verification, coverage percentage, or reusable demo credential is claimed at this stage. The standalone real Cloudinary storage result is recorded separately below. Only the three user-authorized bonus features are tested below; all other bonus work remains deferred. Final delivery checks and final README expansion happen later; keep their requirement statuses NOT STARTED now.

### Authorized bonus coverage added on 2026-09-10

- Pagination/search integration: first and subsequent pages, page-size upper bound, newest-first deterministic database paging, Team Member own-photo restriction, Admin-only filters, filename matching, uploader and selected/unselected filters, combined search/paging, and cross-event exclusion.
- Expiration integration: non-expiring gallery access, future expiration access, expired PIN denial with safe 410 GALLERY_EXPIRED response, expired denial for an already-issued grant and photo-content request, Admin management after expiration, Team Member denial, and removal back to non-expiring behavior.
- Frontend: public Load More appends a subsequent server page with the same gallery grant; Admin search is debounced and combines uploader/selection parameters; optional expiration input and save request are covered.

### Cloudinary evidence recorded on 2026-09-10

- Post-cleanup backend: `mvn -B -ntp -Dmaven.repo.local=C:\Users\AyushRaj\Desktop\photo-sharing-platform\backend\.m2\repository verify` from `backend`, Windows PowerShell, Java 17.0.19, Maven 3.9.11 — BUILD SUCCESS; 21 tests across 5 classes, 0 failures/errors/skips. Uses isolated H2 and synthetic/loopback provider tests, not real Cloudinary or MySQL.
- Retained standalone smoke runner: runtime-only Java compilation and PowerShell syntax checks passed without contacting Cloudinary. Removed `-Compare` option is rejected before execution. Packaged application excludes the standalone runner and signing test. Production-source and frontend file hashes are unchanged by cleanup.
- User-reported real smoke: new `photoshare-local` credential pair; HTTP Basic ping SUCCESS, production-adapter PNG upload SUCCESS with image/authenticated delivery, cleanup SUCCESS. The unchanged adapter succeeds with the new pair after the old pair returned HTTP 401 Invalid Signature. See [final investigation report and retained developer diagnostic](CLOUDINARY_SIGNING_DEBUG.md).
- PHOTO-04: real Cloudinary storage upload/cleanup verified; normal-flow MySQL metadata and protected retrieval are not verified by this standalone command (I-04/I-08 remain partial).
- SEC-04: IMPLEMENTED / verification pending until the user completes the normal protected upload/gallery/PIN flow, including negative access cases.
