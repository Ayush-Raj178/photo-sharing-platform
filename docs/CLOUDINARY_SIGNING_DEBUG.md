# Cloudinary signature investigation

## Final outcome — 2026-09-10

The user retried the standalone smoke with a newly created `photoshare-local` Cloudinary API key/secret pair and reported successful HTTP Basic ping, a real PNG upload through the unchanged production adapter (`resource_type=image`, `type=authenticated`), and cleanup. The earlier request reached Cloudinary but returned HTTP 401 with sanitized message `Invalid Signature [REDACTED]`. Replacing the credential pair resolved that failure without changing upload options or signing. The demonstrated cause was the old credential pair, not the production adapter's upload-option signing. Whether the old pair was mismatched, copied incorrectly, or otherwise invalid is not established by this evidence.

User-supplied runtime evidence (not a real-cloud run performed by the agent):

```text
SMOKE config field=cloudName present=true edgeWhitespace=false
SMOKE config field=apiKey present=true edgeWhitespace=false length=15
SMOKE config field=apiSecret present=true edgeWhitespace=false length=27
SMOKE ping=SUCCESS authentication=HTTP_BASIC
SMOKE upload=SUCCESS adapter=production resourceType=image deliveryType=authenticated
SMOKE cleanup=SUCCESS
SMOKE result=SUCCESS ping=SUCCESS upload=SUCCESS
```

This verifies real Cloudinary storage upload and cleanup with the configured production adapter. It does not verify MySQL metadata, normal PhotoShare authorization, protected retrieval, gallery selection/publication, or PIN access. No credential values are retained in this report.

## Evidence established locally

The normal application receives multipart files in `PhotoController`, validates them in `PhotoService` / `PhotoValidation`, creates pending metadata, and passes the validated bytes and generated key through `PhotoStorage` to `CloudinaryPhotoStorage`. That adapter removes the key's extension for the Cloudinary public ID and delegates to `SdkCloudinaryAssetClient`. No caller provides an API key, secret, timestamp, or signature in upload options.

The actual Maven source JARs and bytecode for `cloudinary-core:2.3.2` and `cloudinary-http5:2.3.2` were inspected. Findings:

- `Uploader.upload` builds a separate parameter map with `Util.buildUploadParams` and passes both parameters and original options to the HTTP strategy.
- The current signed upload fields are `filename_override`, `format`, `overwrite`, `public_id`, `timestamp`, `type`, and `unique_filename`.
- `type` is `authenticated`. Boolean options remain booleans until their `toString()` values (`false`) are used both for signing and multipart serialization.
- The SDK generates a timestamp in epoch seconds, removes empty values, sorts parameter names, signs their serialized values with the configured secret, then adds `signature` and `api_key`. It defaults to SHA-1 and signature version 2; version 2 escapes ampersands within values before signing.
- `file` is a separate binary multipart part. `resource_type` and `cloud_name` determine the URL path. Those fields, `api_key`, and `signature` are not part of the upload signature input.
- `return_error` is read only from the options map by `http5.UploaderStrategy.callApi`. It is absent from `Util.buildUploadParams`, is neither signed nor sent over HTTP, and is passed into response handling only. Removing it does not fix a signature mismatch demonstrated by these tests.
- There is no `folder`, `asset_folder`, `eager`, `transformation`, `tags`, `context`, or `use_filename` option in the application call. The slash-delimited generated `public_id` provides its own path. Delete signs `invalidate`, `public_id`, `timestamp`, and `type`.
- Configuration uses the map constructor: `Cloudinary(Map)` -> `Configuration(Map)` -> exact `cloud_name`, `api_key`, and `api_secret` fields. This constructor does not read `CLOUDINARY_URL` or trim credentials.
- `Api.ping` uses GET `/v1_1/<cloud_name>/ping`, authenticated using HTTP Basic with the same key and secret. It is independent of Upload API parameter signatures. A denied Admin API operation alone does not establish that upload credentials are invalid.

`mvn dependency:tree` resolved Cloudinary core/HTTP5 at 2.3.2, Apache httpclient5 at 5.6.4, and httpcore5/httpcore5-h2 at 5.4.3. Spring Boot manages these Apache versions, overriding SDK defaults 5.3.1/5.2.5. The Cloudinary JSON implementation is bundled as `org.cloudinary.json` in core. No version was changed on speculation.

`CloudinarySigningTransportTest` runs the real installed SDK against a temporary loopback HTTP server. It starts with only `type=authenticated` and adds every application option individually, including a Unicode/ampersand filename and `return_error`. It parses received multipart fields, checks exact signed field names, excludes control/transport fields, detects duplicate parameters, verifies the input options were not mutated, and recomputes the signature with the SDK from the received field values. Every step and the production upload/delete calls passed. This establishes internal signing/transport consistency for the exercised inputs; it is not acceptance by the real Cloudinary service and does not prove external credentials valid or invalid.

## One-upload external-session smoke

Run in the external PowerShell session that already contains the real environment variables:

```powershell
$env:STORAGE_DRIVER = 'cloudinary'
powershell -NoProfile -ExecutionPolicy Bypass -File 'C:\Users\AyushRaj\Desktop\photo-sharing-platform\backend\cloudinary-smoke.ps1'
```

The execution-policy setting applies only to this child PowerShell process. The script builds a runtime-only classpath and compiles `backend/tools/CloudinarySmoke.java` into ignored `target/cloudinary-smoke`. It does not load test resources, launch Spring's web application, connect to MySQL, run migrations, or contact the running backend. It loads normal `application.yml` and the inherited environment for the storage properties, then calls the same production configuration factory and adapter.

It reports presence, leading/trailing whitespace flags, and key/secret lengths without revealing or normalizing credentials. It performs one authenticated ping and exactly one tiny generated PNG upload through the production adapter. Only its newly created, confirmed diagnostic asset is deleted. An upload with an unconfirmed result skips deletion and reports uncertainty. This diagnostic upload is independent of event/user metadata, so it is not full application workflow verification.

Expected successful lines:

```text
SMOKE config field=cloudName present=true edgeWhitespace=false
SMOKE config field=apiKey present=true edgeWhitespace=false length=<length>
SMOKE config field=apiSecret present=true edgeWhitespace=false length=<length>
SMOKE ping=SUCCESS authentication=HTTP_BASIC
SMOKE upload=SUCCESS adapter=production resourceType=image deliveryType=authenticated
SMOKE cleanup=SUCCESS
SMOKE result=SUCCESS ping=SUCCESS upload=SUCCESS
```

On failure, share only `SMOKE` lines and sanitized `Cloudinary operation failed` lines. No credentials, signatures, fingerprints, authorization headers, signed URLs, or complete provider bodies are needed.

## Diagnostic cleanup and retention

- Retain `backend/cloudinary-smoke.ps1` and `backend/tools/CloudinarySmoke.java` as an explicit developer diagnostic, outside application sources and the packaged application. It is not an automatic startup or test step and intentionally contacts real Cloudinary only when the developer runs it.
- Remove the temporary `-Compare` mode and its option-by-option real-upload loop. The retained command has only the one-upload production-adapter path. The retired PowerShell option is rejected before the script runs.
- Correct the diagnostic's final result to report failure if ping fails, matching its existing nonzero exit status. Upload/cleanup failures also prevent overall success.
- Keep `CloudinarySigningTransportTest` and existing provider/configuration/failure/redaction tests. Option-by-option signing checks remain offline against a temporary loopback server with synthetic credentials.
- Keep safe production diagnostics and confirmed-asset cleanup guards. No production adapter, configuration, dependency, authenticated-delivery option, backend authorization, or browser response was changed in this cleanup. No temporary debug endpoint or request/body dump remains.

## Verification and remaining evidence

Post-cleanup backend verification is recorded in [TESTING.md](TESTING.md). Compiling the standalone runner and checking PowerShell syntax do not contact Cloudinary; the user-supplied successful real smoke above remains the real-service evidence. Production sources and frontend files are unchanged in this cleanup. No deployment or Git/GitHub operation was performed.

PHOTO-04 = IMPLEMENTED / real Cloudinary storage upload and cleanup VERIFIED (user-reported smoke); normal-flow metadata and protected retrieval checks remain pending. See I-04/I-08 in [TESTING.md](TESTING.md).

SEC-04 = IMPLEMENTED / verification pending until the normal PhotoShare protected upload/gallery/PIN flow succeeds.

Next manual verification: use the new credential pair in the normal persistent-MySQL backend session. As an assigned Team Member, upload photos through PhotoShare and confirm authorized retrieval and metadata-only MySQL storage. As Admin, select and publish a subset. In an account-free customer session, reject a wrong PIN, accept the correct PIN, load selected photos through the protected backend, and deny unselected/unpublished or cross-event photo access. Check that browser responses contain neither credentials nor raw Cloudinary URLs. Do not treat this standalone smoke as evidence that those security paths have passed.
