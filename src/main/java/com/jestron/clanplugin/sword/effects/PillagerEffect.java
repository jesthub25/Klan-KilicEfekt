package com.jestron.clanplugin.sword.effects;

import com.jestron.clanplugin.ClanPlugin;
import com.jestron.clanplugin.util.VersionUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Pillager;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

/** Pillager: senin için savaşan, arbaletli bir Pillager çağırır (can: efekts.yml "health"). */
public final class PillagerEffect extends SummonEffect {

    public PillagerEffect(final ClanPlugin plugin) {
        super(plugin, "Pillager", 120, 20, 1);
    }

    @Override
    protected String tag() {
        return "&8[&8Pillager&8] &7";
    }

    @Override
    protected Mob spawn(final Player user, final Location at) {
        if (at.getWorld() == null) {
            return null;
        }
        final Pillager pillager = at.getWorld().spawn(at, Pillager.class);
        final EntityEquipment equipment = pillager.getEquipment();
        if (equipment != null) {
            equipment.setItemInMainHand(new ItemStack(Material.CROSSBOW));
            equipment.setItemInMainHandDropChance(0.0f);
        }
        VersionUtil.setMaxHealth(pillager, cfg("health", 200));
        pillager.setCustomName(user.getName() + " Pillager'ı");
        pillager.setCustomNameVisible(true);
        VersionUtil.playSound(at, 1.0f, 1.0f, "ENTITY_PILLAGER_CELEBRATE", "ENTITY_PILLAGER_AMBIENT");
        send(user, tag() + "&6Senin için savaşan bir Pillager çağırıldı! &7(" + getDuration() + "sn)");
        return pillager;
    }
}
