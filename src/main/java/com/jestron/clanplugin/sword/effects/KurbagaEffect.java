package com.jestron.clanplugin.sword.effects;

import com.jestron.clanplugin.ClanPlugin;
import com.jestron.clanplugin.sword.SwordEffect;
import com.jestron.clanplugin.util.VersionUtil;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/** Kurbağa: en yakın düşmanı havaya zıplatır. */
public final class KurbagaEffect extends SwordEffect {

    public KurbagaEffect(final ClanPlugin plugin) {
        super(plugin, "Kurbaga", 5, 90, 2, 1);
    }

    @Override
    protected String tag() {
        return "&8[&6Kurbağa&8] &7";
    }

    @Override
    public boolean apply(final Player user) {
        final Player target = nearestEnemy(user);
        if (target == null) {
            noTarget(user);
            return false;
        }
        potion(target, VersionUtil.jumpBoost(), getDuration() * 20, 5, true);
        target.setVelocity(new Vector(0, 2.5, 0));
        VersionUtil.playSound(target.getLocation(), 1.0f, 1.0f, "ENTITY_SLIME_JUMP");
        send(target, tag() + "&a&lZIPLADIN!");
        send(user, tag() + "&a" + target.getName() + " havaya fırlatıldı!");
        return true;
    }
}
