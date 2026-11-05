package dev.minecraftempires.common.civilization;

import dev.minecraftempires.MinecraftEmpires;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Authoritative world-wide civilization registry. It is always attached to the Overworld,
 * so Nether/End access resolves to the same registry instead of creating dimension copies.
 */
public final class EmpiresWorldData extends SavedData {
    public static final String DATA_NAME = "minecraft_empires_world";
    public static final int CURRENT_SCHEMA_VERSION = 2;
    private static final int MIN_SUPPORTED_SCHEMA_VERSION = 1;

    private final UUID worldId;
    private final int sourceSchemaVersion;
    private final Map<UUID, FactionRecord> factions = new LinkedHashMap<>();
    private final Map<UUID, SettlementRecord> settlements = new LinkedHashMap<>();
    private final Map<UUID, CitizenRecord> citizens = new LinkedHashMap<>();
    private long simulationClock;
    private boolean readOnly;
    private String readOnlyReason = "";
    private CompoundTag preservedRawTag;

    /** Constructor used only by DimensionDataStorage for a brand-new world record. */
    public EmpiresWorldData() {
        this(UUID.randomUUID(), CURRENT_SCHEMA_VERSION, true);
    }

    private EmpiresWorldData(final UUID worldId, final int sourceSchemaVersion, final boolean markDirty) {
        this.worldId = worldId;
        this.sourceSchemaVersion = sourceSchemaVersion;
        if (markDirty) {
            setDirty();
        }
    }

    public static EmpiresWorldData get(final MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                EmpiresWorldData::load,
                EmpiresWorldData::new,
                DATA_NAME
        );
    }

    public static EmpiresWorldData get(final ServerLevel anyLevel) {
        return get(anyLevel.getServer());
    }

    public UUID worldId() { return worldId; }
    public long simulationClock() { return simulationClock; }
    public int factionCount() { return factions.size(); }
    public int settlementCount() { return settlements.size(); }
    public int citizenCount() { return citizens.size(); }
    public int schemaVersion() { return sourceSchemaVersion; }
    public boolean isReadOnly() { return readOnly; }
    public String readOnlyReason() { return readOnlyReason; }

    public Optional<FactionRecord> findFaction(final UUID id) {
        return Optional.ofNullable(factions.get(id));
    }

    public List<FactionRecord> factions() {
        return List.copyOf(factions.values());
    }

    public Optional<SettlementRecord> findSettlement(final UUID id) {
        return Optional.ofNullable(settlements.get(id));
    }

    public List<SettlementRecord> settlements() {
        return List.copyOf(settlements.values());
    }

    public Optional<CitizenRecord> findCitizen(final UUID id) {
        return Optional.ofNullable(citizens.get(id));
    }

    public List<CitizenRecord> citizens() {
        return List.copyOf(citizens.values());
    }

    public List<CitizenRecord> citizensForFaction(final UUID factionId) {
        return citizens.values().stream()
                .filter(citizen -> citizen.ownerFactionId().equals(factionId))
                .collect(Collectors.toUnmodifiableList());
    }

    void putFaction(final FactionRecord record) {
        ensureWritable();
        factions.put(record.id(), record);
        setDirty();
    }

    void putSettlement(final SettlementRecord record) {
        ensureWritable();
        if (!factions.containsKey(record.ownerFactionId())) {
            throw new IllegalArgumentException("Settlement owner faction does not exist: " + record.ownerFactionId());
        }
        settlements.put(record.id(), record);
        setDirty();
    }

    void putCitizen(final CitizenRecord record) {
        ensureWritable();
        if (!factions.containsKey(record.ownerFactionId())) {
            throw new IllegalArgumentException("Citizen owner faction does not exist: " + record.ownerFactionId());
        }
        if (record.homeSettlementId() != null) {
            final SettlementRecord home = settlements.get(record.homeSettlementId());
            if (home == null) {
                throw new IllegalArgumentException("Citizen home settlement does not exist: " + record.homeSettlementId());
            }
            if (!home.ownerFactionId().equals(record.ownerFactionId())) {
                throw new IllegalArgumentException("Citizen home settlement must belong to the same faction");
            }
        }
        citizens.put(record.id(), record);
        setDirty();
    }

    void setSimulationClock(final long gameTime) {
        ensureWritable();
        if (gameTime < simulationClock) {
            throw new IllegalArgumentException("Simulation clock cannot move backwards");
        }
        if (gameTime != simulationClock) {
            simulationClock = gameTime;
            setDirty();
        }
    }

    void ensureWritable() {
        if (readOnly) {
            throw new IllegalStateException("Minecraft Empires world data is read-only: " + readOnlyReason);
        }
    }

    @Override
    public CompoundTag save(final CompoundTag ignored) {
        if (readOnly && preservedRawTag != null) {
            return preservedRawTag.copy();
        }

        final CompoundTag tag = new CompoundTag();
        tag.putInt("schema_version", CURRENT_SCHEMA_VERSION);
        tag.putUUID("world_id", worldId);
        tag.putLong("simulation_clock", simulationClock);

        final ListTag factionTags = new ListTag();
        final ListTag factionIds = new ListTag();
        for (FactionRecord faction : factions.values()) {
            factionTags.add(faction.save());
            factionIds.add(StringTag.valueOf(faction.id().toString()));
        }
        tag.put("factions", factionTags);
        tag.put("faction_ids", factionIds);

        final ListTag settlementTags = new ListTag();
        for (SettlementRecord settlement : settlements.values()) {
            settlementTags.add(settlement.save());
        }
        tag.put("settlements", settlementTags);

        final ListTag citizenTags = new ListTag();
        for (CitizenRecord citizen : citizens.values()) {
            citizenTags.add(citizen.save());
        }
        tag.put("citizens", citizenTags);
        return tag;
    }

    public static EmpiresWorldData load(final CompoundTag tag) {
        final int loadedSchema = tag.getInt("schema_version");
        if (loadedSchema > CURRENT_SCHEMA_VERSION) {
            return readOnlyFallback(tag, "Saved data schema " + loadedSchema
                    + " is newer than this mod supports (" + CURRENT_SCHEMA_VERSION + ")");
        }
        if (loadedSchema < MIN_SUPPORTED_SCHEMA_VERSION || loadedSchema > CURRENT_SCHEMA_VERSION) {
            return readOnlyFallback(tag, "Unsupported or missing saved data schema: " + loadedSchema);
        }

        try {
            if (!tag.hasUUID("world_id")) {
                throw new IllegalArgumentException("Missing world_id");
            }
            final EmpiresWorldData data = new EmpiresWorldData(tag.getUUID("world_id"),
                    CURRENT_SCHEMA_VERSION, false);
            data.simulationClock = tag.getLong("simulation_clock");
            if (data.simulationClock < 0L) {
                throw new IllegalArgumentException("Negative simulation_clock");
            }

            final ListTag factionTags = tag.getList("factions", Tag.TAG_COMPOUND);
            for (int index = 0; index < factionTags.size(); index++) {
                final FactionRecord record = FactionRecord.load(factionTags.getCompound(index));
                if (data.factions.putIfAbsent(record.id(), record) != null) {
                    throw new IllegalArgumentException("Duplicate faction id: " + record.id());
                }
            }

            final ListTag factionIdTags = tag.getList("faction_ids", Tag.TAG_STRING);
            final List<UUID> indexedIds = new ArrayList<>();
            for (int index = 0; index < factionIdTags.size(); index++) {
                indexedIds.add(UUID.fromString(factionIdTags.getString(index)));
            }
            final Set<UUID> indexedIdSet = new LinkedHashSet<>(indexedIds);
            if (indexedIds.size() != data.factions.size()
                    || indexedIdSet.size() != indexedIds.size()
                    || !indexedIdSet.equals(data.factions.keySet())) {
                throw new IllegalArgumentException("Faction ID index does not match faction records");
            }

            final ListTag settlementTags = tag.getList("settlements", Tag.TAG_COMPOUND);
            for (int index = 0; index < settlementTags.size(); index++) {
                final SettlementRecord record = SettlementRecord.load(settlementTags.getCompound(index));
                if (!data.factions.containsKey(record.ownerFactionId())) {
                    throw new IllegalArgumentException("Settlement references missing faction: " + record.ownerFactionId());
                }
                if (data.settlements.putIfAbsent(record.id(), record) != null) {
                    throw new IllegalArgumentException("Duplicate settlement id: " + record.id());
                }
            }

            for (FactionRecord faction : data.factions.values()) {
                if (faction.capitalSettlementId() != null) {
                    final SettlementRecord capital = data.settlements.get(faction.capitalSettlementId());
                    if (capital == null || !capital.ownerFactionId().equals(faction.id())) {
                        throw new IllegalArgumentException("Invalid capital settlement reference for faction " + faction.id());
                    }
                }
            }

            if (loadedSchema >= 2) {
                if (!tag.contains("citizens", Tag.TAG_LIST)) {
                    throw new IllegalArgumentException("Schema 2 data is missing citizens list");
                }
                final ListTag citizenTags = tag.getList("citizens", Tag.TAG_COMPOUND);
                for (int index = 0; index < citizenTags.size(); index++) {
                    final CitizenRecord record = CitizenRecord.load(citizenTags.getCompound(index));
                    if (!data.factions.containsKey(record.ownerFactionId())) {
                        throw new IllegalArgumentException("Citizen references missing faction: " + record.ownerFactionId());
                    }
                    if (record.homeSettlementId() != null) {
                        final SettlementRecord home = data.settlements.get(record.homeSettlementId());
                        if (home == null || !home.ownerFactionId().equals(record.ownerFactionId())) {
                            throw new IllegalArgumentException("Citizen references invalid home settlement: " + record.homeSettlementId());
                        }
                    }
                    if (data.citizens.putIfAbsent(record.id(), record) != null) {
                        throw new IllegalArgumentException("Duplicate citizen id: " + record.id());
                    }
                }
            } else {
                // Schema 1 had no citizen records. Mark dirty so the migrated schema 2 payload is saved.
                data.setDirty();
                MinecraftEmpires.LOGGER.info("Migrated Minecraft Empires world data from schema 1 to schema 2.");
            }
            return data;
        } catch (RuntimeException exception) {
            MinecraftEmpires.LOGGER.error("Unable to safely read Minecraft Empires world data. Preserving the original tag and disabling writes.", exception);
            return readOnlyFallback(tag, "Saved data validation failed: " + exception.getMessage());
        }
    }

    private static EmpiresWorldData readOnlyFallback(final CompoundTag rawTag, final String reason) {
        final UUID preservedWorldId = rawTag.hasUUID("world_id") ? rawTag.getUUID("world_id") : UUID.randomUUID();
        final int preservedSchema = rawTag.getInt("schema_version");
        final EmpiresWorldData data = new EmpiresWorldData(preservedWorldId, preservedSchema, false);
        data.readOnly = true;
        data.readOnlyReason = reason;
        data.preservedRawTag = rawTag.copy();
        MinecraftEmpires.LOGGER.error("Minecraft Empires data is read-only: {}. No new civilization data will be written to this save.", reason);
        return data;
    }
}
