package com.jestron.clanplugin.sword;

import com.jestron.clanplugin.ClanPlugin;
import com.jestron.clanplugin.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * /kilicefektgui
 *  Sol tık  : efekti sunucu genelinde aç/kapat (durum.yml'e kaydedilir)
 *  Sağ tık  : kılıcın bir kopyasını envantere al
 *
 * Menü InventoryHolder ile tanınır; başlık/InventoryView karşılaştırması yapılmaz.
 */
public final class SwordGUI implements Listener {

    public static final class Holder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public static final String PERMISSION = "clansword.kilicefekt.gui";

    private final ClanPlugin plugin;

    public SwordGUI(final ClanPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(final Player player) {
        final SwordManager swords = plugin.getSwordManager();
        final int count = swords.getTemplates().size();
        if (count == 0) {
            player.sendMessage(Text.color("&cHenüz tanımlı kılıç yok!"));
            return;
        }
        final int size = Math.min(54, Math.max(9, ((count + 8) / 9) * 9));
        final Holder holder = new Holder();
        final Inventory gui = Bukkit.createInventory(holder, size,
                Text.color(plugin.getConfig().getString("gui.sword-gui-title", "&cKılıç Efekt Yönetimi")));
        holder.inventory = gui;
        fill(gui);
        player.openInventory(gui);
    }

    private void fill(final Inventory gui) {
        final SwordManager swords = plugin.getSwordManager();
        final Map<String, Integer> counts = swords.countSwords();
        gui.clear();
        int slot = 0;
        for (final Map.Entry<String, ItemStack> entry : swords.getTemplates().entrySet()) {
            if (slot >= gui.getSize()) {
                break;
            }
            final SwordEffect effect = swords.getEffect(entry.getKey());
            if (effect == null) {
                continue;
            }
            final ItemStack display = entry.getValue().clone();
            final ItemMeta meta = display.getItemMeta();
            if (meta == null) {
                continue;
            }
            final boolean configOn = effect.isEnabledInConfig();
            final boolean menuOn = !swords.isDisabledByMenu(effect.getKey());
            final boolean active = configOn && menuOn;

            final List<String> lore = new ArrayList<>();
            if (meta.hasLore()) {
                for (final String line : meta.getLore()) {
                    if (!Text.strip(line).contains("Durum:")) {
                        lore.add(line);
                    }
                }
            }
            lore.add("");
            lore.add(Text.color("&8» &7Durum: " + (active ? "&aAKTİF"
                    : (configOn ? "&cPASİF" : "&cPASİF &8(efekts.yml'de kapalı)"))));
            lore.add(Text.color("&8» &7Sunucudaki toplam: &b" + counts.getOrDefault(effect.getKey(), 0)));
            lore.add(Text.color("&8» &7Bekleme: &e" + effect.getCooldown() + "sn"));
            if (effect.usesRadius()) {
                lore.add(Text.color("&8» &7Menzil: &e" + effect.getRadius() + " blok"));
            }
            lore.add("");
            lore.add(Text.color("&e&lSol Tık: &f" + (menuOn ? "Kapat" : "Aç")));
            lore.add(Text.color("&6&lSağ Tık: &fKopyasını al"));
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(swords.getEffectKey(), PersistentDataType.STRING, effect.getKey());
            display.setItemMeta(meta);
            gui.setItem(slot++, display);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(final InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(final InventoryClickEvent event) {
        final Inventory top = event.getInventory();
        if (!(top.getHolder() instanceof Holder)) {
            return;
        }
        // Menüde hiçbir eşya alınamaz/konulamaz (shift-click, sayı tuşu, çift tık dahil)
        event.setCancelled(true);

        final Inventory clicked = event.getClickedInventory();
        if (clicked == null || !(clicked.getHolder() instanceof Holder)) {
            return;
        }
        final HumanEntity who = event.getWhoClicked();
        if (!(who instanceof Player)) {
            return;
        }
        final Player player = (Player) who;
        final ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return;
        }
        final String effectKey = item.getItemMeta().getPersistentDataContainer()
                .get(plugin.getSwordManager().getEffectKey(), PersistentDataType.STRING);
        final SwordEffect effect = plugin.getSwordManager().getEffect(effectKey);
        if (effect == null) {
            return;
        }
        if (!player.hasPermission(PERMISSION)) {
            player.sendMessage(plugin.getMessageManager().get("general.no-permission"));
            player.closeInventory();
            return;
        }

        final ClickType click = event.getClick();
        if (click == ClickType.LEFT || click == ClickType.SHIFT_LEFT) {
            final SwordManager swords = plugin.getSwordManager();
            final boolean nowOff = !swords.isDisabledByMenu(effect.getKey());
            swords.setDisabledByMenu(effect.getKey(), nowOff);
            if (nowOff) {
                player.sendMessage(Text.color("&c☠ " + effect.getName() + " efekti KAPATILDI!"));
            } else {
                player.sendMessage(Text.color("&a✔ " + effect.getName() + " efekti AKTİF edildi!"));
                if (!effect.isEnabledInConfig()) {
                    player.sendMessage(Text.color("&eNot: bu efekt efekts.yml'de &cenabled: false&e, orada da açman gerekiyor."));
                }
            }
            // Menü kapanmadan yerinde yenilenir
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (top.getHolder() instanceof Holder && !top.getViewers().isEmpty()) {
                    fill(top);
                }
            });
        } else if (click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT) {
            plugin.getSwordManager().giveSwordCopy(player, effect.getKey());
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!top.getViewers().isEmpty()) {
                    fill(top); // "sunucudaki toplam" sayısı güncellensin
                }
            });
        }
    }
}
