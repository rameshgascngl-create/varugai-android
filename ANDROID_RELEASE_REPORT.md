# VARUGAI — Android release report

## Build state

| Milestone | State |
|---|---|
| SOURCE AUDITED | **reached** |
| ANDROID SOURCE IMPLEMENTED | **reached** |
| Attendance logic tests | **18 passed, 0 failed** |
| DEBUG BUILD PASS | not reached — no Android SDK in the authoring environment |
| RELEASE BUILD PASS | not reached |
| APK SIGNATURE VERIFIED | not reached |
| AAB BUILD PASS | not reached |
| DEVICE QA PASS | not reached |
| FINAL | **not reached** |

No APK or AAB exists. Nothing in this report should be read as claiming otherwise.

## Identity

| Field | Value |
|---|---|
| Application ID | `com.gasczoology.varugai` (first authoritative signed release) |
| Version name | `15.1.0` |
| Version code | `15100` |
| Min SDK | 24 |
| Target SDK | 34 |
| Compile SDK | 34 |
| Launcher label | VARUGAI |

The earlier `app.varugai.attendance` package is deliberately abandoned. It was signed with a
different key and could never have been upgraded in place, so nothing is lost by moving to the
institutional identifier now. This is the last point at which the package could change: after
the first signed release it is permanent.

## Permissions in the merged manifest

**None.** The manifest declares no `<uses-permission>` at all — no INTERNET, no storage,
no camera. Exports go through the Storage Access Framework, which needs no permission.

## Source provenance

| Item | Value |
|---|---|
| Canonical source | `VARUGAI_15_Semester_Grid.html` |
| Bundled at | `app/src/main/assets/index.html` (byte-identical) |

The SHA-256 of both is recorded in `SHA256SUMS.txt` beside this report.

## Signing

**No keystore was created here, deliberately.** A private key that passes through a chat
transcript is compromised on arrival. Generate it on your own machine:

```
./tools/make-keystore.sh          # or tools\make-keystore.bat on Windows
```

It writes `varugai-release.jks` (alias `varugai`, RSA 4096, 30 years), prints the certificate
SHA-256 for your records, and tells you how to base64-encode it for CI. The `.jks` is
git-ignored and must stay outside the repository.

Then either:

* **local build** — copy `keystore.properties.example` to `keystore.properties` and fill it in
  (both the `.jks` and that file are git-ignored), or
* **GitHub Actions** — create the four secrets listed in `SECRETS.md`.

If none are present the release build is left **unsigned** rather than silently falling back
to the debug key, so an unsigned artefact can never be mistaken for a release.

### Backing up the key

Losing it means you can never update the installed app again — every user must uninstall and
lose their register. Keep two offline copies in different physical places, plus the password
recorded separately from the file.

## Upgrading over the earlier build

`IN-PLACE UPGRADE NOT TESTABLE / SIGNATURE MISMATCH`

The previously distributed v13 APK was signed with a different key. Android will refuse to
install this over it. The correct sequence:

1. open the current app and **export a full JSON backup**;
2. confirm the backup file exists and opens;
3. only then uninstall the old VARUGAI;
4. install this release;
5. restore the backup and check the student count, a spot sample of marks, and the totals.

Data does not migrate by itself. Do not uninstall before step 2 has genuinely succeeded.

## Policy note

`REQUIRES POLICY VERIFICATION` — the default thresholds (75 / 65 / 50) follow MSU's published
affiliated-college regulation for the 2021–22 batch. Later MSU material refers to a 60% rule.
The thresholds are configurable per register and the active ruleset is recorded on every
statement; nothing has been silently changed in this conversion.
