package dev.minecraftempires.common.civilization;

import dev.minecraftempires.common.building.BuildingRecord;

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
    Optional<BuildingRecord> findBuilding(UUID id);
    List<BuildingRecord> buildings();
    List<BuildingRecord> buildingsForSettlement(UUID settlementId);
    void saveBuilding(BuildingRecord record);
    void saveBuildingReservation(FactionRecord updatedFaction, BuildingRecord reservedBuilding);
    void saveConstructionUpdate(BuildingRecord expectedCurrent, BuildingRecord updatedBuilding);
}
