package com.jestron.clanplugin.sword.effects;

import com.jestron.clanplugin.ClanPlugin;
import com.jestron.clanplugin.sword.SwordEffect;
import com.jestron.clanplugin.util.VersionUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Dondurma: menzildeki tüm düşmanlar süre boyunca yerinden kıpırdayamaz ve zıplayamaz
 * (bakış yönünü çevirebilir, düşüyorsa düşmeye devam eder). Ender incisi/koro meyvesiyle kaçamaz.
 *
 * Eski "Jump Boost 128" hilesi 1.20.5+ sürümlerde tam tersine çalıştığı için hareket
 * doğrudan sunucu tarafında kilitlenir; tüm sürümlerde aynı sonucu verir.
 */
public final class FreezeEffect extends SwordEffect implements Listener {

    private final Map<UUID, Long> frozenUntil = new HashMap<>();

    public FreezeEffect(final ClanPlugin plugin) {
        super(plugin, "Freeze", 5, 30, 3, 1);
    }

    @Override
    protected String tag() {
        return "&8[&bFreeze&8] &7";
    }

    @Override
    public boolean apply(final Player user) {
        final List<Player> targets = enemies(user);
        if (targets.isEmpty()) {
            noTarget(user);
            return false;
        }
        final int seconds = getDuration();
        final long until = System.currentTimeMillis() + seconds * 1000L;
        for (final Player target : targets) {
            frozenUntil.put(target.getUniqueId(), until);
            potion(target, VersionUtil.slowness(), seconds * 20, 6, false);
            VersionUtil.spawnParticle(target.getLocation().add(0, 1, 0), 25, 0.4, 0.02, "SNOWFLAKE", "SNOW_SHOVEL", "CLOUD");
            send(target, tag() + "&b&lDONDUN! &7Hareket edemezsin! &f(" + seconds + " saniye)");
        }
        VersionUtil.playSound(user.getLocation(), 1.0f, 1.4f, "BLOCK_GLASS_BREAK");
        send(user, tag() + "&a" + targets.size() + " düşman " + seconds + " saniye donduruldu!");
        return true;
    }

    private boolean isFrozen(final UUID id) {
        final Long until = frozenUntil.get(id);
        if (until == null) {
            return false;
        }
        if (System.currentTimeMillis() > until) {
            frozenUntil.remove(id);
            return false;
        }
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(final PlayerMoveEvent event) {
        if (frozenUntil.isEmpty()) {
            return;
        }
        final Location to = event.getTo();
        if (to == null || !isFrozen(event.getPlayer().getUniqueId())) {
            return;
        }
        final Location from = event.getFrom();
        if (from.getX() == to.getX() && from.getZ() == to.getZ() && to.getY() <= from.getY()) {
            return; // sadece bakış veya düşüş
        }
        final Location locked = from.clone();
        locked.setYaw(to.getYaw());
        locked.setPitch(to.getPitch());
        if (to.getY() < from.getY()) {
            locked.setY(to.getY());
        }
        event.setTo(locked);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(final PlayerTeleportEvent event) {
        final String cause = String.valueOf(event.getCause());
        if (("ENDER_PEARL".equals(cause) || "CHORUS_FRUIT".equals(cause))
                && isFrozen(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @Override
    public void onQuit(final Player player) {
        frozenUntil.remove(player.getUniqueId());
    }

    @Override
    public void onDisable() {
        frozenUntil.clear();
    }
}
