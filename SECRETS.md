# GitHub Actions secrets for VARUGAI release signing

VARUGAI already has an established production signing identity. **Do not generate a replacement
keystore for package `com.gasczoology.varugai`**. An update signed by a new key would not match
the existing app identity.

The accepted production certificate SHA-256 is:

`A4:1E:9D:E2:48:E9:55:94:86:8A:E5:74:0A:49:2D:35:D3:59:50:AF:64:4C:D7:EF:19:0C:73:16:49:82:6B:9B`

Create these four under **Settings → Secrets and variables → Actions → New repository secret**.
Nothing is committed to the repository; CI reconstructs the keystore only in the job's temporary
directory.

| Secret name | Required value |
|---|---|
| `VARUGAI_KEYSTORE_BASE64` | Base64 of the **existing production/upload .jks** |
| `VARUGAI_KEYSTORE_PASSWORD` | Password for that existing keystore |
| `VARUGAI_KEY_ALIAS` | Actual alias inside that existing keystore |
| `VARUGAI_KEY_PASSWORD` | Password for that alias |

Before storing the secrets, verify the local keystore:

```bash
keytool -list -v -keystore /path/to/existing-varugai.jks
```

The SHA-256 shown by `keytool` must equal the certificate above. If it does not, stop; do not
use that keystore for VARUGAI.

## Rules

- Never commit the keystore, passwords, Base64 value, or a populated `keystore.properties`.
- Never paste the private keystore or passwords into issues, source files, workflow YAML, or chat.
- The release workflow fails when signing secrets are absent and never falls back to the debug key.
- CI verifies the keystore certificate before building and verifies the APK certificate again after
  signing.
- Keep at least two secure offline copies of the production keystore and record its passwords
  separately.
