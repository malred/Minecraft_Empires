package dev.minecraftempires.common.building;

import dev.minecraftempires.MinecraftEmpires;
import dev.minecraftempires.common.EmpiresConfig;
import dev.minecraftempires.common.civilization.CivilizationService;
import dev.minecraftempires.common.civilization.EmpiresWorldData;
import dev.minecraftempires.common.civilization.FactionRecord;
import dev.minecraftempires.common.civilization.FactionStatus;
import dev.minecraftempires.common.civilization.SettlementRecord;
import dev.minecraftempires.common.civilization.SettlementStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;

import java.util.Optional;
import java.util.UUID;

/** Server-authoritative, loaded-chunk-only adapter that materializes a validated blueprint template. */
public final class BuildingPlacementService {
    private static final int SET_BLOCK_FLAGS = 3;

    private BuildingPlacementService() {
    }

    /**
     * Validates a physical build site and only then creates its logical PLANNED record.
     * The expected faction ID is supplied by the server-side caller as the ownership context;
     * this method never trusts a client-provided faction ID.
     */
    public static PlanResult planBuilding(
            final ServerLevel level,
            final CivilizationService service,
            final UUID expectedFactionId,
            final UUID settlementId,
            final ResourceLocation blueprintId,
            final BlockPos origin,
            final int rotationDegrees,
            final long gameTime
    ) {
        service.worldData().ensureWritable();

        final SettlementRecord settlement = service.findSettlement(settlementId)
                .orElseThrow(() -> new IllegalArgumentException("Settlement does not exist: " + settlementId));
        if (!settlement.ownerFactionId().equals(expectedFactionId)) {
            throw new IllegalArgumentException("Settlement " + settlementId
                    + " does not belong to faction " + expectedFactionId);
        }
        if (settlement.status() != SettlementStatus.ACTIVE) {
            throw new IllegalArgumentException("Cannot plan a building in an inactive settlement");
        }
        if (!settlement.dimensionId().equals(level.dimension().location())) {
            throw new IllegalArgumentException("Settlement belongs to dimension " + settlement.dimensionId()
                    + "; planning must run in that dimension");
        }

        final FactionRecord faction = service.findFaction(expectedFactionId)
                .orElseThrow(() -> new IllegalArgumentException("Faction does not exist: " + expectedFactionId));
        if (faction.status() != FactionStatus.ACTIVE) {
            throw new IllegalArgumentException("Cannot plan a building for an inactive faction");
        }

        final BlueprintDefinition definition = BlueprintCatalog.find(blueprintId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown blueprint: " + blueprintId));
        if (!definition.allowsRotation(rotationDegrees)) {
            throw new IllegalArgumentException("Blueprint " + blueprintId
                    + " does not allow rotation " + rotationDegrees);
        }

        final BuildingRecord candidate = BuildingRecord.planned(definition, expectedFactionId,
                settlementId, level.dimension().location(), origin, rotationDegrees, gameTime);
        final BlueprintTemplate template = BlueprintTemplate.load(level.getServer().getResourceManager(), definition);
        final Validation validation = validateRange(level, candidate, definition, template,
                0, template.blocks().size(), true);
        if (validation.status() == ValidationStatus.WAITING_FOR_CHUNKS) {
            throw new IllegalArgumentException("Placement precheck requires all target and support chunks "
                    + "to be already loaded; no chunks were force-loaded and no building record was created");
        }
        if (validation.status() == ValidationStatus.CONFLICT) {
            throw new IllegalArgumentException("Placement precheck rejected the site before creating a building record: "
                    + validation.reason());
        }

        // CivilizationService performs the authoritative logical checks again (including recorded
        // footprint overlap and M1-002 headquarters uniqueness) before persisting the record.
        final BuildingRecord planned = service.planBuilding(settlementId, blueprintId,
                level.dimension().location(), origin, rotationDegrees, gameTime);
        return new PlanResult(planned, template.blocks().size(), template.checksum());
    }

    public static StartResult beginPlacement(final ServerLevel level, final UUID buildingId) {
        return beginPlacement(level, CivilizationService.forLevel(level), buildingId);
    }

    /**
     * Explicit-service overload used by isolated integration tests and controlled server adapters.
     * The default command path continues to resolve the authoritative Overworld SavedData service.
     */
    public static StartResult beginPlacement(
            final ServerLevel level,
            final CivilizationService service,
            final UUID buildingId
    ) {
        final BuildingRecord building = service.findBuilding(buildingId)
                .orElseThrow(() -> new IllegalArgumentException("Building does not exist: " + buildingId));
        if (!building.dimensionId().equals(level.dimension().location())) {
            throw new IllegalArgumentException("Building belongs to dimension " + building.dimensionId()
                    + "; run this command in that dimension");
        }
        if (building.state() != BuildingState.PLANNED || building.worldSyncState() != WorldSyncState.NOT_PLACED
                || building.placementCursor() != 0) {
            throw new IllegalStateException("Building is not an unplaced PLANNED record: state=" + building.state()
                    + ", worldSync=" + building.worldSyncState() + ", cursor=" + building.placementCursor());
        }

        final BlueprintDefinition definition = resolveDefinition(building);
        final BlueprintTemplate template = BlueprintTemplate.load(level.getServer().getResourceManager(), definition);
        final Validation validation = validateRange(level, building, definition, template, 0,
                template.blocks().size(), true);
        if (validation.status() == ValidationStatus.WAITING_FOR_CHUNKS) {
            throw new IllegalArgumentException("All template chunks must already be loaded before placement; move closer and retry");
        }
        if (validation.status() == ValidationStatus.CONFLICT) {
            throw new IllegalArgumentException("Blueprint placement rejected before changing the world: " + validation.reason());
        }

        final BuildingRecord pending = building.withPlacementCursor(0, WorldSyncState.PENDING);
        service.updateBuildingPlacement(pending);
        return new StartResult(pending, template.blocks().size());
    }

    public static StartResult resumePlacement(final ServerLevel level, final UUID buildingId) {
        return resumePlacement(level, CivilizationService.forLevel(level), buildingId);
    }

    /** Explicit-service overload for isolated integration tests and controlled server adapters. */
    public static StartResult resumePlacement(
            final ServerLevel level,
            final CivilizationService service,
            final UUID buildingId
    ) {
        final BuildingRecord building = service.findBuilding(buildingId)
                .orElseThrow(() -> new IllegalArgumentException("Building does not exist: " + buildingId));
        if (!building.dimensionId().equals(level.dimension().location())) {
            throw new IllegalArgumentException("Building belongs to dimension " + building.dimensionId()
                    + "; run this command in that dimension");
        }
        if (building.state() != BuildingState.NEEDS_REPAIR || building.worldSyncState() != WorldSyncState.NEEDS_REPAIR) {
            throw new IllegalStateException("Only a NEEDS_REPAIR placement can be resumed");
        }

        final BlueprintDefinition definition = resolveDefinition(building);
        final BlueprintTemplate template = BlueprintTemplate.load(level.getServer().getResourceManager(), definition);
        if (building.placementCursor() > template.blocks().size()) {
            throw new IllegalStateException("Saved placement cursor exceeds template block count; manual repair is required");
        }
        final Validation validation = validateRange(level, building, definition, template,
                building.placementCursor(), template.blocks().size(), true);
        if (validation.status() == ValidationStatus.WAITING_FOR_CHUNKS) {
            throw new IllegalArgumentException("All remaining template chunks must already be loaded before resuming");
        }
        if (validation.status() == ValidationStatus.CONFLICT) {
            throw new IllegalArgumentException("Placement cannot resume until this conflict is cleared: " + validation.reason());
        }

        final BuildingRecord pending = building.withState(BuildingState.PLANNED, WorldSyncState.PENDING)
                .withPlacementCursor(building.placementCursor(), WorldSyncState.PENDING);
        service.updateBuildingPlacement(pending);
        return new StartResult(pending, template.blocks().size());
    }

    /** Called once per server END tick. Processes no more than the configured block budget. */
    public static int processPending(final MinecraftServer server) {
        final EmpiresWorldData data = EmpiresWorldData.get(server);
        final CivilizationService service = CivilizationService.forLevel(server.overworld());
        return processPending(server, data, service);
    }

    /**
     * Explicit-data overload keeps integration tests isolated from the server's persistent world save.
     * Production scheduler calls the default overload above.
     */
    public static int processPending(
            final MinecraftServer server,
            final EmpiresWorldData data,
            final CivilizationService service
    ) {
        if (data.isReadOnly()) {
            return 0;
        }
        int remainingBudget = EmpiresConfig.BUILDING_PLACEMENT_BLOCKS_PER_TICK.get();
        int placedOrSkipped = 0;
        for (BuildingRecord snapshot : data.buildings()) {
            if (remainingBudget <= 0) {
                break;
            }
            if (snapshot.worldSyncState() != WorldSyncState.PENDING) {
                continue;
            }

            final Optional<ServerLevel> targetLevel = findLoadedLevel(server, snapshot.dimensionId());
            if (targetLevel.isEmpty()) {
                continue;
            }
            final BuildingRecord current = service.findBuilding(snapshot.id()).orElse(null);
            if (current == null || current.worldSyncState() != WorldSyncState.PENDING) {
                continue;
            }

            try {
                processOneBuilding(service, targetLevel.get(), current, remainingBudget);
                final BuildingRecord after = service.findBuilding(current.id()).orElse(current);
                final int advanced = Math.max(0, after.placementCursor() - current.placementCursor());
                if (advanced > 0) {
                    remainingBudget -= advanced;
                    placedOrSkipped += advanced;
                }
            } catch (RuntimeException exception) {
                MinecraftEmpires.LOGGER.error("Blueprint placement tick failed for building {} at cursor {}. No remote chunks were requested.",
                        current.id(), current.placementCursor(), exception);
                markNeedsRepair(service, current, current.placementCursor(), "Unexpected placement exception: " + exception.getMessage());
            }
        }
        return placedOrSkipped;
    }

    private static void processOneBuilding(
            final CivilizationService service,
            final ServerLevel level,
            final BuildingRecord building,
            final int availableBudget
    ) {
        final BlueprintDefinition definition = resolveDefinition(building);
        final BlueprintTemplate template = BlueprintTemplate.load(level.getServer().getResourceManager(), definition);
        if (building.placementCursor() > template.blocks().size()) {
            markNeedsRepair(service, building, building.placementCursor(), "Saved placement cursor exceeds template block count");
            return;
        }
        final int endExclusive = Math.min(template.blocks().size(), building.placementCursor() + availableBudget);
        final Validation validation = validateRange(level, building, definition, template,
                building.placementCursor(), endExclusive, true);
        if (validation.status() == ValidationStatus.WAITING_FOR_CHUNKS) {
            // This is a pause, not a failure: hasChunkAt only observes current chunk availability.
            return;
        }
        if (validation.status() == ValidationStatus.CONFLICT) {
            markNeedsRepair(service, building, building.placementCursor(), validation.reason());
            return;
        }

        int cursor = building.placementCursor();
        while (cursor < endExclusive) {
            final BlueprintTemplate.TemplateBlock entry = template.blocks().get(cursor);
            final BlockPos target = worldPosition(building, definition, entry.position());
            final BlockState desired = resolveBlockState(entry.blockId());
            final BlockState existing = level.getBlockState(target);
            if (!existing.equals(desired)) {
                if (!canReplace(level, target, existing, definition)) {
                    markNeedsRepair(service, building, cursor,
                            "Protected or non-replaceable block appeared during placement at " + target);
                    return;
                }
                final boolean changed = level.setBlock(target, desired, SET_BLOCK_FLAGS);
                if (!changed && !level.getBlockState(target).equals(desired)) {
                    markNeedsRepair(service, building, cursor, "World rejected block placement at " + target);
                    return;
                }
            }
            // Cursor advances only after the desired block is present. Replaying after a crash is idempotent.
            cursor++;
        }

        final WorldSyncState nextSync = cursor >= template.blocks().size()
                ? WorldSyncState.SYNCED : WorldSyncState.PENDING;
        service.updateBuildingPlacement(building.withPlacementCursor(cursor, nextSync));
        if (nextSync == WorldSyncState.SYNCED && dev.minecraftempires.common.EmpiresConfig.DEBUG_LOGGING.get()) {
            MinecraftEmpires.LOGGER.info("Blueprint placement completed: building={}, blocks={}, templateChecksum={}",
                    building.id(), cursor, template.checksum());
        }
    }

    private static Validation validateRange(
            final ServerLevel level,
            final BuildingRecord building,
            final BlueprintDefinition definition,
            final BlueprintTemplate template,
            final int startInclusive,
            final int endExclusive,
            final boolean validateGround
    ) {
        if (startInclusive < 0 || endExclusive < startInclusive || endExclusive > template.blocks().size()) {
            return Validation.conflict("Invalid template cursor range " + startInclusive + ".." + endExclusive);
        }
        final int minY = level.getMinBuildHeight();
        final int maxY = minY + level.getHeight();
        final long minStructureY = building.origin().getY();
        final long maxStructureY = minStructureY + definition.sizeY();
        if (minStructureY < minY || maxStructureY > maxY) {
            return Validation.conflict("Blueprint volume exceeds build height: originY="
                    + minStructureY + ", height=" + definition.sizeY()
                    + ", allowed=" + minY + ".." + (maxY - 1));
        }

        final WorldBorder border = level.getWorldBorder();
        final int footprintX = definition.rotatedSizeX(building.rotationDegrees());
        final int footprintZ = definition.rotatedSizeZ(building.rotationDegrees());
        for (int x = 0; x < footprintX; x++) {
            for (int z = 0; z < footprintZ; z++) {
                final BlockPos footprintCell = building.origin().offset(x, 0, z);
                if (!border.isWithinBounds(footprintCell.getX() + 0.5D, footprintCell.getZ() + 0.5D)) {
                    return Validation.conflict("Blueprint footprint exceeds the world border at " + footprintCell);
                }
                if (validateGround && definition.requireSolidGround()) {
                    final BlockPos support = footprintCell.below();
                    if (!level.hasChunkAt(support)) {
                        return Validation.waiting();
                    }
                    if (!level.getBlockState(support).isFaceSturdy(level, support, Direction.UP)) {
                        return Validation.conflict("Solid ground is required under the entire footprint; unsupported at " + support);
                    }
                }
            }
        }

        for (int index = startInclusive; index < endExclusive; index++) {
            final BlueprintTemplate.TemplateBlock entry = template.blocks().get(index);
            final BlockPos target = worldPosition(building, definition, entry.position());
            if (!level.hasChunkAt(target)) {
                return Validation.waiting();
            }
            final BlockState existing = level.getBlockState(target);
            final BlockState desired = resolveBlockState(entry.blockId());
            if (!existing.equals(desired) && !canReplace(level, target, existing, definition)) {
                return Validation.conflict("Protected or non-replaceable block at " + target + " (" + existing + ")");
            }
        }
        return Validation.ready();
    }

    private static boolean canReplace(
            final ServerLevel level,
            final BlockPos position,
            final BlockState existing,
            final BlueprintDefinition definition
    ) {
        if (!definition.allowFluids() && !existing.getFluidState().isEmpty()) {
            return false;
        }
        if (level.getBlockEntity(position) != null) {
            return false;
        }
        return switch (definition.protectedBlocksPolicy()) {
            case NEVER_REPLACE -> existing.isAir();
            case REPLACE_REPLACEABLE_ONLY -> existing.isAir() || existing.canBeReplaced();
        };
    }

    private static BlockState resolveBlockState(final ResourceLocation blockId) {
        return BuiltInRegistries.BLOCK.getOptional(blockId)
                .orElseThrow(() -> new IllegalArgumentException("Template references an unknown block: " + blockId))
                .defaultBlockState();
    }

    private static BlockPos worldPosition(
            final BuildingRecord building,
            final BlueprintDefinition definition,
            final BlockPos local
    ) {
        final int x;
        final int z;
        switch (building.rotationDegrees()) {
            case 0 -> { x = local.getX(); z = local.getZ(); }
            case 90 -> { x = definition.sizeZ() - 1 - local.getZ(); z = local.getX(); }
            case 180 -> { x = definition.sizeX() - 1 - local.getX(); z = definition.sizeZ() - 1 - local.getZ(); }
            case 270 -> { x = local.getZ(); z = definition.sizeX() - 1 - local.getX(); }
            default -> throw new IllegalArgumentException("Unsupported building rotation: " + building.rotationDegrees());
        }
        return building.origin().offset(x, local.getY(), z);
    }

    private static BlueprintDefinition resolveDefinition(final BuildingRecord building) {
        final BlueprintDefinition definition = BlueprintCatalog.find(building.blueprintId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown blueprint: " + building.blueprintId()));
        if (definition.version() != building.blueprintVersion()) {
            throw new IllegalStateException("Building blueprint version mismatch: saved=" + building.blueprintVersion()
                    + ", current=" + definition.version());
        }
        if (!definition.templateChecksum().equals(building.templateChecksum())) {
            throw new IllegalStateException("Building blueprint manifest checksum mismatch; refusing placement: " + building.id());
        }
        if (!definition.allowsRotation(building.rotationDegrees())) {
            throw new IllegalStateException("Saved building rotation is not allowed by blueprint: " + building.id());
        }
        return definition;
    }

    private static Optional<ServerLevel> findLoadedLevel(final MinecraftServer server, final ResourceLocation dimensionId) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().equals(dimensionId)) {
                return Optional.of(level);
            }
        }
        return Optional.empty();
    }

    private static void markNeedsRepair(
            final CivilizationService service,
            final BuildingRecord building,
            final int cursor,
            final String reason
    ) {
        final BuildingRecord repair = building.withState(BuildingState.NEEDS_REPAIR, WorldSyncState.NEEDS_REPAIR)
                .withPlacementCursor(cursor, WorldSyncState.NEEDS_REPAIR);
        service.updateBuildingPlacement(repair);
        MinecraftEmpires.LOGGER.warn("Building placement paused for repair: building={}, cursor={}, reason={}",
                building.id(), cursor, reason);
    }

    public record PlanResult(BuildingRecord building, int totalBlocks, String templateChecksum) {
    }

    public record StartResult(BuildingRecord building, int totalBlocks) {
    }

    private enum ValidationStatus { READY, WAITING_FOR_CHUNKS, CONFLICT }

    private record Validation(ValidationStatus status, String reason) {
        private static Validation ready() { return new Validation(ValidationStatus.READY, ""); }
        private static Validation waiting() { return new Validation(ValidationStatus.WAITING_FOR_CHUNKS, ""); }
        private static Validation conflict(final String reason) { return new Validation(ValidationStatus.CONFLICT, reason); }
    }
}
