# Persistent local backend (Windows PowerShell)

Use the normal backend configuration for manual testing. The database is the existing local MySQL database selected by `DB_JDBC_URL`; photo bytes can use Cloudinary independently of the database choice.

## Required environment in the PowerShell window that starts Maven

Set these variables privately in that window before running the command below:

- `DB_JDBC_URL`: the JDBC URL of your **existing** local MySQL database, including its exact schema name, for example `jdbc:mysql://localhost:3306/YOUR_EXISTING_DATABASE`. Replace the example with your actual URL and preserve your existing connection options. Do not embed credentials in the URL or enable automatic database creation to work around a wrong schema name.
- `DB_USERNAME` and `DB_PASSWORD`: the existing MySQL account credentials.
- `STAFF_JWT_SECRET_BASE64` and `GALLERY_JWT_SECRET_BASE64`: separate Base64-encoded signing keys, each containing at least 32 random bytes. Keep these values stable across restarts; do not copy the public test keys. Changing a signing key invalidates active tokens, but does not delete MySQL users or change their passwords.
- `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, and `CLOUDINARY_API_SECRET`: values from the same Cloudinary Product Environment.

The application does not automatically load the root `.env` or `.env.example`. Variables set in another PowerShell process are not inherited by an already open window. No database name or real credentials are recorded in this guide.

## Start the backend

Stop the old manually managed backend before starting its replacement. In the PowerShell window with the variables above:

```powershell
Set-Location -LiteralPath 'C:\Users\AyushRaj\Desktop\photo-sharing-platform\backend'
$env:STORAGE_DRIVER = 'cloudinary'

if ($env:DB_JDBC_URL -notmatch '^jdbc:mysql://(?:localhost|127\.0\.0\.1)(?::[0-9]+)?/[^?\s/]+(?:\?.*)?$') {
    throw 'Set DB_JDBC_URL to your existing local MySQL database URL before starting.'
}

mvn -B -ntp "-Dmaven.repo.local=C:\Users\AyushRaj\Desktop\photo-sharing-platform\backend\.m2\repository" "-Dspring-boot.run.useTestClasspath=false" spring-boot:run
```

Run this command without the old `spring-boot.run.arguments` setting. Do not include `--spring.config.additional-location=file:./src/test/resources/application.yml`, `spring-boot:test-run`, or a test classpath. If you previously configured datasource/config overrides through `SPRING_DATASOURCE_*`, `SPRING_CONFIG_*`, `SPRING_APPLICATION_JSON`, or JVM arguments, remove those test overrides from the launching window as well. The command uses the normal `src/main/resources/application.yml` via Maven's compiled application resources.

The default backend URL is `http://localhost:8080/api/v1`. Normal configuration maps `DB_JDBC_URL`, `DB_USERNAME`, and `DB_PASSWORD` into the datasource and maps `STORAGE_DRIVER` into `photoshare.storage.driver`. A missing/unreachable MySQL configuration must be corrected; do not add the H2 test classpath as a fallback.

Startup should report a `jdbc:mysql:` datasource and the intended schema, not `jdbc:h2:mem:photoshare`. Hibernate uses `ddl-auto: validate`; Flyway applies versioned migrations once and retains its history. There is no database reset or drop command in this procedure. If the existing schema conflicts with migration history, inspect the error without dropping data, cleaning Flyway, or blindly baselining the schema.

## Why the previous command lost accounts

`spring-boot.run.useTestClasspath=true` made the test-scoped H2 driver available. Loading `src/test/resources/application.yml` as an additional configuration selected:

```text
jdbc:h2:mem:photoshare;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1
```

This is an H2 database in the backend JVM's memory. `MODE=MySQL` is SQL compatibility mode, not a MySQL server connection. `DB_CLOSE_DELAY=-1` only keeps the database alive while that JVM lives. Stopping the JVM destroys its in-memory users, events, memberships, photo metadata, and galleries. On the next run, Flyway migrates a new empty H2 database, so the old accounts no longer exist.

Accounts previously created only in a stopped H2 process are not transferred into MySQL by changing this command. Existing MySQL data remains separate and is not deleted. Register new manual-test accounts in MySQL if necessary; do not interpret an empty but differently named schema as loss of data from your intended schema.

## Tests and manual verification

`mvn verify` continues to use the isolated test configuration and its H2 database. It does not prove real MySQL restart persistence or a real Cloudinary upload.

After starting with MySQL, register an Admin, create an event and Team Member, then stop and restart with the same database URL and credentials. Log in again and confirm the records remain. Separately retry the real Cloudinary upload after correcting its environment credentials. Cloudinary delivery remains authenticated and reads remain authorized through the backend; no public-delivery change is needed for the startup correction.

PHOTO-04 records successful real authenticated Cloudinary upload and cleanup from the user's standalone smoke on 2026-09-10; normal PhotoShare metadata and protected retrieval checks are still pending. SEC-04 remains IMPLEMENTED / verification pending until the normal protected upload/gallery/PIN flow succeeds. The retained [developer smoke diagnostic and evidence](CLOUDINARY_SIGNING_DEBUG.md) are separate from that flow. Deployment configuration and automated-test configuration remain unchanged.
