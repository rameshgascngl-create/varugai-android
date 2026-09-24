# VARUGAI 16 — Phase 3 native attendance-grid implementation report

Phase 3 replaces the Grid placeholder with native Compose attendance entry backed directly by Room.

Implemented contract:
- one mark per student per hour;
- blank / Present / Absent / On Duty states;
- OD remains separately reportable and counts as attended;
- attendance denominator uses only working days explicitly completed by faculty;
- holidays/excluded days contribute zero;
- short days use configured teaching hours;
- student counts-from/counts-until dates exclude out-of-range hours;
- All Present, clear day, clear hour/column, complete/reopen, and one-step undo;
- date navigation, Today jump, active/all student filter, name/roll search, configurable date-window size;
- completion is blocked while any active-student cell is blank;
- edits to a completed day automatically reopen it;
- native LazyColumn rendering; no HTML/WebView grid.

Release exclusions remain unchanged: no CAMERA permission, photo evidence, OCR, scanned-attendance ingestion, INTERNET permission, or WebView renderer.

Phase 4 Summary must not begin until the Phase-3 CI gate passes architecture audit, Phase-2/Phase-3 source audits, unit tests, lint, debug compilation and APK renderer inspection.
