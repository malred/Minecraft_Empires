package dev.minecraftempires.common.building;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable, validated block palette loaded from a data-pack JSON template. */
public final class BlueprintTemplate {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    private static final Comparator<TemplateBlock> BLOCK_ORDER = Comparator
            .comparingInt((TemplateBlock block) -> block.position().getY())
            .thenComparingInt(block -> block.position().getZ())
            .thenComparingInt(block -> block.position().getX());

    private final ResourceLocation id;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final List<TemplateBlock> blocks;
    private final String checksum;

    private BlueprintTemplate(
            final ResourceLocation id,
            final int sizeX,
            final int sizeY,
            final int sizeZ,
            final List<TemplateBlock> blocks,
            final String checksum
    ) {
        this.id = id;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.blocks = List.copyOf(blocks);
        this.checksum = checksum;
    }

    public static BlueprintTemplate load(
            final ResourceManager resourceManager,
            final BlueprintDefinition definition
    ) {
        Objects.requireNonNull(resourceManager, "resourceManager");
        Objects.requireNonNull(definition, "definition");

        final ResourceLocation resourceId = new ResourceLocation(
                definition.templateId().getNamespace(), definition.templateId().getPath() + ".json");
        final Resource resource = resourceManager.getResource(resourceId)
                .orElseThrow(() -> new IllegalArgumentException("Missing blueprint template resource: " + resourceId));

        try (InputStreamReader reader = new InputStreamReader(resource.open(), StandardCharsets.UTF_8)) {
            final JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) {
                throw new IllegalArgumentException("Blueprint template root must be a JSON object: " + resourceId);
            }
            final JsonObject root = parsed.getAsJsonObject();
            final int schemaVersion = requiredInt(root, "schema_version");
            if (schemaVersion != CURRENT_SCHEMA_VERSION) {
                throw new IllegalArgumentException("Unsupported template schema " + schemaVersion + " in " + resourceId);
            }
            final ResourceLocation declaredId = parseId(requiredString(root, "id"), "template id");
            if (!declaredId.equals(definition.templateId())) {
                throw new IllegalArgumentException("Template id mismatch: expected " + definition.templateId()
                        + ", found " + declaredId);
            }

            final JsonArray size = requiredArray(root, "size");
            if (size.size() != 3) {
                throw new IllegalArgumentException("Template size must contain exactly three integers: " + resourceId);
            }
            final int sizeX = requiredInt(size, 0, "size[0]");
            final int sizeY = requiredInt(size, 1, "size[1]");
            final int sizeZ = requiredInt(size, 2, "size[2]");
            if (sizeX != definition.sizeX() || sizeY != definition.sizeY() || sizeZ != definition.sizeZ()) {
                throw new IllegalArgumentException("Template dimensions do not match blueprint " + definition.id()
                        + ": expected " + definition.sizeX() + "x" + definition.sizeY() + "x" + definition.sizeZ()
                        + ", found " + sizeX + "x" + sizeY + "x" + sizeZ);
            }

            final JsonArray paletteJson = requiredArray(root, "palette");
            if (paletteJson.size() == 0 || paletteJson.size() > 256) {
                throw new IllegalArgumentException("Template palette must contain 1-256 block IDs: " + resourceId);
            }
            final List<ResourceLocation> palette = new ArrayList<>(paletteJson.size());
            for (int index = 0; index < paletteJson.size(); index++) {
                final JsonElement entry = paletteJson.get(index);
                if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()) {
                    throw new IllegalArgumentException("Palette entry " + index + " must be a block resource ID");
                }
                palette.add(parseId(entry.getAsString(), "palette entry " + index));
            }

            final JsonArray blocksJson = requiredArray(root, "blocks");
            final long maxVolume = (long) sizeX * sizeY * sizeZ;
            if (blocksJson.size() == 0 || blocksJson.size() > maxVolume
                    || blocksJson.size() > BlueprintDefinition.MAX_BLOCK_VOLUME) {
                throw new IllegalArgumentException("Template block count is outside its supported bounds: " + resourceId);
            }
            final List<TemplateBlock> blocks = new ArrayList<>(blocksJson.size());
            final Set<Long> occupiedPositions = new HashSet<>();
            for (int index = 0; index < blocksJson.size(); index++) {
                final JsonElement element = blocksJson.get(index);
                if (!element.isJsonObject()) {
                    throw new IllegalArgumentException("Template block " + index + " must be an object");
                }
                final JsonObject block = element.getAsJsonObject();
                final JsonArray pos = requiredArray(block, "pos");
                if (pos.size() != 3) {
                    throw new IllegalArgumentException("Template block position must have three integers at index " + index);
                }
                final int x = requiredInt(pos, 0, "blocks[" + index + "].pos[0]");
                final int y = requiredInt(pos, 1, "blocks[" + index + "].pos[1]");
                final int z = requiredInt(pos, 2, "blocks[" + index + "].pos[2]");
                if (x < 0 || y < 0 || z < 0 || x >= sizeX || y >= sizeY || z >= sizeZ) {
                    throw new IllegalArgumentException("Template block position is outside declared dimensions at index " + index);
                }
                final long packed = (((long) x & 0x3ffffffL) << 38)
                        | (((long) z & 0x3ffffffL) << 12) | ((long) y & 0xfffL);
                if (!occupiedPositions.add(packed)) {
                    throw new IllegalArgumentException("Template contains duplicate block position " + x + "," + y + "," + z);
                }
                final int paletteIndex = requiredInt(block, "palette");
                if (paletteIndex < 0 || paletteIndex >= palette.size()) {
                    throw new IllegalArgumentException("Template palette index is invalid at block " + index);
                }
                blocks.add(new TemplateBlock(new net.minecraft.core.BlockPos(x, y, z), paletteIndex,
                        palette.get(paletteIndex)));
            }

            blocks.sort(BLOCK_ORDER);
            final String calculatedChecksum = calculateChecksum(declaredId, sizeX, sizeY, sizeZ, palette, blocks);
            final String declaredChecksum = requiredString(root, "checksum").toLowerCase();
            if (!declaredChecksum.matches("[0-9a-f]{64}") || !declaredChecksum.equals(calculatedChecksum)) {
                throw new IllegalArgumentException("Blueprint template checksum is invalid for " + declaredId
                        + ": declared=" + declaredChecksum + ", calculated=" + calculatedChecksum);
            }
            if (!definition.templateContentChecksum().isEmpty()
                    && !definition.templateContentChecksum().equals(calculatedChecksum)) {
                throw new IllegalArgumentException("Blueprint catalog checksum mismatch for " + declaredId
                        + ": expected=" + definition.templateContentChecksum() + ", actual=" + calculatedChecksum);
            }
            return new BlueprintTemplate(declaredId, sizeX, sizeY, sizeZ, blocks, calculatedChecksum);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read blueprint template " + resourceId, exception);
        } catch (RuntimeException exception) {
            if (exception instanceof IllegalArgumentException || exception instanceof IllegalStateException) {
                throw exception;
            }
            throw new IllegalArgumentException("Malformed blueprint template " + resourceId + ": " + exception.getMessage(), exception);
        }
    }

    public ResourceLocation id() { return id; }
    public int sizeX() { return sizeX; }
    public int sizeY() { return sizeY; }
    public int sizeZ() { return sizeZ; }
    public List<TemplateBlock> blocks() { return blocks; }
    public String checksum() { return checksum; }

    private static String calculateChecksum(
            final ResourceLocation id,
            final int sizeX,
            final int sizeY,
            final int sizeZ,
            final List<ResourceLocation> palette,
            final List<TemplateBlock> blocks
    ) {
        final StringBuilder canonical = new StringBuilder()
                .append(CURRENT_SCHEMA_VERSION).append('|').append(id).append('|')
                .append(sizeX).append('x').append(sizeY).append('x').append(sizeZ);
        for (int index = 0; index < palette.size(); index++) {
            canonical.append("|palette=").append(index).append(':').append(palette.get(index));
        }
        for (TemplateBlock block : blocks) {
            canonical.append("|block=").append(block.position().getX()).append(',')
                    .append(block.position().getY()).append(',').append(block.position().getZ())
                    .append(',').append(block.paletteIndex());
        }
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

    private static JsonArray requiredArray(final JsonObject object, final String key) {
        if (!object.has(key) || !object.get(key).isJsonArray()) {
            throw new IllegalArgumentException("Blueprint template is missing array '" + key + "'");
        }
        return object.getAsJsonArray(key);
    }

    private static String requiredString(final JsonObject object, final String key) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()
                || !object.getAsJsonPrimitive(key).isString()) {
            throw new IllegalArgumentException("Blueprint template is missing string '" + key + "'");
        }
        return object.get(key).getAsString();
    }

    private static int requiredInt(final JsonObject object, final String key) {
        if (!object.has(key)) {
            throw new IllegalArgumentException("Blueprint template is missing integer '" + key + "'");
        }
        return exactInt(object.get(key), key);
    }

    private static int requiredInt(final JsonArray array, final int index, final String description) {
        if (index < 0 || index >= array.size()) {
            throw new IllegalArgumentException("Blueprint template is missing integer '" + description + "'");
        }
        return exactInt(array.get(index), description);
    }

    private static int exactInt(final JsonElement element, final String description) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Blueprint template field must be an integer: " + description);
        }
        try {
            return element.getAsBigDecimal().intValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new IllegalArgumentException("Blueprint template field must be an exact 32-bit integer: " + description, exception);
        }
    }

    private static ResourceLocation parseId(final String text, final String description) {
        final ResourceLocation id = ResourceLocation.tryParse(text);
        if (id == null) {
            throw new IllegalArgumentException("Invalid resource ID for " + description + ": " + text);
        }
        return id;
    }

    public record TemplateBlock(net.minecraft.core.BlockPos position, int paletteIndex, ResourceLocation blockId) {
        public TemplateBlock {
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(blockId, "blockId");
            position = position.immutable();
        }
    }
}
