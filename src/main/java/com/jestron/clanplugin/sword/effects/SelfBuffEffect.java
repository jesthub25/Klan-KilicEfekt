package com.jestron.clanplugin.sword.effects;

import com.jestron.clanplugin.ClanPlugin;
import com.jestron.clanplugin.sword.SwordEffect;
import com.jestron.clanplugin.util.VersionUtil;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.function.Supplier;

/** Sadece kullanana iksir etkisi veren efektler (Golem Direnç, Golem Kuvvet, Yenilenme). */
public final class SelfBuffEffect extends SwordEffect {

    private final Supplier<PotionEffectType> type;
    private final String label;
    private final String color;
    private final String[] sound;

    public SelfBuffEffect(final ClanPlugin plugin, final String name, final int cooldown, final int duration,
                          final int amplifier, final Supplier<PotionEffectType> type, final String label,
                          final String color, final String... sound) {
        super(plugin, name, 0, cooldown, duration, amplifier);
        this.type = type;
        this.label = label;
        this.color = color;
        this.sound = sound;
    }

    @Override
    public boolean usesRadius() {
        return false;
    }

    @Override
    protected String tag() {
        return "&8[" + color + label + "&8] &7";
    }

    @Override
    public boolean apply(final Player user) {
        final PotionEffectType effect = type.get();
        if (effect == null) {
            send(user, "&cBu efekt bu sunucu sürümünde desteklenmiyor.");
            return false;
        }
        final int seconds = getDuration();
        final int level = getAmplifier();
        potion(user, effect, seconds * 20, level - 1, true);
        VersionUtil.playSound(user.getLocation(), 1.0f, 1.0f, sound);
        send(user, tag() + color + "&l" + label + " " + level + " &aaktif! &7(" + seconds + " saniye)");
        return true;
    }

    public static SelfBuffEffect golemResistance(final ClanPlugin plugin) {
        return new SelfBuffEffect(plugin, "GolemD", 90, 3, 2, VersionUtil::resistance,
                "Direnç", "&f", "ENTITY_IRON_GOLEM_REPAIR", "ENTITY_IRON_GOLEM_HURT");
    }

    public static SelfBuffEffect golemStrength(final ClanPlugin plugin) {
        return new SelfBuffEffect(plugin, "GolemK", 90, 3, 12, VersionUtil::strength,
                "Kuvvet", "&c", "ENTITY_IRON_GOLEM_ATTACK");
    }

    public static SelfBuffEffect regeneration(final ClanPlugin plugin) {
        return new SelfBuffEffect(plugin, "Yenilenme", 90, 3, 4, VersionUtil::regeneration,
                "Yenilenme", "&d", "ENTITY_PLAYER_LEVELUP");
    }
}
