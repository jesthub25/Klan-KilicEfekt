package com.jestron.clanplugin.sword.effects;

import com.jestron.clanplugin.ClanPlugin;
import com.jestron.clanplugin.sword.SwordEffect;
import com.jestron.clanplugin.util.VersionUtil;
import org.bukkit.entity.Player;

import java.util.List;

/** Shulker: menzildeki TÜM düşmanlara havaya yükselme (Levitation II). */
public final class ShulkerEffect extends SwordEffect {

    public ShulkerEffect(final ClanPlugin plugin) {
        super(plugin, "Shulker", 5, 20, 2, 2);
    }

    @Override
    protected String tag() {
        return "&8[&dShulker&8] &7";
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
            potion(target, VersionUtil.levitation(), ticks, 1, true);
            send(target, tag() + "&dHavaya kaldırıldın!");
        }
        VersionUtil.playSound(user.getLocation(), 1.0f, 1.0f, "ENTITY_SHULKER_SHOOT");
        send(user, tag() + "&a" + targets.size() + " düşman havaya kaldırıldı!");
        return true;
    }
}
