package com.jestron.clanplugin.clan;

import com.jestron.clanplugin.ClanPlugin;
import com.jestron.clanplugin.util.Tags;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Klan içi / dost klan arası dost ateşi koruması, sıralama menüsü koruması ve skor takibi.
 */
public final class ClanListener implements Listener {

    private final ClanPlugin plugin;
    private final Map<UUID, Long> lastWarn = new HashMap<>();

    public ClanListener(final ClanPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    // Sıralama menüsü
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(final InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof RankingGUI.Holder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(final InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof RankingGUI.Holder) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------
    // Dost ateşi
    // ------------------------------------------------------------------

    /** Hasarı veren oyuncuyu bulur: doğrudan vuruş, ok/mermi, TNT, kristal, iksir bulutu. */
    static Player resolveAttacker(final Entity damager) {
        if (damager instanceof Player) {
            return (Player) damager;
        }
        if (damager instanceof Projectile) {
            final ProjectileSource shooter = ((Projectile) damager).getShooter();
            return shooter instanceof Player ? (Player) shooter : null;
        }
        if (damager instanceof TNTPrimed) {
            final Entity source = ((TNTPrimed) damager).getSource();
            return source instanceof Player ? (Player) source : null;
        }
        if (damager instanceof AreaEffectCloud) {
            final ProjectileSource source = ((AreaEffectCloud) damager).getSource();
            return source instanceof Player ? (Player) source : null;
        }
        if (damager instanceof EnderCrystal) {
            final UUID owner = Tags.parse(Tags.get(damager, Tags.CRYSTAL_OWNER));
            return owner == null ? null : damager.getServer().getPlayer(owner);
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFriendlyFire(final EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        if (plugin.getConfig().getBoolean("clan.friendly-fire", false)) {
            return;
        }
        // Kılıç efektlerinin oluşturduğu varlıklar SwordListener'da ayrıca ele alınır.
        if (Tags.effect(event.getDamager()) != null) {
            return;
        }
        final Player victim = (Player) event.getEntity();
        final Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        final ClanManager clans = plugin.getClanManager();
        final Clan a = clans.getPlayerClan(attacker.getUniqueId());
        if (a == null) {
            return;
        }
        final Clan v = clans.getPlayerClan(victim.getUniqueId());
        if (v == null) {
            return;
        }
        if (a == v) {
            event.setCancelled(true);
            warn(attacker, plugin.getMessageManager().get("sword.friendly-fire"));
        } else if (plugin.getAllyManager().isAllied(a.getName(), v.getName())) {
            event.setCancelled(true);
            warn(attacker, plugin.getMessageManager().get("klan.ally-fire", "§cDost klan üyelerine vuramazsın!"));
        }
    }

    /** Kristale vuran oyuncuyu kaydeder: patlama hasarı ona yazılır. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCrystalHit(final EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof EnderCrystal)) {
            return;
        }
        final Player attacker = resolveAttacker(event.getDamager());
        if (attacker != null) {
            Tags.set(event.getEntity(), Tags.CRYSTAL_OWNER, attacker.getUniqueId().toString());
        }
    }

    private void warn(final Player player, final String message) {
        final long now = System.currentTimeMillis();
        final Long last = lastWarn.get(player.getUniqueId());
        if (last == null || now - last > 1500L) {
            lastWarn.put(player.getUniqueId(), now);
            player.sendMessage(message);
        }
    }

    // ------------------------------------------------------------------
    // Skor
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(final PlayerDeathEvent event) {
        final Player victim = event.getEntity();
        final ClanManager clans = plugin.getClanManager();
        clans.addDeath(clans.getPlayerClan(victim.getUniqueId()));
        final Player killer = victim.getKiller();
        if (killer != null && !killer.getUniqueId().equals(victim.getUniqueId())) {
            clans.addKill(clans.getPlayerClan(killer.getUniqueId()));
        }
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        lastWarn.remove(event.getPlayer().getUniqueId());
        plugin.getInviteManager().forgetPlayer(event.getPlayer().getUniqueId());
    }
}
