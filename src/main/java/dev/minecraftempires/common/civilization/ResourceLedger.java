package dev.minecraftempires.common.civilization;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Immutable resource balances. Values are non-negative integer units.
 * All mutations return a new ledger so callers cannot bypass service validation.
 */
public final class ResourceLedger {
    private static final ResourceLedger EMPTY = new ResourceLedger(Map.of());
    private final Map<ResourceLocation, Long> amounts;

    private ResourceLedger(final Map<ResourceLocation, Long> amounts) {
        final Map<ResourceLocation, Long> sorted = new TreeMap<>((a, b) -> a.toString().compareTo(b.toString()));
        for (Map.Entry<ResourceLocation, Long> entry : amounts.entrySet()) {
            final ResourceLocation id = Objects.requireNonNull(entry.getKey(), "resource id");
            final long amount = Objects.requireNonNull(entry.getValue(), "resource amount");
            if (amount < 0L) {
                throw new IllegalArgumentException("Resource amount cannot be negative: " + id);
            }
            if (amount > 0L) {
                sorted.put(id, amount);
            }
        }
        this.amounts = Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
    }

    public static ResourceLedger empty() {
        return EMPTY;
    }

    public static ResourceLedger of(final Map<ResourceLocation, Long> amounts) {
        return amounts.isEmpty() ? EMPTY : new ResourceLedger(amounts);
    }

    public long get(final ResourceLocation resourceId) {
        return amounts.getOrDefault(resourceId, 0L);
    }

    public Map<ResourceLocation, Long> asMap() {
        return amounts;
    }

    public boolean isEmpty() {
        return amounts.isEmpty();
    }

    /** Returns true when this ledger can cover every entry in the requested cost ledger. */
    public boolean contains(final ResourceLedger required) {
        Objects.requireNonNull(required, "required");
        for (Map.Entry<ResourceLocation, Long> entry : required.amounts.entrySet()) {
            if (get(entry.getKey()) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns a new ledger after subtracting every requested amount.
     * The current instance remains unchanged when any resource is insufficient.
     */
    public ResourceLedger minus(final ResourceLedger cost) {
        Objects.requireNonNull(cost, "cost");
        if (!contains(cost)) {
            for (Map.Entry<ResourceLocation, Long> entry : cost.amounts.entrySet()) {
                final long available = get(entry.getKey());
                if (available < entry.getValue()) {
                    throw new IllegalArgumentException("Insufficient resource " + entry.getKey()
                            + ": required=" + entry.getValue() + ", available=" + available
                            + ", missing=" + (entry.getValue() - available));
                }
            }
        }
        final Map<ResourceLocation, Long> copy = new LinkedHashMap<>(amounts);
        for (Map.Entry<ResourceLocation, Long> entry : cost.amounts.entrySet()) {
            final long remaining = get(entry.getKey()) - entry.getValue();
            if (remaining == 0L) {
                copy.remove(entry.getKey());
            } else {
                copy.put(entry.getKey(), remaining);
            }
        }
        return of(copy);
    }

    /** Returns a new ledger with another ledger added, rejecting arithmetic overflow. */
    public ResourceLedger plus(final ResourceLedger other) {
        Objects.requireNonNull(other, "other");
        final Map<ResourceLocation, Long> copy = new LinkedHashMap<>(amounts);
        for (Map.Entry<ResourceLocation, Long> entry : other.amounts.entrySet()) {
            final long current = get(entry.getKey());
            final long updated;
            try {
                updated = Math.addExact(current, entry.getValue());
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException("Resource amount overflow: " + entry.getKey(), exception);
            }
            copy.put(entry.getKey(), updated);
        }
        return of(copy);
    }

    public ResourceLedger withAmount(final ResourceLocation resourceId, final long amount) {
        Objects.requireNonNull(resourceId, "resourceId");
        if (amount < 0L) {
            throw new IllegalArgumentException("Resource amount cannot be negative: " + resourceId);
        }
        final Map<ResourceLocation, Long> copy = new LinkedHashMap<>(amounts);
        if (amount == 0L) {
            copy.remove(resourceId);
        } else {
            copy.put(resourceId, amount);
        }
        return of(copy);
    }

    public ResourceLedger plus(final ResourceLocation resourceId, final long delta) {
        final long current = get(resourceId);
        final long updated;
        try {
            updated = Math.addExact(current, delta);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Resource amount overflow: " + resourceId, exception);
        }
        return withAmount(resourceId, updated);
    }

    CompoundTag save() {
        final CompoundTag tag = new CompoundTag();
        final ListTag entries = new ListTag();
        for (Map.Entry<ResourceLocation, Long> entry : amounts.entrySet()) {
            final CompoundTag item = new CompoundTag();
            item.putString("id", entry.getKey().toString());
            item.putLong("amount", entry.getValue());
            entries.add(item);
        }
        tag.put("entries", entries);
        return tag;
    }

    static ResourceLedger load(final CompoundTag tag) {
        final Map<ResourceLocation, Long> loaded = new LinkedHashMap<>();
        final ListTag entries = tag.getList("entries", Tag.TAG_COMPOUND);
        for (int index = 0; index < entries.size(); index++) {
            final CompoundTag item = entries.getCompound(index);
            final ResourceLocation id = ResourceLocation.tryParse(item.getString("id"));
            if (id == null) {
                throw new IllegalArgumentException("Invalid resource id in saved ledger at index " + index);
            }
            final long amount = item.getLong("amount");
            if (amount < 0L || loaded.putIfAbsent(id, amount) != null) {
                throw new IllegalArgumentException("Invalid or duplicate resource entry: " + id);
            }
        }
        return of(loaded);
    }
}
