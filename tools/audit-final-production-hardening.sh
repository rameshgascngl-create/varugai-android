#!/usr/bin/env bash
set -euo pipefail

fail(){ echo "FAIL: $*"; exit 1; }
pass(){ echo "PASS: $*"; }

GRADLE=app/build.gradle.kts
MANIFEST=app/src/main/AndroidManifest.xml
ABOUT=app/src/main/java/com/gasczoology/varugai/ui/about/AboutScreen.kt
LOCK=app/src/main/java/com/gasczoology/varugai/ui/lock/LockViewModel.kt
PREFS=app/src/main/java/com/gasczoology/varugai/data/preferences/VarugaiPreferences.kt
CRYPTO=app/src/main/java/com/gasczoology/varugai/data/backup/PortableBackupCrypto.kt
GRID=app/src/main/java/com/gasczoology/varugai/ui/grid/GridScreen.kt
ROSTER=app/src/main/java/com/gasczoology/varugai/data/importer/RosterFileParser.kt
BACKUP=app/src/main/java/com/gasczoology/varugai/data/backup/BackupCodec.kt

grep -q 'versionName = "16.0.2"' "$GRADLE" || fail "versionName is not 16.0.2"
grep -q 'versionCode = 16002' "$GRADLE" || fail "versionCode is not 16002"
grep -q 'applicationId = "com.gasczoology.varugai"' "$GRADLE" || fail "package changed"
pass "release identity"

grep -q 'android:allowBackup="false"' "$MANIFEST" || fail "allowBackup must remain false"
grep -q 'android:fullBackupContent="false"' "$MANIFEST" || fail "fullBackupContent must remain false"
grep -q 'android:dataExtractionRules="@xml/data_extraction_rules"' "$MANIFEST" || fail "Android 12+ extraction rules missing"
grep -q 'android:usesCleartextTraffic="false"' "$MANIFEST" || fail "cleartext traffic not disabled"
grep -q 'android:screenOrientation="fullUser"' "$MANIFEST" || fail "orientation support regressed"
! grep -q 'android.permission.INTERNET' "$MANIFEST" || fail "INTERNET permission added"
! grep -q 'android.permission.CAMERA' "$MANIFEST" || fail "CAMERA permission added"
pass "manifest privacy and orientation"

test -f docs/privacy-policy.html || fail "privacy policy missing"
test -f PLAY_DATA_SAFETY.md || fail "Data Safety preparation missing"
test -f OPEN_SOURCE_NOTICES.md || fail "open-source notices missing"
test -f PLAY_REVIEWER_GUIDE.md || fail "reviewer guide missing"
grep -q 'About & Privacy' "$ABOUT" || fail "in-app privacy screen missing"
grep -q 'does not synchronize' "$ABOUT" || fail "local-only disclosure missing"
pass "privacy/help documentation"

grep -q 'pinFailedAttempts' "$PREFS" || fail "persistent PIN failure count missing"
grep -q 'pinLockoutUntilEpochMs' "$PREFS" || fail "persistent PIN lockout deadline missing"
grep -q 'PinRateLimit.lockoutMillis' "$LOCK" || fail "PIN throttling not wired"
grep -q 'MessageDigest.isEqual' app/src/main/java/com/gasczoology/varugai/security/PinSecurity.kt || fail "constant-time PIN digest comparison missing"
pass "PIN security hardening"

grep -q 'Encrypted backup could not be opened' "$CRYPTO" || fail "generic encrypted-backup failure missing"
grep -q 'duplicate register numbers' "$BACKUP" || fail "backup duplicate register validation missing"
grep -q 'Duplicate register number' "$ROSTER" || fail "roster duplicate register validation missing"
pass "backup/import validation"

grep -q 'Accessible selected-day view' "$GRID" || fail "accessible attendance mode missing"
grep -q 'contentDescription = description' "$GRID" || fail "attendance-cell semantics missing"
pass "attendance accessibility path"

if grep -RInE 'firebase|analytics|crashlytics|admob|appsflyer|adjust-sdk' app/build.gradle.kts build.gradle.kts app/src/main >/dev/null; then
  fail "analytics/advertising/tracking token detected"
fi
pass "no analytics/advertising SDK token"

echo "FINAL PRODUCTION HARDENING SOURCE AUDIT: PASS"
