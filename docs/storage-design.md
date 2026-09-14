# Photo storage lifecycle

Status: the provider-neutral boundary, local adapter, and Cloudinary adapter are implemented. Automated provider/configuration/mapping/failure tests pass without credentials; real authenticated Cloudinary upload and cleanup succeeded in the user's standalone smoke on 2026-09-10. Normal-flow metadata and protected retrieval verification remain pending. See [runtime evidence](CLOUDINARY_SIGNING_DEBUG.md). Covers PHOTO-01 through PHOTO-07, SEC-04, SEC-07.

## Storage boundary

MySQL holds the seven SRD metadata examples plus validation/state fields; photo bytes live in private object/file storage. `PHOTOSHARE_STORAGE_DRIVER=cloudinary` selects Cloudinary authenticated assets for production-capable storage, while `PHOTOSHARE_STORAGE_DRIVER=local` keeps the API-only local adapter outside the webroot for development/tests. Production must not depend on ephemeral host disk.

Use one storage interface for put, read, and delete. The backend mediates uploads and image reads. The Cloudinary adapter maps the generated storage key to a Cloudinary public ID plus validated format, records original filename/content type/file size through the existing metadata flow, uploads with delivery type `authenticated`, and downloads through a short-lived signed URL consumed only by the backend. Browser redirects/provider URLs, direct-to-storage uploads, public assets, thumbnails, resizing, and archive/bulk tooling are not in the baseline.

## Provider configuration

Cloudinary mode requires `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, and `CLOUDINARY_API_SECRET`; missing values fail application startup with the missing variable name and never echo a value. These values bind only to backend configuration. Tests explicitly select local storage or construct the Cloudinary boundary with synthetic settings and never require real credentials.

## Proposed acceptance policy (assumption A-07)

- Accepted types: image/jpeg with .jpg/.jpeg, and image/png with .png; match extension, reported MIME, actual signature, and image decoding.
- Per-file limit: 10 MiB = 10,485,760 bytes, nonempty.
- Per-request limit: 20 files, 210 MiB = 220,200,960 bytes inclusive of multipart overhead. Reject invalid envelope/aggregate limits before processing where possible; enforce actual streamed bytes even without Content-Length.
- Decoded limit: 40,000,000 pixels, positive dimensions; cap decoding memory/time. Reject unsupported/corrupt images instead of silently converting.
- Sanitize original filenames for display: remove paths/control characters, limit to 255 characters, and escape when rendering. Never use them as object keys.
- Apply the same request size ceiling at proxy, server multipart parsing, and application validation. Bounded temporary disk/streaming avoids loading an entire batch in memory.

These are project defaults, not SRD-specified types, limits, or performance guarantees. Multiple upload means selecting multiple files in one bounded request; optional bulk upload is deferred.

## Storage keys and metadata

Generate a fresh opaque key such as events/<event-id>/photos/<random-uuid>.<validated-extension>. Derive event ID server-side after authorization. A unique key prevents collisions and overwriting existing images. A user filename, email, PIN, or token must never become a key.

Record Photo ID, Event ID, Uploaded By, Filename, Storage Location (storage_key with bucket/endpoint supplied by environment), File Size, Created At, validated content type/dimensions, status, updated timestamp, and internal failure code. The API exposes safe metadata and a protected contentPath; it does not return keys. Uploaded By always comes from the staff principal.

## Multiple-photo upload lifecycle

1. Authenticate the TEAM_MEMBER and verify current assignment to the path event. Reject authorization failures before storing any file.
2. Validate request count/aggregate bounds. Process files independently in request order with bounded concurrency (initially sequential to simplify failures).
3. For each file, validate complete content, dimensions, and actual byte count using a bounded temporary resource. Recheck event assignment at metadata insertion.
4. Insert a PENDING photo row with a generated key in a short database transaction; do not show it to users or allow selection.
5. Write the validated bytes to that private key outside a database transaction. Abort incomplete multipart provider uploads on failure.
6. After successful object persistence, commit status READY in another short transaction. Only now return UPLOADED with a Photo DTO.
7. On failure, return a sanitized per-file FAILED result, leave no visible READY row, and clean up as below. Successful files in a mixed batch remain available.
8. Release temporary resources in a finally/guaranteed cleanup path for success, failure, and connection cancellation.

The request returns 200 with indexed per-file results after it has passed request-level checks; even all per-file failures may yield that envelope. The UI must inspect each result. A request-level 400/413/415/503 uses the standard error response. See [api-contract.md](api-contract.md) for exact semantics.

## Failure and reconciliation rules

- Invalid content: reject that file before object storage/PENDING metadata; no cleanup object is necessary.
- PENDING insertion fails: do not write the object; return metadata failure.
- Object write fails or times out: outcome may be uncertain. Attempt abort/delete for that exact generated key, mark row FAILED with a sanitized reason, and retain the key for reconciliation if cleanup cannot be confirmed.
- Object write succeeds but READY update fails: attempt deletion for that key. A database commit timeout may have committed READY; read back when possible. Only compensate after proving the row is not READY; if state is uncertain, leave the object for reconciliation to avoid deleting a usable photo.
- Cleanup or database connection fails: log only a recovery identifier/category, retain enough metadata/key context for reconciliation, and never claim success. Recovery reconciles database state before deciding to delete anything.
- Process crash: stale PENDING rows and provider multipart leftovers are possible. A future bounded maintenance job/command compares rows and private keys. Apply a conservative age threshold longer than the maximum upload timeout; proposed one hour for pending/failed recovery, 24 hours for unreferenced objects.
- Reconciliation preserves READY objects and every selected READY photo. For stale PENDING/FAILED rows, delete only their exact keys after confirming no READY reference, then retain/delete failed metadata according to an approved retention policy. For orphan objects without any row, verify age and absence of a database reference twice before deletion.
- HTTP response lost after success: refresh the member's own-photo list and show an uncertain result. Retrying creates a new photo/key; automatic deduplication/idempotency is not promised. Do not blindly retry the whole batch.

Storage cleanup is a consistency mechanism for failed uploads, not a user-facing photo deletion feature. A future feature to delete events/photos must define retention and gallery-reference behavior first.

## Read lifecycle and public/private access

Staff image reads check owner or assigned uploader scope and READY state. Public reads check the correct gallery grant, PUBLISHED state, and GalleryPhoto membership of a READY photo. The backend then streams validated image bytes; no redirect to an anonymous object URL.

Keep both original objects and draft/selected/published objects private. Publication changes relational access state, not bucket permissions or object location. Use Cache-Control: private, no-store, X-Content-Type-Options: nosniff, and no shared proxy caching. Browser Blob URLs exist only for authorized fetched bytes and should be revoked on screen exit/logout.

Missing stored bytes produce a controlled storage error and image-retry affordance, not a broken authorization bypass. Record operational health discrepancies for investigation; do not silently replace or expose unrelated objects.

## Capacity, backups, and deferred work

Cloud deployment needs durable originals and backups of metadata with a compatible storage recovery point. Cloudinary service tier, retention, capacity, quotas, costs, and backup support remain undecided. Thumbnails/resizing, browser-direct delivery, gallery expiration, and sophisticated bulk upload stay optional. Original-image grids may use more bandwidth; do not claim performance at the illustrative 1,250-photo example has been verified.

Implementation acceptance includes private-object access tests, mixed batch failure tests, uncertain storage outcomes, stale upload reconciliation, and ensuring no database column stores image bytes. See [TESTING.md](TESTING.md).
