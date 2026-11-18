package com.jestron.clanplugin.sword.effects;

import com.jestron.clanplugin.ClanPlugin;
import com.jestron.clanplugin.sword.SwordEffect;
import com.jestron.clanplugin.util.VersionUtil;
import org.bukkit.entity.Player;

import java.util.List;

/** Cadı: menzildeki tüm düşmanlara Açlık III + Bulantı. */
public final class CadiEffect extends SwordEffect {

    public CadiEffect(final ClanPlugin plugin) {
        super(plugin, "Cadi", 5, 60, 5, 3);
    }

    @Override
    protected String tag() {
        return "&8[&5Cadı&8] &7";
    }

    @Override
    public boolean apply(final Player user) {
        final List<Player> targets = enemies(user);
        if (targets.isEmpty()) {
            noTarget(user);
            return false;
        }
        final int ticks = getDuration() * 20;
        for (final Player target : targets) {
            potion(target, VersionUtil.hunger(), ticks, 2, true);
            potion(target, VersionUtil.nausea(), ticks, 0, true);
            send(target, tag() + "&cBir cadı büyüsüne yakalandın!");
        }
        VersionUtil.playSound(user.getLocation(), 1.0f, 1.0f, "ENTITY_WITCH_CELEBRATE", "ENTITY_WITCH_AMBIENT");
        send(user, tag() + "&a" + targets.size() + " düşman büyülendi!");
        return true;
    }
}
