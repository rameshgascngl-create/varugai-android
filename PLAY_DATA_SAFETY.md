# VARUGAI 16.0.2 — Google Play Data Safety preparation

Package: `com.gasczoology.varugai`

## Current architecture findings

- The app stores student and academic attendance records locally on the user's device.
- The release manifest has no `INTERNET` permission.
- The dependency set contains no advertising, analytics, behavioral tracking, cloud-sync or crash-reporting SDK.
- Android automatic backup is disabled.
- Users can explicitly export CSV/XLSX/PDF files and encrypted VARUGAI backups using Android system file interfaces.

## Suggested Play Console answers for this exact build

These answers use Google Play's concept of *collection* as data transmitted off the device to the developer or a third party.

- Data collected by the developer/app service: **No**, for the current offline build.
- Data shared with third parties by the app: **No**, for the current offline build.
- Data stored locally on device: **Yes** — institution/course metadata, student identifiers, attendance, notes and audit history.
- User-initiated transfer: **Yes** — the user may deliberately export/share files to a destination they choose.
- Encrypted portable backup: **Yes** — VARUGAI backup files use authenticated encryption and a recovery key.
- CSV/XLSX/PDF exports: **Not encrypted by VARUGAI after export**; the destination application's handling applies.
- User deletion: **Yes** — individual registers can be deleted; uninstall removes app-local data.
- Ads: **No**.
- Analytics: **No**.
- Crash-reporting SDK: **No**.
- Cloud synchronization: **No**.

Re-check these answers whenever dependencies, permissions or networking behavior changes.
