package com.jestron.clanplugin.sword;

import com.jestron.clanplugin.ClanPlugin;
import com.jestron.clanplugin.util.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Locale;

/**
 * Kılıç efekti temeli.
 *
 * apply() true dönerse efekt GERÇEKTEN işlemiştir ve bekleme süresi başlar.
 * false dönerse (hedef yok, hedeflerin hepsi dost, alan dolu vb.) bekleme süresi başlamaz.
 */
public abstract class SwordEffect {

    protected final ClanPlugin plugin;
    private final String name;
    private final String key;
    private final int defRadius;
    private final int defCooldown;
    private final int defDuration;
    private final int defAmplifier;

    protected SwordEffect(final ClanPlugin plugin, final String name, final int defRadius, final int defCooldown,
                          final int defDuration, final int defAmplifier) {
        this.plugin = plugin;
        this.name = name;
        this.key = name.toLowerCase(Locale.ROOT);
        this.defRadius = defRadius;
        this.defCooldown = defCooldown;
        this.defDuration = defDuration;
        this.defAmplifier = defAmplifier;
    }

    public final String getName() {
        return name;
    }

    public final String getKey() {
        return key;
    }

    /** @return true = efekt uygulandı (bekleme süresi başlar) */
    public abstract boolean apply(Player user);

    /** Menüde menzil satırı gösterilsin mi (kendine etki eden efektlerde false). */
    public boolean usesRadius() {
        return true;
    }

    // ------------------------------------------------------------------
    // Ayarlar (efekts.yml, yoksa sınıftaki varsayılan)
    // ------------------------------------------------------------------

    protected final int cfg(final String option, final int def) {
        final int v = plugin.getConfigManager().getEffectInt(name, option, def);
        return v > 0 ? v : def;
    }

    public int getRadius()    { return cfg("radius", defRadius); }
    public int getDuration()  { return cfg("duration", defDuration); }
    public int getAmplifier() { return cfg("amplifier", defAmplifier); }

    public int getCooldown() {
        final int v = plugin.getConfigManager().getEffectInt(name, "cooldown", defCooldown);
        return v >= 0 ? v : defCooldown;
    }

    public boolean isEnabledInConfig() {
        return plugin.getConfigManager().isEffectEnabled(name);
    }

    public String getPermission() {
        return plugin.getConfigManager().getEffectPermission(name);
    }

    // ------------------------------------------------------------------
    // Yardımcılar
    // ------------------------------------------------------------------

    protected final SwordManager swords() {
        return plugin.getSwordManager();
    }

    /** Menzildeki saldırılabilir düşmanlar (kendisi, klanı, dost klanlar ve PvP kapalı alanlar hariç), yakından uzağa. */
    protected final List<Player> enemies(final Player user) {
        return swords().getEnemies(user, getRadius());
    }

    protected final Player nearestEnemy(final Player user) {
        final List<Player> list = enemies(user);
        return list.isEmpty() ? null : list.get(0);
    }

    protected final void send(final CommandSender to, final String message) {
        to.sendMessage(Text.color(message));
    }

    protected final void noTarget(final Player user) {
        user.sendMessage(plugin.getMessageManager().get("sword.no-target"));
    }

    /** Etiket: "&8[&bİsim&8] &7" */
    protected String tag() {
        return "&8[&b" + name + "&8] &7";
    }

    /** Var olan aynı efekti ezerek uygular (eski force=true davranışı, tüm sürümlerde aynı). */
    protected static void potion(final LivingEntity entity, final PotionEffectType type, final int ticks,
                                 final int amplifier, final boolean particles) {
        if (entity == null || type == null || ticks <= 0) {
            return;
        }
        entity.removePotionEffect(type);
        entity.addPotionEffect(new PotionEffect(type, ticks, Math.max(0, amplifier), false, particles));
    }

    // Yaşam döngüsü kancaları
    public void onJoin(final Player player) {
    }

    public void onQuit(final Player player) {
    }

    public void onDisable() {
    }
}
