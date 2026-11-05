package dev.minecraftempires.common.civilization;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence boundary for civilization records. */
public interface CivilizationRepository {
    Optional<FactionRecord> findFaction(UUID id);
    List<FactionRecord> factions();
    void saveFaction(FactionRecord record);
    Optional<SettlementRecord> findSettlement(UUID id);
    List<SettlementRecord> settlements();
    void saveSettlement(SettlementRecord record);
    Optional<CitizenRecord> findCitizen(UUID id);
    List<CitizenRecord> citizens();
    List<CitizenRecord> citizensForFaction(UUID factionId);
    void saveCitizen(CitizenRecord record);
}
