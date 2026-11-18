package com.jestron.clanplugin.sword.effects;

import com.jestron.clanplugin.ClanPlugin;
import com.jestron.clanplugin.sword.SwordEffect;
import com.jestron.clanplugin.util.Tags;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sahibi için savaşan geçici mob çağıran efektlerin ortak temeli (Pillager, Slime).
 * Mob; sahibine, klanına ve dost klanlara saldırmaz, eşya/xp düşürmez, süre bitince kaybolur.
 */
public abstract class SummonEffect extends SwordEffect implements Listener {

    private static final double TARGET_RANGE = 16.0;

    private final Map<UUID, UUID> summons = new HashMap<>(); // mob -> sahip

    protected SummonEffect(final ClanPlugin plugin, final String name, final int cooldown, final int duration,
                           final int amplifier) {
        super(plugin, name, 0, cooldown, duration, amplifier);
    }

    protected abstract Mob spawn(Player user, Location at);

    @Override
    public boolean usesRadius() {
        return false;
    }

    @Override
    public boolean apply(final Player user) {
        final Mob mob = spawn(user, user.getLocation());
        if (mob == null) {
            return false;
        }
        final UUID owner = user.getUniqueId();
        Tags.mark(mob, getKey(), owner);
        mob.setRemoveWhenFarAway(false);
        summons.put(mob.getUniqueId(), owner);
        retarget(mob, owner);

        final long lifeTicks = getDuration() * 20L;
        new BukkitRunnable() {
            private long lived;

            @Override
            public void run() {
                lived += 20L;
                if (!mob.isValid() || mob.isDead() || lived >= lifeTicks || Bukkit.getPlayer(owner) == null) {
                    despawn(mob);
                    cancel();
                    return;
                }
                retarget(mob, owner);
            }
        }.runTaskTimer(plugin, 20L, 20L);
        return true;
    }

    private void retarget(final Mob mob, final UUID owner) {
        final LivingEntity current = mob.getTarget();
        if (current instanceof Player && current.isValid()
                && !swords().isSafe(owner, (Player) current)
                && swords().isPvPEnabledAt(current.getLocation())) {
            return; // geçerli düşman hedefi var
        }
        final List<Player> enemies = swords().enemiesAround(owner, mob.getLocation(), TARGET_RANGE, null);
        mob.setTarget(enemies.isEmpty() ? null : enemies.get(0));
    }

    private void despawn(final Mob mob) {
        summons.remove(mob.getUniqueId());
        if (mob.isValid()) {
            mob.remove();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(final EntityTargetEvent event) {
        final UUID owner = summons.get(event.getEntity().getUniqueId());
        if (owner == null) {
            return;
        }
        final Entity target = event.getTarget();
        if (target instanceof Player
                && (swords().isSafe(owner, (Player) target) || !swords().isPvPEnabledAt(target.getLocation()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(final EntityDeathEvent event) {
        if (summons.remove(event.getEntity().getUniqueId()) != null) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
    }

    protected final boolean isSummon(final Entity entity) {
        return summons.containsKey(entity.getUniqueId());
    }

    @Override
    public void onDisable() {
        for (final UUID id : new ArrayList<>(summons.keySet())) {
            final Entity entity = Bukkit.getEntity(id);
            if (entity != null) {
                entity.remove();
            }
        }
        summons.clear();
    }
}
