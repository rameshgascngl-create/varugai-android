#!/usr/bin/env bash
set -euo pipefail
fail(){ echo "FAIL: $*" >&2; exit 1; }
pass(){ echo "PASS: $*"; }

BUILD=app/build.gradle.kts
MANIFEST=app/src/main/AndroidManifest.xml
MAIN=app/src/main/java/com/gasczoology/varugai/MainActivity.kt
DB=app/src/main/java/com/gasczoology/varugai/data/db/VarugaiDatabase.kt
KEY=app/src/main/java/com/gasczoology/varugai/security/DatabaseKeyManager.kt
PIN=app/src/main/java/com/gasczoology/varugai/security/PinSecurity.kt
LOCK_VM=app/src/main/java/com/gasczoology/varugai/ui/lock/LockViewModel.kt
LOCK_UI=app/src/main/java/com/gasczoology/varugai/ui/lock/LockScreen.kt
README=README.md
PROGUARD=app/proguard-rules.pro
APP=app/src/main/java/com/gasczoology/varugai/VarugaiApplication.kt
RECOVERY=app/src/main/java/com/gasczoology/varugai/ui/recovery/DatabaseRecoveryScreen.kt
PREFS=app/src/main/java/com/gasczoology/varugai/data/preferences/VarugaiPreferences.kt
SETUP=app/src/main/java/com/gasczoology/varugai/ui/setup/SetupScreen.kt
EXPORT=app/src/main/java/com/gasczoology/varugai/ui/export/ExportScreen.kt

for f in "$BUILD" "$MANIFEST" "$MAIN" "$DB" "$KEY" "$PIN" "$LOCK_VM" "$LOCK_UI" "$README" "$PROGUARD" "$APP" "$RECOVERY" "$PREFS" "$SETUP" "$EXPORT"; do
  [[ -f "$f" ]] || fail "missing required file: $f"
done

grep -q 'create("release")' "$BUILD" || fail "release signingConfig missing"
grep -q 'VARUGAI_KEYSTORE_PATH' "$BUILD" || fail "external release keystore wiring missing"
grep -q 'isDebuggable = false' "$BUILD" || fail "release debuggable=false missing"
grep -q 'isMinifyEnabled = true' "$BUILD" || fail "R8 minification disabled"
grep -q 'isShrinkResources = true' "$BUILD" || fail "resource shrinking disabled"
grep -q '@androidx.room.Entity' "$PROGUARD" || fail "Room keep rule missing"
grep -q 'androidx.compose.runtime' "$PROGUARD" || fail "Compose keep rule missing"
grep -q '\$\$serializer' "$PROGUARD" || fail "serialization keep rule missing"
pass "release signing/R8 source configuration"

grep -q 'splits {' "$BUILD" || fail "ABI split configuration missing"
grep -q 'include("arm64-v8a", "armeabi-v7a")' "$BUILD" || fail "required handset ABIs not explicitly selected"
grep -q 'isUniversalApk = true' "$BUILD" || fail "universal fallback APK disabled"
pass "AAB/sideload ABI delivery configuration"


grep -q 'net.zetetic:sqlcipher-android:4.13.0' "$BUILD" || fail "modern SQLCipher dependency missing"
grep -q 'SupportOpenHelperFactory' "$DB" || fail "Room is not wired through SQLCipher"
grep -q 'AndroidKeyStore' "$KEY" || fail "database passphrase not protected by Android Keystore"
grep -q 'HmacSHA256' "$KEY" || fail "database passphrase is not derived by Keystore-backed HMAC"
grep -q 'DERIVATION_CONTEXT' "$KEY" || fail "database key derivation context missing"
pass "encrypted database source configuration"

! grep -RIn 'fallbackToDestructiveMigration' app/src/main/java >/dev/null || fail "destructive Room migration fallback present"
grep -q 'createAndVerify' "$DB" || fail "database open is not verified"
grep -q 'DatabaseOpenState.RecoveryRequired' "$APP" || fail "database recovery state missing"
grep -q 'Restore from a JSON backup' "$RECOVERY" || fail "database recovery primary action missing"
grep -q 'Delete local data and reset' "$RECOVERY" || fail "explicit destructive reset confirmation missing"
grep -q 'deleteMasterKey' "$KEY" || fail "explicit reset cannot rotate lost database key"
pass "unrecoverable database fails into explicit recovery rather than destructive recreation"

grep -q 'last_full_json_backup_' "$PREFS" || fail "per-register successful backup timestamp missing"
grep -q 'BackupStatusCard' "$SETUP" || fail "home backup-age warning missing"
grep -q 'BackupStatusCard' "$EXPORT" || fail "export backup-age warning missing"
grep -q 'only one place' app/src/main/java/com/gasczoology/varugai/ui/common/BackupHealth.kt || fail "14-day single-copy risk text missing"
grep -q 'BuildConfig.VERSION_NAME' "$EXPORT" || fail "About version is not sourced from BuildConfig"
pass "backup durability exposure and BuildConfig version identity"


grep -q 'LockScreen' "$MAIN" || fail "lock screen not wired at composition root"
grep -q 'ON_STOP' "$MAIN" || fail "background lock lifecycle hook missing"
grep -q 'ON_START' "$MAIN" || fail "resume lock lifecycle hook missing"
grep -q 'lockTimeoutSeconds' "$LOCK_VM" || fail "configurable lock timeout missing"
grep -q 'AndroidKeyStore' "$PIN" || fail "PIN verifier is not Keystore-protected"
grep -q 'FLAG_SECURE' "$MAIN" || fail "FLAG_SECURE missing"
pass "app lock and screen-capture protection"

if grep -RInE 'NotificationManagerCompat|AlarmManager|SCHEDULE_EXACT_ALARM|USE_EXACT_ALARM|RECEIVE_BOOT_COMPLETED' app/src/main/java app/src/main/AndroidManifest.xml >/dev/null; then
  fail "notification/alarm feature code remains although reminders are descoped"
fi
pass "notification and alarm features removed/descoped"

if grep -RInE 'androidx\.camera|CameraX|com\.google\.mlkit|BarcodeScanner|BarcodeScanning|BarcodeFormat|ACTION_IMAGE_CAPTURE|android\.permission\.CAMERA' app/src/main/java app/src/main/AndroidManifest.xml >/dev/null; then
  fail "camera/QR scan code detected; scan verification is import validation only"
fi
grep -q 'validateRows' app/src/main/java/com/gasczoology/varugai/data/importer/RosterFileParser.kt || fail "roster row validation missing"
grep -q 'Duplicate roll number' app/src/main/java/com/gasczoology/varugai/data/importer/RosterFileParser.kt || fail "duplicate-roll validation missing"
grep -q 'Roster size changes by' app/src/main/java/com/gasczoology/varugai/ui/roster/RosterScreen.kt || fail "roster-size mismatch not surfaced"
pass "scan-verification decision implemented as roster import validation"

grep -q 'debugImplementation("androidx.compose.ui:ui-tooling")' "$BUILD" || fail "Compose tooling not debug-scoped"
grep -q 'androidx.activity.ComponentActivity' "$MANIFEST" || fail "ComponentActivity merger override missing"
grep -q 'tools:node="remove"' "$MANIFEST" || fail "ComponentActivity remove override missing"
pass "manifest hygiene source configuration"

grep -q 'offline-only by design' "$README" || fail "offline-only sync decision not documented"
grep -q 'roster-import validation' "$README" || fail "scan decision not documented"
[[ ! -e app/src/main/res/xml/backup_rules.xml ]] || fail "backup_rules.xml should be absent with allowBackup=false"
[[ ! -e app/src/main/res/xml/data_extraction_rules.xml ]] || fail "data_extraction_rules.xml should be absent with allowBackup=false"
grep -q 'android:allowBackup="false"' "$MANIFEST" || fail "allowBackup must remain false"
grep -q 'android:screenOrientation="fullUser"' "$MANIFEST" || fail "fullUser orientation declaration missing"
pass "offline/backup/orientation decisions"

echo "REQUIRED CORRECTIONS SOURCE AUDIT: PASS"
