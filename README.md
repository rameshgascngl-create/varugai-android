# VARUGAI 15 — Android

An offline attendance register. The whole application is one HTML file bundled inside
the APK; the wrapper serves it locally and adds file export, camera capture and backup.

## What makes this different from the earlier build

| | v13 wrapper | this project |
|---|---|---|
| Where the app lives | fetched from a website | bundled in `assets/` |
| Works in flight mode | no | yes |
| `INTERNET` permission | required | **not declared at all** |
| Survives the host going away | no | yes |
| Storage origin | remote site | `https://appassets.androidplatform.net` |
| Android backup | disabled | enabled, includes the register |
| Excel export | blocked (`blob:` URLs rejected) | works, via the system file picker |
| Back button | disabled | navigates, then double-press to exit |
| Rotation | recreated the activity | handled, state preserved |

## Branch

Android work lives on `claude/varugai-android-release`. The Windows workflow is untouched;
the Android pipeline is a separate workflow file, `.github/workflows/android-release.yml`.

## Getting an APK without installing anything

1. Create a **private** repository on GitHub and push this folder to it.
2. Open the **Actions** tab. The `Build VARUGAI APK` workflow runs automatically.
3. When it finishes, download the `VARUGAI-apk` artifact. `app-debug.apk` is installable
   as-is; sideload it on the phone with "Install unknown apps" allowed for your browser
   or file manager.

Keep the repository private. It contains your roster-handling code, not student data,
but there is no reason to publish it.

## Building locally instead

Android Studio Koala or newer, then `./gradlew assembleDebug`. Output lands in
`app/build/outputs/apk/debug/`.

## Signing for real distribution

The debug APK is signed with a throwaway key. For an APK you hand to colleagues term
after term, generate one keystore and keep it safe:

```
keytool -genkey -v -keystore varugai.jks -keyalg RSA -keysize 2048 \
        -validity 10000 -alias varugai
```

Then add a `signingConfigs` block to `app/build.gradle.kts` and reference it from
`buildTypes.release`. **If you lose that keystore you can never update the app in
place again** — every user has to uninstall and lose their data. Back it up somewhere
that is not the phone.

## Replacing the old v13 app

The new APK is signed with a different key from your existing v13 build, so Android
will refuse to install it over the top. Before switching:

1. Open whatever you are using now and export a **full JSON backup**.
2. Uninstall the old VARUGAI.
3. Install this APK.
4. Restore the backup from the Export tab.

Data does not migrate by itself — the old build stored it against a website's origin,
not the app's.

## Updating the bundled app later

Replace `app/src/main/assets/index.html`, raise `versionCode` in
`app/build.gradle.kts` (it must only ever increase), and rebuild. The register itself
lives in WebView storage and is untouched by an update.

## Known limits

- `.xlsx` and `.docx` reading needs `DecompressionStream`, so a phone with a very old
  System WebView will fall back to CSV. Updating Android System WebView from the Play
  Store fixes it.
- The register is held in WebView local storage, roughly a 5 MB budget shared with any
  stored register photograph. Export a JSON backup at the end of every session. Android's
  automatic backup is a safety net, not a substitute.
- Nothing here makes the app the authoritative record. The paper register remains the
  document of record; this reconciles against it.
