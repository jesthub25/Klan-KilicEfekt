package com.jestron.clanplugin.sword.effects;

import com.jestron.clanplugin.ClanPlugin;
import com.jestron.clanplugin.sword.SwordEffect;
import com.jestron.clanplugin.util.VersionUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.util.BoundingBox;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Örümcek: menzildeki TÜM düşmanlara örümcek ağı kurar.
 *
 * - Normal duruşta ağ, oyuncunun hitbox'ının ayak kısmına en çok denk gelen bloğa konur.
 * - Elytra ile süzülürken (veya yüzme/emekleme gibi alçak hitbox'ta) hitbox'ın tam merkezindeki bloğa konur.
 * - Oyuncu bloğun ortasında değilse, hitbox'ın en çok kapladığı blok seçilir.
 * - O blokta su, lav, buton, başka bir ağ vb. HERHANGİ bir şey varsa o oyuncuya ağ kurulmaz.
 * - Bekleme süresi yalnızca en az bir oyuncuya gerçekten ağ kurulduysa başlar.
 */
public final class SpiderEffect extends SwordEffect implements Listener {

    private final Set<Block> webs = new HashSet<>();

    public SpiderEffect(final ClanPlugin plugin) {
        super(plugin, "Spider", 5, 90, 3, 1);
    }

    @Override
    protected String tag() {
        return "&8[&#8A3A02Spider&8] &7";
    }

    @Override
    public boolean apply(final Player user) {
        final List<Player> targets = enemies(user);
        if (targets.isEmpty()) {
            noTarget(user);
            return false;
        }
        final long lifeTicks = getDuration() * 20L;
        int placed = 0;
        final StringBuilder blocked = new StringBuilder();
        for (final Player target : targets) {
            final Block block = webBlockFor(target);
            if (block == null || !block.getType().isAir()) {
                if (blocked.length() > 0) {
                    blocked.append(", ");
                }
                blocked.append(target.getName());
                continue;
            }
            block.setType(Material.COBWEB);
            webs.add(block);
            placed++;
            send(target, tag() + "&cÖrümcek ağına yakalandın!");
            VersionUtil.spawnParticle(block.getLocation().add(0.5, 0.5, 0.5), 12, 0.3, 0.01, "CLOUD");
            Bukkit.getScheduler().runTaskLater(plugin, () -> removeWeb(block), lifeTicks);
        }

        if (placed == 0) {
            send(user, tag() + "&cHiçbir hedefe ağ kurulamadı &7(hedeflerin bulunduğu blok dolu). &aBekleme süresi başlamadı.");
            return false;
        }
        VersionUtil.playSound(user.getLocation(), 1.0f, 0.8f, "ENTITY_SPIDER_AMBIENT");
        send(user, tag() + "&a" + placed + " düşmana ağ kuruldu!");
        if (blocked.length() > 0) {
            send(user, tag() + "&7Ağ kurulamayan: &f" + blocked);
        }
        return true;
    }

    /**
     * Ağın konacağı blok.
     *  X/Z: hitbox merkezinin bulunduğu sütun (0.6 genişlikte hitbox'ta en büyük kesişim her zaman bu sütundur).
     *  Y  : süzülme/alçak hitbox -> merkez; ayakta -> ayak seviyesindeki iki bloktan hitbox'la daha çok kesişen.
     */
    private static Block webBlockFor(final Player player) {
        final World world = player.getWorld();
        final BoundingBox box = player.getBoundingBox();
        final int x = floor(box.getCenterX());
        final int z = floor(box.getCenterZ());
        final int y;
        if (player.isGliding() || box.getHeight() < 1.0) {
            y = floor(box.getCenterY());
        } else {
            final double minY = box.getMinY();
            final double maxY = box.getMaxY();
            final int k = floor(minY);
            final double overlapFeet = Math.min(k + 1, maxY) - minY;      // ayakların bulunduğu blok
            final double overlapAbove = Math.min(k + 2, maxY) - (k + 1);  // bir üstü
            y = overlapFeet >= overlapAbove ? k : k + 1;
        }
        if (y < 0 && !hasNegativeHeight(world)) {
            return null;
        }
        if (y >= world.getMaxHeight()) {
            return null;
        }
        return world.getBlockAt(x, y, z);
    }

    /** World#getMinHeight 1.17'de geldi; 1.16 API'sinde yok, reflection ile çağrılır. */
    private static boolean hasNegativeHeight(final World world) {
        try {
            final Object min = World.class.getMethod("getMinHeight").invoke(world);
            return min instanceof Integer && (Integer) min < 0;
        } catch (final Throwable ignored) {
            return false; // 1.16: dünya 0'dan başlar
        }
    }

    private static int floor(final double v) {
        return (int) Math.floor(v);
    }

    private void removeWeb(final Block block) {
        webs.remove(block);
        if (block.getType() == Material.COBWEB) {
            block.setType(Material.AIR);
        }
    }

    /** Efekt ağları kırılınca ip düşmesin (ip çiftliği engeli). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(final BlockBreakEvent event) {
        if (webs.remove(event.getBlock())) {
            event.setDropItems(false);
        }
    }

    @Override
    public void onDisable() {
        for (final Block block : webs) {
            if (block.getType() == Material.COBWEB) {
                block.setType(Material.AIR);
            }
        }
        webs.clear();
    }
}
