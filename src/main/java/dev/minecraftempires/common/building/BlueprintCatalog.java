package dev.minecraftempires.common.building;

import dev.minecraftempires.common.civilization.ResourceLedger;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.LinkedHashMap;

/** Built-in, immutable blueprint catalog. Datapack loading is intentionally deferred. */
public final class BlueprintCatalog {
    public static final ResourceLocation WAREHOUSE_PROTOTYPE_ID =
            new ResourceLocation("empires", "warehouse_prototype");
    public static final ResourceLocation HEADQUARTERS_HALL_ID =
            new ResourceLocation("empires", "headquarters_hall");

    private static final Map<ResourceLocation, BlueprintDefinition> DEFINITIONS = createDefinitions();

    private BlueprintCatalog() {
    }

    public static Optional<BlueprintDefinition> find(final ResourceLocation id) {
        return Optional.ofNullable(DEFINITIONS.get(id));
    }

    public static List<BlueprintDefinition> all() {
        return List.copyOf(DEFINITIONS.values());
    }

    private static Map<ResourceLocation, BlueprintDefinition> createDefinitions() {
        final Map<ResourceLocation, BlueprintDefinition> definitions = new LinkedHashMap<>();
        final ResourceLedger materials = ResourceLedger.of(Map.of(
                new ResourceLocation("minecraft", "oak_planks"), 9L,
                new ResourceLocation("minecraft", "cobblestone"), 9L
        ));
        final BlueprintDefinition warehouse = new BlueprintDefinition(
                WAREHOUSE_PROTOTYPE_ID,
                BlueprintDefinition.CURRENT_SCHEMA_VERSION,
                1,
                BuildingCategory.STORAGE,
                new ResourceLocation("empires", "structures/warehouse_prototype"),
                3, 3, 3,
                materials,
                100L,
                List.of(new ResourceLocation("empires", "builder")),
                Set.of(0, 90, 180, 270),
                true,
                false,
                ProtectedBlocksPolicy.REPLACE_REPLACEABLE_ONLY,
                "8a47481cbe75ded3675a14b840bba48b38b39180cfe40ae53007c5a096530e3a"
        );
        definitions.put(warehouse.id(), warehouse);

        final ResourceLedger headquartersMaterials = ResourceLedger.of(Map.of(
                new ResourceLocation("minecraft", "stone_bricks"), 49L,
                new ResourceLocation("minecraft", "spruce_planks"), 139L,
                new ResourceLocation("minecraft", "spruce_log"), 12L,
                new ResourceLocation("minecraft", "glass"), 3L
        ));
        final BlueprintDefinition headquarters = new BlueprintDefinition(
                HEADQUARTERS_HALL_ID,
                BlueprintDefinition.CURRENT_SCHEMA_VERSION,
                1,
                BuildingCategory.HEADQUARTERS,
                new ResourceLocation("empires", "structures/headquarters_hall"),
                7, 8, 7,
                headquartersMaterials,
                800L,
                List.of(new ResourceLocation("empires", "builder")),
                Set.of(0, 90, 180, 270),
                true,
                false,
                ProtectedBlocksPolicy.REPLACE_REPLACEABLE_ONLY,
                "b221b5eea84f2087225c5ec4ad185b52523c6e6512b712d64e321fe4f01ea821"
        );
        definitions.put(headquarters.id(), headquarters);
        return Map.copyOf(definitions);
    }
}
