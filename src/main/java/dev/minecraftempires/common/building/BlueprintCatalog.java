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
        return Map.copyOf(definitions);
    }
}
