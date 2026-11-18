package com.jestron.clanplugin.config;

import com.jestron.clanplugin.ClanPlugin;
import com.jestron.clanplugin.util.Text;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * messages.yml. Mesajlarda %1%, %2% ... (veya eski stil %1, %2) yer tutucuları kullanılır.
 */
public final class MessageManager {

    private final ClanPlugin plugin;
    private volatile FileConfiguration messages;

    public MessageManager(final ClanPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        messages = ConfigManager.load(plugin, "messages.yml");
    }

    public String get(final String path) {
        return get(path, "&cMesaj yok: " + path);
    }

    public String get(final String path, final String def) {
        final FileConfiguration cfg = messages;
        final String raw = cfg == null ? def : cfg.getString(path, def);
        return Text.color(raw == null ? def : raw);
    }

    /** Geriye dönük uyumluluk. */
    public String getMessage(final String path) {
        return get(path);
    }

    public String format(final String path, final Object... args) {
        String msg = get(path);
        for (int i = args.length; i >= 1; i--) {
            final String value = String.valueOf(args[i - 1]);
            msg = msg.replace("%" + i + "%", value).replace("%" + i, value);
        }
        return msg;
    }
}
