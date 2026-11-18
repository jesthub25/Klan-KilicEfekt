package com.jestron.clanplugin.sword.effects;

import com.jestron.clanplugin.ClanPlugin;
import com.jestron.clanplugin.sword.SwordEffect;
import com.jestron.clanplugin.util.Tags;
import com.jestron.clanplugin.util.VersionUtil;
import org.bukkit.entity.DragonFireball;
import org.bukkit.entity.Player;

/**
 * Ejderha: ejderha ateş topu fırlatır; çarptığı yerde ejderha nefesi bulutu oluşur.
 *
 * Bulutun hasarı (AreaEffectCloudApplyEvent) ve doğrudan hasar SwordListener'da süzülür:
 * kullanan oyuncu, klan üyeleri ve dost klan üyeleri hiç etkilenmez.
 */
public final class EjderhaEffect extends SwordEffect {

    public EjderhaEffect(final ClanPlugin plugin) {
        super(plugin, "Ejderha", 0, 60, 10, 1);
    }

    @Override
    public boolean usesRadius() {
        return false;
    }

    @Override
    protected String tag() {
        return "&8[&5Ejderha&8] &7";
    }

    @Override
    public boolean apply(final Player user) {
        final DragonFireball fireball = user.launchProjectile(DragonFireball.class);
        fireball.setShooter(user);
        fireball.setVelocity(user.getEyeLocation().getDirection().multiply(1.5));
        Tags.mark(fireball, getKey(), user.getUniqueId());
        VersionUtil.playSound(user.getLocation(), 1.0f, 1.0f, "ENTITY_ENDER_DRAGON_SHOOT");
        send(user, tag() + "&7Ejderha ateşi fırlattın!");
        return true;
    }
}
