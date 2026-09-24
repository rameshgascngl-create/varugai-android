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
- target version: `16.0.0`
- target versionCode: `16000`

The production release workflow is intentionally locked. It may be enabled only after the Phase-5 CI gate passes, the production signer is verified against the existing certificate, and physical-device QA is signed off.
