package com.jestron.clanplugin.clan;

import com.jestron.clanplugin.ClanPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Klan / dost klan sohbet modları. AsyncPlayerChatEvent 1.16.5 - 1.21.x arası tüm
 * Spigot/Paper/Purpur sürümlerinde mevcuttur.
 */
public final class ClanChatManager implements Listener {

    private final ClanPlugin plugin;
    private final Set<UUID> clanChat = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<UUID> allyChat = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public ClanChatManager(final ClanPlugin plugin) {
        this.plugin = plugin;
    }

    public void toggleClanChat(final Player player) {
        final UUID id = player.getUniqueId();
        if (clanChat.remove(id)) {
            player.sendMessage("§cKlan sohbeti kapatıldı.");
            return;
        }
        if (plugin.getClanManager().getPlayerClan(id) == null) {
            player.sendMessage("§cBir klanda değilsin!");
            return;
        }
        allyChat.remove(id);
        clanChat.add(id);
        player.sendMessage("§aKlan sohbeti açıldı. Yazdıkların sadece klan üyelerine gidecek.");
    }

    public void toggleAllyChat(final Player player) {
        final UUID id = player.getUniqueId();
        if (allyChat.remove(id)) {
            player.sendMessage("§cDost klan sohbeti kapatıldı.");
            return;
        }
        final Clan clan = plugin.getClanManager().getPlayerClan(id);
        if (clan == null) {
            player.sendMessage("§cBir klanda değilsin!");
            return;
        }
        final Set<String> allies = plugin.getAllyManager().getAllies(clan.getName());
        if (allies.isEmpty()) {
            player.sendMessage("§cAktif dost klanın yok!");
            return;
        }
        clanChat.remove(id);
        allyChat.add(id);
        player.sendMessage("§dDost klan sohbeti açıldı. Yazdıkların klanına ve dost klanlara gidecek.");
        player.sendMessage("§7Dost klanlar: §f" + String.join(", ", allies));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(final AsyncPlayerChatEvent event) {
        final Player player = event.getPlayer();
        final UUID id = player.getUniqueId();
        final boolean clanMode = clanChat.contains(id);
        final boolean allyMode = !clanMode && allyChat.contains(id);
        if (!clanMode && !allyMode) {
            return;
        }
        final Clan clan = plugin.getClanManager().getPlayerClan(id);
        if (clan == null) {
            clanChat.remove(id);
            allyChat.remove(id);
            return; // normal sohbete düşer
        }

        final Set<UUID> recipients = new LinkedHashSet<>(clan.getMembers());
        final String message;
        if (clanMode) {
            message = "§8[§bKlan§8] §f" + player.getName() + "§7: §f" + event.getMessage();
        } else {
            final Set<String> allies = plugin.getAllyManager().getAllies(clan.getName());
            if (allies.isEmpty()) {
                allyChat.remove(id);
                player.sendMessage("§cArtık aktif dost klanın yok, normal sohbete dönüldü.");
                return;
            }
            for (final String allyName : allies) {
                final Clan ally = plugin.getClanManager().getClan(allyName);
                if (ally != null) {
                    recipients.addAll(ally.getMembers());
                }
            }
            message = "§8[§dDost§8] §e" + clan.getName() + " §f" + player.getName()
                    + "§7: §f" + event.getMessage();
        }

        event.setCancelled(true);
        for (final UUID uuid : recipients) {
            final Player target = Bukkit.getPlayer(uuid);
            if (target != null) {
                target.sendMessage(message);
            }
        }
        Bukkit.getConsoleSender().sendMessage(message);
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        // Mod tercihi oturum boyunca korunur; klandan çıkmışsa temizlenir.
        final UUID id = event.getPlayer().getUniqueId();
        if (plugin.getClanManager().getPlayerClan(id) == null) {
            clanChat.remove(id);
            allyChat.remove(id);
        }
    }
}
