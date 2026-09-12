# UI guidelines and implemented baseline

Status: the mandatory frontend is implemented under `frontend/` and locally verified against the real backend. Supports UI-01 and the role workflows in SRD sections 1-3. The implementation uses React, Vite, Tailwind CSS, React Router, Axios, and Lucide icons with a white/forest-green, photo-led visual system derived from the retained concepts in `frontend/design/`.

## Shared presentation and behavior

Use a clean neutral layout, one clear primary action per screen, readable text, consistent spacing, and photo-focused content. Keep desktop navigation simple and collapse it accessibly on narrow screens. Plan keyboard access, visible focus, labeled inputs, sensible contrast, descriptive errors, and touch-friendly controls. These are project quality decisions supporting the SRD's responsive UI and section 13 UX hints.

Every data view needs loading, empty, error, and success states. Avoid asserting actions succeeded until the API confirms them. Disable duplicate submissions while pending; restore controls after failures. Preserve non-secret form values when useful, but clear password/PIN fields when leaving their flow. Never include secrets in URLs, alerts, analytics, screenshots, or diagnostic output.

Render names/filenames as plain escaped text. Responsive photo grids should fit narrow phones through desktops without horizontal page overflow. Use originals for the baseline; generated thumbnails, search/filtering, pagination/infinite scrolling, downloads, and photo editing are deferred.

## Staff access screens

- /register: Admin registration with display name, email, password, validation, and link to login. No role picker.
- /login: shared Admin/Team Member sign-in; generic credential errors, loading state, and rate-limit feedback.
- After login: route by the server-returned role to owned or assigned events. Validate current session through /auth/me when needed.
- Staff session restoration: keep the short-lived staff session in tab-scoped `sessionStorage`, revalidate it through `/auth/me` on reload, and clear it if invalid or expired. Logout also clears it. Gallery grants remain in memory and require PIN re-entry after reload.
- Logout: clear token, protected data, and Blob URLs; no claim of server-side revocation.

## Admin screens and flow

### Owned events: /admin/events

Show only the Admin's events with name and creation information. Empty state invites Create event. The create form accepts name and optional description. Validation and dependency failures stay on the form with a useful message.

### Event workspace: /admin/events/:eventId

Event header with Photos, Team, and Gallery sections. Load only after backend confirms ownership. Unknown/foreign event gets an unavailable screen without leaking its name.

Team section: show assigned members. Add member offers either selection from this Admin's existing roster or creation with display name, email, and initial password. Explain that the Admin must communicate the initial password separately; never echo saved passwords. No invite-email claim, global account search, remove-member control, or role editor.

Photos section: show all READY event uploads, each with safe filename, uploader, and upload time. Empty state explains that assigned members need to upload. Admin can preview images using authorized content reads. No Admin upload control is included under assumption A-04.

Gallery draft section: create a gallery title, then select/unselect event photos with a visible selected count. Persist the ordered selection through the API. Clearly distinguish unsaved changes, saving, saved, and failure states. Show a review of selected photos before publishing. No other-event photos can be selected even through a manipulated client.

PIN field: labeled masked input that preserves leading zeros. Allow temporary reveal while entering, but never show a recovered PIN from the server. A saved draft displays only "PIN set". The Admin keeps the chosen PIN for sharing.

Publish action: enabled only after selection is saved, at least one photo is selected, and a PIN is set. Server remains authoritative. Show a confirmation identifying the gallery and selected count. On failure keep the draft available; on success display published status and shareable link.

Published section: Copy the stable gallery link plus instruction to share the chosen PIN separately. Do not display a fake generated/recovered PIN. Copy failure must be visible. Allow the Admin to stage a complete replacement selection and optionally enter a replacement PIN, review the changes, and re-publish them in one request. Keep the current published gallery live until the server commits the replacement. Explain that rotating the PIN signs existing customer sessions out; a selection-only update does not. An unchanged publish request returns the existing publication.

## Team Member screens and flow

### Assigned events: /team/events

Show only assigned events. Empty state explains that an Admin must assign an event. Do not display Create event, member management, or gallery publication actions.

### Own uploads: /team/events/:eventId

Show event context, an Upload photos action, and only the member's READY uploads. Preview images through the authenticated API. There are no controls to see/manage another member's photos or select/publish a gallery.

Multiple upload flow: select JPEG/PNG files, list filenames/sizes before submission, show the configured file/count/aggregate limits, and permit removing queued files before upload. Client validation gives immediate feedback; backend repeats it. While uploading show an honest pending indicator; show a progress percentage only if real upload progress is available.

Results must be per file: succeeded, rejected, or failed with a retry option for failed files only. A mixed batch is not a complete success. For a lost HTTP response, explain that the outcome is uncertain, refresh own photos, and ask the user to review before retrying; avoid automatic whole-batch retry. Do not infer committed metadata from transfer progress.

## Customer/public screens and flow

### PIN gate: /gallery/:shareToken

No account, registration, or staff login required. Present a generic gallery access heading and labeled PIN field. Show no gallery title, photo thumbnails, filenames, uploader details, or event data before successful verification.

Submitting the correct PIN opens the gallery. Wrong PIN shows an accessible inline error and no protected data. Rate limiting shows retry timing. Unknown/unpublished links show the same gallery-unavailable screen. Do not put the PIN in a URL or query string.

### Authorized gallery: same route after verification

Show PublicGallery.title and a responsive grid containing only published selected photos, in the Admin's selected order. A simple accessible preview allows browsing next/previous photos and closing via keyboard. This is core browsing, not a downloading feature.

Fetch images using the scoped grant and create/revoke Blob URLs carefully. On grant expiry clear protected state and return to the PIN gate. A missing/failed image has a neutral retry state without revealing object paths. Do not expose unselected photo counts or staff identity.

An authenticated customer can inherently retain viewed image bytes; do not promise that saving or screenshots are prevented. Thumbnails, expiration scheduling, sharing to social platforms, and download buttons are deferred.

## Responsive verification targets

Plan narrow phone (360 px), tablet (768 px), and desktop (1280 px) checks, plus keyboard-only navigation. Verify long filenames/titles, validation messages, upload result lists, empty/large photo grids, preview focus behavior, and PIN entry without clipping or horizontal overflow. These sizes are verification choices, not SRD limits.

Use safe synthetic/demo photos for screenshots. The [screenshots](screenshots/) folder is intentionally empty until real UI exists and is verified. No mock screenshot should be presented as implemented functionality.

See [api-contract.md](api-contract.md) for server outcomes and [TESTING.md](TESTING.md) for planned end-to-end verification.
