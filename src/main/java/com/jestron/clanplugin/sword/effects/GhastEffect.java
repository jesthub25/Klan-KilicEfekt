package com.jestron.clanplugin.sword.effects;

import com.jestron.clanplugin.ClanPlugin;
import com.jestron.clanplugin.sword.SwordEffect;
import com.jestron.clanplugin.util.Tags;
import com.jestron.clanplugin.util.VersionUtil;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LargeFireball;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;

/**
 * Ghast: ateş topu fırlatır. Patlama blok kırmaz ve ateş çıkarmaz.
 * Kendine, klanına ve dost klanlara patlama/çarpma hasarı SwordListener'da iptal edilir.
 */
public final class GhastEffect extends SwordEffect implements Listener {

    private static final String KEY = "ghast";

    public GhastEffect(final ClanPlugin plugin) {
        super(plugin, "Ghast", 0, 60, 3, 1);
    }

    @Override
    public boolean usesRadius() {
        return false;
    }

    @Override
    protected String tag() {
        return "&8[&fGhast&8] &7";
    }

    @Override
    public boolean apply(final Player user) {
        final Fireball fireball = user.launchProjectile(LargeFireball.class);
        fireball.setShooter(user);
        fireball.setIsIncendiary(false);
        fireball.setYield(1.5f);
        Tags.mark(fireball, KEY, user.getUniqueId());
        VersionUtil.playSound(user.getLocation(), 1.0f, 1.0f, "ENTITY_GHAST_SHOOT");
        send(user, tag() + "&7Ateş topu fırlattın!");
        return true;
    }

    private static boolean ours(final Object entity) {
        return entity instanceof Fireball && KEY.equals(Tags.effect((Fireball) entity));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrime(final ExplosionPrimeEvent event) {
        if (ours(event.getEntity())) {
            event.setFire(false);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplode(final EntityExplodeEvent event) {
        if (ours(event.getEntity())) {
            event.blockList().clear();
            event.setYield(0.0f);
        }
    }
}
