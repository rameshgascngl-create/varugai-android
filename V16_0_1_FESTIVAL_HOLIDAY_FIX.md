# VARUGAI 16.0.1 — festival-holiday control correction

Device QA found that the festival-holiday action remained disabled after date selection.

## Root cause

The Compose button was enabled only when the selected date existed in the generated semester calendar **and** the holiday/festival name field was non-blank. The descriptive name should not have been a prerequisite for enabling the date action.

## Correction

- The holiday button enables as soon as the selected date exists in the generated semester calendar.
- Holiday/festival name is explicitly optional.
- A blank name is stored as `Festival / holiday`.
- A dedicated repository `markHoliday()` action updates the current Room calendar row.
- A date containing attendance cannot be converted to a holiday until that day's attendance is cleared, preventing silent denominator changes.
- Recurring weekday-to-holiday conversion receives the same attendance-protection guard.
- Version advanced to 16.0.1 / 16001 for unambiguous device-upgrade QA.

Release remains blocked until CI and physical-device verification of this correction pass.