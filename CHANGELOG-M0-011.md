# Changelog — M0-011 World Data Validation

日期：2026-11-06

## Added

- Added `WorldDataValidator`, a read-only consistency checker for loaded world SavedData.
- Added OP-only `/empires debug validate [page]` command with 12 diagnostics per page.
- Added checks for faction capital references, settlement ownership and building reverse indexes.
- Added citizen owner/home references, duplicate persistent entity UUID links and faction population cache consistency checks.
- Added building owner/settlement/dimension references, blueprint ID/version/manifest checksum/rotation validation, placement cursor bounds and same-dimension footprint overlap checks.
- Replaced the obsolete root README with current project status and actual Forge 1.20.1 technical baseline.
- Updated M0 roadmap and Chinese acceptance steps.

## Safety

- No SavedData schema change.
- No automatic repair or NBT rewrite.
- No chunk loading, block reads/writes, force-load tickets, or live entity scans.
- Does not treat an entity link whose chunk is unloaded as a missing citizen.
- Physical structure JSON checksum validation remains in the M0-010 template loader.

## Verification

- Source and documentation committed to GitHub `main`.
- Local `gradlew.bat build`: NOT RUN by the patch author.
- Forge client command test: NOT RUN by the patch author.
- Dedicated Server test: NOT RUN by the patch author.
- Automated damaged-save fixture tests: NOT RUN; repository currently has no `src/test` tree.
