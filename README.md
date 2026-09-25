# VARUGAI 16 — Native Android migration

VARUGAI 16 replaces the previous bundled HTML/WebView renderer with a genuine native Android application.

## Native release branch

Branch: `release/varugai-native-v16`

Implemented workflows:
- **Setup** — register metadata, attendance thresholds, teaching calendar, holidays/short days/special working days.
- **Roster** — stable student IDs, add/edit/reorder, paste/import, CSV/TSV/TXT/XLSX/DOCX roster ingestion, XLSX roster export.
- **Grid** — native P/A/OD hourly attendance, All Present, clear hour/day, complete/reopen, undo, date navigation and active-student filtering.
- **Summary** — Present/OD/Absent/count hours, equivalent days, exact percentage classification, 75/65/50 boundaries, verify band and forecast.
- **Export** — XLSX workbook, summary CSV, audit CSV, native PDF statement, schema-3 JSON backup and legacy schema-1/2 JSON restore.

Architecture:
- Kotlin + Jetpack Compose + Material 3.
- Room is the authoritative attendance store; DataStore holds lightweight UI preferences only.
- No WebView, WebViewAssetLoader, bundled HTML/CSS/JS application renderer or JavaScript bridge.
- No INTERNET permission.
- No CAMERA permission, CameraX, live photo capture, photo evidence, OCR or scanned-attendance capture.
- Automatic Android/cloud backup disabled; user-controlled exports use the Storage Access Framework.

Identity:
- package: `com.gasczoology.varugai`
- target version: `16.0.1`
- target versionCode: `16001`

The production release workflow is intentionally locked. It may be enabled only after the Phase-5 CI gate passes, the production signer is verified against the existing certificate, and physical-device QA is signed off.


## Security and scope decisions for 16.0.1

- **Release signing:** production updates must use the existing VARUGAI production/upload key. Do not generate a replacement key for this package. CI rejects any release whose signer SHA-256 is not `A41E9DE248E95594868AE5740A492D35D35950AF644CD7EF190C731649826B9B`.
- **Release hardening:** the release build is non-debuggable, R8/minification and resource shrinking are mandatory, and production signing credentials are supplied only through local ignored properties or CI secrets.
- **Database at rest:** Room is backed by SQLCipher. The SQLCipher passphrase is derived at runtime by HMAC-SHA256 from a non-exportable Android Keystore key; no reusable database password is hardcoded or stored in app files.
- **App access lock:** cold start is PIN-gated. Returning from background after the user-selected timeout (15 s, 30 s, 1 min, 5 min, or 15 min) re-locks the app. The PIN verifier is protected by a non-exportable Android Keystore HMAC key.
- **Screen capture:** `FLAG_SECURE` is applied at the activity level so roster, grid, summary, and export data are not available to screenshots/screen recording.
- **Notifications/reminders:** not a VARUGAI 16.0.1 feature. There is no app-owned `NotificationManagerCompat`, `AlarmManager`, exact-alarm permission, boot receiver, or reminder workflow. Do not add notification/alarm permissions merely because dependency classes appear in a debug DEX.
- **Scan verification:** this means **roster-import validation**, not camera/QR roll call. CameraX, ML Kit barcode scanning, photo capture, QR attendance, OCR, and CAMERA permission are intentionally out of scope. Import validation rejects malformed/duplicate rows and previews roster-size/omission effects before commit; students with existing attendance cannot be silently removed.
- **Sync:** VARUGAI 16.0.1 is permanently **offline-only by design**. It does not communicate with the hosted web application. User-controlled JSON/XLSX/CSV/PDF export and JSON restore are the supported transfer/backup mechanism.
- **Android backup:** `allowBackup=false`; cloud/ADB automatic backup rule files are intentionally absent. Device migration is performed through explicit encrypted-at-rest local data plus user-controlled export/import.
- **Localisation:** 16.0.1 UI chrome remains English-only for this release. Tamil UI localisation is deferred rather than partially shipping an inconsistent bilingual surface.

