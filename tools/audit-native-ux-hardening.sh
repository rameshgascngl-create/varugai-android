#!/usr/bin/env bash
set -euo pipefail
fail(){ echo "FAIL: $*" >&2; exit 1; }
pass(){ echo "PASS: $*"; }

SETUP=app/src/main/java/com/gasczoology/varugai/ui/setup/SetupScreen.kt
GRID=app/src/main/java/com/gasczoology/varugai/ui/grid/GridScreen.kt
ROSTER=app/src/main/java/com/gasczoology/varugai/ui/roster/RosterScreen.kt
DATES=app/src/main/java/com/gasczoology/varugai/ui/common/DateFormatting.kt
REPO=app/src/main/java/com/gasczoology/varugai/data/repository/VarugaiRepository.kt
QA=DEVICE_QA_NATIVE_V16.md

for f in "$SETUP" "$GRID" "$ROSTER" "$DATES" "$REPO" "$QA"; do [[ -f "$f" ]] || fail "missing $f"; done

grep -q 'DateDropdownPicker("Start date"' "$SETUP" || fail "start date dropdown missing"
grep -q 'DateDropdownPicker("End date"' "$SETUP" || fail "end date dropdown missing"
grep -q 'Total hours per day' "$SETUP" || fail "hours-per-day selector missing"
grep -q 'Mark selected date as holiday' "$SETUP" || fail "festival holiday action missing"
grep -q 'Semester plan' "$SETUP" || fail "semester plan summary missing"
grep -q 'Special working date must be inside the selected semester' "$REPO" || fail "semester-bound working-day guard missing"
pass "setup/calendar hardening"

grep -q 'Semester grid — student names stay on the left' "$GRID" || fail "frozen-name ledger guidance missing"
grep -q 'horizontalScroll(matrixScroll)' "$GRID" || fail "shared longitudinal scroll missing"
grep -q 'StatusLegend("OD", "On Duty")' "$GRID" || fail "OD legend missing"
grep -q 'displayStatus(status)' "$GRID" || fail "OD display mapping missing"
grep -q 'Recommended entry: All Present' "$GRID" || fail "fast attendance workflow guidance missing"
pass "ledger usability hardening"

grep -q 'Delete this register?' "$SETUP" || fail "register deletion confirmation missing"
grep -q 'Remove student?' "$ROSTER" || fail "student removal confirmation missing"
pass "destructive-action confirmation"

grep -q 'dd/MM/yyyy' "$DATES" || fail "DD/MM/YYYY formatter missing"
[[ -f app/src/test/java/com/gasczoology/varugai/ui/common/DateFormattingTest.kt ]] || fail "date formatting tests missing"
grep -q '150 students and 90 working days' "$QA" || fail "stress device-QA case missing"
grep -q 'three-button navigation' "$QA" || fail "system-navigation QA case missing"
pass "display/date/device QA coverage"

echo "NATIVE UX HARDENING AUDIT: PASS"
