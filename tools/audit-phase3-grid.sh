#!/usr/bin/env bash
set -euo pipefail
fail(){ echo "FAIL: $*" >&2; exit 1; }
pass(){ echo "PASS: $*"; }

./tools/audit-native-architecture.sh >/dev/null
./tools/audit-phase2-roster.sh >/dev/null
pass "Phase 1/2 gates still pass"

MAIN=app/src/main/java/com/gasczoology/varugai/MainActivity.kt
GRID=app/src/main/java/com/gasczoology/varugai/ui/grid/GridScreen.kt
VM=app/src/main/java/com/gasczoology/varugai/ui/grid/GridViewModel.kt
REPO=app/src/main/java/com/gasczoology/varugai/data/repository/VarugaiRepository.kt
DAO=app/src/main/java/com/gasczoology/varugai/data/db/Daos.kt
CALC=app/src/main/java/com/gasczoology/varugai/domain/attendance/AttendanceCalculator.kt

for f in "$GRID" "$VM" "$CALC"; do [[ -f "$f" ]] || fail "missing $f"; done
grep -q 'GridScreen(' "$MAIN" || fail "Grid route is still a placeholder"
! grep -q 'PhasePlaceholder("Grid"' "$MAIN" || fail "Grid placeholder remains"
pass "native Grid route wired"

grep -q 'observeForRegister' "$DAO" || fail "attendance observation missing"
grep -q 'deleteCell' "$DAO" || fail "blank-cell operation missing"
grep -q 'deleteHour' "$DAO" || fail "clear-hour operation missing"
grep -q 'deleteDay' "$DAO" || fail "clear-day operation missing"
pass "attendance DAO operations present"

for token in setAttendanceMark allPresent clearAttendanceDay clearAttendanceHour setDayComplete restoreDaySnapshot; do
  grep -q "$token" "$REPO" || fail "repository operation missing: $token"
done
pass "transactional attendance operations present"

grep -q 'dayCompletionCheck' "$CALC" || fail "completion invariant missing"
grep -q 'isActiveOn' "$CALC" || fail "later-admission/date-range logic missing"
grep -q 'odHours' "$CALC" || fail "OD accounting missing"
grep -q 'countedHours' "$CALC" || fail "completed-hour denominator missing"
pass "attendance calculation invariants present"

grep -q 'All Present' "$GRID" || fail "All Present UI missing"
grep -q 'Undo' "$GRID" || fail "Undo UI missing"
grep -q 'Reopen' "$GRID" || fail "reopen UI missing"
grep -q 'Today' "$GRID" || fail "Today navigation missing"
pass "required Grid controls present"

SETUP=app/src/main/java/com/gasczoology/varugai/ui/setup/SetupScreen.kt

grep -q 'DateDropdownPicker("Start date"' "$SETUP" || fail "DD/MM/YYYY start-date selector missing"
grep -q 'DateDropdownPicker("End date"' "$SETUP" || fail "DD/MM/YYYY end-date selector missing"
grep -q 'Total hours per day' "$SETUP" || fail "initial total-hours-per-day selector missing"
grep -q 'Mark selected date as holiday' "$SETUP" || fail "festival/weekday holiday selector missing"
grep -q 'Semester grid — student names stay on the left' "$GRID" || fail "left-name horizontal semester matrix missing"
grep -q 'horizontalScroll(matrixScroll)' "$GRID" || fail "shared longitudinal date scrolling missing"
pass "requested setup/calendar/matrix controls present"

[[ -f app/src/test/java/com/gasczoology/varugai/domain/attendance/AttendanceCalculatorTest.kt ]] || fail "attendance unit tests missing"
pass "Phase 3 unit tests present"

echo "PHASE 3 GRID SOURCE AUDIT: PASS"
