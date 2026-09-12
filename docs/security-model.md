# Security model

Status: core controls and the Cloudinary storage boundary are implemented and locally tested; real Cloudinary/MySQL and deployed-environment verification remain pending. Limits and algorithms are project choices; the SRD requires the security outcomes but does not prescribe these mechanisms.

## Authorization policy

ADMIN can create events and provision/assign their Team Members; list/read all READY photos only in owned events; create a draft, select photos, set its PIN, and publish. ADMIN is not a global superuser. Client-supplied ownership/role/uploader fields are rejected; ownership comes from the authenticated principal.

TEAM_MEMBER can list assigned events and upload there. They can list/read only their own READY photos while still assigned. No gallery mutation, publishing, member management, photo management, or other-member photo access is allowed. Hiding controls in the UI is only presentation; service authorization must reject direct HTTP attempts.

Customer has no user account. A correct PIN grants temporary read access to one published gallery and its selected READY photos only. A gallery grant cannot access staff routes or another gallery; staff JWTs cannot bypass the public PIN flow.

Use default-deny routing and explicit service guards. Event lookup is scoped by owner/member. Every nested photo/gallery must also match the event in the path. For bulk selection, verify all IDs belong to the same owned event and are READY before mutating anything. Never authorize solely from the first item, path ID, uploadedBy request field, token role, or unpredictable identifiers.

## Credential lifecycle

Admin registration fixes role ADMIN. Admin-owned provisioning fixes TEAM_MEMBER and provisioned_by. Email is normalized and globally unique. There is no role-change API. Initial member password is communicated by the Admin outside the application; no email or invitation service is in scope.

Passwords and PINs use separate salted Argon2id hashes; proposed minimum parameters are 19 MiB memory, two iterations, parallelism one, calibrated on the deployment host. Store the encoded algorithm/salt/hash, never plaintext or reversible encryption. PIN strings retain leading zeros. These choices follow the [OWASP password storage guidance](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html); implementation must verify library support and capacity.

Staff JWTs last 15 minutes and carry sub, iss, aud=photoshare-staff, iat, exp, and a token type. Use one explicitly permitted signing algorithm (planned HS256 with a cryptographically random key of at least 256 bits), and verify signature, algorithm, issuer, audience, type, and expiry. Load current user/role and event relationships server-side rather than trusting stale role claims alone. Never accept unsigned tokens or a signing algorithm supplied unchecked by the client.

Keep gallery grants in browser memory. Keep the short-lived staff token only in tab-scoped `sessionStorage`, never in URLs or localStorage; verify a restored token through `/auth/me` before relying on the session. Logout, expiry, or a staff 401 clears the stored token and protected state. No refresh tokens or individual token revocation is planned. A same-origin script compromise could read a valid tab token until expiry; broad signing-key rotation revokes its class of tokens. Report this limitation honestly.

## Gallery PIN and public authorization

The Admin chooses a proposed six-digit PIN, hashes it on the server, and retains it for sharing. GET responses cannot recover it. A draft may replace its PIN. After publication, the Admin may atomically re-publish a complete replacement selection and optionally rotate the PIN while keeping the same share link. A random 128-bit share token gives a non-sequential URL but is only an identifier.

PIN verification checks that the gallery is PUBLISHED, enforces rate limits, verifies the hash, and issues a 15-minute gallery grant with gallery ID, current pinVersion, issuer, aud=photoshare-gallery, iat, exp, and its own token type. Sign it with a different secret from staff JWTs. The API must reject token substitution between these classes. Each public request compares the claim to the current PIN version, so rotating the PIN invalidates prior grants immediately.

No photo list, title, filename, uploader information, or object address is exposed before verification. Every public metadata/image request rechecks the grant's gallery, published status, and selected READY photo membership. Return generic public errors for missing/draft/foreign resources. Wrong PIN grants no access and returns generic 401. No "public bucket" shortcut or alternate unauthenticated media URL is permitted.

A PIN has low entropy even when hashed. Apply configurable limits to PIN verification (proposed 5 attempts per share-token + client IP per 5 minutes, plus 50 per client IP across tokens in that window), and bound concurrent hash operations. This limits online guessing but does not make a six-digit PIN resistant to a database leak. Do not log successful or failed PIN values.

## Request protection and error handling

- Validate lengths, formats, enums, positive IDs, multipart counts/bytes, and request fields at the boundary. Use parameterized database operations. Escape user text in the UI; do not render uploaded filenames/descriptions as HTML.
- Proposed login limits: 10 attempts per normalized account + client IP per 5 minutes and 50 per IP across accounts. Registration/provisioning: 5 per IP per 5 minutes for registration, and an authenticated-Admin limit for provisioning. Return 429 with Retry-After.
- Use one backend instance and bounded in-memory counters initially. Respect forwarded client IP only from the known reverse proxy; do not trust arbitrary forwarding headers. Restart resets counters; multi-instance deployment requires a shared limiter or edge enforcement before scaling.
- Use TLS in deployment. Prefer same-origin frontend/API routing; allow only configured exact development origins for CORS. Never use wildcard authenticated origins.
- Authentication is explicitly Bearer-header-only; no cookies authenticate these APIs. Do not accept tokens from query parameters. If cookies are introduced later, reassess CSRF before that change.
- Never include credentials, JWTs, PINs, Authorization headers, raw request bodies, private object keys, SQL errors, or stack traces in logs/error responses. Include a request ID and sanitized error category.
- Restrict public error detail; return 403 for wrong role and 404 for inaccessible scoped resources. Login failures do not disclose whether an email exists. Registration's generic unavailable-email error still allows limited inference; rate limiting bounds abuse.
- Serve private images through authenticated API reads using no-store caching and nosniff; protected responses must not be cached by a shared edge. Use a restrictive frontend content security policy (allow blob images for authenticated reads) and avoid third-party scripts on gallery pages.

## Upload and object access

Allow only JPEG/PNG in the proposed baseline. Validate extension, claimed type, actual signature, and successful image decoding consistently, with size/dimension limits; reject SVG, HTML, archives, executable data, and mismatches. Generate storage names, sanitize display filenames, and store outside the webroot. These controls follow [OWASP file upload guidance](https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html).

The API alone holds private storage access. With Cloudinary selected, uploads use the `authenticated` delivery type and generated public IDs; after database authorization, the backend generates a short-lived signed download and consumes it server-side. Cloudinary URLs and storage keys are never returned as photo authorization. Clients send application IDs, never object keys or filesystem paths. Staff/public byte reads run the relevant database authorization check before object access. Storage provider errors must not reveal credentials or internal locations.

Validate before a file becomes READY; select/serve only READY content. Bound decoding, request memory, temporary files, and concurrent uploads to reduce resource exhaustion. Compensate failed writes/finalization as specified in [storage-design.md](storage-design.md).

## Unpublished and cross-event protection

An owning Admin may review unpublished team photos and assigned uploaders may view their own; this authorized staff workflow is required. SEC-07 protects unpublished content from customers and unauthorized users, not from authorized review.

A public grant covers the current selected photos in its own PUBLISHED gallery. Unselected photos in that same event, photos in drafts, and photos belonging to another event must return 404. Re-publication atomically changes the selected set. Numeric photo IDs, storage knowledge, and correct PINs for other galleries do not alter this rule. Test every metadata and content route, not just the gallery screen.

## Secret management and known limits

[.env.example](../.env.example) contains placeholders only; Spring consumes actual backend values from the process environment rather than loading that example file. Development values belong in ignored local files or the current shell; production values are injected from the host's secret facility. No private keys, database passwords, storage keys, JWT secrets, demo credentials, or gallery PINs go into Git, documentation, screenshots, frontend Vite variables, or build artifacts.

Use separate secrets per environment and separate staff/gallery keys. Rotate exposed secrets, invalidate affected grants, and inspect access logs. Do not collect real customer photographs for demos without permission. Image metadata such as EXIF/GPS may remain in originals; automatic stripping is not implemented or promised, so use privacy-reviewed demo images.

The baseline provides access control, not DRM: a customer who can view a photo can save its bytes or take a screenshot. Expiry or PIN rotation denies future API requests but cannot revoke bytes already received. Password reset, individual staff-token revocation, MFA, antivirus infrastructure, gallery expiration, and publication history/rollback are outside the specified baseline; revisit only if approved.
