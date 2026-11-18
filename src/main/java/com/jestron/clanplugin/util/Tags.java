package com.jestron.clanplugin.util;

import org.bukkit.entity.Entity;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.metadata.Metadatable;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.UUID;

/**
 * Efektlerin oluşturduğu varlıkları (mermi, çağrılan mob, kristal) sahibine bağlar.
 */
public final class Tags {

    public static final String EFFECT = "csp_effect";
    public static final String OWNER = "csp_owner";
    public static final String CRYSTAL_OWNER = "csp_crystal_owner";

    private static Plugin plugin;

    private Tags() {
    }

    public static void init(final Plugin owner) {
        plugin = owner;
    }

    public static void mark(final Entity entity, final String effect, final UUID owner) {
        if (entity == null || plugin == null) {
            return;
        }
        entity.setMetadata(EFFECT, new FixedMetadataValue(plugin, effect));
        entity.setMetadata(OWNER, new FixedMetadataValue(plugin, owner.toString()));
    }

    public static void set(final Metadatable target, final String key, final String value) {
        if (target == null || plugin == null) {
            return;
        }
        target.setMetadata(key, new FixedMetadataValue(plugin, value));
    }

    public static String get(final Metadatable target, final String key) {
        if (target == null || plugin == null || !target.hasMetadata(key)) {
            return null;
        }
        final List<MetadataValue> values = target.getMetadata(key);
        for (final MetadataValue value : values) {
            if (value.getOwningPlugin() == plugin) {
                return value.asString();
            }
        }
        return null;
    }

    public static String effect(final Metadatable target) {
        return get(target, EFFECT);
    }

    public static UUID owner(final Metadatable target) {
        return parse(get(target, OWNER));
    }

    public static UUID parse(final String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (final IllegalArgumentException ex) {
            return null;
        }
    }
}
