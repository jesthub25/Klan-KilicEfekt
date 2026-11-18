package com.jestron.clanplugin.sword.effects;

import com.jestron.clanplugin.ClanPlugin;
import com.jestron.clanplugin.sword.SwordEffect;
import com.jestron.clanplugin.util.VersionUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Enderman: baktığın yöne (yatay) güvenli zemine ışınlar.
 * Duvarların içinden geçmez, ayak ve kafa bloğu boş olmalı, altında sağlam ve tehlikesiz zemin olmalı.
 * Mesafe: efekts.yml "distance" (yoksa eski uyumluluk için "duration").
 */
public final class EndermanEffect extends SwordEffect {

    public EndermanEffect(final ClanPlugin plugin) {
        super(plugin, "Enderman", 0, 60, 3, 1);
    }

    @Override
    public boolean usesRadius() {
        return false;
    }

    @Override
    protected String tag() {
        return "&8[&5Enderman&8] &7";
    }

    @Override
    public boolean apply(final Player user) {
        final int distance = cfg("distance", getDuration());
        final Location start = user.getLocation();
        final World world = start.getWorld();
        if (world == null) {
            return false;
        }
        final Vector dir = start.getDirection().setY(0);
        if (dir.lengthSquared() < 1.0E-4) {
            send(user, tag() + "&cDüz bir yöne bakmalısın (yukarı/aşağı değil).");
            return false;
        }
        dir.normalize();

        // Yarım blok/basamak üstünde durulsa da doğru ayak seviyesi
        final int feetY = (int) Math.floor(start.getY() + 0.5);
        Location best = null;
        for (double step = 0.5; step <= distance + 1.0E-6; step += 0.5) {
            final Location probe = start.clone().add(dir.clone().multiply(step));
            probe.setY(feetY);
            final Block feet = world.getBlockAt(probe.getBlockX(), feetY, probe.getBlockZ());
            final Block head = feet.getRelative(0, 1, 0);
            if (!passable(feet) || !passable(head)) {
                break; // duvar: içinden geçme
            }
            if (safeGround(feet.getRelative(0, -1, 0))) {
                best = probe;
            }
        }
        if (best == null || best.distanceSquared(start) < 1.0) {
            send(user, tag() + "&c&lIşınlanamadın! &7İleride güvenli zemin yok.");
            return false;
        }
        best.setYaw(start.getYaw());
        best.setPitch(start.getPitch());
        VersionUtil.spawnParticle(start.clone().add(0, 1, 0), 30, 0.4, 0.2, "PORTAL");
        user.teleport(best);
        VersionUtil.spawnParticle(best.clone().add(0, 1, 0), 30, 0.4, 0.2, "PORTAL");
        VersionUtil.playSound(best, 1.0f, 1.0f, "ENTITY_ENDERMAN_TELEPORT");
        send(user, tag() + "&dIşınlandın!");
        return true;
    }

    private static boolean passable(final Block block) {
        final Material type = block.getType();
        return block.isPassable() && type != Material.LAVA && !type.name().contains("FIRE")
                && type != Material.COBWEB && type != Material.SWEET_BERRY_BUSH;
    }

    private static boolean safeGround(final Block block) {
        final Material type = block.getType();
        return type.isSolid() && type != Material.CACTUS && type != Material.MAGMA_BLOCK
                && !type.name().contains("CAMPFIRE");
    }
}
