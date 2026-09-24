# VARUGAI 16 — Phase 2 native roster implementation report

Phase 2 implements the Roster workflow natively.

Implemented:
- ordered roster with stable `sid` identity
- roll number, optional register number, student name, counts-from and counts-until dates
- add/edit/remove/reorder operations
- CSV/TSV/TXT/XLSX/DOCX import and paste workflow
- duplicate/malformed-row rejection
- roll corrections retain attendance because marks are keyed by stable student ID
- imported-roster omission is blocked when the omitted student already has attendance
- native XLSX roster export through Storage Access Framework
- Room parent writes use `@Upsert`, avoiding destructive INSERT OR REPLACE cascade behaviour
- Room schema v2 adds `attendanceEndDate`

Release-scope exclusions remain enforced: no WebView/HTML renderer, no INTERNET permission, no CAMERA permission, no CameraX/photo evidence/OCR/scanned-attendance capture.

Phase 3 Grid must not begin until the GitHub native phase gate compiles and passes tests/lint/architecture audit.
