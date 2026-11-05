# Implementation context — M0-009 Blueprint / BuildingRecord

Target baseline: `malred/Minecraft_Empires` main commit `eb6a4562eae22f435ae36a86c6ac3dfbab49f6a1`.
Target runtime: Minecraft 1.20.1 / Forge 47.4.28 / Java 17.

Implement only the logical blueprint catalog and persistent BuildingRecord milestone. Read `AGENTS.md`, README, GDD, ARCHITECTURE, TECHNICAL SPEC, DATA CONTRACTS, roadmap, blueprint/construction system spec and acceptance matrix first.

Constraints:
- Preserve the existing M0-008 scheduler and diagnostic command.
- Reuse the Overworld EmpiresWorldData and simulation clock; migrate schema 1/2 to schema 3 without changing faction, settlement, citizen, resources or simulationClock values.
- Unknown future schema or corrupt cross-references must remain on the existing read-only raw-NBT preservation path.
- Blueprint definitions are immutable versioned content, not building instances.
- Building records are authoritative logical records. Entities/blocks are not the source of identity.
- M0-009 cannot load/query chunks, query blocks, place blocks, reserve/debit materials, create worker jobs, or simulate production.
- All mutations go through CivilizationService / CivilizationRepository / EmpiresWorldData.
- Settlement reverse index and BuildingRecord insert must be consistent.
- Keep debug commands OP-only and use ResourceLocationArgument for namespace-qualified IDs.
- Include tests that regression-check M0-008: clock advances while remote settlement chunks remain unloaded, test wood changes in-session, no force-load, no offline catch-up.

Definition of Done: build; migration fixture checks for schema 1 and 2; plan/list/show building; duplicate 3D logical footprint rejected; no resource deduction or block change; save/reload preserves IDs; M0-008 regression passes. Never claim user-side client/server verification until actually run.
