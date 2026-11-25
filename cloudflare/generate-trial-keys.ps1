$ErrorActionPreference = 'Stop'
if (-not (Get-Command openssl -ErrorAction SilentlyContinue)) {
    throw 'OpenSSL not found. Install OpenSSL first, then reopen PowerShell.'
}
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$privatePem = Join-Path $root 'trial-signing-private.pem'
$privateDer = Join-Path $root 'trial-signing-private.pk8.der'
$publicPem = Join-Path $root 'trial-signing-public.pem'
$publicDer = Join-Path $root 'trial-signing-public.spki.der'

openssl genpkey -algorithm ED25519 -out $privatePem
if ($LASTEXITCODE -ne 0) { throw 'Failed to generate Ed25519 private key.' }
openssl pkey -in $privatePem -outform DER -out $privateDer
if ($LASTEXITCODE -ne 0) { throw 'Failed to export PKCS8 private key.' }
openssl pkey -in $privatePem -pubout -out $publicPem
if ($LASTEXITCODE -ne 0) { throw 'Failed to export public key.' }
openssl pkey -pubin -in $publicPem -outform DER -out $publicDer
if ($LASTEXITCODE -ne 0) { throw 'Failed to export SPKI public key.' }

$privateB64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($privateDer))
$publicB64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($publicDer))
Write-Host ''
Write-Host 'TRIAL_SIGNING_PRIVATE_KEY_B64 (ONLY set as a Cloudflare Worker secret):' -ForegroundColor Yellow
Write-Host $privateB64
Write-Host ''
Write-Host 'TRIAL_PUBLIC_KEY_B64 (paste into Main.kt; public key is safe to ship):' -ForegroundColor Green
Write-Host $publicB64
Write-Host ''
Write-Warning "Keep $privatePem and $privateDer offline. NEVER commit or upload the private key to GitHub."
