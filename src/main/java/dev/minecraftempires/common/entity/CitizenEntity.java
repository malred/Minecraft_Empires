package dev.minecraftempires.common.entity;

import dev.minecraftempires.common.civilization.CitizenRecord;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

import java.util.UUID;

/**
 * First in-world representation of a logical citizen.
 *
 * This milestone intentionally gives citizens a safe, stationary placeholder body and basic
 * look-at behavior. Work, navigation, combat, species-specific models, and job AI are later work.
 */
public final class CitizenEntity extends PathfinderMob {
    private static final EntityDataAccessor<String> DATA_CITIZEN_ID =
            SynchedEntityData.defineId(CitizenEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_FACTION_ID =
            SynchedEntityData.defineId(CitizenEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_DISPLAY_NAME =
            SynchedEntityData.defineId(CitizenEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_SPECIES_ID =
            SynchedEntityData.defineId(CitizenEntity.class, EntityDataSerializers.STRING);

    public CitizenEntity(final EntityType<? extends CitizenEntity> entityType, final Level level) {
        super(entityType, level);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.20D)
                .add(Attributes.FOLLOW_RANGE, 16.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_CITIZEN_ID, "");
        this.entityData.define(DATA_FACTION_ID, "");
        this.entityData.define(DATA_DISPLAY_NAME, "Citizen");
        this.entityData.define(DATA_SPECIES_ID, "minecraft:player");
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(2, new RandomLookAroundGoal(this));
    }

    /** Populate the synchronized identity fields before adding the entity to a ServerLevel. */
    public void applyCitizenRecord(final CitizenRecord citizen) {
        this.entityData.set(DATA_CITIZEN_ID, citizen.id().toString());
        this.entityData.set(DATA_FACTION_ID, citizen.ownerFactionId().toString());
        this.entityData.set(DATA_DISPLAY_NAME, citizen.displayName());
        this.entityData.set(DATA_SPECIES_ID, citizen.speciesId().toString());
        this.setCustomName(net.minecraft.network.chat.Component.literal(citizen.displayName()));
        this.setCustomNameVisible(true);
        this.setPersistenceRequired();
    }

    public UUID getCitizenId() {
        return parseUuid(this.entityData.get(DATA_CITIZEN_ID));
    }

    public UUID getFactionId() {
        return parseUuid(this.entityData.get(DATA_FACTION_ID));
    }

    public String getCitizenDisplayName() {
        return this.entityData.get(DATA_DISPLAY_NAME);
    }

    public ResourceLocation getSpeciesId() {
        ResourceLocation parsed = ResourceLocation.tryParse(this.entityData.get(DATA_SPECIES_ID));
        return parsed == null ? new ResourceLocation("minecraft", "player") : parsed;
    }

    private static UUID parseUuid(final String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    @Override
    public void addAdditionalSaveData(final CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("EmpiresCitizenId", this.entityData.get(DATA_CITIZEN_ID));
        tag.putString("EmpiresFactionId", this.entityData.get(DATA_FACTION_ID));
        tag.putString("EmpiresDisplayName", this.entityData.get(DATA_DISPLAY_NAME));
        tag.putString("EmpiresSpeciesId", this.entityData.get(DATA_SPECIES_ID));
    }

    @Override
    public void readAdditionalSaveData(final CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(DATA_CITIZEN_ID, tag.getString("EmpiresCitizenId"));
        this.entityData.set(DATA_FACTION_ID, tag.getString("EmpiresFactionId"));
        this.entityData.set(DATA_DISPLAY_NAME,
                tag.contains("EmpiresDisplayName") ? tag.getString("EmpiresDisplayName") : "Citizen");
        this.entityData.set(DATA_SPECIES_ID,
                tag.contains("EmpiresSpeciesId") ? tag.getString("EmpiresSpeciesId") : "minecraft:player");
        this.setCustomName(net.minecraft.network.chat.Component.literal(this.entityData.get(DATA_DISPLAY_NAME)));
        this.setCustomNameVisible(true);
        this.setPersistenceRequired();
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
