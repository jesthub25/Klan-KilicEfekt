package com.jestron.clanplugin.sword.effects;

import com.jestron.clanplugin.ClanPlugin;
import com.jestron.clanplugin.sword.SwordEffect;
import com.jestron.clanplugin.util.VersionUtil;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.List;

/** Efekt Silen: menzildeki düşmanların tüm iksir efektlerini siler. Silinecek efekt yoksa bekleme başlamaz. */
public final class EfektsilenEffect extends SwordEffect {

    public EfektsilenEffect(final ClanPlugin plugin) {
        super(plugin, "Efektsilen", 3, 90, 1, 1);
    }

    @Override
    protected String tag() {
        return "&8[&2Efekt Silen&8] &7";
    }

    @Override
    public boolean apply(final Player user) {
        final List<Player> targets = enemies(user);
        if (targets.isEmpty()) {
            noTarget(user);
            return false;
        }
        int cleared = 0;
        for (final Player target : targets) {
            final List<PotionEffect> active = new ArrayList<>(target.getActivePotionEffects());
            if (active.isEmpty()) {
                continue;
            }
            for (final PotionEffect effect : active) {
                target.removePotionEffect(effect.getType());
            }
            cleared++;
            send(target, tag() + "&cTüm efektlerin silindi!");
        }
        if (cleared == 0) {
            send(user, tag() + "&eYakındaki düşmanlarda silinecek efekt yok. &aBekleme süresi başlamadı.");
            return false;
        }
        VersionUtil.playSound(user.getLocation(), 1.0f, 1.2f, "BLOCK_BEACON_DEACTIVATE", "ENTITY_ZOMBIE_VILLAGER_CURE");
        send(user, tag() + "&a" + cleared + " düşmanın efektleri silindi!");
        return true;
    }

    @Override
    public boolean usesRadius() {
        return true;
    }
}
