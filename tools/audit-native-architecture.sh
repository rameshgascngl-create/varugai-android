#!/usr/bin/env bash
set -euo pipefail

fail() { echo "FAIL: $*" >&2; exit 1; }
pass() { echo "PASS: $*"; }

MANIFEST="app/src/main/AndroidManifest.xml"
BUILD="app/build.gradle.kts"
SRC="app/src/main"

[[ -f "$MANIFEST" ]] || fail "manifest missing"
[[ -f "$BUILD" ]] || fail "app build file missing"

grep -q 'applicationId = "com.gasczoology.varugai"' "$BUILD" || fail "package identity changed"
grep -q 'versionName = "16.0.2"' "$BUILD" || fail "versionName is not 16.0.2"
grep -q 'versionCode = 16002' "$BUILD" || fail "versionCode is not 16002"
grep -q 'compileSdk = 36' "$BUILD" || fail "compileSdk is not 36"
grep -q 'targetSdk = 36' "$BUILD" || fail "targetSdk is not 36"
grep -q 'isCoreLibraryDesugaringEnabled = true' "$BUILD" || fail "java.time desugaring not enabled for minSdk 24"
pass "identity/version/API baseline"

if find "$SRC" -type f \( -name '*.html' -o -name '*.js' -o -name '*.css' \) | grep -q .; then
  fail "HTML/CSS/JS application renderer remains under app/src/main"
fi
pass "no packaged HTML/CSS/JS renderer"

if grep -RInE 'android\.webkit\.WebView|WebViewAssetLoader|WebViewClient|WebChromeClient|JavascriptInterface|appassets\.androidplatform\.net|VarugaiAndroid|VarugaiNative' "$SRC" "$BUILD" app/proguard-rules.pro >/dev/null; then
  fail "legacy WebView architecture reference remains"
fi
if grep -q 'androidx.webkit' "$BUILD"; then fail "androidx.webkit dependency remains"; fi
pass "no app-owned WebView architecture"

if grep -qE 'android.permission.(CAMERA|INTERNET)' "$MANIFEST"; then
  fail "forbidden CAMERA or INTERNET permission declared"
fi
if grep -RInE 'CameraX|camera2|ACTION_IMAGE_CAPTURE|capture="environment"|androidx\.camera' "$SRC" "$BUILD" >/dev/null; then
  fail "camera/photo capture pathway detected"
fi
pass "camera/photo capture pathway absent"

grep -q 'android:allowBackup="false"' "$MANIFEST" || fail "automatic Android backup is not disabled"
pass "automatic cloud backup disabled"

grep -q 'setContent' app/src/main/java/com/gasczoology/varugai/MainActivity.kt || fail "MainActivity is not Compose-native"
grep -q 'Room.databaseBuilder' app/src/main/java/com/gasczoology/varugai/data/db/VarugaiDatabase.kt || fail "Room database missing"
pass "Compose entry point and Room persistence present"

echo "NATIVE ARCHITECTURE AUDIT: PASS"
