# VARUGAI 16.0.2 — Native Device QA Gate

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
- Select a weekday inside the generated range and confirm **Mark selected date as holiday** becomes enabled immediately after date selection; the holiday/festival name is optional.
- Mark it as a holiday with the name left blank and confirm the default label "Festival / holiday" is stored and the day is excluded from attendance.
- Mark another weekday as a named festival/holiday and confirm the custom name remains visible.
- On a day that already contains attendance, confirm holiday conversion is blocked with an instruction to clear that day's attendance first.
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
- Export XLSX, CSV, PDF and audit CSV; confirm the unencrypted-export privacy warning is visible.
- Open each exported file independently and confirm it contains no PIN, database key or recovery key.
- Create and record the VARUGAI recovery key, then export an encrypted backup.
- Restore that encrypted backup on the same device and compare roster, calendar, marks and totals.
- Perform replacement-device style recovery: uninstall/reinstall or use a second clean device, import the encrypted backup, enter the written recovery key, and compare the restored data.
- Enter a wrong recovery key and confirm restore is rejected without modifying current data.
- Tamper with/truncate an encrypted backup and confirm it is rejected with a generic safe error.
- Test one verified VARUGAI 15.x schema-2 backup.
- Test one older unencrypted VARUGAI 16 schema-3 backup.
- Confirm a checksum-modified backup is rejected without damaging current data.
- Confirm restore requires preview and confirmation.

## 7. Android UI robustness
- Test portrait and landscape repeatedly.
- Test gesture navigation and three-button navigation; bottom Setup · Roster · Grid · Summary · Export bar must remain visible.
- Test system font sizes 100%, 130%, 150% and 200%.
- Test the screen-reader-friendly selected-day attendance view with TalkBack; each cell must announce student, date, hour, state and action.
- Test on a small phone, large phone, 8-inch tablet and 10-inch tablet where available.
- Test split-screen/multi-window mode where the device supports it.
- Test keyboard opening/closing in Setup, Roster and search fields.
- Confirm Back dismisses dialogs/keyboard before leaving the current screen where appropriate.
- Confirm no clipping at status/navigation bars and no content hidden behind system UI.

## 8. Process death / restart
- Unlock the app, background it beyond the configured timeout and confirm it relocks.
- Force-stop the app and relaunch; protected attendance content must not appear before unlock.
- Rotate during PIN entry and while unlocked; the lock must not be bypassed.
- After a successful restore, kill and relaunch the process and verify the restored register remains intact.
- Reboot the device and verify the same persistence and lock behavior.

## 9. Stress / offline
- Test at least 150 students and 90 working days.
- Drag across the semester repeatedly and rapidly enter attendance for several days.
- Switch tabs repeatedly, rotate the device and background/foreground the app.
- Use Airplane mode throughout; all core functions must continue to work.
- No freeze, app close, ANR, lost marks or visibly desynchronised grid is acceptable.

## 10. Play / installation validation
- Install the exact signed APK produced by the audited commit.
- Perform an upgrade install over the previously deployed VARUGAI release and verify data preservation.
- Record Google Play Console pre-launch report results for the AAB.
- Record any accessibility, crash, ANR, security or compatibility findings and resolve blockers.

## Release decision
Only after every applicable item passes:
1. record device model, Android version and navigation mode;
2. record the tested commit SHA;
3. produce the signed APK/AAB with the existing production keystore;
4. verify package identity and signing-certificate SHA-256;
5. then merge/promote and resubmit to the store.


## Required security verification before any wider distribution

### Release signing
- Build only the release candidate with the existing VARUGAI production/upload keystore.
- Run `apksigner verify --verbose --print-certs <apk>`.
- PASS only if signer SHA-256 is `A41E9DE248E95594868AE5740A492D35D35950AF644CD7EF190C731649826B9B`.
- FAIL if the signer is Android Debug or if the release manifest is debuggable.

### SQLCipher database-at-rest check
- Use a debug/root-enabled test device or emulator solely for this verification.
- Create attendance data, close the app, then pull `/data/data/com.gasczoology.varugai/databases/varugai.db` (or the debug-suffixed package equivalent).
- Confirm the file header is not `SQLite format 3`.
- Attempt to open it with a plain SQLite browser/CLI without the key; PASS only if it fails as a normal SQLite database.
- Reopen VARUGAI and confirm the same data remains readable through the app.

### App lock
- Cold-start the app: no register/roster/grid content may appear before PIN setup/unlock.
- Configure each supported timeout at least once; retain one practical setting for final QA.
- Background the unlocked app for less than the timeout: returning may remain unlocked.
- Background it for longer than the timeout: returning must show the lock screen before any attendance data is visible.
- Kill the process and relaunch: the app must start locked.

### FLAG_SECURE
- Attempt a screenshot on Roster and Grid.
- Attempt screen recording if available.
- PASS only if Android blocks the capture or the captured content is blank/black.

### Manifest/R8
- Inspect the release manifest dump: `debuggable` must be false; `androidx.compose.ui.tooling.PreviewActivity` and `androidx.activity.ComponentActivity` must not be exported app components.
- Inspect release DEX strings: `data/repository/VarugaiRepository` must not remain as a readable app class path.
- Confirm `libsqlcipher.so` is packaged in the release APK.

### Scope decisions
- Notifications/reminders are not part of 16.0.0; no notification/alarm permission or app-owned scheduling code is expected.
- Scan verification is roster-import validation only; no camera/QR workflow is expected.
- Sync is offline-only manual export/import; no network sync is expected.
