package com.jestron.clanplugin.sword;

import com.jestron.clanplugin.ClanPlugin;
import com.jestron.clanplugin.clan.Clan;
import com.jestron.clanplugin.clan.ClanManager;
import com.jestron.clanplugin.hook.WorldGuardHook;
import com.jestron.clanplugin.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public final class SwordManager {

    private static final long TRIGGER_DEBOUNCE_MS = 150L;
    private static final long NOTICE_INTERVAL_MS = 900L;

    private final ClanPlugin plugin;
    private final NamespacedKey effectKey;
    private final NamespacedKey activeKey;
    private final Map<String, SwordEffect> effects = new LinkedHashMap<>();
    private final Map<String, ItemStack> templates = new LinkedHashMap<>();
    private final Set<String> disabled = new LinkedHashSet<>();
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    private final Map<UUID, Long> lastTrigger = new HashMap<>();
    private final Map<UUID, Long> lastNotice = new HashMap<>();
    private final File stateFile;
    private boolean worldGuardErrorLogged;

    public SwordManager(final ClanPlugin plugin) {
        this.plugin = plugin;
        this.effectKey = new NamespacedKey(plugin, "sword-effect");
        this.activeKey = new NamespacedKey(plugin, "sword-active");
        this.stateFile = new File(plugin.getDataFolder(), "durum.yml");
        loadState();
    }

    // ------------------------------------------------------------------
    // Kayıt / şablonlar
    // ------------------------------------------------------------------

    public void registerEffect(final SwordEffect effect) {
        effects.put(effect.getKey(), effect);
    }

    public Collection<SwordEffect> getEffects() {
        return Collections.unmodifiableCollection(effects.values());
    }

    public SwordEffect getEffect(final String name) {
        return name == null ? null : effects.get(name.toLowerCase(Locale.ROOT));
    }

    public NamespacedKey getEffectKey() {
        return effectKey;
    }

    public Map<String, ItemStack> getTemplates() {
        return Collections.unmodifiableMap(templates);
    }

    public void reloadSwords() {
        templates.clear();
        for (final SwordEffect effect : effects.values()) {
            final ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
            final ItemMeta meta = sword.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(plugin.getConfigManager().getSwordName(effect.getName()));
                meta.setLore(plugin.getConfigManager().getSwordLore(effect.getName()));
                final PersistentDataContainer pdc = meta.getPersistentDataContainer();
                pdc.set(effectKey, PersistentDataType.STRING, effect.getKey());
                pdc.set(activeKey, PersistentDataType.BYTE, (byte) 1);
                sword.setItemMeta(meta);
            }
            templates.put(effect.getKey(), sword);
        }
    }

    // ------------------------------------------------------------------
    // Global aç/kapat (durum.yml - yeniden başlatmada korunur)
    // ------------------------------------------------------------------

    private void loadState() {
        disabled.clear();
        if (!stateFile.exists()) {
            return;
        }
        final YamlConfiguration cfg = YamlConfiguration.loadConfiguration(stateFile);
        for (final String s : cfg.getStringList("disabled-effects")) {
            disabled.add(s.toLowerCase(Locale.ROOT));
        }
    }

    private void saveState() {
        final YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("disabled-effects", new ArrayList<>(disabled));
        try {
            cfg.save(stateFile);
        } catch (final IOException e) {
            plugin.getLogger().log(Level.WARNING, "durum.yml kaydedilemedi", e);
        }
    }

    /** Hem efekts.yml'de açık hem de menüden kapatılmamış mı. */
    public boolean isEffectGloballyActive(final String name) {
        final SwordEffect effect = getEffect(name);
        return effect != null && effect.isEnabledInConfig() && !disabled.contains(effect.getKey());
    }

    public boolean isDisabledByMenu(final String name) {
        return name != null && disabled.contains(name.toLowerCase(Locale.ROOT));
    }

    public void setDisabledByMenu(final String name, final boolean off) {
        final String k = name.toLowerCase(Locale.ROOT);
        if (off) {
            disabled.add(k);
        } else {
            disabled.remove(k);
        }
        saveState();
    }

    // ------------------------------------------------------------------
    // Eşya işlemleri
    // ------------------------------------------------------------------

    public static boolean isSword(final ItemStack item) {
        return item != null && item.getType().name().endsWith("_SWORD");
    }

    public String getSwordEffect(final ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return null;
        }
        final ItemMeta meta = item.getItemMeta();
        return meta == null ? null : meta.getPersistentDataContainer().get(effectKey, PersistentDataType.STRING);
    }

    public boolean isSwordActive(final ItemStack item) {
        final String effect = getSwordEffect(item);
        if (effect == null || !isEffectGloballyActive(effect)) {
            return false;
        }
        final Byte active = item.getItemMeta().getPersistentDataContainer().get(activeKey, PersistentDataType.BYTE);
        return active == null || active != 0;
    }

    public boolean giveSwordCopy(final Player player, final String effectName) {
        final SwordEffect effect = getEffect(effectName);
        final ItemStack template = effect == null ? null : templates.get(effect.getKey());
        if (template == null) {
            return false;
        }
        final ItemStack copy = template.clone();
        final ItemMeta meta = copy.getItemMeta();
        if (meta != null) {
            final boolean active = isEffectGloballyActive(effect.getKey());
            final List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<String>();
            for (int i = 0; i < lore.size(); i++) {
                if (Text.strip(lore.get(i)).contains("Durum:")) {
                    lore.set(i, Text.color("&7Durum: " + (active ? "&aAKTİF" : "&cPASİF")));
                }
            }
            meta.setLore(lore);
            copy.setItemMeta(meta);
        }
        final Map<Integer, ItemStack> leftover = player.getInventory().addItem(copy);
        if (leftover.isEmpty()) {
            player.sendMessage(Text.color("&a✓ " + effect.getName() + " kılıcı envanterine eklendi!"));
        } else {
            for (final ItemStack drop : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
            player.sendMessage(Text.color("&e⚠ Envanterin doluydu, kılıç yere bırakıldı!"));
        }
        return true;
    }

    /** Tüm online oyuncuların envanterindeki efektli kılıç sayıları (tek geçiş). */
    public Map<String, Integer> countSwords() {
        final Map<String, Integer> counts = new HashMap<>();
        for (final Player p : Bukkit.getOnlinePlayers()) {
            for (final ItemStack item : p.getInventory().getContents()) {
                final String effect = getSwordEffect(item);
                if (effect != null) {
                    counts.merge(effect, item.getAmount(), Integer::sum);
                }
            }
        }
        return counts;
    }

    public boolean addEffectToSword(final ItemStack item, final SwordEffect effect) {
        if (!isSword(item) || getSwordEffect(item) != null) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            meta = Bukkit.getItemFactory().getItemMeta(item.getType());
            if (meta == null) {
                return false;
            }
        }
        final PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(effectKey, PersistentDataType.STRING, effect.getKey());
        pdc.set(activeKey, PersistentDataType.BYTE, (byte) 1);
        meta.setDisplayName(plugin.getConfigManager().getSwordName(effect.getName()));
        meta.setLore(plugin.getConfigManager().getSwordLore(effect.getName()));
        item.setItemMeta(meta);
        return true;
    }

    public boolean removeEffectFromSword(final ItemStack item) {
        if (getSwordEffect(item) == null) {
            return false;
        }
        final ItemMeta meta = item.getItemMeta();
        final PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.remove(effectKey);
        pdc.remove(activeKey);
        meta.setDisplayName(null);
        meta.setLore(null);
        item.setItemMeta(meta);
        return true;
    }

    // ------------------------------------------------------------------
    // Dost / düşman / PvP kararları
    // ------------------------------------------------------------------

    /** Hedef, sahibi için güvenli mi: kendisi, aynı klan veya dost klan. */
    public boolean isSafe(final UUID owner, final Player target) {
        if (owner == null || target == null) {
            return false;
        }
        if (owner.equals(target.getUniqueId())) {
            return true;
        }
        final ClanManager clans = plugin.getClanManager();
        final Clan a = clans.getPlayerClan(owner);
        if (a == null) {
            return false;
        }
        final Clan b = clans.getPlayerClan(target.getUniqueId());
        if (b == null) {
            return false;
        }
        return a == b || plugin.getAllyManager().isAllied(a.getName(), b.getName());
    }

    public boolean isSafe(final Player user, final Player target) {
        return user != null && isSafe(user.getUniqueId(), target);
    }

    /**
     * WorldGuard yoksa her yer serbest. WorldGuard varsa bölge + __global__ PvP flag'i
     * birlikte değerlendirilir; "sword.require-pvp-region: true" ise ayrıca bölge içinde olmak gerekir.
     */
    public boolean isPvPEnabledAt(final Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) {
            return true;
        }
        try {
            return WorldGuardHook.isPvPAllowed(location, plugin.getConfig().getBoolean("sword.require-pvp-region", false));
        } catch (final Throwable t) {
            if (!worldGuardErrorLogged) {
                worldGuardErrorLogged = true;
                plugin.getLogger().log(Level.WARNING, "WorldGuard PvP sorgusu başarısız, PvP serbest kabul ediliyor.", t);
            }
            return true;
        }
    }

    /** Efektin hedef alabileceği oyuncu mu (hayatta, survival/adventure, NPC değil). */
    public static boolean isTargetable(final Player p) {
        if (p == null || !p.isOnline() || p.isDead() || !p.isValid() || p.hasMetadata("NPC")) {
            return false;
        }
        final GameMode mode = p.getGameMode();
        return mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE;
    }

    public List<Player> getEnemies(final Player user, final int radius) {
        if (radius <= 0 || !isPvPEnabledAt(user.getLocation())) {
            return Collections.emptyList();
        }
        return enemiesAround(user.getUniqueId(), user.getLocation(), radius, user);
    }

    /** Bir konum çevresinde, sahibi için düşman olan oyuncular (çağrılan moblar için). */
    public List<Player> enemiesAround(final UUID owner, final Location center, final double radius,
                                      final Entity exclude) {
        if (center == null || center.getWorld() == null) {
            return Collections.emptyList();
        }
        final double r2 = radius * radius;
        final List<Player> out = new ArrayList<>();
        for (final Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof Player) || entity.equals(exclude)) {
                continue;
            }
            final Player p = (Player) entity;
            if (!isTargetable(p) || p.getLocation().distanceSquared(center) > r2) {
                continue;
            }
            if (isSafe(owner, p) || !isPvPEnabledAt(p.getLocation())) {
                continue;
            }
            out.add(p);
        }
        out.sort(Comparator.comparingDouble((Player p) -> p.getLocation().distanceSquared(center)));
        return out;
    }

    // ------------------------------------------------------------------
    // Bekleme süresi
    // ------------------------------------------------------------------

    public long getCooldownRemaining(final Player player, final SwordEffect effect) {
        final Map<String, Long> map = cooldowns.get(player.getUniqueId());
        if (map == null) {
            return 0L;
        }
        final Long end = map.get(effect.getKey());
        if (end == null) {
            return 0L;
        }
        final long remaining = end - System.currentTimeMillis();
        return remaining <= 0 ? 0L : (remaining + 999L) / 1000L;
    }

    private void setCooldown(final Player player, final SwordEffect effect) {
        final int seconds = effect.getCooldown();
        if (seconds <= 0) {
            return;
        }
        cooldowns.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>())
                .put(effect.getKey(), System.currentTimeMillis() + seconds * 1000L);
    }

    /** Süresi dolmuş kayıtları temizler (periyodik). */
    public void purgeExpired() {
        final long now = System.currentTimeMillis();
        final Iterator<Map.Entry<UUID, Map<String, Long>>> it = cooldowns.entrySet().iterator();
        while (it.hasNext()) {
            final Map<String, Long> map = it.next().getValue();
            map.values().removeIf(end -> end <= now);
            if (map.isEmpty()) {
                it.remove();
            }
        }
    }

    private void notice(final Player player, final String message) {
        final long now = System.currentTimeMillis();
        final Long last = lastNotice.get(player.getUniqueId());
        if (last == null || now - last >= NOTICE_INTERVAL_MS) {
            lastNotice.put(player.getUniqueId(), now);
            player.sendMessage(message);
        }
    }

    // ------------------------------------------------------------------
    // Kullanım
    // ------------------------------------------------------------------

    /** @return bu eşya efektli bir kılıçsa true (olay iptal edilmeli) */
    public boolean handleUse(final Player player, final ItemStack item) {
        final String effectName = getSwordEffect(item);
        if (effectName == null || !isSword(item)) {
            return false;
        }
        final long now = System.currentTimeMillis();
        final Long last = lastTrigger.get(player.getUniqueId());
        if (last != null && now - last < TRIGGER_DEBOUNCE_MS) {
            return true;
        }
        lastTrigger.put(player.getUniqueId(), now);

        final SwordEffect effect = getEffect(effectName);
        if (effect == null) {
            return true;
        }
        if (!isEffectGloballyActive(effectName)) {
            notice(player, Text.color("&cBu efekt sunucu tarafından devre dışı bırakılmış!"));
            return true;
        }
        if (!isSwordActive(item)) {
            notice(player, Text.color("&cBu kılıç deaktif edilmiş!"));
            return true;
        }
        if (plugin.getConfig().getBoolean("sword.check-effect-permission", false)
                && !player.hasPermission(effect.getPermission())) {
            notice(player, plugin.getMessageManager().get("sword.no-permission-effect"));
            return true;
        }
        if (!isPvPEnabledAt(player.getLocation())) {
            notice(player, plugin.getMessageManager().get("sword.pvp-only",
                    "&cBurada efekt kullanamazsın! Sadece PvP açık bölgelerde çalışır."));
            return true;
        }
        final long remaining = getCooldownRemaining(player, effect);
        if (remaining > 0) {
            notice(player, plugin.getMessageManager().format("sword.effect-on-cooldown", remaining));
            return true;
        }
        final boolean used;
        try {
            used = effect.apply(player);
        } catch (final Throwable t) {
            plugin.getLogger().log(Level.SEVERE, effect.getName() + " efekti çalışırken hata oluştu", t);
            player.sendMessage(Text.color("&cEfekt hatası! Konsolu kontrol edin."));
            return true;
        }
        if (used) {
            setCooldown(player, effect);
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Yaşam döngüsü
    // ------------------------------------------------------------------

    public void onJoin(final Player player) {
        for (final SwordEffect effect : effects.values()) {
            try {
                effect.onJoin(player);
            } catch (final Throwable t) {
                plugin.getLogger().log(Level.WARNING, effect.getName() + " onJoin hatası", t);
            }
        }
    }

    public void onQuit(final Player player) {
        lastTrigger.remove(player.getUniqueId());
        lastNotice.remove(player.getUniqueId());
        for (final SwordEffect effect : effects.values()) {
            try {
                effect.onQuit(player);
            } catch (final Throwable t) {
                plugin.getLogger().log(Level.WARNING, effect.getName() + " onQuit hatası", t);
            }
        }
    }

    public void shutdown() {
        for (final SwordEffect effect : effects.values()) {
            try {
                effect.onDisable();
            } catch (final Throwable t) {
                plugin.getLogger().log(Level.WARNING, effect.getName() + " kapatma hatası", t);
            }
        }
    }
}
