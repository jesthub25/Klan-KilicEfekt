package com.jestron.clanplugin.sword.effects;

import com.jestron.clanplugin.ClanPlugin;
import com.jestron.clanplugin.sword.SwordEffect;
import com.jestron.clanplugin.util.VersionUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Blaze: menzildeki tüm düşmanları tutuşturur; kullanan kısa süre ateşten etkilenmez. */
public final class BlazeEffect extends SwordEffect implements Listener {

    private final Map<UUID, Long> fireImmuneUntil = new HashMap<>();

    public BlazeEffect(final ClanPlugin plugin) {
        super(plugin, "Blaze", 4, 60, 3, 3);
    }

    @Override
    protected String tag() {
        return "&8[&6Blaze&8] &7";
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
            target.setFireTicks(Math.max(target.getFireTicks(), seconds * 20));
            VersionUtil.spawnParticle(target.getLocation().add(0, 1, 0), 20, 0.4, 0.02, "FLAME");
            send(target, tag() + "&c&lATEŞTE YANIYORSUN! &7(" + seconds + "sn)");
        }
        fireImmuneUntil.put(user.getUniqueId(), System.currentTimeMillis() + (seconds + 1) * 1000L);
        VersionUtil.playSound(user.getLocation(), 1.0f, 1.0f, "ENTITY_BLAZE_SHOOT");
        send(user, tag() + "&a" + targets.size() + " düşman tutuşturuldu!");
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFire(final EntityDamageEvent event) {
        if (fireImmuneUntil.isEmpty() || !(event.getEntity() instanceof Player)) {
            return;
        }
        final EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause != EntityDamageEvent.DamageCause.FIRE && cause != EntityDamageEvent.DamageCause.FIRE_TICK) {
            return;
        }
        final Long until = fireImmuneUntil.get(event.getEntity().getUniqueId());
        if (until == null) {
            return;
        }
        if (System.currentTimeMillis() > until) {
            fireImmuneUntil.remove(event.getEntity().getUniqueId());
            return;
        }
        event.setCancelled(true);
        event.getEntity().setFireTicks(0);
    }

    @Override
    public void onQuit(final Player player) {
        fireImmuneUntil.remove(player.getUniqueId());
    }
}
