# Run on the license issuer's offline/admin workstation. Requires OpenSSL.
# IMPORTANT: never publish or send dev-license-private.pem to customers.
openssl genpkey -algorithm ED25519 -out license-private.pem
openssl pkey -in license-private.pem -pubout -out license-public.pem
$pub = openssl pkey -pubin -in license-public.pem -outform DER | ForEach-Object { $_ }
Write-Host "Public key generated. Replace PUBLIC_KEY_B64 in Main.kt with the Base64-encoded DER public key."
Write-Host "Keep license-private.pem offline and backed up securely."
