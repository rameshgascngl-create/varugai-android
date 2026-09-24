# VARUGAI 16 — Phase 5 native Export / Backup implementation report

Phase 5 replaces the Export placeholder with native Android file operations. The primary application remains Kotlin/Compose/Room and contains no HTML/WebView renderer.

Implemented:
- XLSX attendance workbook with Summary, Calendar, Attendance and Audit sheets;
- CSV attendance summary;
- CSV audit/correction log;
- native Android PDF attendance statement using PdfDocument;
- schema-3 JSON backup with SHA-256 integrity checking;
- schema-1/2 legacy VARUGAI backup import with JavaScript-compatible FNV-1a verification;
- explicit acceptance of legacy backups whose stale renderer metadata says appVersion 15.1.0;
- system Storage Access Framework / Activity Result APIs for create/open document;
- no broad storage permission;
- restore preview and explicit confirmation;
- transaction-scoped restore into the currently selected register, so a failed restore leaves the old Room state intact;
- native summary recomputation after restore;
- visible storage/privacy status: app-private Room data, automatic Android backup disabled, no network sync;
- no CAMERA permission, CameraX, photographic evidence, OCR or scanned-attendance capture.

The production release workflow remains locked. After this phase passes CI, the next gate is release signing verification followed by physical-device QA. No store APK should be submitted before those gates pass.
