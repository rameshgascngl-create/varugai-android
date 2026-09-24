# VARUGAI 16 — Phase 4 native Summary implementation report

Phase 4 replaces the Summary placeholder with native Room-backed attendance summaries.

Implemented:
- per-student Present, On Duty, Absent and counted-hour totals;
- OD remains separately displayed while counting as attended;
- precise percentage calculated from completed working hours only;
- no rounding before attendance standing classification;
- 75% eligible boundary;
- 65–<75% condonation — form + fee;
- 50–<65% condonation — form + fee + medical;
- <50% repeat semester;
- minimum-counted-hours provisional warning;
- near-threshold verification annotation that never changes the underlying category;
- filters for All / Eligible / Condonation / Repeat / Near threshold;
- name/roll/register-number search;
- forecast of hours needed to reach eligibility, or additional absence hours affordable while retaining the configured eligible threshold.

The production-release workflow remains locked. Phase 5 Export/Backup must not begin until Phase-4 CI passes.
