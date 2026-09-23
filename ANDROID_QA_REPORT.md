# VARUGAI — Android QA report

## Evidence basis

Everything below marked **verified** was executed in this environment. Everything marked
**untested** was not, because there is no Android SDK, emulator or device here. No device
QA has been performed. `node tests/attendance.test.js` is the only test that actually ran.

## Automated attendance tests — verified, 18/18

| Case | Result |
|---|---|
| A — 90 planned days, 20 completed, student present throughout | **100%**, denominator 100 hours, not 22.22% |
| B — 45 present, 5 absent, 50 counted | **90%** |
| C — On Duty counts as attended, tracked separately | pass |
| D — a declared holiday adds 0 hours to the denominator | pass |
| E — hours before admission excluded; late student 90%, full-term student 100% | pass |
| G — intact backup verifies; a tampered backup is rejected | pass |
| Threshold boundaries 49.99 / 50 / 64.99 / 65 / 74.99 / 75 on precise values | pass |
| Verification band annotates rather than reclassifying (78% = Eligible · verify) | pass |

Case A is the release blocker in §6. It passes.

## Static verification — verified

| Check | Result |
|---|---|
| Bundled asset identical to canonical HTML | SHA-256 match |
| Merged manifest permissions | none declared |
| INTERNET permission | absent |
| Camera / FileProvider / storage permissions | absent |
| Remote URLs, CDNs, fonts, analytics in the page | none |
| `WebViewAssetLoader` local origin | configured |
| `setWebContentsDebuggingEnabled(BuildConfig.DEBUG)` | present |
| `allowFileAccess=false`, `allowContentAccess=false`, `MIXED_CONTENT_NEVER_ALLOW` | present |
| Navigation restricted to `appassets.androidplatform.net` | present |
| Bridge surface | 5 methods, extension allow-list, 24 MB payload cap |
| Launcher icons | 5 legacy densities + round + adaptive |
| Signing falls back to unsigned, never to the debug key | verified in `build.gradle.kts` |
| `.gitignore` covers `*.jks`, `*.keystore`, `keystore.properties` | present |

## Device QA — untested, you must run this

Cold launch · airplane-mode launch · portrait · landscape · Back from each tab ·
Back from the statement overlay · create register · duplicate register · switch register ·
generate calendar · holiday · short day · special Saturday · roster import (CSV, XLSX, DOCX) ·
mark P / A / OD · All Present · Clear column · Complete · Reopen · Undo · summary filters ·
integrity checks · correction log · Excel export · CSV export · JSON backup · JSON restore ·
tampered-backup rejection · statement build · print · app restart · phone restart.

Two that most often fail in a WebView and deserve deliberate attention:

1. **Open the file picker, cancel it, open it again.** If the second attempt does nothing,
   the null-on-cancel path has regressed.
2. **Mark attendance, kill the app from the task switcher, reopen.** The marks must be there.

## Performance — untested

The heavy case to try is 100–150 students across 90–120 days with several registers. The grid
pages the DOM by default (25 days), so the whole-semester view is the one to watch on a
low-end device.

## Known limitations

* `.xlsx` and `.docx` import need `DecompressionStream`. A very old System WebView will report
  the limitation and point to CSV rather than importing partially.
* All registers share the WebView storage budget of roughly 5 MB. The Export tab shows usage
  and warns above 80%. IndexedDB migration is the recommended next piece of work.
* The paper register remains the document of record. This application reconciles against it.
