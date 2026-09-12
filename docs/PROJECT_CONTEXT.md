# PhotoShare project context

Status: mandatory backend/frontend flows and a production-capable Cloudinary storage adapter are implemented; real authenticated Cloudinary upload/cleanup passed the user's standalone smoke on 2026-09-10. Normal Cloudinary/MySQL workflow verification, cloud deployment, final README/delivery artifacts, and submission remain outstanding.
Last updated: 2026-09-10 (Cloudinary verification evidence only; no new requirements audit).

## Purpose and authority

PhotoShare lets a photography/event team upload event photographs, an Admin/Lead review and select them, and customers browse a published, PIN-protected gallery through a shareable link without accounts.

The official source of product requirements is `TrizenAI_Full_Stack_Internship_Challenge.pdf`, 5 pages, at `C:/Users/AyushRaj/Downloads/TrizenAI_Full_Stack_Internship_Challenge.pdf`. All sections 1-14 and the submission guidelines have been read. Stable obligations and source sections are in [project-requirements.md](project-requirements.md). If a later interpretation conflicts with the SRD, record the discrepancy and resolve it before changing scope.

The user approved backend implementation and then frontend implementation on 2026-09-08, and explicitly authorized exactly photo pagination, Admin photo search/filter, and optional gallery expiration on 2026-09-10. No approval authorized Git/GitHub actions, cloud resource creation, deployment, submission, or other bonus features.

## Roles and complete workflow

- ADMIN (Admin/Lead): register/log in, create owned events, add team members, review all photos in owned events, select photos, create/publish galleries, generate shareable links, and set PINs.
- TEAM_MEMBER: log in, see assigned events, upload event photos, and see their own uploaded photos. Cannot publish galleries or manage other users' photos.
- Customer: no account or staff role; uses a gallery link and correct PIN to browse only that published gallery.

The SRD sequence is: (1) Admin creates an event and adds team members; (2) assigned Team Member uploads photos; (3) Admin reviews all event uploads and selects photos; (4) Admin publishes selected photos as a gallery and obtains link + PIN; (5) Customer opens the link, enters the PIN, and views/browses published photos. Security denials and upload failures must be handled throughout.

## Chosen stack and rationale

User-selected direction: React + Vite for the browser UI, Tailwind CSS for styling, Java + Spring Boot for one REST backend, Spring Security + JWT for staff authentication, MySQL for relational data, and separate private object/file storage for photo bytes. Use a modular monolith to keep one deployable backend while separating domain responsibilities.

The implemented backend pins Java 17, Spring Boot 4.1.1, Maven, MySQL Connector/J 9.7.0 (managed by Spring Boot), Flyway 12.4.0 (managed by Spring Boot), Bouncy Castle 1.85.2, and Cloudinary Java SDK `cloudinary-http5` 2.3.2. The implemented browser application pins React/React DOM 19.2.8, Vite 8.2.2, Tailwind CSS 4.3.3, React Router DOM 7.18.3, Axios 1.20.0, and Lucide React 1.42.0. Verification uses Vitest 5.0.0, Testing Library 16.3.3, and Playwright 1.63.0. Java 17 and Node.js 24 are installed local baselines; the SRD does not mandate these technologies or versions.

The implementation uses Admin-created TEAM_MEMBER accounts with an initial password and permits reuse only within that Admin's private roster. It implements one owning Admin and one stable-link gallery per event, atomic re-publication, six-digit PINs, separate 15-minute staff/gallery JWTs, 8-128 character passwords, configurable in-memory rate limits, and configurable JPEG/PNG upload limits. The React client keeps the short-lived staff session in tab-scoped `sessionStorage`, verifies a restored token through `/auth/me`, keeps gallery grants only in memory, fetches protected images as authorized blobs, and exposes role-specific routes without treating client checks as authorization. These remain implementation decisions rather than new SRD requirements. `STORAGE_DRIVER` explicitly selects `local` for development/tests or `cloudinary` for production-capable storage. Cloudinary objects use authenticated delivery and are fetched through short-lived signed server-side downloads only after existing API authorization; real upload/cleanup succeeded with the user's new credential pair, while normal protected upload/gallery/PIN-flow verification remains pending. See [Cloudinary evidence](CLOUDINARY_SIGNING_DEBUG.md).

## Architecture principles

- Keep the frontend responsible for presentation; the backend owns authorization, validation, business rules, and publishing.
- Organize backend modules by authentication, users, events/membership, photos/storage, and galleries/public access. Share only small cross-cutting error/security utilities.
- Store metadata and relationships in MySQL; store photo binaries outside MySQL and outside the webroot.
- Deny access by default. A staff role alone does not grant access to another Admin's event.
- Treat gallery selection as draft state before first publication. Publish or re-publish an atomic, validated set behind one stable share link; customers never observe a partially updated selection. Private originals never become publicly addressable objects.
- Use transactions for relational invariants and explicit compensation for object storage failures. No microservices, event bus, or distributed transaction framework is planned.
- Keep contracts in these docs aligned before implementation changes. Distinguish SRD obligations from chosen implementation details.

## Development order and mandatory-first policy

1. Obtain approval of this documentation foundation.
2. Scaffold approved backend/frontend, configuration, migrations, and test harness.
3. Implement staff registration/login, role checks, event ownership, user provisioning, and membership.
4. Implement private storage, multiple-photo upload, metadata, failure handling, and staff photo visibility.
5. Implement Admin draft selection, PIN setting, atomic publication/re-publication, and shareable links.
6. Implement account-free PIN verification and public gallery/photo access.
7. Complete responsive role-specific flows and integration/end-to-end security tests alongside each feature.
8. Audit all mandatory IDs, complete truthful README/setup/deployment documentation, and prepare cloud deployment.
9. Only after the project is complete and audited, obtain the user's direction for Git/GitHub, repository delivery, and publishing/submission actions.

Only the three explicitly authorized bonus features—photo pagination, Admin photo search/filter, and optional gallery expiration—are implemented. Do not add other bonus features without separate authorization. Mandatory multi-file upload is core despite the separate optional "bulk upload" wording.

## Security rules

Backend authorization must bind every event/photo/gallery operation to its owner, assigned member, or correctly scoped gallery grant. Team Members may view only their own event uploads under the planned baseline. A customer grant is never a staff credential. Enforce published state and gallery-photo membership on every public photo request.

Hash passwords and PINs; never log or return stored hashes, raw secrets, storage credentials, or private keys. Validate content, byte limits, and upload ownership. Keep storage private and deliver authorized photo bytes through the API. Use TLS, short-lived credentials, input validation, generic public errors, and rate limits. Details are in [security-model.md](security-model.md).

## Deployment goal

Deliver an online frontend and API, private MySQL, and private persistent Cloudinary photo storage. Cloudinary is selected for photo storage; application/database hosting and costs remain unselected. Submission needs live URL, source repository/code, demo Admin and Team Member credentials, demo gallery URL/PIN, tests, README, and architecture/database explanation. Deadline: September 20, 2026, 11:59 PM IST; recipient: talent@trizen-ai.com. Keep demo credentials outside version control and deliver them through a user-approved private channel.

## Assumptions and unresolved SRD details

These are proposed defaults for approval, not new SRD requirements. Use their IDs when revising a dependent design.

### Classification key and audit disposition

- A - Explicit SRD requirement: stated by the SRD and tracked as mandatory in the requirement register.
- B - Necessary implementation decision: the SRD requires an outcome, and implementation must choose a mechanism or boundary to deliver it safely.
- C - Optional design choice: one reasonable baseline among alternatives; it may be changed without violating the SRD.

The listed assumptions classify as follows. Where a policy is necessary but its numeric value is optional, the policy and value are separated instead of giving one misleading classification.

| Assumption | Class | Final audit disposition |
| --- | --- | --- |
| Admin-created Team Member accounts | C | Retain as the simplest onboarding baseline; the explicit A-level requirement is only that Admin can add members and members can log in. |
| One Admin owner per event | B | Retain as the minimal server-side authority boundary needed to enforce cross-event isolation; co-ownership/organizations are unspecified. |
| One gallery per event | C | Retain because the SRD consistently describes a singular event gallery and one stable link is easy to explain; multiple galleries remain possible later. |
| Permanently frozen selection after first publication | C | Removed as unnecessarily restrictive. Admin may atomically replace the selected set by re-publishing the existing gallery. |
| Permanently frozen PIN after first publication | C | Removed as unnecessarily restrictive. Admin may atomically rotate the PIN while re-publishing. |
| PIN format/length | C | Six numeric digits remains a provisional baseline based on the example, not an SRD mandate. |
| JWTs have a finite lifetime | B | Required to define the chosen JWT session mechanism safely; JWT itself is a user-selected implementation direction, not an SRD mandate. |
| Fifteen-minute staff/gallery token value | C | Provisional configurable value. |
| A password validation policy exists | B | Needed for registration, login, validation, and basic security. |
| Password length of 8-128 characters | C | Provisional validation value. |
| Online login/PIN attempts are rate-limited | B | Needed to make password/PIN verification reasonably safe, especially for low-entropy gallery PINs. |
| Exact rate-limit thresholds/windows | C | Provisional configurable values. |
| Upload type, byte/count, and decoded-dimension validation exists | B | Needed for secure uploads and input validation. Multiple-photo upload itself is A and mandatory. |
| JPEG/PNG, 10 MiB, 20 files, 210 MiB, and 40 megapixels | C | Provisional configurable values. |

- A-01 Team-member onboarding: the SRD says "add team members" without defining invitations or registration. Plan Admin-created TEAM_MEMBER accounts with an initial password communicated outside the app. No email service, invitations, self-registration, password reset, or role editing in the baseline. Provisioning occurs within an owned event; already-existing eligible members are assigned by user ID. Newly provisioned members belong to the creating Admin's roster.
- A-02 Multiple versus bulk upload: multiple files in one bounded multipart request is mandatory. Large batch tooling, archives, resumable uploads, and background bulk processing are deferred interpretations of optional bulk upload.
- A-03 Ownership: each event has one owning Admin. Each Team Member account is provisioned by one Admin and may join multiple events owned by that Admin. Other Admins cannot discover/assign that account or access its events/photos. The SRD does not specify organizations, co-leads, shared rosters, or ownership transfer.
- A-04 Team visibility: Team Members can list/read their own photos within assigned events; other members' photos are hidden. Team Members cannot edit/delete/select photos or publish. Admin photo upload, photo editing/deletion, event deletion, and user/membership removal are outside the stated baseline.
- A-05 Gallery lifecycle: plan one gallery per event, with DRAFT then PUBLISHED states and one stable share link. Publishing requires at least one ready photo and a PIN. After first publication, the Admin may submit a complete replacement selection and optional replacement PIN through one atomic re-publish operation; the current published version remains visible until that transaction commits. Multiple simultaneous galleries, unpublish, independent version history, and expiration are not specified.
- A-06 PIN/access policy: plan a six-digit numeric PIN chosen by the Admin (leading zeros preserved), salted slow hash, a 15-minute gallery-scoped access grant, and configurable login/PIN rate limits. The SRD's six-digit example is illustrative; length, lifetime, and limits are project choices. No PIN recovery: the Admin supplies and retains it securely. A PIN rotation during re-publication increments an internal PIN version so grants issued under the old PIN stop working.
- A-07 Upload constraints: implemented baseline JPEG and PNG, at most 10 MiB per file, 20 files and 210 MiB per request (including multipart overhead), decoded dimensions at most 40 megapixels. These configurable limits/types are absent from the SRD and remain project choices.
- A-08 Delivery: use the provider-neutral `PhotoStorage` boundary with Cloudinary authenticated assets in production-capable mode and local private files for development/tests. All reads remain API-mediated. Hosting provider, region, costs, domain, backup retention, resource sizing, and submission repository visibility remain undecided.
- A-09 Account policy: Admin registration remains available because the SRD requires it; no user-supplied role field. Passwords are 8-128 characters. The 15-minute staff JWT is stored only for the current browser tab, restored after reload, revalidated through `/auth/me`, and removed on logout/expiry; localStorage, refresh tokens, and cross-tab/persistent-login behavior remain deferred. The need for password/token policies is B; these exact values and browser-session mechanism are C.
- A-10 Final README: the SRD requires a full final README, but the user explicitly requests only title, overview, and current status now. DOC-02 through DOC-05 remain NOT STARTED until the final delivery README is completed and checked.
- A-11 Scale and semantics: sample event name/counts/PIN are examples. No minimum photo count, search, downloading, thumbnailing, or performance SLA is mandated. Galleries display originals in a responsive grid; optional enhancements remain deferred.

### Remaining implementation and deployment decisions

The backend, frontend, and Cloudinary storage adapter baselines above are implemented but remain configurable or changeable with coordinated contract/tests updates. Still unresolved are normal-flow real Cloudinary protected upload/retrieval and gallery/PIN verification, application/database hosting providers, region, cost/resource sizing, backup retention, domain/TLS arrangement, production rate-limit topology if multiple instances are used, and private demo-credential delivery method.

## Rules future agents must not violate

- Read this file and the requirement register before feature work; consult the original SRD when scope is unclear.
- Do not start production object-storage integration, deployment, bonus, Git/GitHub, or submission work without the corresponding user approval.
- Do not initialize Git, create a GitHub repository, commit, or push unless explicitly authorized. Do not infer permission from the SRD's repository deliverable.
- Preserve user changes; do not invent completed features, tests, URLs, credentials, or deployment claims.
- Change requirement implementation/verification statuses only when supported by concrete implementation and verification evidence.
- Never store image binaries in MySQL or expose storage directly through public bucket permissions.
- Never rely on frontend controls alone for role, ownership, gallery, or PIN authorization.
- Never commit secrets, credentials, uploaded photos, database dumps, or private test data.
- Do not expand into bonus features or paid services without authorization.
- Update assumptions, contracts, schema, and tests together when an approved decision changes.
