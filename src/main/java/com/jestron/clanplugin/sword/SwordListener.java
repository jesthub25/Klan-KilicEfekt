package com.jestron.clanplugin.sword;

import com.jestron.clanplugin.ClanPlugin;
import com.jestron.clanplugin.util.Tags;
import com.jestron.clanplugin.util.VersionUtil;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;

import java.util.UUID;

/**
 * Kılıç kullanımı ve efekt kaynaklı hasarların dost/düşman süzgeci.
 */
public final class SwordListener implements Listener {

    private final ClanPlugin plugin;

    public SwordListener(final ClanPlugin plugin) {
        this.plugin = plugin;
    }

    private SwordManager swords() {
        return plugin.getSwordManager();
    }

    // ------------------------------------------------------------------
    // Kullanım
    // ------------------------------------------------------------------

    /*
     * ignoreCancelled KULLANILMAZ: havaya sağ tık olayı Bukkit'te varsayılan olarak
     * "iptal edilmiş" gelir; ignoreCancelled=true olsaydı havaya sağ tık hiç çalışmazdı.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(final PlayerInteractEvent event) {
        final Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return; // ikinci el için ikinci kez tetiklenmesin
        }
        final Player player = event.getPlayer();
        final ItemStack item = player.getInventory().getItemInMainHand();
        if (swords().handleUse(player, item)) {
            event.setCancelled(true);
        }
    }

    /** Nişangah bir oyuncunun/mobun üzerindeyken sağ tık PlayerInteractEvent üretmez; burada yakalanır. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(final PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        final Player player = event.getPlayer();
        final ItemStack item = player.getInventory().getItemInMainHand();
        if (swords().handleUse(player, item)) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------
    // Efekt kaynaklı hasarlar
    // ------------------------------------------------------------------

    /** Efekt varlığından gelen hasarın sahibini bulur (mermi, patlama, çağrılan mob, mobun oku, ejderha nefesi). */
    private static UUID effectOwner(final Entity damager) {
        UUID owner = Tags.owner(damager);
        if (owner != null) {
            return owner;
        }
        if (damager instanceof Projectile) {
            final ProjectileSource shooter = ((Projectile) damager).getShooter();
            if (shooter instanceof Entity) {
                owner = Tags.owner((Entity) shooter);
                if (owner != null) {
                    return owner;
                }
            }
        }
        if (damager instanceof AreaEffectCloud) {
            final AreaEffectCloud cloud = (AreaEffectCloud) damager;
            if (cloud.getSource() instanceof Player && VersionUtil.isDragonBreath(cloud)) {
                return ((Player) cloud.getSource()).getUniqueId();
            }
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEffectDamage(final EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        final UUID owner = effectOwner(event.getDamager());
        if (owner == null) {
            return;
        }
        final Player victim = (Player) event.getEntity();
        if (swords().isSafe(owner, victim) || !swords().isPvPEnabledAt(victim.getLocation())) {
            event.setCancelled(true);
        }
    }

    /** Ejderha nefesi bulutu: kendine, klanına ve dost klanlara hiç işlemez. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCloudApply(final AreaEffectCloudApplyEvent event) {
        final AreaEffectCloud cloud = event.getEntity();
        if (!(cloud.getSource() instanceof Player) || !VersionUtil.isDragonBreath(cloud)) {
            return;
        }
        final UUID owner = ((Player) cloud.getSource()).getUniqueId();
        event.getAffectedEntities().removeIf((LivingEntity le) -> le instanceof Player
                && (swords().isSafe(owner, (Player) le) || !swords().isPvPEnabledAt(le.getLocation())));
    }

    // ------------------------------------------------------------------
    // Giriş / çıkış
    // ------------------------------------------------------------------

    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        swords().onJoin(event.getPlayer());
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        swords().onQuit(event.getPlayer());
    }
}
