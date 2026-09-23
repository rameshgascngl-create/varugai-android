# GitHub Actions secrets for the Android release

Create these four under **Settings → Secrets and variables → Actions → New repository secret**.
Nothing here is stored in the repository; the workflow reads them at run time and the keystore
is reconstructed only inside the job's temporary directory.

| Secret name | What it contains | How to get it |
|---|---|---|
| `VARUGAI_KEYSTORE_BASE64` | the whole `.jks` file, base64 encoded on one line | `base64 -w0 varugai-release.jks` (macOS: `base64 -i varugai-release.jks`) |
| `VARUGAI_KEYSTORE_PASSWORD` | the keystore password you chose | from your password manager |
| `VARUGAI_KEY_ALIAS` | `varugai` | the alias used by `tools/make-keystore.sh` |
| `VARUGAI_KEY_PASSWORD` | the key password | usually the same as the keystore password unless you set a separate one |

## Rules

* Never paste any of these into a file, a commit, an issue, a chat, or the workflow YAML.
* The workflow never echoes them; `secrets.*` values are masked in Actions logs by default,
  but do not add `set -x` or `echo` around them.
* If the keystore is unavailable the release build is left **unsigned** rather than falling
  back to the debug key, so an unsigned artefact can never be mistaken for a release.
* Losing the `.jks` or its password means no future version can be installed over this app.
  Two offline backups, in different places, password recorded separately.
