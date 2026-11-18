package com.jestron.clanplugin.hook;

import com.jestron.clanplugin.ClanPlugin;
import com.jestron.clanplugin.clan.Clan;
import com.jestron.clanplugin.clan.ClanManager;
import com.jestron.clanplugin.util.Text;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.util.List;
import java.util.Locale;

/**
 * %klan_name%, %klan_kills%, %klan_deaths%, %klan_kdr%
 * %klan_leaderboards_<sıra>_<kill|death|kdr>[_<name|kill|death|kdr>]%
 */
public final class PlaceholderHook extends PlaceholderExpansion {

    private static final String NONE = Text.color("&c✘");

    private final ClanPlugin plugin;
    private final ClanManager clanManager;

    public PlaceholderHook(final ClanPlugin plugin) {
        this.plugin = plugin;
        this.clanManager = plugin.getClanManager();
    }

    @Override
    public String getIdentifier() {
        return "klan";
    }

    @Override
    public String getAuthor() {
        return "Jestron";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String onRequest(final OfflinePlayer player, final String params) {
        if (params == null) {
            return null;
        }
        final String p = params.toLowerCase(Locale.ROOT);

        if (p.startsWith("leaderboards_")) {
            return leaderboard(p.split("_"));
        }

        final Clan clan = player == null ? null : clanManager.getPlayerClan(player.getUniqueId());
        switch (p) {
            case "name":
                return clan != null ? clan.getName() : NONE;
            case "kills":
                return clan != null ? String.valueOf(clan.getKills()) : "0";
            case "deaths":
                return clan != null ? String.valueOf(clan.getDeaths()) : "0";
            case "kdr":
                return clan != null ? String.valueOf(clan.getKDR()) : "0.0";
            case "members":
                return clan != null ? String.valueOf(clan.getSize()) : "0";
            default:
                return null;
        }
    }

    private String leaderboard(final String[] parts) {
        if (parts.length < 3) {
            return null;
        }
        final int rank;
        try {
            rank = Integer.parseInt(parts[1]);
        } catch (final NumberFormatException ex) {
            return "0";
        }
        final String type = normalize(parts[2]);
        final List<Clan> top = clanManager.getTop(type, 10);
        if (rank <= 0 || rank > top.size()) {
            return "Boş";
        }
        final Clan clan = top.get(rank - 1);
        final String field = parts.length >= 4 ? normalize(parts[3]) : type;
        switch (field) {
            case "name":
                return clan.getName();
            case "kills":
                return String.valueOf(clan.getKills());
            case "deaths":
                return String.valueOf(clan.getDeaths());
            case "kdr":
                return String.valueOf(clan.getKDR());
            default:
                return clan.getName();
        }
    }

    private static String normalize(final String raw) {
        switch (raw) {
            case "isim":
            case "name":
                return "name";
            case "kill":
            case "kills":
            case "öldürme":
            case "oldurme":
                return "kills";
            case "death":
            case "deaths":
            case "ölüm":
            case "olum":
            case "olme":
                return "deaths";
            default:
                return "kdr";
        }
    }
}
