# VARUGAI — Open-source notices

VARUGAI incorporates open-source software. This notice is a release checklist and does not replace the license texts distributed by the upstream projects.

| Component | Project / family | License family to verify in the exact distributed artifact |
|---|---|---|
| Kotlin | JetBrains Kotlin | Apache License 2.0 |
| AndroidX / Jetpack Compose | Android Open Source Project / AndroidX | Apache License 2.0 |
| Material 3 | AndroidX Material | Apache License 2.0 |
| Room | AndroidX Room | Apache License 2.0 |
| DataStore | AndroidX DataStore | Apache License 2.0 |
| Kotlin Coroutines | kotlinx.coroutines | Apache License 2.0 |
| kotlinx.serialization | Kotlin serialization | Apache License 2.0 |
| SQLCipher Android | Zetetic SQLCipher | Verify the license/notice shipped with the exact `net.zetetic:sqlcipher-android:4.13.0` artifact before production publication |

Before store submission, preserve all required copyright and attribution notices from the exact dependency artifacts and confirm that no additional transitive native library introduces an unmet notice obligation.
