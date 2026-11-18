package com.jestron.clanplugin.sword.effects;

import com.jestron.clanplugin.ClanPlugin;
import com.jestron.clanplugin.sword.SwordEffect;
import com.jestron.clanplugin.util.VersionUtil;
import org.bukkit.entity.Player;

import java.util.List;

/** Kaplumba: menzildeki tüm düşmanlara Yavaşlık III. */
public final class KaplumbaEffect extends SwordEffect {

    public KaplumbaEffect(final ClanPlugin plugin) {
        super(plugin, "Kaplumba", 5, 60, 3, 3);
    }

    @Override
    protected String tag() {
        return "&8[&2Kaplumba&8] &7";
    }

    @Override
    public boolean apply(final Player user) {
        final List<Player> targets = enemies(user);
        if (targets.isEmpty()) {
            noTarget(user);
            return false;
        }
        final int seconds = getDuration();
        for (final Player target : targets) {
            potion(target, VersionUtil.slowness(), seconds * 20, getAmplifier() - 1, true);
            send(target, tag() + "&cYavaşlatıldın! &7(" + seconds + "sn)");
        }
        VersionUtil.playSound(user.getLocation(), 1.0f, 0.7f, "ENTITY_TURTLE_AMBIENT_LAND");
        send(user, tag() + "&a" + targets.size() + " düşman yavaşlatıldı!");
        return true;
    }
}
