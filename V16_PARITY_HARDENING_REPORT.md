# VARUGAI 16 parity hardening gate

Base: `2b832f5f908bad51b54230aaccb3293a1491a32a`

This branch treats VARUGAI 16 as a complete architectural replacement. No behavioural assurance is inherited from the WebView implementation merely because the application ID or version lineage is continuous.

## Added release evidence

- Canonical synthetic VARUGAI 15 schema-2 backup generated from the actual v15 export contract.
- JavaScript-compatible FNV-1a checksum gate: `c07a00d3`.
- 90 planned working days with only 20 completed days; full-term denominator must be exactly 100 hours, not 450.
- Exact 75 / 65 / 50 classification boundaries and below-boundary controls.
- OD is attended but separately counted.
- Inclusive admission/start and attendance-end windows.
- PROVISIONAL behaviour when counted hours are below the configured minimum.
- Tamil roster text survives the legacy import.
- Stable `sid` remains the attendance identity even when roll/register-number display fields change.

## CI policy

The phase workflow now runs `LegacyMigrationGoldenTest` as an explicit named gate before the complete unit-test suite. The release-candidate workflow contains the same named gate so migration parity is visible independently of the general test task.

## Still outside this gate

This fixture is synthetic, not a recovered real classroom register. Device-level SQLCipher recovery, PIN/resume timeout, rotation/process death, PDF/XLSX visual inspection, and encrypted portable-backup design remain separate release gates. Android Auto Backup remains disabled.
