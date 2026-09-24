#!/usr/bin/env bash
set -euo pipefail
fail(){ echo "FAIL: $*" >&2; exit 1; }
pass(){ echo "PASS: $*"; }

./tools/audit-native-architecture.sh >/dev/null
./tools/audit-phase2-roster.sh >/dev/null
./tools/audit-phase3-grid.sh >/dev/null
./tools/audit-phase4-summary.sh >/dev/null
pass "Phase 1–4 gates still pass"

MAIN=app/src/main/java/com/gasczoology/varugai/MainActivity.kt
SCREEN=app/src/main/java/com/gasczoology/varugai/ui/export/ExportScreen.kt
VM=app/src/main/java/com/gasczoology/varugai/ui/export/ExportViewModel.kt
BACKUP=app/src/main/java/com/gasczoology/varugai/data/backup/BackupCodec.kt
WRITERS=app/src/main/java/com/gasczoology/varugai/data/export/NativeExportWriters.kt
PDF=app/src/main/java/com/gasczoology/varugai/data/export/AttendancePdfWriter.kt
REPO=app/src/main/java/com/gasczoology/varugai/data/repository/VarugaiRepository.kt

for f in "$SCREEN" "$VM" "$BACKUP" "$WRITERS" "$PDF"; do [[ -f "$f" ]] || fail "missing $f"; done
grep -q 'ExportScreen(' "$MAIN" || fail "Export route is still a placeholder"
! grep -q 'PhasePlaceholder("Export"' "$MAIN" || fail "Export placeholder remains"
pass "native Export route wired"

grep -q 'schema", 3' "$BACKUP" || fail "schema-3 native backup missing"
grep -q 'sha256' "$BACKUP" || fail "SHA-256 backup integrity missing"
grep -q 'LegacyChecksum' "$BACKUP" || fail "legacy FNV verification missing"
grep -q 'schema in 1..3' "$BACKUP" || fail "legacy schema compatibility gate missing"
pass "native and legacy backup integrity paths present"

grep -q 'restoreRegisterBundle' "$REPO" || fail "transactional restore operation missing"
grep -q 'db.withTransaction' "$REPO" || fail "Room transaction use missing"
grep -q 'deleteForRegister(targetRegisterId)' "$REPO" || fail "restore replacement cleanup missing"
grep -q 'Backup restored from' "$REPO" || fail "restore audit event missing"
pass "transaction-scoped restore path present"

grep -q 'CreateDocument' "$SCREEN" || fail "native SAF export missing"
grep -q 'OpenDocument' "$SCREEN" || fail "native SAF restore picker missing"
grep -q 'attendanceXlsx' "$WRITERS" || fail "XLSX export missing"
grep -q 'summaryCsv' "$WRITERS" || fail "summary CSV export missing"
grep -q 'auditCsv' "$WRITERS" || fail "audit CSV export missing"
grep -q 'PdfDocument' "$PDF" || fail "native PDF export missing"
pass "required native export formats present"

grep -q 'Automatic Android/cloud backup: disabled' "$SCREEN" || fail "storage/privacy status missing"
grep -q 'Restore replaces the current register only after validation and confirmation' "$SCREEN" || fail "restore warning missing"
pass "restore and privacy disclosures present"

[[ -f app/src/test/java/com/gasczoology/varugai/data/backup/BackupCodecTest.kt ]] || fail "backup codec tests missing"
pass "Phase 5 backup tests present"

echo "PHASE 5 EXPORT/BACKUP SOURCE AUDIT: PASS"
