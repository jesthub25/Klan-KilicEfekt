package com.jestron.clanplugin.sword.effects;

import com.jestron.clanplugin.ClanPlugin;
import com.jestron.clanplugin.sword.SwordEffect;
import com.jestron.clanplugin.util.VersionUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * Creeper: kullanıcının etrafında patlama görüntüsü; sadece menzildeki düşmanlar hasar alır
 * ve havaya fırlar. Gerçek patlama oluşturulmaz: blok kırılmaz, kendine/klana/dostlara işlemez.
 */
public final class CreeperEffect extends SwordEffect {

    public CreeperEffect(final ClanPlugin plugin) {
        super(plugin, "Creeper", 5, 90, 3, 1);
    }

    @Override
    protected String tag() {
        return "&8[&aCreeper&8] &7";
    }

    @Override
    public boolean apply(final Player user) {
        final List<Player> targets = enemies(user);
        if (targets.isEmpty()) {
            noTarget(user);
            return false;
        }
        final Location center = user.getLocation();
        VersionUtil.spawnParticle(center.clone().add(0, 1, 0), 3, 0.6, 0.1, "EXPLOSION", "EXPLOSION_LARGE");
        VersionUtil.playSound(center, 1.0f, 1.0f, "ENTITY_GENERIC_EXPLODE");
        for (final Player target : targets) {
            target.damage(6.0, user);
            final Vector push = target.getLocation().toVector().subtract(center.toVector());
            push.setY(0);
            if (push.lengthSquared() > 0.0001) {
                push.normalize().multiply(0.6);
            }
            push.setY(1.2);
            target.setVelocity(push);
            send(target, tag() + "&cPatlamaya yakalandın!");
        }
        send(user, tag() + "&a" + targets.size() + " düşman patlamaya yakalandı!");
        return true;
    }
}
