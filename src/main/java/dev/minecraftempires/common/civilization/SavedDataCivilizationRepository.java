package dev.minecraftempires.common.civilization;

import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** SavedData-backed repository. Always resolves the global registry through the Overworld. */
public final class SavedDataCivilizationRepository implements CivilizationRepository {
    private final EmpiresWorldData data;

    public SavedDataCivilizationRepository(final EmpiresWorldData data) {
        this.data = data;
    }

    public static SavedDataCivilizationRepository forServer(final MinecraftServer server) {
        return new SavedDataCivilizationRepository(EmpiresWorldData.get(server));
    }

    public EmpiresWorldData worldData() { return data; }

    @Override public Optional<FactionRecord> findFaction(final UUID id) { return data.findFaction(id); }
    @Override public List<FactionRecord> factions() { return data.factions(); }
    @Override public void saveFaction(final FactionRecord record) { data.putFaction(record); }
    @Override public Optional<SettlementRecord> findSettlement(final UUID id) { return data.findSettlement(id); }
    @Override public List<SettlementRecord> settlements() { return data.settlements(); }
    @Override public void saveSettlement(final SettlementRecord record) { data.putSettlement(record); }
    @Override public Optional<CitizenRecord> findCitizen(final UUID id) { return data.findCitizen(id); }
    @Override public List<CitizenRecord> citizens() { return data.citizens(); }
    @Override public List<CitizenRecord> citizensForFaction(final UUID factionId) { return data.citizensForFaction(factionId); }
    @Override public void saveCitizen(final CitizenRecord record) { data.putCitizen(record); }
}
