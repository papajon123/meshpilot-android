# Signing recovery envelope

`ci/SigningRecovery.java` writes one strongly encrypted JSON file that contains the Android
release keystore and its password. It is an independent recovery copy: the RSA private key
never enters CI, and plaintext signing material never touches disk or logs.

The helper uses only the Java 17 standard library and is run as a single-file source program:

```text
java ci/SigningRecovery.java <output.json>   # write an envelope
java ci/SigningRecovery.java --self-test     # crypto self-check, writes no files
```

## Lifecycle

1. The owner creates an RSA key pair of at least 3072 bits offline. The private key is kept
   outside CI (for example in a password manager or Vault).
2. The public key, PEM encoded with `-----BEGIN PUBLIC KEY-----`, is stored as the protected
   environment secret `SIGNING_RECOVERY_PUBLIC_KEY` in the gated `release` environment.
3. A tagged release runs the helper only after the signed APK has been built and verified. It
   passes the existing `ANDROID_RELEASE_KEYSTORE_B64` and `ANDROID_RELEASE_PASSWORD` secrets
   plus the recipient public key.
4. The workflow uploads the file as the `signing-recovery` artifact with 90-day retention. It
   is never attached to public release downloads.
5. The owner downloads the artifact into private storage and decrypts it offline (see below).
   The restored keystore should be checked by comparing its certificate SHA-256 fingerprint
   with the repository's recorded release signer fingerprint before it is trusted.
6. Rotate the recipient by generating a new key pair, replacing the secret and exporting a
   fresh envelope from the next gated release. Never reuse an old private key after rotation.

## Envelope format

UTF-8 JSON, one object per file, produced with a trailing newline:

```json
{
  "schemaVersion": 1,
  "algorithm": "RSA-OAEP-256+A256GCM",
  "recipientSha256": "<lowercase hex SHA-256 of the DER SubjectPublicKeyInfo>",
  "wrappedKey": "<Base64 of the RSA-OAEP ciphertext of the 32-byte AES key>",
  "nonce": "<Base64 of the 12-byte GCM nonce>",
  "ciphertext": "<Base64 of AES-256-GCM ciphertext with the 16-byte tag appended>"
}
```

All Base64 values use the standard alphabet with padding. `schemaVersion` is the integer `1`
and `algorithm` is exactly `RSA-OAEP-256+A256GCM`. Extra fields must be ignored by consumers.

## Payload format

The ciphertext decrypts to a single UTF-8 JSON object with exactly two string fields:

```json
{"keystoreBase64":"<Base64 of the keystore bytes>","keystorePassword":"<keystore password>"}
```

Strings use standard JSON escaping. The producer writes the fields in the order shown, but
consumers must parse by name. The payload contains no key alias, file name or other metadata;
the alias remains repository configuration.

## Cryptography

- A random 32-byte AES key and a random 12-byte nonce are generated for every envelope.
- Payload encryption: AES-256-GCM (`AES/GCM/NoPadding`), 128-bit tag, no additional
  authenticated data. The tag is the last 16 bytes of `ciphertext`.
- Key wrapping: RSA-OAEP with SHA-256 and MGF1 with SHA-256, explicitly supplied as
  `OAEPParameterSpec`.
- `recipientSha256` binds the envelope to the exact DER SubjectPublicKeyInfo of the recipient.
- The recipient key must be RSA and at least 3072 bits; smaller or non-RSA keys are rejected.

## Decrypting with .NET 8 (PowerShell 7.4+)

```powershell
$envelope = Get-Content -Raw -LiteralPath .\signing-recovery.json | ConvertFrom-Json
$rsa = [System.Security.Cryptography.RSA]::Create()
$rsa.ImportFromPem((Get-Content -Raw -LiteralPath .\recovery-private.pem))

$fingerprint = [Convert]::ToHexString(
    [System.Security.Cryptography.SHA256]::HashData($rsa.ExportSubjectPublicKeyInfo())
).ToLowerInvariant()
if ($fingerprint -ne $envelope.recipientSha256) { throw 'recipient mismatch' }

$aesKey = $rsa.Decrypt(
    [Convert]::FromBase64String($envelope.wrappedKey),
    [System.Security.Cryptography.RSAEncryptionPadding]::OaepSHA256)

$blob = [Convert]::FromBase64String($envelope.ciphertext)
$tag = [byte[]]$blob[-16..-1]
$cipher = [byte[]]$blob[0..($blob.Length - 17)]
$plain = [byte[]]::new($cipher.Length)
$aes = [System.Security.Cryptography.AesGcm]::new($aesKey, 16)
$aes.Decrypt([Convert]::FromBase64String($envelope.nonce), $cipher, $tag, $plain)

$payload = [System.Text.Encoding]::UTF8.GetString($plain) | ConvertFrom-Json
[IO.File]::WriteAllBytes(
    'release.keystore',
    [Convert]::FromBase64String($payload.keystoreBase64))
```

The two-argument `AesGcm` constructor above requires .NET 8 or newer (PowerShell 7.4+).
Keep the private key and the decrypted keystore on
protected storage, and do not echo `keystorePassword`.

## Failure behaviour

The helper reads secrets only from `ANDROID_RELEASE_KEYSTORE_B64`, `ANDROID_RELEASE_PASSWORD`
and `SIGNING_RECOVERY_PUBLIC_KEY`. It refuses to overwrite an existing output path (the check
runs before any secret is read), creates the file with `CREATE_NEW`, sets owner-only
permissions where the platform supports POSIX modes, and deletes a partial file if writing
fails. Errors are fixed messages without exception details, stack traces or secret values;
on any failure no envelope is produced.
