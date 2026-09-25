# VARUGAI 16 encrypted portable-backup hardening

Base release commit: `f4c7e72971a7536745120e72ee3064b73090fc96`

## Recovery model

VARUGAI now uses one app-generated recovery key for portable encrypted backups.

- 20 Crockford Base32 symbols (100 bits), shown as five groups of four.
- Generated once from SecureRandom.
- The same recovery key protects subsequent backups.
- The device-local copy is AES-GCM wrapped with a non-exportable Android Keystore key.
- The displayed key must be recorded separately by the teacher; it is the cross-device recovery path.

## Portable backup cryptography

- Outer envelope: varugai-backup-encrypted, envelope schema 1.
- Per-backup random 128-bit salt.
- HKDF-HMAC-SHA256 from the high-entropy recovery key.
- AES-256-GCM with a fresh 96-bit IV.
- Fixed authenticated context: VARUGAI/portable-backup/v1.
- Inner payload remains VARUGAI schema-3 JSON with SHA-256 integrity checking.
- Student/register content is not exposed in the encrypted envelope.

## Compatibility

- Existing VARUGAI 15 schema-1/2 backups remain importable.
- Existing unencrypted VARUGAI 16 schema-3 backups remain importable.
- Encrypted backups auto-open with the locally stored recovery key when possible.
- On a replacement device, the app asks for the recovery key.
- If a replacement device has no recovery key yet, a successfully used key is adopted for future backups.
- An existing device recovery key is never silently overwritten by a different imported key.

## Release gate

Encrypted export is disabled until the recovery key has been generated and recorded. Android Auto Backup remains disabled because the SQLCipher database is bound to Android Keystore material.
