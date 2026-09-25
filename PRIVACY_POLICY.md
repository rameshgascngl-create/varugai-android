# VARUGAI Privacy Policy

**Effective date:** 25 September 2026  
**Applies to:** VARUGAI 16.0.2 (`com.gasczoology.varugai`)

VARUGAI is an offline attendance register and academic attendance manager intended for authorized faculty or institutional staff.

## Data stored locally

VARUGAI may store institution, department, faculty, course, class, semester and academic-year information; student names, roll numbers and register numbers; attendance marks; calendar and holiday information; notes; and audit records. These are personal or academic records even though they remain local to the device.

## Collection and sharing

The current version does not transmit attendance or student records to a developer server. It contains no advertising, analytics, behavioral tracking, cloud synchronization or crash-reporting SDK. It does not request Internet, camera, microphone, contacts, location, SMS or phone permissions.

Files leave VARUGAI only when the user explicitly exports, shares or restores them through Android's system file interfaces. CSV, XLSX and PDF exports are not encrypted by VARUGAI after they are handed to the destination application. Portable VARUGAI backups are encrypted.

## Local security

The local Room database uses SQLCipher. Database key material and PIN verification use Android Keystore-backed cryptographic material. Sensitive app screens are protected using Android's secure-window mechanism. Portable backups use authenticated encryption and require the VARUGAI recovery key.

## Recovery key

Record the recovery key separately from the phone. If both the device-held key and the separately recorded recovery key are lost, encrypted portable backups may be unrecoverable. VARUGAI cannot reconstruct a lost recovery key.

## Deletion

A register can be deleted from Setup, removing its locally stored roster, calendar, attendance and audit history. Uninstalling VARUGAI removes its local application data. Android automatic backup and device-transfer backup are disabled for VARUGAI data.

## Contact

Support and privacy questions:  
https://github.com/rameshgascngl-create/varugai-android/issues

If a future version introduces networking, analytics, advertising, cloud synchronization or materially different data handling, this policy and the store Data Safety declaration must be updated before release.
