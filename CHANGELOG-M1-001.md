# Changelog — M1-001 Headquarters Content

日期：2026-11-06

## Added

- Registered `empires:headquarters_core` as a managed structural marker block.
- Registered `empires:headquarters_blueprint` as a single-stack blueprint selection item in Tools & Utilities.
- Added the official `empires:headquarters_hall` blueprint definition: 7×8×7, 204 block entries, 800 initial work points.
- Added a structure with a stone-brick foundation, spruce structure, glass windows, stepped roof, and a dedicated core marker.
- Added en_us / zh_cn localization, block model/state, item model, and a loot table for the core marker.
- Added GameTest `headquartersContentIsRegisteredAndTemplateValid`.

## Safety / scope

- No SavedData schema changes.
- Existing saves and existing BuildingRecords remain unchanged.
- The headquarters core has no BlockItem; it cannot be manually placed outside the managed blueprint path.
- Blueprint item is registered, but user-facing placement flow is not enabled yet.
- Capital uniqueness, terrain/territory prechecks, material escrow and construction are not implemented by M1-001.

## Verification

- Source/resources committed to GitHub `main`.
- Local `gradlew.bat build`: NOT RUN after this change.
- Local `gradlew.bat runGameTestServer`: NOT RUN after this change.
- Creative-tab visual check: NOT RUN.
