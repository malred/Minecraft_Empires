package dev.minecraftempires.common.building;

import dev.minecraftempires.common.civilization.ResourceLedger;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Immutable content definition for an official building blueprint. */
public final class BlueprintDefinition {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final int MAX_AXIS_SIZE = 64;
    public static final int MAX_BLOCK_VOLUME = 32768;

    private final ResourceLocation id;
    private final int schemaVersion;
    private final int version;
    private final BuildingCategory category;
    private final ResourceLocation templateId;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final ResourceLedger materials;
    private final long workPoints;
    private final List<ResourceLocation> requiredProfessions;
    private final Set<Integer> allowedRotations;
    private final boolean requireSolidGround;
    private final boolean allowFluids;
    private final ProtectedBlocksPolicy protectedBlocksPolicy;
    private final String templateChecksum;
    private final String templateContentChecksum;

    public BlueprintDefinition(
            final ResourceLocation id,
            final int schemaVersion,
            final int version,
            final BuildingCategory category,
            final ResourceLocation templateId,
            final int sizeX,
            final int sizeY,
            final int sizeZ,
            final ResourceLedger materials,
            final long workPoints,
            final List<ResourceLocation> requiredProfessions,
            final Set<Integer> allowedRotations,
            final boolean requireSolidGround,
            final boolean allowFluids,
            final ProtectedBlocksPolicy protectedBlocksPolicy
    ) {
        this(id, schemaVersion, version, category, templateId, sizeX, sizeY, sizeZ, materials,
                workPoints, requiredProfessions, allowedRotations, requireSolidGround, allowFluids,
                protectedBlocksPolicy, "");
    }

    public BlueprintDefinition(
            final ResourceLocation id,
            final int schemaVersion,
            final int version,
            final BuildingCategory category,
            final ResourceLocation templateId,
            final int sizeX,
            final int sizeY,
            final int sizeZ,
            final ResourceLedger materials,
            final long workPoints,
            final List<ResourceLocation> requiredProfessions,
            final Set<Integer> allowedRotations,
            final boolean requireSolidGround,
            final boolean allowFluids,
            final ProtectedBlocksPolicy protectedBlocksPolicy,
            final String templateContentChecksum
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.category = Objects.requireNonNull(category, "category");
        this.templateId = Objects.requireNonNull(templateId, "templateId");
        this.materials = Objects.requireNonNull(materials, "materials");
        this.protectedBlocksPolicy = Objects.requireNonNull(protectedBlocksPolicy, "protectedBlocksPolicy");
        if (schemaVersion != CURRENT_SCHEMA_VERSION || version < 1) {
            throw new IllegalArgumentException("Unsupported blueprint schema/version for " + id);
        }
        if (sizeX < 1 || sizeY < 1 || sizeZ < 1
                || sizeX > MAX_AXIS_SIZE || sizeY > MAX_AXIS_SIZE || sizeZ > MAX_AXIS_SIZE
                || (long) sizeX * sizeY * sizeZ > MAX_BLOCK_VOLUME) {
            throw new IllegalArgumentException("Blueprint dimensions are outside the supported bounds: " + id);
        }
        if (workPoints < 1L) {
            throw new IllegalArgumentException("Blueprint workPoints must be positive: " + id);
        }

        final List<ResourceLocation> professions = new ArrayList<>(Objects.requireNonNull(requiredProfessions, "requiredProfessions"));
        if (professions.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Blueprint contains a null required profession: " + id);
        }
        this.requiredProfessions = List.copyOf(professions);

        final Set<Integer> rotations = new TreeSet<>(Objects.requireNonNull(allowedRotations, "allowedRotations"));
        if (rotations.isEmpty() || rotations.stream().anyMatch(rotation -> rotation == null
                || (rotation != 0 && rotation != 90 && rotation != 180 && rotation != 270))) {
            throw new IllegalArgumentException("Blueprint rotations must be a non-empty subset of 0/90/180/270: " + id);
        }
        this.allowedRotations = Collections.unmodifiableSet(rotations);
        final String contentChecksum = Objects.requireNonNull(templateContentChecksum, "templateContentChecksum")
                .trim().toLowerCase();
        if (!contentChecksum.isEmpty() && !contentChecksum.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Template content checksum must be a 64-character SHA-256 hex string: " + id);
        }
        this.templateContentChecksum = contentChecksum;
        this.schemaVersion = schemaVersion;
        this.version = version;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.workPoints = workPoints;
        this.requireSolidGround = requireSolidGround;
        this.allowFluids = allowFluids;
        this.templateChecksum = calculateChecksum();
    }

    public ResourceLocation id() { return id; }
    public int schemaVersion() { return schemaVersion; }
    public int version() { return version; }
    public BuildingCategory category() { return category; }
    public ResourceLocation templateId() { return templateId; }
    public int sizeX() { return sizeX; }
    public int sizeY() { return sizeY; }
    public int sizeZ() { return sizeZ; }
    public ResourceLedger materials() { return materials; }
    public long workPoints() { return workPoints; }
    public List<ResourceLocation> requiredProfessions() { return requiredProfessions; }
    public Set<Integer> allowedRotations() { return allowedRotations; }
    public boolean requireSolidGround() { return requireSolidGround; }
    public boolean allowFluids() { return allowFluids; }
    public ProtectedBlocksPolicy protectedBlocksPolicy() { return protectedBlocksPolicy; }
    public String templateChecksum() { return templateChecksum; }
    public String templateContentChecksum() { return templateContentChecksum; }

    public boolean allowsRotation(final int degrees) {
        return allowedRotations.contains(degrees);
    }

    public int rotatedSizeX(final int rotationDegrees) {
        validateRotation(rotationDegrees);
        return rotationDegrees == 90 || rotationDegrees == 270 ? sizeZ : sizeX;
    }

    public int rotatedSizeZ(final int rotationDegrees) {
        validateRotation(rotationDegrees);
        return rotationDegrees == 90 || rotationDegrees == 270 ? sizeX : sizeZ;
    }

    private void validateRotation(final int rotationDegrees) {
        if (!allowsRotation(rotationDegrees)) {
            throw new IllegalArgumentException("Rotation " + rotationDegrees + " is not allowed for blueprint " + id);
        }
    }

    /** Stable checksum of the versioned blueprint manifest; physical template bytes use templateContentChecksum. */
    private String calculateChecksum() {
        final StringBuilder canonical = new StringBuilder()
                .append(id).append('|').append(schemaVersion).append('|').append(version).append('|')
                .append(category).append('|').append(templateId).append('|')
                .append(sizeX).append('x').append(sizeY).append('x').append(sizeZ).append('|')
                .append(workPoints).append('|').append(requireSolidGround).append('|').append(allowFluids)
                .append('|').append(protectedBlocksPolicy);
        materials.asMap().forEach((resource, amount) -> canonical.append('|').append(resource).append('=').append(amount));
        requiredProfessions.stream().map(ResourceLocation::toString).sorted()
                .forEach(profession -> canonical.append("|profession=").append(profession));
        allowedRotations.forEach(rotation -> canonical.append("|rotation=").append(rotation));
        try {
            final byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            final StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format("%02x", value & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
        }
    }
}
