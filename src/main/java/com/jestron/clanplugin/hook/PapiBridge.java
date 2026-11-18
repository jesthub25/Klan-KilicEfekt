package com.jestron.clanplugin.hook;

import com.jestron.clanplugin.ClanPlugin;

/**
 * PlaceholderAPI'yi sadece eklenti yüklüyken yükler. ClanPlugin bu sınıfı çağırır,
 * PlaceholderAPI tipleri ana sınıfa hiç sızmaz.
 */
public final class PapiBridge {

    private PapiBridge() {
    }

    public static boolean register(final ClanPlugin plugin) {
        return new PlaceholderHook(plugin).register();
    }
}
