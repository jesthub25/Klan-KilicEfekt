package com.jestron.clanplugin.sword.effects;

import com.jestron.clanplugin.ClanPlugin;
import com.jestron.clanplugin.util.Tags;
import com.jestron.clanplugin.util.VersionUtil;
import org.bukkit.Location;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.SlimeSplitEvent;

/** Slime: senin için savaşan bir Slime çağırır (boyut: efekts.yml "amplifier"). Ölünce bölünmez. */
public final class SlimeEffect extends SummonEffect {

    public SlimeEffect(final ClanPlugin plugin) {
        super(plugin, "Slime", 90, 15, 4);
    }

    @Override
    protected String tag() {
        return "&8[&aSlime&8] &7";
    }

    @Override
    protected Mob spawn(final Player user, final Location at) {
        if (at.getWorld() == null) {
            return null;
        }
        final Slime slime = at.getWorld().spawn(at, Slime.class);
        slime.setSize(Math.max(1, Math.min(8, getAmplifier())));
        slime.setCustomName(user.getName() + " Slime'ı");
        slime.setCustomNameVisible(true);
        VersionUtil.playSound(at, 1.0f, 1.0f, "ENTITY_SLIME_SQUISH");
        send(user, tag() + "&aSenin için savaşan bir Slime çağırıldı! &7(" + getDuration() + "sn)");
        return slime;
    }

    /** Çağrılan slime ölünce sahipsiz (herkese saldıran) küçük slime'lara bölünmesin. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSplit(final SlimeSplitEvent event) {
        // Ölüm olayı bölünmeden önce gelebileceği için metadata etiketine bakılır
        if (isSummon(event.getEntity()) || getKey().equals(Tags.effect(event.getEntity()))) {
            event.setCancelled(true);
        }
    }
}
