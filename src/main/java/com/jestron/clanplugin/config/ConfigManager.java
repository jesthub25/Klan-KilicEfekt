package com.jestron.clanplugin.config;

import com.jestron.clanplugin.ClanPlugin;
import com.jestron.clanplugin.util.Text;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

/**
 * config.yml ve efekts.yml yönetimi. Kullanıcı dosyasında eksik olan anahtarlar
 * JAR içindeki varsayılan dosyadan okunur.
 */
public final class ConfigManager {

    private static final String EFFECTS_FILE = "efekts.yml";

    private final ClanPlugin plugin;
    private volatile FileConfiguration effects = new YamlConfiguration();

    public ConfigManager(final ClanPlugin plugin) {
        this.plugin = plugin;
        reloadAll();
    }

    public void reloadAll() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        effects = load(plugin, EFFECTS_FILE);
    }

    /** UTF-8 ile okur, JAR'daki aynı dosyayı varsayılan olarak bağlar. */
    static FileConfiguration load(final ClanPlugin plugin, final String name) {
        final File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) {
            plugin.saveResource(name, false);
        }
        final YamlConfiguration cfg = new YamlConfiguration();
        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            cfg.load(reader);
        } catch (final Exception e) {
            plugin.getLogger().log(Level.SEVERE, name + " okunamadı! YAML hatasını düzelt. Varsayılanlar kullanılıyor.", e);
        }
        final InputStream defaults = plugin.getResource(name);
        if (defaults != null) {
            try (Reader reader = new InputStreamReader(defaults, StandardCharsets.UTF_8)) {
                cfg.setDefaults(YamlConfiguration.loadConfiguration(reader));
            } catch (final Exception ignored) {
                // varsayılan yoksa sorun değil
            }
        }
        return cfg;
    }

    private static String path(final String effect, final String key) {
        return "effects." + effect + '.' + key;
    }

    public int getEffectInt(final String effect, final String key, final int def) {
        return effects.getInt(path(effect, key), def);
    }

    public boolean isEffectEnabled(final String effect) {
        return effects.getBoolean(path(effect, "enabled"), true);
    }

    public String getEffectPermission(final String effect) {
        return effects.getString(path(effect, "permission"),
                "clansword.effect." + effect.toLowerCase(Locale.ROOT));
    }

    public String getSwordName(final String effect) {
        return Text.color(effects.getString(path(effect, "sword.name"), "&c" + effect + " Kılıcı"));
    }

    public List<String> getSwordLore(final String effect) {
        List<String> lore = effects.getStringList(path(effect, "sword.lore"));
        if (lore == null || lore.isEmpty()) {
            lore = new ArrayList<>();
            lore.add("&7Efekt: &e" + effect);
            lore.add("&7Durum: &aAKTİF");
            lore.add("&eSağ tık &7ile kullan");
        }
        return Text.color(lore);
    }
}
