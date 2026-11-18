package com.jestron.clanplugin.util;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffectType;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 1.16.5 - 1.21.x arası sürüm farklarını çalışma zamanında çözer.
 *
 * 1.20.5'te PotionEffectType / Particle isimleri, 1.21.3'te Attribute isimleri değişti;
 * 1.21.3'te Sound enum olmaktan çıktı. Bu sınıf sabitlere doğrudan derleme zamanında
 * bağlanmak yerine alan (field) adıyla reflection üzerinden erişir; böylece aynı JAR
 * eski ve yeni sunucularda NoSuchFieldError atmadan çalışır.
 */
public final class VersionUtil {

    private static final Object MISSING = new Object();
    private static final Map<String, Object> CACHE = new ConcurrentHashMap<>();

    private VersionUtil() {
    }

    // ------------------------------------------------------------------
    // Genel alan çözümleyici
    // ------------------------------------------------------------------

    private static <T> T lookup(final Class<T> type, final String... names) {
        final String cacheKey = type.getName() + ':' + String.join("|", names);
        final Object cached = CACHE.get(cacheKey);
        if (cached != null) {
            return cached == MISSING ? null : type.cast(cached);
        }
        Object found = null;
        for (final String name : names) {
            try {
                final Field field = type.getField(name);
                final Object value = field.get(null);
                if (type.isInstance(value)) {
                    found = value;
                    break;
                }
            } catch (final Throwable ignored) {
                // bu sürümde yok, sıradakini dene
            }
        }
        CACHE.put(cacheKey, found == null ? MISSING : found);
        return found == null ? null : type.cast(found);
    }

    // ------------------------------------------------------------------
    // Potion efektleri
    // ------------------------------------------------------------------

    @SuppressWarnings("deprecation")
    public static PotionEffectType potion(final String... names) {
        final PotionEffectType byField = lookup(PotionEffectType.class, names);
        if (byField != null) {
            return byField;
        }
        for (final String name : names) {
            try {
                final PotionEffectType byName = PotionEffectType.getByName(name);
                if (byName != null) {
                    return byName;
                }
            } catch (final Throwable ignored) {
                // devam
            }
        }
        return null;
    }

    public static PotionEffectType slowness()     { return potion("SLOWNESS", "SLOW"); }
    public static PotionEffectType jumpBoost()    { return potion("JUMP_BOOST", "JUMP"); }
    public static PotionEffectType resistance()   { return potion("RESISTANCE", "DAMAGE_RESISTANCE"); }
    public static PotionEffectType strength()     { return potion("STRENGTH", "INCREASE_DAMAGE"); }
    public static PotionEffectType nausea()       { return potion("NAUSEA", "CONFUSION"); }
    public static PotionEffectType regeneration() { return potion("REGENERATION"); }
    public static PotionEffectType wither()       { return potion("WITHER"); }
    public static PotionEffectType blindness()    { return potion("BLINDNESS"); }
    public static PotionEffectType darkness()     { return potion("DARKNESS"); }
    public static PotionEffectType levitation()   { return potion("LEVITATION"); }
    public static PotionEffectType hunger()       { return potion("HUNGER"); }
    public static PotionEffectType invisibility() { return potion("INVISIBILITY"); }

    // ------------------------------------------------------------------
    // Partikül & ses
    // ------------------------------------------------------------------

    public static Particle particle(final String... names) {
        return lookup(Particle.class, names);
    }

    public static Sound sound(final String... names) {
        return lookup(Sound.class, names);
    }

    public static void playSound(final Location location, final float volume, final float pitch,
                                 final String... names) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        final Sound sound = sound(names);
        if (sound == null) {
            return;
        }
        try {
            location.getWorld().playSound(location, sound, volume, pitch);
        } catch (final Throwable ignored) {
            // ses kritik değil
        }
    }

    public static void spawnParticle(final Location location, final int count, final double offset,
                                     final double speed, final String... names) {
        if (location == null) {
            return;
        }
        final World world = location.getWorld();
        final Particle particle = particle(names);
        if (world == null || particle == null) {
            return;
        }
        try {
            world.spawnParticle(particle, location, count, offset, offset, offset, speed);
        } catch (final Throwable ignored) {
            // veri isteyen partikül vb. - görsel, kritik değil
        }
    }

    public static boolean isDragonBreath(final AreaEffectCloud cloud) {
        try {
            final Particle particle = cloud.getParticle();
            return particle != null && "DRAGON_BREATH".equals(String.valueOf(particle));
        } catch (final Throwable ignored) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Attribute
    // ------------------------------------------------------------------

    public static void setMaxHealth(final LivingEntity entity, final double health) {
        if (entity == null || health <= 0) {
            return;
        }
        try {
            final Attribute attribute = lookup(Attribute.class, "MAX_HEALTH", "GENERIC_MAX_HEALTH");
            if (attribute == null) {
                return;
            }
            final AttributeInstance instance = entity.getAttribute(attribute);
            if (instance == null) {
                return;
            }
            instance.setBaseValue(health);
            entity.setHealth(Math.min(health, instance.getValue()));
        } catch (final Throwable ignored) {
            // can ayarlanamazsa varsayılan canla devam
        }
    }
}
