# VARUGAI 16 — Native Device QA Gate

This checklist is release-blocking. Perform it on at least one real Android phone before merging the native branch to `main`.

## 1. Install / persistence
- Install the latest debug APK fresh.
- Confirm app opens without network access and without requesting camera/storage/network permissions.
- Create one class/register, close the app, remove it from Recents, reopen it and confirm data persists.
- Reboot the phone and confirm the same register reopens correctly.

## 2. Setup and calendar
- Confirm Start date and End date are selected with Day / Month / Year dropdowns and displayed as DD/MM/YYYY.
- Confirm an invalid end-before-start range cannot generate a calendar.
- Choose Total hours per day in Setup from 1–8 and confirm newly generated working days inherit it.
- Generate a semester calendar spanning at least 60 calendar days.
- Mark a weekday inside the range as a named festival/holiday; confirm the name remains visible and the day is excluded from attendance.
- Convert one weekend date inside the range into a special working day and choose different hours.
- Confirm a date outside the semester cannot be added as a special working day.
- Confirm Semester plan totals update after holiday/working-day changes.

## 3. Roster
- Add, edit, reorder and remove an unmarked student.
- Confirm removal requires confirmation.
- Import CSV/TXT, XLSX and DOCX rosters.
- Confirm duplicate roll numbers are rejected.
- Change a student's roll number after attendance exists and verify attendance remains attached to that student.
- Confirm a roster replacement cannot silently remove a student who already has attendance.

## 4. Attendance ledger
- In portrait, confirm student name/roll stays on the left while dates drag horizontally to the right.
- Repeat in landscape.
- Scroll across the full semester and confirm the same horizontal position is maintained between header and student rows.
- Confirm headers use DD/MM and weekday labels.
- Confirm holidays display HOL and the holiday/festival name.
- Confirm P, A and OD are visually distinct and OD is displayed as "OD", not "O".
- Use the intended fast workflow: All Present → change exceptions to A/OD → Complete day.
- Confirm tap cycle is blank → P → A → OD → blank.
- Confirm a holiday cannot accept attendance.
- Confirm a student outside Counts from / Counts until shows Not counted and cannot be marked.
- Confirm a completed day contributes to totals; an open day does not.
- Confirm Reopen, Clear hour, Clear day and Undo behave correctly.
- Test a 1-hour day and an 8-hour day.

## 5. Summary / attendance rules
- Verify exact boundary cases around 50%, 65%, 75% and the configured verification band.
- Verify OD counts as attended hours but is reported separately.
- Verify holiday hours are excluded.
- Verify short-day hours use the actual configured hours.
- Verify later-admission students do not receive denominator hours before their Counts from date.

## 6. Export / backup
- Export XLSX, CSV, PDF and JSON.
- Open each exported file independently.
- Restore a VARUGAI 16 JSON backup and compare roster, calendar, marks and totals.
- Test one verified VARUGAI 15.x schema-2 backup.
- Confirm a corrupted/edited backup is rejected without damaging current data.
- Confirm restore requires confirmation.

## 7. Android UI robustness
- Test portrait and landscape repeatedly.
- Test gesture navigation and three-button navigation; bottom Setup · Roster · Grid · Summary · Export bar must remain visible.
- Test system font sizes 100%, 130% and 150%.
- Test keyboard opening/closing in Setup, Roster and search fields.
- Confirm Back dismisses dialogs/keyboard before leaving the current screen where appropriate.
- Confirm no clipping at status/navigation bars and no content hidden behind system UI.

## 8. Stress / offline
- Test at least 150 students and 90 working days.
- Drag across the semester repeatedly and rapidly enter attendance for several days.
- Switch tabs repeatedly, rotate the device and background/foreground the app.
- Use Airplane mode throughout; all core functions must continue to work.
- No freeze, app close, ANR, lost marks or visibly desynchronised grid is acceptable.

## Release decision
Only after every applicable item passes:
1. record device model, Android version and navigation mode;
2. record the tested commit SHA;
3. produce the signed APK/AAB with the existing production keystore;
4. verify package identity and signing-certificate SHA-256;
5. then merge/promote and resubmit to the store.
