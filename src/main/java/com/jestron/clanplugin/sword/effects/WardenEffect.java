package com.jestron.clanplugin.sword.effects;

import com.jestron.clanplugin.ClanPlugin;
import com.jestron.clanplugin.sword.SwordEffect;
import com.jestron.clanplugin.util.VersionUtil;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

/** Warden: menzildeki tüm düşmanlara Körlük (1.19+ sunucularda ek olarak Karanlık). */
public final class WardenEffect extends SwordEffect {

    public WardenEffect(final ClanPlugin plugin) {
        super(plugin, "Warden", 5, 60, 3, 1);
    }

    @Override
    protected String tag() {
        return "&8[&#0B569DWarden&8] &7";
    }

    @Override
    public boolean apply(final Player user) {
        final List<Player> targets = enemies(user);
        if (targets.isEmpty()) {
            noTarget(user);
            return false;
        }
        final int ticks = getDuration() * 20;
        final PotionEffectType darkness = VersionUtil.darkness(); // 1.16-1.18'de null
        for (final Player target : targets) {
            potion(target, VersionUtil.blindness(), ticks, 0, true);
            potion(target, darkness, ticks, 0, true);
            send(target, tag() + "&cGözlerin karardı! &7(" + getDuration() + "sn)");
        }
        VersionUtil.playSound(user.getLocation(), 1.0f, 1.0f, "ENTITY_WARDEN_SONIC_BOOM", "ENTITY_ELDER_GUARDIAN_CURSE");
        send(user, tag() + "&a" + targets.size() + " düşman kör edildi!");
        return true;
    }
}
