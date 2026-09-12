# PhotoShare requirements register

Status: mandatory backend and frontend flows and the production-capable Cloudinary adapter are implemented; real authenticated Cloudinary upload/cleanup passed the user's standalone smoke on 2026-09-10. Normal Cloudinary/MySQL workflow verification, cloud deployment, final README/delivery artifacts, and submission remain outstanding. Source: `TrizenAI_Full_Stack_Internship_Challenge.pdf`, 5 pages, reviewed in full on 2026-09-08. Section references below refer to that SRD. The source PDF remains at `C:/Users/AyushRaj/Downloads/TrizenAI_Full_Stack_Internship_Challenge.pdf`; it has not been copied into the project.

## Authority and interpretation

The SRD controls product requirements. The user's documentation-only brief controls this phase and supplies the preferred implementation stack. This register summarizes explicit obligations without treating illustrative values or quality hints as additional features. Repeated SRD statements share one stable ID and retain all relevant section references.

Statuses reflect the evidence available on 2026-09-08. `IMPLEMENTED` means the requirement exists in the backend or its required artifact; `VERIFIED` means an automated test or concrete inspection passed. `PARTIAL` is used where the development implementation exists but a mandatory production dependency remains outstanding.

## Mandatory requirements

### AUTH-01

- Exact requirement summary: Allow an Admin/Lead to register.
- SRD section: 2.1.
- Classification: MANDATORY.
- Implementation status: IMPLEMENTED.
- Verification/test status: VERIFIED — `BackendCoreIntegrationTest` registration flow.

### AUTH-02

- Exact requirement summary: Allow Admin/Lead and Team Member users to log in.
- SRD section: 2.1; 2.2.
- Classification: MANDATORY.
- Implementation status: IMPLEMENTED.
- Verification/test status: VERIFIED — Admin and Team Member login exercised by `BackendCoreIntegrationTest`.

### USER-01

- Exact requirement summary: Allow the Admin to add team members.
- SRD section: 2.1; 3 step 1.
- Classification: MANDATORY.
- Implementation status: IMPLEMENTED.
- Verification/test status: VERIFIED — Admin provisioning and assignment flows pass integration tests.

### USER-02

- Exact requirement summary: Prevent Team Members from managing other users' photos.
- SRD section: 2.2.
- Classification: MANDATORY.
- Implementation status: IMPLEMENTED.
- Verification/test status: VERIFIED — another member's photo is hidden and Team Member management endpoints are denied.

### EVENT-01

- Exact requirement summary: Allow the Admin to create an event.
- SRD section: 2.1; 3 step 1.
- Classification: MANDATORY.
- Implementation status: IMPLEMENTED.
- Verification/test status: VERIFIED — owned event creation passes integration tests.

### EVENT-02

- Exact requirement summary: Allow Team Members to view their assigned events.
- SRD section: 2.2.
- Classification: MANDATORY.
- Implementation status: IMPLEMENTED.
- Verification/test status: VERIFIED — assigned-event list/detail and unassigned-event denial pass.

### PHOTO-01

- Exact requirement summary: Allow Team Members to upload event photos.
- SRD section: 2.2; 3 step 2.
- Classification: MANDATORY.
- Implementation status: IMPLEMENTED.
- Verification/test status: VERIFIED — assigned Team Member multipart uploads pass.

### PHOTO-02

- Exact requirement summary: Allow Team Members to view their own uploaded photos.
- SRD section: 2.2.
- Classification: MANDATORY.
- Implementation status: IMPLEMENTED.
- Verification/test status: VERIFIED — own-photo listing and other-member isolation pass.

### PHOTO-03

- Exact requirement summary: Allow the Admin to view/review all photos uploaded by the team for the event.
- SRD section: 2.1; 3 step 3.
- Classification: MANDATORY.
- Implementation status: IMPLEMENTED.
- Verification/test status: VERIFIED — Admin event-wide photo listing passes.

### PHOTO-04

- Exact requirement summary: Store photo files in appropriate object/file storage (such as an equivalent cloud service); do not store image files directly in the database.
- SRD section: 4.
- Classification: MANDATORY.
- Implementation status: IMPLEMENTED — bytes remain outside MySQL behind `PhotoStorage`; local and Cloudinary adapters are explicitly selectable.
- Verification/test status: VERIFIED — real Cloudinary authenticated upload and cleanup passed the user's standalone production-adapter smoke on 2026-09-10 ([evidence](CLOUDINARY_SIGNING_DEBUG.md)); provider/configuration/mapping/failure behavior and the no-binary-column schema check also pass automatically. Normal-flow protected gallery verification remains tracked under SEC-04, not PHOTO-04.

### PHOTO-05

- Exact requirement summary: Store photo metadata in the database. The SRD examples are Photo ID, Event ID, Uploaded By, Filename, Storage Location, File Size, and Created At; the project adopts all seven as its baseline.
- SRD section: 4 (metadata examples adopted as the baseline).
- Classification: MANDATORY.
- Implementation status: IMPLEMENTED.
- Verification/test status: VERIFIED — Flyway schema validation and upload persistence pass.

### PHOTO-06

- Exact requirement summary: Support multiple photo uploads.
- SRD section: 4.
- Classification: MANDATORY.
- Implementation status: IMPLEMENTED.
- Verification/test status: VERIFIED — two files are uploaded in one multipart request.

### PHOTO-07

- Exact requirement summary: Appropriately handle failed photo uploads.
- SRD section: 6.
- Classification: MANDATORY.
- Implementation status: IMPLEMENTED.
- Verification/test status: VERIFIED — invalid image returns a per-file failure and is excluded from ready-photo listings.

### GALLERY-01

- Exact requirement summary: Allow the Admin to select photos for sharing in a gallery.
- SRD section: 1; 2.1; 3 step 3; 5.
- Classification: MANDATORY.
- Implementation status: IMPLEMENTED.
- Verification/test status: VERIFIED — event-scoped selection replacement and cross-event rejection pass.

### GALLERY-02

- Exact requirement summary: Allow the Admin to create and publish a gallery containing selected photos.
- SRD section: 2.1; 3 step 4; 5.
- Classification: MANDATORY.
- Implementation status: IMPLEMENTED.
- Verification/test status: VERIFIED — initial publish and atomic re-publish pass.

### GALLERY-03

- Exact requirement summary: Generate a shareable gallery link for the Admin to provide to customers.
- SRD section: 1; 2.1; 2.3; 3 step 4.
- Classification: MANDATORY.
- Implementation status: IMPLEMENTED.
- Verification/test status: VERIFIED — publication returns a stable link retained across re-publication.

### GALLERY-04

- Exact requirement summary: Allow the Admin to set a gallery PIN.
- SRD section: 2.1; 3 step 4.
- Classification: MANDATORY.
- Implementation status: IMPLEMENTED.
- Verification/test status: VERIFIED — PIN set and rotation with grant invalidation pass.

### PUBLIC-01

- Exact requirement summary: Allow a Customer to use the gallery link and PIN without creating an account.
- SRD section: 1; 2.3; 3 step 5.
- Classification: MANDATORY.
- Implementation status: IMPLEMENTED.
- Verification/test status: VERIFIED — public access uses only link, PIN, and gallery-scoped grant.

### PUBLIC-02

- Exact requirement summary: Allow a Customer with the correct PIN to view published photos and browse the gallery; only the correct PIN grants gallery access.
- SRD section: 2.3; 3 step 5; 5.
- Classification: MANDATORY.
- Implementation status: IMPLEMENTED.
- Verification/test status: VERIFIED — correct PIN grants selected published gallery/photo access.

### PUBLIC-03

- Exact requirement summary: Appropriately handle an incorrect gallery PIN.
- SRD section: 6.
- Classification: MANDATORY.
- Implementation status: IMPLEMENTED.
- Verification/test status: VERIFIED — incorrect and superseded PINs are rejected.

### SEC-01

- Exact requirement summary: Provide authentication and role-based authorization.
- SRD section: 6.
- Classification: MANDATORY.
- Implementation status: IMPLEMENTED.
- Verification/test status: VERIFIED — staff JWT authentication and role denials pass integration tests.

### SEC-02

- Exact requirement summary: Appropriately handle a user attempting to access another event.
- SRD section: 6.
- Classification: MANDATORY.
- Implementation status: IMPLEMENTED.
- Verification/test status: VERIFIED — cross-owner and unassigned event access return not found.

### SEC-03

- Exact requirement summary: Prevent Team Members from publishing galleries, including attempted API access.
- SRD section: 2.2; 6.
- Classification: MANDATORY.
- Implementation status: IMPLEMENTED.
- Verification/test status: VERIFIED — Team Member gallery creation and publication attempts are forbidden.

### SEC-04

- Exact requirement summary: Provide secure photo uploads and object storage integration.
- SRD section: 6.
- Classification: MANDATORY.
- Implementation status: IMPLEMENTED — validated uploads use the selected private storage adapter, including authenticated Cloudinary assets and API-mediated reads.
- Verification/test status: PARTIAL / verification pending — upload/security and automated Cloudinary boundaries pass, and the user's standalone real storage smoke succeeded. Normal PhotoShare protected upload/retrieval, gallery publication/selection, and PIN-flow verification remain pending; the smoke does not complete SEC-04.

### SEC-05

- Exact requirement summary: Provide input validation and error handling.
- SRD section: 6.
- Classification: MANDATORY.
- Implementation status: IMPLEMENTED.
- Verification/test status: VERIFIED — DTO/file validation and consistent API errors are exercised by integration tests.

### SEC-06

- Exact requirement summary: Apply basic security practices.
- SRD section: 6.
- Classification: MANDATORY.
- Implementation status: IMPLEMENTED for the backend core.
- Verification/test status: VERIFIED for implemented backend boundaries; deployment hardening remains under DEPLOY-01.

### SEC-07

- Exact requirement summary: Protect against attempted access to unpublished photos.
- SRD section: 6.
- Classification: MANDATORY.
- Implementation status: IMPLEMENTED.
- Verification/test status: VERIFIED — drafts, unselected photos, cross-gallery photos, and stale grants are denied.

### SEC-08

- Exact requirement summary: Do not commit secrets or credentials to Git.
- SRD section: 8 (continued on page 4).
- Classification: MANDATORY.
- Implementation status: IMPLEMENTED — runtime secrets are environment supplied and secret-bearing local files are ignored.
- Verification/test status: VERIFIED — tracked project source/config inspection found placeholders/test-only keys, not real credentials; no Git operation was performed.

### UI-01

- Exact requirement summary: Provide a responsive UI.
- SRD section: 6.
- Classification: MANDATORY.
- Implementation status: IMPLEMENTED — responsive Admin, Team Member, login/registration, and account-free public gallery screens exist under `frontend/src`.
- Verification/test status: VERIFIED — the live browser journey passed at desktop, tablet, and 390 px mobile viewports; implementation screenshots were inspected against the approved concepts.

### ARCH-01

- Exact requirement summary: Provide database schema design and API implementation.
- SRD section: 6.
- Classification: MANDATORY.
- Implementation status: IMPLEMENTED for the mandatory backend core.
- Verification/test status: VERIFIED — compilation, Flyway migration, Hibernate schema validation, APIs, and integration suite pass.

### DOC-01

- Exact requirement summary: Document the chosen technologies; no technology stack is mandated by the SRD.
- SRD section: 7; 9.
- Classification: MANDATORY.
- Implementation status: IMPLEMENTED — selected backend and frontend versions and rationale are recorded in `PROJECT_CONTEXT.md`.
- Verification/test status: VERIFIED — Maven and npm resolved and built the recorded versions.

### DOC-02

- Exact requirement summary: Include project overview and technology stack in the final README.
- SRD section: 9; 14.
- Classification: MANDATORY.
- Implementation status: NOT STARTED.
- Verification/test status: NOT STARTED.

### DOC-03

- Exact requirement summary: Include system architecture and database design/explanation in the final README and submission.
- SRD section: 9; 14.
- Classification: MANDATORY.
- Implementation status: NOT STARTED.
- Verification/test status: NOT STARTED.

### DOC-04

- Exact requirement summary: Include local setup instructions and environment variables in the final README.
- SRD section: 9.
- Classification: MANDATORY.
- Implementation status: NOT STARTED.
- Verification/test status: NOT STARTED.

### DOC-05

- Exact requirement summary: Include deployment steps and known limitations in the final README.
- SRD section: 9.
- Classification: MANDATORY.
- Implementation status: NOT STARTED.
- Verification/test status: NOT STARTED.

### DOC-06

- Exact requirement summary: Understand and be able to explain the submitted code; be prepared to explain the architecture or make a small change during evaluation.
- SRD section: 12.
- Classification: MANDATORY.
- Implementation status: NOT STARTED.
- Verification/test status: NOT STARTED.

### TEST-01

- Exact requirement summary: Include basic tests for authentication and authorization.
- SRD section: 10; 14.
- Classification: MANDATORY.
- Implementation status: IMPLEMENTED.
- Verification/test status: VERIFIED — authentication, membership, RBAC, and event-isolation integration cases pass.

### TEST-02

- Exact requirement summary: Include basic tests for photo access controls.
- SRD section: 10; 14.
- Classification: MANDATORY.
- Implementation status: IMPLEMENTED.
- Verification/test status: VERIFIED — own/Admin visibility, invalid upload, unselected, and cross-event access cases pass.

### TEST-03

- Exact requirement summary: Include basic tests for gallery publishing workflows.
- SRD section: 10; 14.
- Classification: MANDATORY.
- Implementation status: IMPLEMENTED.
- Verification/test status: VERIFIED — publish authorization, selection, stable-link, and re-publication cases pass.

### TEST-04

- Exact requirement summary: Include basic tests for PIN-protected access verification.
- SRD section: 10; 14.
- Classification: MANDATORY.
- Implementation status: IMPLEMENTED.
- Verification/test status: VERIFIED — wrong/correct/rotated PIN and gallery-grant isolation cases pass.

### DEPLOY-01

- Exact requirement summary: Deploy the application to the cloud and make it accessible online.
- SRD section: 6; 8; 14.
- Classification: MANDATORY.
- Implementation status: NOT STARTED.
- Verification/test status: NOT STARTED.

### DEPLOY-02

- Exact requirement summary: Include the live application URL in the submission.
- SRD section: 8; 14.
- Classification: MANDATORY.
- Implementation status: NOT STARTED.
- Verification/test status: NOT STARTED.

### DEPLOY-03

- Exact requirement summary: Include source code and a source code repository in the submission.
- SRD section: 8; 14.
- Classification: MANDATORY.
- Implementation status: NOT STARTED.
- Verification/test status: NOT STARTED.

### DEPLOY-04

- Exact requirement summary: Provide demo Admin credentials in the submission.
- SRD section: 8; 14.
- Classification: MANDATORY.
- Implementation status: NOT STARTED.
- Verification/test status: NOT STARTED.

### DEPLOY-05

- Exact requirement summary: Provide demo Team Member credentials in the submission.
- SRD section: 8; 14.
- Classification: MANDATORY.
- Implementation status: NOT STARTED.
- Verification/test status: NOT STARTED.

### DEPLOY-06

- Exact requirement summary: Provide a demo Gallery URL and Gallery PIN in the submission.
- SRD section: 8.
- Classification: MANDATORY.
- Implementation status: NOT STARTED.
- Verification/test status: NOT STARTED.

### DEPLOY-07

- Exact requirement summary: Submit the completed deliverables to talent@trizen-ai.com by September 20, 2026, 11:59 PM IST; ensure all required deliverables are accessible and functional at submission.
- SRD section: Document header; 14 Submission Guidelines.
- Classification: MANDATORY.
- Implementation status: NOT STARTED.
- Verification/test status: NOT STARTED.

## Optional / bonus requirements

The user explicitly authorized exactly BONUS-02, BONUS-03, and BONUS-06 on 2026-09-10. All other bonus items remain out of scope. Core requirements retain their independent statuses (SRD section 11).

### BONUS-01

- Exact requirement summary: Image thumbnails/resizing.
- SRD section: 11.
- Classification: OPTIONAL / BONUS.
- Implementation status: NOT STARTED.
- Verification/test status: NOT STARTED.

### BONUS-02

- Exact requirement summary: Pagination/infinite scrolling.
- SRD section: 11.
- Classification: OPTIONAL / BONUS.
- Implementation status: IMPLEMENTED — database-level photo pagination with deterministic ordering and bounded page sizes; React uses explicit Load More controls.
- Verification/test status: VERIFIED — backend first/subsequent page, bounds, role/event scope and combined search pagination tests pass; frontend Load More behavior passes.

### BONUS-03

- Exact requirement summary: Photo search/filtering.
- SRD section: 11.
- Classification: OPTIONAL / BONUS.
- Implementation status: IMPLEMENTED — Admin filename, uploader, and selected/unselected filters execute server-side within the owned event.
- Verification/test status: VERIFIED — filename/uploader/selection/search-plus-pagination/cross-event backend coverage and debounced Admin UI coverage pass.

### BONUS-04

- Exact requirement summary: Bulk upload.
- SRD section: 11; ambiguity A-02.
- Classification: OPTIONAL / BONUS.
- Implementation status: NOT STARTED.
- Verification/test status: NOT STARTED.

### BONUS-05

- Exact requirement summary: Photo downloading.
- SRD section: 11.
- Classification: OPTIONAL / BONUS.
- Implementation status: NOT STARTED.
- Verification/test status: NOT STARTED.

### BONUS-06

- Exact requirement summary: Gallery expiration.
- SRD section: 11.
- Classification: OPTIONAL / BONUS.
- Implementation status: IMPLEMENTED — nullable UTC gallery expiration is Admin-managed and enforced for PIN access, public metadata, and protected public photo reads.
- Verification/test status: VERIFIED locally — non-expiring/future/expired/stale-grant/Admin/Team Member backend cases and the Admin expiry UI test pass. MySQL migration and timezone display still require manual local verification.

### BONUS-07

- Exact requirement summary: CDN usage.
- SRD section: 11.
- Classification: OPTIONAL / BONUS.
- Implementation status: NOT STARTED.
- Verification/test status: NOT STARTED.

### BONUS-08

- Exact requirement summary: Automated CI/CD pipelines.
- SRD section: 11.
- Classification: OPTIONAL / BONUS.
- Implementation status: NOT STARTED.
- Verification/test status: NOT STARTED.

### BONUS-09

- Exact requirement summary: A simple architecture diagram in the final README (encouraged).
- SRD section: 9.
- Classification: OPTIONAL / BONUS.
- Implementation status: NOT STARTED.
- Verification/test status: NOT STARTED.

## Section coverage and non-requirement material

- Sections 1-3: covered by AUTH, USER, EVENT, PHOTO, GALLERY, and PUBLIC IDs. The complete five-step workflow is preserved in [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md).
- Section 4: PHOTO-04 through PHOTO-06. The listed metadata is introduced by "such as"; this project adopts all seven examples.
- Section 5: GALLERY-01, GALLERY-02, PUBLIC-02. The wedding name, 1,250 uploaded / 600 selected counts, sample URL, and PIN 482917 are illustrative, not required seed data, limits, or real credentials.
- Section 6: PHOTO-07, PUBLIC-03, SEC-01 through SEC-07, UI-01, ARCH-01, DEPLOY-01.
- Section 7: DOC-01; stack selection is unrestricted by the SRD.
- Section 8: SEC-08, DEPLOY-01 through DEPLOY-06.
- Section 9: DOC-01 through DOC-05; the encouraged diagram is BONUS-09. The user independently requires Mermaid diagrams in the planning documents now.
- Section 10: TEST-01 through TEST-04.
- Section 11: BONUS-01 through BONUS-08; mandatory-first policy.
- Section 12: DOC-06; tool/framework freedom does not add a feature.
- Section 13: evaluation hints about functionality, UX, API maintainability, data models, security, deployment/monitoring, tests, and documentation. These guide quality; they do not independently mandate search, monitoring infrastructure, scalability targets, or other new features.
- Section 14/header: DOC-02, DOC-03, DOC-06, TEST-01 through TEST-04, DEPLOY-01 through DEPLOY-07. An email address/deadline is a delivery instruction for later, not authorization to send anything now.

## Project decisions and open questions

See the numbered assumptions in [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md). Proposed PIN length, upload limits, ownership rules, endpoints, schema fields beyond the metadata examples, and deployment topology are project decisions, not additional mandatory SRD requirements.

## Implementation and verification traceability

This matrix assigns every mandatory ID an implementation/delivery location and at least one verification case. Rows through ARCH-01 require runtime behavior; DOC, TEST, and DEPLOY rows are documentary, test-suite, or release obligations. Production-storage and deployment locations remain planned where their statuses are not complete.

| Requirement | Planned implementation or delivery location | Verification mapping |
| --- | --- | --- |
| AUTH-01 | backend/auth + frontend /register | U-01, I-01, E-01 |
| AUTH-02 | backend/auth + frontend /login | U-01, I-01, E-01 |
| USER-01 | backend/users + events/membership + Admin Team UI | I-02, E-01 |
| USER-02 | backend/photos service/query authorization + Team UI | U-02, I-03, E-02 |
| EVENT-01 | backend/events + Admin event UI | I-02, E-01 |
| EVENT-02 | backend/events/membership query + Team event UI | U-02, I-02, E-01 |
| PHOTO-01 | backend/photos/storage + Team upload UI | I-04, E-01 |
| PHOTO-02 | backend/photos own-upload query/content + Team UI | U-02, I-03, E-01 |
| PHOTO-03 | backend/photos owner query/content + Admin UI | U-02, I-03, E-01 |
| PHOTO-04 | backend/storage adapter + private deployment storage | U-03, I-04, I-08 |
| PHOTO-05 | backend/photos entity/repository + MySQL migration | U-03, I-04 |
| PHOTO-06 | backend multipart upload + Team multi-file UI | U-03, I-04, E-01 |
| PHOTO-07 | backend upload state/compensation + per-file UI results | U-03, I-05, E-02 |
| GALLERY-01 | backend/galleries selection service + Admin gallery UI | U-04, I-06, E-01 |
| GALLERY-02 | backend/galleries publish/re-publish service + Admin UI | U-04, I-06, E-01 |
| GALLERY-03 | backend/galleries share-token/link response + Admin UI | U-04, I-06, E-01 |
| GALLERY-04 | backend/galleries PIN hashing/versioning + Admin UI | U-04, I-06, E-01 |
| PUBLIC-01 | backend/public gallery access + public PIN UI | I-07, E-01 |
| PUBLIC-02 | backend/public gallery/content authorization + public gallery UI | U-04, I-07, I-08, E-01 |
| PUBLIC-03 | backend/public PIN errors/limits + PIN UI | U-04, I-07, E-02 |
| SEC-01 | Spring Security/auth plus service authorization | U-01, U-02, I-01, I-03 |
| SEC-02 | backend event-scoped services/repositories | U-02, I-02, I-03, I-06, E-02 |
| SEC-03 | backend gallery role guards | U-02, I-03, I-06, E-02 |
| SEC-04 | backend upload validation/storage adapter + private storage policy | U-03, I-04, I-05 |
| SEC-05 | backend DTO validation/error handling + UI error states | U-03, I-01, I-02, I-05, I-09 |
| SEC-06 | security/configuration boundaries across backend/frontend/deployment | U-01, I-01, I-07, I-09 |
| SEC-07 | backend public gallery/content queries + private storage | U-02, U-04, I-03, I-08, E-02 |
| SEC-08 | secret configuration and repository/submission audit | D-03 |
| UI-01 | frontend role-specific responsive screens | E-03 |
| ARCH-01 | backend modules, REST controllers, repositories, migrations | I-04, I-09 |
| DOC-01 | final README technology section | D-01 |
| DOC-02 | final README overview/stack | D-01 |
| DOC-03 | final README architecture/database explanation | D-01 |
| DOC-04 | final README local setup/environment section | D-02 |
| DOC-05 | final README deployment/limitations section | D-02 |
| DOC-06 | implemented-code/architecture evaluation preparation | D-05 |
| TEST-01 | unit/integration/E2E authentication suites | U-01, U-02, I-01, I-02, I-03, E-01 |
| TEST-02 | unit/integration/E2E photo access suites | U-02, I-03, I-04, I-05, I-08, E-01, E-02 |
| TEST-03 | unit/integration/E2E gallery publication suites | U-04, I-06, E-01 |
| TEST-04 | unit/integration/E2E PIN/public access suites | U-04, I-07, I-08, E-01, E-02 |
| DEPLOY-01 | deployment topology and eventual cloud resources | D-04 |
| DEPLOY-02 | final submission package/live URL | D-04 |
| DEPLOY-03 | final source tree/repository delivery | D-03 |
| DEPLOY-04 | final private demo Admin credential delivery | D-04 |
| DEPLOY-05 | final private demo Team Member credential delivery | D-04 |
| DEPLOY-06 | final private demo gallery link/PIN delivery | D-04 |
| DEPLOY-07 | final submission checklist and accessibility smoke check | D-04 |

The detailed scope and actual evidence for each case are in [TESTING.md](TESTING.md). Passing backend, frontend-unit, build, and browser suites provide evidence only for the status lines explicitly marked VERIFIED above; production-storage, documentary, and deployment checks are not implied complete.

## Verification navigation

[TESTING.md](TESTING.md) maps every mandatory ID to planned tests or documentary/delivery checks. Supporting designs: [architecture](architecture.md), [database](database-design.md), [API](api-contract.md), [security](security-model.md), [storage](storage-design.md), [UI](ui-guidelines.md), [deployment](DEPLOYMENT.md).
