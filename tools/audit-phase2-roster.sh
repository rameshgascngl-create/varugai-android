#!/usr/bin/env bash
set -euo pipefail
fail(){ echo "FAIL: $*" >&2; exit 1; }
pass(){ echo "PASS: $*"; }

./tools/audit-native-architecture.sh >/dev/null
pass "native architecture gate still passes"

MAIN=app/src/main/java/com/gasczoology/varugai/MainActivity.kt
ROSTER=app/src/main/java/com/gasczoology/varugai/ui/roster/RosterScreen.kt
VM=app/src/main/java/com/gasczoology/varugai/ui/roster/RosterViewModel.kt
REPO=app/src/main/java/com/gasczoology/varugai/data/repository/VarugaiRepository.kt
DAO=app/src/main/java/com/gasczoology/varugai/data/db/Daos.kt
PARSER=app/src/main/java/com/gasczoology/varugai/data/importer/RosterFileParser.kt
XLSX=app/src/main/java/com/gasczoology/varugai/data/export/RosterXlsxExporter.kt
MANIFEST=app/src/main/AndroidManifest.xml

grep -q 'RosterScreen(' "$MAIN" || fail "Roster route is still a placeholder"
! grep -q 'PhasePlaceholder("Roster"' "$MAIN" || fail "Roster placeholder remains"
pass "native Roster route wired"

for f in "$ROSTER" "$VM" "$PARSER" "$XLSX"; do [[ -f "$f" ]] || fail "missing $f"; done
pass "roster UI/view-model/import/export sources present"

if grep -q 'OnConflictStrategy.REPLACE' "$DAO"; then
  fail "INSERT OR REPLACE remains in DAO and could cascade-delete child attendance data"
fi
grep -q '@Upsert' "$DAO" || fail "safe Room upsert missing"
pass "Room parent/roster writes avoid REPLACE cascade semantics"

grep -q 'attendanceEndDate' app/src/main/java/com/gasczoology/varugai/data/db/Entities.kt || fail "counts-until field missing"
grep -q 'Migration(1, 2)' app/src/main/java/com/gasczoology/varugai/data/db/VarugaiDatabase.kt || fail "Room 1→2 migration missing"
pass "roster date-range schema and migration present"

grep -q 'countForStudent' "$DAO" || fail "attendance-aware delete guard missing"
grep -q 'getStudentIdsWithAttendance' "$DAO" || fail "attendance lookup for roster-import guard missing"
grep -q 'getStudentIdsWithAttendance' "$REPO" || fail "repository does not check attendance before roster replacement"
grep -q 'require(!plan.isBlocked)' "$REPO" || fail "blocked import is not enforced at commit"
grep -q 'omittedWithAttendance' app/src/main/java/com/gasczoology/varugai/domain/roster/RosterModels.kt || fail "protected-omission model missing"
grep -q 'stable student ID' "$REPO" || fail "roll-change audit trail missing"
pass "attendance-history protection present"

grep -q 'parseXlsx' "$PARSER" || fail "XLSX import missing"
grep -q 'parseDocx' "$PARSER" || fail "DOCX import missing"
grep -q 'parseDelimited' "$PARSER" || fail "CSV/TSV/TXT import missing"
grep -q 'OpenDocument' "$ROSTER" || fail "Storage Access Framework roster import missing"
grep -q 'CreateDocument' "$ROSTER" || fail "Storage Access Framework roster export missing"
pass "native roster file import/export pathways present"

if grep -qE '<uses-permission[^>]+android.permission.(READ_EXTERNAL_STORAGE|WRITE_EXTERNAL_STORAGE|MANAGE_EXTERNAL_STORAGE|CAMERA|INTERNET)' "$MANIFEST"; then
  fail "forbidden broad storage/camera/network permission present"
fi
pass "no broad storage, camera, or network permission"

[[ -f app/src/test/java/com/gasczoology/varugai/data/importer/RosterFileParserTest.kt ]] || fail "roster parser tests missing"
pass "roster parser unit tests present"

echo "PHASE 2 ROSTER SOURCE AUDIT: PASS"
