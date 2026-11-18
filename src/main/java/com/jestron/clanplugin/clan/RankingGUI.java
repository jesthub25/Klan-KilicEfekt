package com.jestron.clanplugin.clan;

import com.jestron.clanplugin.ClanPlugin;
import com.jestron.clanplugin.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Klan sıralama menüsü. Tanıma InventoryHolder ile yapılır (başlık karşılaştırması yok),
 * bu yüzden 1.21'de InventoryView'in interface'e dönüşmesinden etkilenmez.
 */
public final class RankingGUI {

    public static final class Holder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private RankingGUI() {
    }

    public static void open(final ClanPlugin plugin, final Player player) {
        final int rows = Math.max(1, Math.min(6, plugin.getConfig().getInt("clan.ranking-gui-rows", 3)));
        final int size = rows * 9;
        final Holder holder = new Holder();
        final String title = Text.color(plugin.getConfig().getString("gui.ranking-title", "&6Klan Sıralaması")
                .replace("%page%", "1"));
        final Inventory gui = Bukkit.createInventory(holder, size, title);
        holder.inventory = gui;

        final List<Clan> top = plugin.getClanManager().getTop("kdr", size);
        for (int i = 0; i < top.size(); i++) {
            final Clan clan = top.get(i);
            final ItemStack item = new ItemStack(i == 0 ? Material.GOLD_INGOT : Material.PAPER);
            final ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                continue;
            }
            meta.setDisplayName(Text.color("&6#" + (i + 1) + " &e" + clan.getName()));
            final List<String> lore = new ArrayList<>();
            lore.add(Text.color("&eKDR: &a" + clan.getKDR()));
            lore.add(Text.color("&eÖldürme: &f" + clan.getKills()));
            lore.add(Text.color("&eÖlüm: &f" + clan.getDeaths()));
            lore.add(Text.color("&eÜyeler: &f" + clan.getSize() + "/" + clan.getMemberLimit()));
            lore.add(Text.color("&eDost Klanlar: &f" + plugin.getAllyManager().getAllies(clan.getName()).size()));
            final String owner = Bukkit.getOfflinePlayer(clan.getOwner()).getName();
            lore.add(Text.color("&eSahip: &f" + (owner != null ? owner : "Bilinmiyor")));
            meta.setLore(lore);
            item.setItemMeta(meta);
            gui.setItem(i, item);
        }
        player.openInventory(gui);
    }
}
