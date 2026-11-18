package com.jestron.clanplugin.hook;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Vault'a dokunan TEK sınıf. Ekonomi sağlayıcısı (Essentials, CMI vb.) bizden sonra
 * yüklense bile ilk kullanımda tekrar aranır.
 */
public final class EconomyHook {

    private static Economy economy;

    private EconomyHook() {
    }

    private static Economy get() {
        if (economy != null) {
            return economy;
        }
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return null;
        }
        try {
            final RegisteredServiceProvider<Economy> rsp =
                    Bukkit.getServicesManager().getRegistration(Economy.class);
            if (rsp != null) {
                economy = rsp.getProvider();
            }
        } catch (final Throwable ignored) {
            economy = null;
        }
        return economy;
    }

    public static boolean isAvailable() {
        return get() != null;
    }

    public static boolean has(final OfflinePlayer player, final double amount) {
        final Economy eco = get();
        return eco != null && eco.has(player, amount);
    }

    public static boolean withdraw(final OfflinePlayer player, final double amount) {
        final Economy eco = get();
        return eco != null && eco.withdrawPlayer(player, amount).transactionSuccess();
    }

    public static boolean deposit(final OfflinePlayer player, final double amount) {
        final Economy eco = get();
        return eco != null && eco.depositPlayer(player, amount).transactionSuccess();
    }

    public static String format(final double amount) {
        final Economy eco = get();
        if (eco != null) {
            try {
                return eco.format(amount);
            } catch (final Throwable ignored) {
                // düz formata düş
            }
        }
        return String.format(java.util.Locale.ROOT, "%,.0f", amount);
    }
}
