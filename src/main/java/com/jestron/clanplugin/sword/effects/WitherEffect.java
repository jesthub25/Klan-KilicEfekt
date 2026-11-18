package com.jestron.clanplugin.sword.effects;

import com.jestron.clanplugin.ClanPlugin;
import com.jestron.clanplugin.sword.SwordEffect;
import com.jestron.clanplugin.util.Tags;
import com.jestron.clanplugin.util.VersionUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.WitherSkull;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;

import java.util.UUID;

/**
 * Wither: wither kafası fırlatır. Patlama blok kırmaz, ateş çıkarmaz.
 * Patlamanın kendisi ve ek 8 hasar + Wither II yalnızca düşmanlara işler;
 * kendine, klanına ve dost klanlara patlama hasarı SwordListener'da iptal edilir.
 */
public final class WitherEffect extends SwordEffect implements Listener {

    private static final String KEY = "wither";

    public WitherEffect(final ClanPlugin plugin) {
        super(plugin, "Wither", 0, 60, 4, 2);
    }

    @Override
    public boolean usesRadius() {
        return false;
    }

    @Override
    protected String tag() {
        return "&8[&0Wither&8] &7";
    }

    @Override
    public boolean apply(final Player user) {
        final WitherSkull skull = user.launchProjectile(WitherSkull.class);
        skull.setShooter(user);
        skull.setIsIncendiary(false);
        skull.setYield(0.0f);
        Tags.mark(skull, KEY, user.getUniqueId());
        VersionUtil.playSound(user.getLocation(), 1.0f, 1.0f, "ENTITY_WITHER_SHOOT");
        send(user, tag() + "&7Wither kafası fırlattın!");
        return true;
    }

    private static boolean ours(final Object entity) {
        return entity instanceof WitherSkull && KEY.equals(Tags.effect((WitherSkull) entity));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrime(final ExplosionPrimeEvent event) {
        if (ours(event.getEntity())) {
            event.setFire(false);
            event.setRadius(2.0f);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplode(final EntityExplodeEvent event) {
        if (!ours(event.getEntity())) {
            return;
        }
        event.blockList().clear();
        event.setYield(0.0f);
        final Location loc = event.getLocation();
        VersionUtil.spawnParticle(loc, 3, 0.2, 0.1, "EXPLOSION", "EXPLOSION_LARGE");
        final UUID owner = Tags.owner(event.getEntity());
        final Player shooter = owner == null ? null : plugin.getServer().getPlayer(owner);
        if (owner == null) {
            return;
        }
        final int ticks = getDuration() * 20;
        for (final Player target : swords().enemiesAround(owner, loc, 3.0, null)) {
            if (shooter != null) {
                target.damage(8.0, shooter);
            } else {
                target.damage(8.0);
            }
            potion(target, VersionUtil.wither(), ticks, getAmplifier() - 1, true);
        }
    }
}
