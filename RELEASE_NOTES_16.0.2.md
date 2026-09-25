# VARUGAI 16.0.2 release notes

- Added in-app About, Privacy and Help information.
- Added persistent PIN-attempt throttling with increasing temporary lockouts.
- Clear PIN-entry fields after failed verification.
- Added screen-reader-friendly selected-day attendance entry with explicit student/date/hour/state semantics.
- Added duplicate register-number validation for roster entry, import and backup restore.
- Hardened encrypted-backup errors so wrong-key, malformed and damaged-file failures do not expose diagnostic detail.
- Added Android 12+ data-extraction exclusions consistent with the no-auto-backup policy.
- Added privacy policy, Play Data Safety preparation, reviewer instructions and open-source notices.
- Added negative tests for malformed/truncated encrypted backups, duplicate register numbers and PIN throttling.
- Package name and Room schema remain unchanged.
