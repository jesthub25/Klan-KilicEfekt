package com.jestron.clanplugin.sword;

import com.jestron.clanplugin.ClanPlugin;
import com.jestron.clanplugin.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * /kilicefekt ekle <efekt> | kaldır | liste
 * /kilicefektgui
 */
public final class SwordCommand implements CommandExecutor, TabCompleter {

    private final ClanPlugin plugin;

    public SwordCommand(final ClanPlugin plugin) {
        this.plugin = plugin;
    }

    private static String norm(final String s) {
        return s.toLowerCase(Locale.ROOT).replace('ı', 'i');
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getMessageManager().get("general.player-only"));
            return true;
        }
        final Player player = (Player) sender;

        if (command.getName().equalsIgnoreCase("kilicefektgui")) {
            if (!player.hasPermission(SwordGUI.PERMISSION)) {
                player.sendMessage(plugin.getMessageManager().get("general.no-permission"));
                return true;
            }
            plugin.getSwordGUI().open(player);
            return true;
        }

        if (args.length == 0) {
            sendUsage(player);
            return true;
        }
        switch (norm(args[0])) {
            case "ekle":
                if (!player.hasPermission("clansword.kilicefekt.ekle")) {
                    player.sendMessage(plugin.getMessageManager().get("general.no-permission"));
                    return true;
                }
                add(player, args);
                break;
            case "kaldir":
                if (!player.hasPermission("clansword.kilicefekt.kaldir")) {
                    player.sendMessage(plugin.getMessageManager().get("general.no-permission"));
                    return true;
                }
                remove(player);
                break;
            case "liste":
                final List<String> names = new ArrayList<>();
                for (final SwordEffect effect : plugin.getSwordManager().getEffects()) {
                    names.add(effect.getName());
                }
                player.sendMessage(plugin.getMessageManager().format("sword.effect-list", String.join(", ", names)));
                break;
            default:
                sendUsage(player);
        }
        return true;
    }

    private void sendUsage(final Player player) {
        player.sendMessage(Text.color("&e/kilicefekt ekle <efekt> &7- Elindeki kılıca efekt ekle"));
        player.sendMessage(Text.color("&e/kilicefekt kaldır &7- Elindeki kılıçtan efekti kaldır"));
        player.sendMessage(Text.color("&e/kilicefekt liste &7- Efekt listesi"));
        player.sendMessage(Text.color("&e/kilicefektgui &7- Yönetim menüsü"));
    }

    private void add(final Player player, final String[] args) {
        if (args.length < 2) {
            player.sendMessage(Text.color("&cKullanım: /kilicefekt ekle <efekt>"));
            return;
        }
        final ItemStack item = player.getInventory().getItemInMainHand();
        if (!SwordManager.isSword(item)) {
            player.sendMessage(plugin.getMessageManager().get("sword.not-sword"));
            return;
        }
        final SwordEffect effect = plugin.getSwordManager().getEffect(args[1]);
        if (effect == null) {
            player.sendMessage(plugin.getMessageManager().get("sword.invalid-effect"));
            return;
        }
        if (plugin.getSwordManager().getSwordEffect(item) != null) {
            player.sendMessage(Text.color("&cBu kılıçta zaten bir efekt var! Önce kaldır."));
            return;
        }
        if (plugin.getSwordManager().addEffectToSword(item, effect)) {
            player.sendMessage(plugin.getMessageManager().format("sword.effect-added", effect.getName()));
        } else {
            player.sendMessage(Text.color("&cEfekt eklenemedi!"));
        }
    }

    private void remove(final Player player) {
        final ItemStack item = player.getInventory().getItemInMainHand();
        if (!SwordManager.isSword(item)) {
            player.sendMessage(plugin.getMessageManager().get("sword.not-sword"));
            return;
        }
        final String effect = plugin.getSwordManager().getSwordEffect(item);
        if (effect == null) {
            player.sendMessage(plugin.getMessageManager().get("sword.no-effect"));
            return;
        }
        plugin.getSwordManager().removeEffectFromSword(item);
        player.sendMessage(plugin.getMessageManager().format("sword.effect-removed", effect));
    }

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command, final String alias,
                                      final String[] args) {
        if (!(sender instanceof Player) || command.getName().equalsIgnoreCase("kilicefektgui")) {
            return Collections.emptyList();
        }
        final List<String> out = new ArrayList<>();
        if (args.length == 1) {
            if (sender.hasPermission("clansword.kilicefekt.ekle")) {
                out.add("ekle");
            }
            if (sender.hasPermission("clansword.kilicefekt.kaldir")) {
                out.add("kaldır");
            }
            out.add("liste");
        } else if (args.length == 2 && "ekle".equals(norm(args[0]))) {
            for (final SwordEffect effect : plugin.getSwordManager().getEffects()) {
                out.add(effect.getName());
            }
        }
        final String last = norm(args[args.length - 1]);
        final List<String> filtered = new ArrayList<>();
        for (final String s : out) {
            if (norm(s).startsWith(last)) {
                filtered.add(s);
            }
        }
        return filtered;
    }
}
