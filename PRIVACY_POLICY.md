# VARUGAI Privacy Policy

**Effective date:** 30 September 2026  
**Applies to:** VARUGAI 16.0.4 (`com.gasczoology.varugai`)

VARUGAI is an offline Android attendance-register application for authorized college faculty and academic institutions.

## Services provided by VARUGAI

VARUGAI provides the following academic attendance-management services:

- creation and management of class/register details;
- student roster entry and import;
- period-wise attendance marking using Present, Absent and On Duty (OD);
- teaching-day, holiday and working-day calendar management;
- attendance totals, cumulative percentages and eligibility summaries;
- attendance reports and user-initiated export to CSV, XLSX and PDF;
- encrypted backup and restore of VARUGAI register data;
- PIN-protected access and local audit/history records.

VARUGAI does not provide social networking, messaging, advertising, e-commerce, location tracking, online payments or cloud attendance services.

## Data handled by the app

Depending on how the faculty user configures the app, VARUGAI may store institution, department, faculty, course, class, semester and academic-year details; student names; roll numbers; register numbers; attendance marks; teaching-calendar information; notes; and audit/history records.

These records are stored locally on the user's device for attendance-register management.

## Offline operation and data transmission

VARUGAI is designed to operate offline. The current version does not upload student rosters, attendance records or academic records to a developer-operated server and does not synchronize them to a cloud service.

The app contains no advertising, analytics, behavioral-tracking or crash-reporting SDK. It does not request Internet, camera, microphone, contacts, location, SMS or phone permissions.

## Export, sharing and backup

Files leave VARUGAI only when the user explicitly chooses an export, share, backup or restore action through Android's system file interfaces.

CSV, XLSX and PDF exports are not encrypted by VARUGAI after they are handed to the destination application selected by the user.

Portable VARUGAI backup files are encrypted and require the VARUGAI recovery key for restoration.

## Security

The local Room database uses SQLCipher. Database key material and PIN verification use Android Keystore-backed cryptographic material. Sensitive app screens use Android secure-window protection. Portable backups use authenticated encryption.

## Data retention and deletion

VARUGAI retains locally stored register information until the user deletes the register or uninstalls the application.

Deleting a register removes its locally stored roster, calendar, attendance and audit/history data. Uninstalling VARUGAI removes its local app data. Android automatic backup and device-transfer backup are disabled for VARUGAI data.

## User responsibility

VARUGAI is intended for authorized faculty or institutional users. Users are responsible for handling student information in accordance with their institution's policies and applicable privacy requirements.

## Developer / grievance contact

**Developer:** Ramesh Rajamoni  
**Support and privacy email:** rameshgascngl@gmail.com  
**Project support:** https://github.com/rameshgascngl-create/varugai-android/issues

For privacy, data-handling or grievance-related questions, users may contact the email address above.

## Changes to this policy

If a future version introduces networking, analytics, advertising, cloud synchronization or materially different data handling, this Privacy Policy and the corresponding app-store Data Safety declaration will be updated before release.
