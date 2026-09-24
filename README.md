# VARUGAI 16 — Native Android migration

VARUGAI 16 replaces the previous bundled HTML/WebView renderer with a native Android application.

## Current branch gate

Branch: `release/varugai-native-v16`

Implemented at the Phase-2 gate:
- Kotlin + Jetpack Compose + Material 3 UI
- Room local database and DataStore preferences
- Native Setup workflow
- Native Roster workflow with stable student IDs
- CSV/TSV/TXT/XLSX/DOCX roster import
- Native XLSX roster export through Android Storage Access Framework
- attendance-history protection when roll numbers change or imported rosters omit existing students
- no WebView, WebViewAssetLoader, HTML/CSS/JS application renderer
- no INTERNET permission
- no CAMERA permission, CameraX, photo evidence, OCR, or scanned-attendance capture

Grid, Summary and Export are deliberately held behind later audited phases. Production release is locked until all phases and physical-device QA pass.

Package identity remains `com.gasczoology.varugai`; target release version is `16.0.0` / `16000`.
