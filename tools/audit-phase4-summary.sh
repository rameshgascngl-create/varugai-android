#!/usr/bin/env bash
set -euo pipefail
fail(){ echo "FAIL: $*" >&2; exit 1; }
pass(){ echo "PASS: $*"; }

./tools/audit-native-architecture.sh >/dev/null
./tools/audit-phase2-roster.sh >/dev/null
./tools/audit-phase3-grid.sh >/dev/null
pass "Phase 1–3 gates still pass"

MAIN=app/src/main/java/com/gasczoology/varugai/MainActivity.kt
SCREEN=app/src/main/java/com/gasczoology/varugai/ui/summary/SummaryScreen.kt
VM=app/src/main/java/com/gasczoology/varugai/ui/summary/SummaryViewModel.kt
CLASSIFIER=app/src/main/java/com/gasczoology/varugai/domain/attendance/EligibilityClassifier.kt

for f in "$SCREEN" "$VM" "$CLASSIFIER"; do [[ -f "$f" ]] || fail "missing $f"; done
grep -q 'SummaryScreen(' "$MAIN" || fail "Summary route is still a placeholder"
! grep -q 'PhasePlaceholder("Summary"' "$MAIN" || fail "Summary placeholder remains"
pass "native Summary route wired"

for token in ELIGIBLE CONDONATION_FEE CONDONATION_MEDICAL REPEAT PROVISIONAL; do
  grep -q "$token" "$CLASSIFIER" || fail "attendance category missing: $token"
done
grep -q 'pct >= register.passMark' "$CLASSIFIER" || fail "75/eligible boundary logic missing"
grep -q 'pct >= register.condonationFeeFloor' "$CLASSIFIER" || fail "65 boundary logic missing"
grep -q 'pct >= register.condonationMedicalFloor' "$CLASSIFIER" || fail "50 boundary logic missing"
grep -q 'minimumCountedHours' "$CLASSIFIER" || fail "minimum-hours provisional gate missing"
grep -q 'nearThreshold' "$CLASSIFIER" || fail "near-threshold annotation missing"
grep -q 'forecast' "$CLASSIFIER" || fail "forecast logic missing"
pass "Summary scientific/eligibility semantics present"

for token in Eligible Condonation Repeat "Near threshold"; do
  grep -q "$token" "$SCREEN" || fail "summary filter/control missing: $token"
done
pass "Summary filters present"

[[ -f app/src/test/java/com/gasczoology/varugai/domain/attendance/EligibilityClassifierTest.kt ]] || fail "eligibility boundary tests missing"
pass "Phase 4 boundary tests present"

echo "PHASE 4 SUMMARY SOURCE AUDIT: PASS"
