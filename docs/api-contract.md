# Planned REST API contract

Status: implemented local contract; deployment verification remains pending. Base path /api/v1. DTOs and errors below are project decisions supporting the [requirements register](project-requirements.md). IDs are JSON strings; timestamps are UTC ISO 8601 strings. Request examples use placeholders, not usable credentials.

## Shared rules, authentication, and errors

Staff authentication: Authorization: Bearer <staff-jwt>. Staff JWT has the staff audience and the authenticated user ID; each request loads current user role and ownership/membership. Customer authentication uses Authorization: Bearer <gallery-grant>, a separate token type/key/audience restricted to one published gallery. These credentials are not interchangeable.

All bodies are JSON unless multipart or image content is specified. Return JSON DTOs without persistence entities, hashes, raw storage keys, private bucket URLs, or secrets. Unknown fields (including ownerId, uploadedBy, role, status, pinHash) are rejected on write DTOs. Browser image reads use authenticated fetch -> Blob URL because an img tag cannot attach a Bearer header.

Shared error body: {"error":{"code":"VALIDATION_ERROR","message":"Request could not be accepted","fields":[{"field":"name","message":"Required"}],"requestId":"<id>"}}. fields is optional and must not echo secrets. No stack traces.

Common errors apply to every endpoint, in addition to errors listed below: 400 malformed/invalid input, 415 wrong request content type, 429 rate limit (Retry-After header), 500 sanitized unexpected failure, 503 unavailable dependency. Protected routes also return 401 missing/invalid/expired/wrong-audience credential. Role-forbidden operations return 403 before resource lookup; inaccessible or nonexistent scoped resources return 404 to avoid disclosing another event's data. Wrong gallery PIN uses generic 401 INVALID_GALLERY_ACCESS; unknown/draft public links return 404 GALLERY_NOT_FOUND. Requests rejected by a proxy may lack the JSON envelope; the UI must handle them.

Validation choices: names/titles 1-150 characters; displayName 1-100; description at most 2000; email at most 254 and valid format; password 8-128 characters; PIN exactly six ASCII digits, represented as a string. These values are project choices, not SRD requirements. IDs must be positive decimal strings; selection IDs unique. Upload limits/types are in [storage-design.md](storage-design.md). Photo page numbers start at zero; pageSize defaults to 24 and is bounded to 1-100. Empty pages return 200 with an empty item/photo array and pagination metadata.

## Response models

- User: {id, email, displayName, role}. Member records expose only the owning Admin's roster; no password/hash fields.
- Auth: {accessToken, tokenType:"Bearer", expiresIn:<configured-seconds>, user:User}.
- Event: {id, name, description, createdAt}. ownerId may be included for ADMIN, but never used as client authority.
- Member: {user:User, addedAt}.
- Photo: {id, eventId, uploadedBy, filename, contentType, fileSizeBytes, widthPx, heightPx, createdAt, contentPath}. contentPath is a protected relative API path, not a storage URL.
- Gallery: {id, eventId, title, status, photoIds, pinSet, publishedAt, expiresAt, shareUrl}. publishedAt/shareUrl are null in DRAFT; expiresAt is nullable; no PIN or hash is returned.
- PublicGallery: {title, photos:[{id, contentPath, widthPx, heightPx}], page, pageSize, totalItems, totalPages, hasNext}. Only selected READY photos, in selection order. Excludes staff identity, original filename, event metadata, expiration, and storage location.

## Authentication

### POST /auth/register

- Authentication/role: anonymous; creates ADMIN only (AUTH-01, SEC-01).
- Request: {email, displayName, password}; no role field.
- Success: 201 Auth; creates the account and signs in.
- Important errors: 400 validation/extra fields; 409 ACCOUNT_UNAVAILABLE for an unavailable email; 429 registration limit. Never allow this route to assign membership or change an existing account.

### POST /auth/login

- Authentication/role: anonymous; ADMIN or TEAM_MEMBER credentials (AUTH-02).
- Request: {email, password}.
- Success: 200 Auth.
- Important errors: 401 INVALID_CREDENTIALS with the same response for unknown email and wrong password; 429 login limit.

### GET /auth/me

- Authentication/role: staff JWT; ADMIN or TEAM_MEMBER.
- Request: no body.
- Success: 200 User.
- Important errors: 401 invalid/expired credential or unavailable user.
- Logout is client-side token disposal in the baseline. There is no server refresh/logout/revocation endpoint.

## Events and membership

### POST /events

- Authentication/role: staff JWT; ADMIN (EVENT-01).
- Request: {name, description?}.
- Success: 201 Event with Location header; backend derives owner from principal.
- Important errors: 400 invalid fields; 403 TEAM_MEMBER.

### GET /events

- Authentication/role: staff JWT; ADMIN or TEAM_MEMBER (EVENT-02).
- Request: no body or filters.
- Success: 200 {items:[Event]}; owned events for Admin, assigned events for member.
- Important errors: 401 invalid credential. Never return other owners' events.

### GET /events/{eventId}

- Authentication/role: staff JWT; owning ADMIN or assigned TEAM_MEMBER.
- Request: path eventId.
- Success: 200 Event.
- Important errors: 404 missing/inaccessible event (SEC-02).

### GET /team-members

- Authentication/role: staff JWT; ADMIN.
- Request: no body.
- Success: 200 {items:[User]}; only TEAM_MEMBER accounts with provisioned_by = current Admin, enabling assignment of an existing roster member.
- Important errors: 403 TEAM_MEMBER. No global email/user search.

### POST /events/{eventId}/members

- Authentication/role: staff JWT; owning ADMIN (USER-01).
- Request: exactly one of {userId} for an existing member in this Admin's roster, or {email, displayName, password} to provision and assign a new TEAM_MEMBER. Mixed forms are invalid.
- Success: 201 Member. New account creation and membership insertion commit atomically.
- Important errors: 400 invalid/mixed input; 403 TEAM_MEMBER; 404 inaccessible event or unavailable roster member; 409 ALREADY_ASSIGNED or ACCOUNT_UNAVAILABLE for duplicate email. Do not attach an arbitrary existing account by email or disclose its owner. Initial password is never echoed.

### GET /events/{eventId}/members

- Authentication/role: staff JWT; owning ADMIN.
- Request: path eventId.
- Success: 200 {items:[Member]}.
- Important errors: 403 TEAM_MEMBER; 404 missing/inaccessible event.
- Member removal, invitations, password reset, and user role changes are outside this contract (A-01, A-04).

## Photos

### POST /events/{eventId}/photos

- Authentication/role: staff JWT; assigned TEAM_MEMBER (PHOTO-01, PHOTO-06, PHOTO-07).
- Request: multipart/form-data; repeated files fields, one to 20 files; no eventId/owner/uploader metadata in the body. Bounds and content checks in storage design.
- Success: 200 {results:[{index,filename,status:"UPLOADED",photo:Photo} or {index,filename,status:"FAILED",error:{code,message}}]}. Request-level authorization and envelope checks happen first. Per-file acceptance is independent; 200 can contain partial or all file failures. Counts and status must be inspected; do not claim whole-batch success.
- Important errors: 400 missing files/too many files; 401 invalid credential; 403 ADMIN (upload not in Admin baseline); 404 unassigned/missing event; 413 aggregate size limit; 415 non-multipart envelope; 503 storage unavailable before file processing starts.
- Per-file failure codes: FILE_TOO_LARGE, UNSUPPORTED_IMAGE_TYPE, INVALID_IMAGE, IMAGE_DIMENSIONS_EXCEEDED, STORAGE_WRITE_FAILED, METADATA_SAVE_FAILED. Do not return a READY Photo on failure. If the HTTP connection fails, show an uncertain outcome and refresh the own-photo list before retrying.

### GET /events/{eventId}/photos

- Authentication/role: staff JWT; owning ADMIN or assigned TEAM_MEMBER (PHOTO-02, PHOTO-03).
- Request: path eventId; page=0 and pageSize=24 are optional for both roles. Admin additionally supports search (case-insensitive original filename, at most 255 characters), uploaderId, and selected=true|false. Blank/omitted filters return the normal page. TEAM_MEMBER cannot use Admin filters.
- Success: 200 {items:[Photo],page,pageSize,totalItems,totalPages,hasNext}; database-paged READY rows ordered newest first by createdAt then id. Admin receives only the owned event; Team Member receives only their own uploads in the assigned event.
- Important errors: 400 invalid page/pageSize/search/uploaderId; 403 Team Member attempts Admin filters; 404 unassigned/foreign/missing event. Filter values cannot expand event scope.
- Failed and pending records are internal recovery state, not a photo browser feature.

### GET /events/{eventId}/photos/{photoId}

- Authentication/role: staff JWT; owning ADMIN or assigned uploader TEAM_MEMBER.
- Request: eventId and photoId.
- Success: 200 Photo.
- Important errors: 404 cross-event, other member's photo, non-READY, or missing photo.

### GET /events/{eventId}/photos/{photoId}/content

- Authentication/role: staff JWT; owning ADMIN or assigned uploader TEAM_MEMBER.
- Request: eventId and photoId; no storage key supplied by client.
- Success: 200 image/jpeg or image/png bytes; Cache-Control: private, no-store; X-Content-Type-Options: nosniff. Use a sanitized server-controlled inline filename.
- Important errors: 404 inaccessible/non-READY/missing photo; 503 unavailable stored object. A mid-stream failure may terminate the response and must be shown as an image load failure.
- No edit/delete/download-as-attachment API is planned. Inline display bytes necessarily reach the browser; preventing screenshots/saving is not promised.

## Gallery draft, selection, PIN, publication

All routes below require staff JWT and owning ADMIN. TEAM_MEMBER returns 403 (SEC-03); foreign event/gallery returns 404. Every galleryId must belong to the path eventId. The separate selection/PIN endpoints mutate DRAFT galleries only; after publication, changes are supplied together through the atomic publish endpoint.

### POST /events/{eventId}/galleries

- Request: {title}.
- Success: 201 Gallery in DRAFT with empty photoIds, pinSet:false, null shareUrl. Generate internal random share token at creation.
- Important errors: 400 invalid title; 409 GALLERY_EXISTS (one per event).
- Supports GALLERY-02.

### GET /events/{eventId}/galleries

- Request: eventId.
- Success: 200 {items:[Gallery]}; zero or one gallery.
- Important errors: common role/scope errors.

### GET /events/{eventId}/galleries/{galleryId}

- Request: eventId and galleryId.
- Success: 200 Gallery.
- Important errors: common role/scope errors.

### PUT /events/{eventId}/galleries/{galleryId}/photos

- Request: {photoIds:["<photo-id>", "..."]}; ordered unique IDs, empty allowed for draft.
- Success: 200 Gallery with entire draft selection replaced atomically (GALLERY-01).
- Important errors: 400 duplicate/invalid IDs; 404 PHOTO_NOT_AVAILABLE for any foreign, missing, or non-READY photo; 409 GALLERY_ALREADY_PUBLISHED. No partial selection updates if any entry fails.

### PUT /events/{eventId}/galleries/{galleryId}/pin

- Request: {pin:"<six-digit-pin>"}.
- Success: 204 no body; replaces only a draft's salted PIN hash (GALLERY-04).
- Important errors: 400 invalid PIN format; 409 GALLERY_ALREADY_PUBLISHED. Never echo the PIN or return its hash.
- The Admin keeps the supplied PIN to share separately; GET cannot recover it.

### PUT /events/{eventId}/galleries/{galleryId}/expiry

- Request: {expiresAt:"<UTC ISO-8601 instant>"} to set/change expiration or {expiresAt:null} to remove it. This is an optional bonus setting independent of draft/published state.
- Success: 200 Gallery. The browser converts the Admin's local datetime input to UTC; the server and database compare/store the resulting instant consistently.
- Important errors: 400 malformed timestamp; 403 TEAM_MEMBER; 404 foreign/missing event or gallery. Admin ownership is checked server-side.

### POST /events/{eventId}/galleries/{galleryId}/publish

- Request: for first publication, {} uses the saved draft selection/PIN. For an already-published gallery, {photoIds?:["<photo-id>"], pin?:"<six-digit-pin>"}; at least one field is required to change it. photoIds is the complete ordered replacement; omitted fields retain the current value.
- Success: 200 Gallery with status PUBLISHED, the last publishedAt, and stable shareUrl = configured frontend origin + /gallery/{shareToken} (GALLERY-01 through GALLERY-04). First publication or re-publication replaces all supplied state atomically.
- Important errors: 409 EMPTY_GALLERY, PIN_REQUIRED, or PHOTO_NOT_READY; 404 inaccessible resources.
- Publish locks the gallery and validates every selected photo before changing anything. For a published gallery, an empty request is idempotent and returns its current representation without changing time/state. A replacement keeps the share URL stable. Changing the PIN increments its internal version and immediately invalidates gallery grants issued under the old PIN; changing selection alone keeps valid gallery grants applicable to the updated gallery.

## Customer/public gallery access

### POST /public/galleries/{shareToken}/access

- Authentication/role: anonymous customer; no account required (PUBLIC-01, PUBLIC-03).
- Request: {pin:"<six-digit-pin>"}.
- Success: 200 {accessToken:"<gallery-grant>",tokenType:"Bearer",expiresIn:<configured-seconds>}. Grant is scoped to this gallery and the current internal PIN version.
- Important errors: 400 malformed PIN shape; 401 INVALID_GALLERY_ACCESS for wrong PIN; 404 GALLERY_NOT_FOUND for unknown/draft link; 410 GALLERY_EXPIRED with safe message "Gallery has expired."; 429 PIN limit. No gallery/photo metadata before successful verification.

### GET /public/galleries/{shareToken}

- Authentication/role: gallery grant for this exact published gallery; no staff role (PUBLIC-02).
- Request: path shareToken and Authorization header; optional page/pageSize as above.
- Success: 200 PublicGallery.
- Important errors: 401 invalid/expired/wrong-type grant; 404 unknown/draft gallery or grant belongs to a different gallery; 410 GALLERY_EXPIRED.

### GET /public/galleries/{shareToken}/photos/{photoId}/content

- Authentication/role: gallery grant for this exact published gallery.
- Request: shareToken and photoId.
- Success: 200 validated image bytes with private, no-store and nosniff headers.
- Important errors: 401 missing/invalid/expired grant; 404 wrong gallery, unselected, unpublished, foreign, non-READY, or missing photo; 410 GALLERY_EXPIRED; 503 storage read failure.
- Must recheck Gallery.status, current PIN version, expiration, and GalleryPhoto membership on each request; possession of a photo ID, a grant issued before PIN rotation, an unexpired grant for a gallery that has since expired, or another gallery's grant never authorizes a read (SEC-07).

The frontend route /gallery/{shareToken} is a static PIN gate and reveals no sensitive metadata by itself. There is no unauthenticated gallery listing, object redirect, or alternate media endpoint. Staff credentials do not bypass the PIN on public routes; Admin previews use the protected staff content route.
