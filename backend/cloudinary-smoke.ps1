[CmdletBinding()]
param(
    [ValidatePattern('^events/[0-9]+/photos/[a-zA-Z0-9_-]+\.(jpg|png)$')]
    [string]$ExistingStorageKey
)

$ErrorActionPreference = 'Stop'
Push-Location -LiteralPath $PSScriptRoot
try {
    $cachePath = Join-Path $PSScriptRoot '.m2\repository'
    & mvn -B -ntp "-Dmaven.repo.local=$cachePath" compile dependency:build-classpath '-DincludeScope=runtime' '-Dmdep.outputFile=target/cloudinary-smoke-classpath.txt'
    if ($LASTEXITCODE -ne 0) { throw 'Smoke preparation failed.' }
    $runtimeClasspath = (Get-Content -LiteralPath 'target/cloudinary-smoke-classpath.txt' -Raw).Trim()
    $classpath = "target/classes;$runtimeClasspath"
    & javac -cp $classpath -d target/cloudinary-smoke tools/CloudinarySmoke.java
    if ($LASTEXITCODE -ne 0) { throw 'Smoke compilation failed.' }
    $smokeArguments = @()
    if ($ExistingStorageKey) { $smokeArguments += $ExistingStorageKey }
    & java -cp "target/cloudinary-smoke;$classpath" com.photoshare.photo.storage.CloudinarySmoke @smokeArguments
    if ($LASTEXITCODE -ne 0) { throw 'Cloudinary smoke failed; share only SMOKE and sanitized Cloudinary operation failed lines.' }
} finally {
    Pop-Location
}
