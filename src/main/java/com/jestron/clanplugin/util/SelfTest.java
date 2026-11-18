package com.jestron.clanplugin.util;

import com.jestron.clanplugin.ClanPlugin;
import com.jestron.clanplugin.clan.Clan;
import com.jestron.clanplugin.hook.WorldGuardHook;
import com.jestron.clanplugin.sword.SwordGUI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Pillager;
import org.bukkit.entity.Slime;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Sadece -Dclansword.selftest=true ile çalışır (CI sunucu testleri). Normal sunucuda hiçbir etkisi yoktur.
 * Sürüme göre değişen tüm API çözümlemelerini ve temel akışları gerçek sunucuda dener, sonucu loglar.
 */
public final class SelfTest {

    private final ClanPlugin plugin;
    private final List<String> failures = new ArrayList<>();
    private int checks;

    private SelfTest(final ClanPlugin plugin) {
        this.plugin = plugin;
    }

    public static void scheduleIfRequested(final ClanPlugin plugin) {
        if (!Boolean.getBoolean("clansword.selftest")) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            new SelfTest(plugin).run();
            if (Boolean.getBoolean("clansword.selftest.shutdown")) {
                Bukkit.shutdown();
            }
        }, 40L);
    }

    private void check(final String name, final boolean ok) {
        checks++;
        if (!ok) {
            failures.add(name);
        }
    }

    private void run() {
        try {
            // İksir efektleri (1.20.5 isim değişikliği)
            check("potion slowness", VersionUtil.slowness() != null);
            check("potion jumpBoost", VersionUtil.jumpBoost() != null);
            check("potion resistance", VersionUtil.resistance() != null);
            check("potion strength", VersionUtil.strength() != null);
            check("potion nausea", VersionUtil.nausea() != null);
            check("potion regeneration", VersionUtil.regeneration() != null);
            check("potion wither", VersionUtil.wither() != null);
            check("potion blindness", VersionUtil.blindness() != null);
            check("potion levitation", VersionUtil.levitation() != null);
            check("potion hunger", VersionUtil.hunger() != null);
            check("potion invisibility", VersionUtil.invisibility() != null);

            // Partiküller
            check("particle explosion", VersionUtil.particle("EXPLOSION", "EXPLOSION_LARGE") != null);
            check("particle cloud", VersionUtil.particle("CLOUD") != null);
            check("particle flame", VersionUtil.particle("FLAME") != null);
            check("particle portal", VersionUtil.particle("PORTAL") != null);
            check("particle snow", VersionUtil.particle("SNOWFLAKE", "SNOW_SHOVEL", "CLOUD") != null);
            check("particle smoke", VersionUtil.particle("SMOKE_NORMAL", "SMOKE") != null);

            // Sesler (1.21.3'te Sound enum değil)
            check("sound explode", VersionUtil.sound("ENTITY_GENERIC_EXPLODE") != null);
            check("sound enderman", VersionUtil.sound("ENTITY_ENDERMAN_TELEPORT") != null);
            check("sound wither", VersionUtil.sound("ENTITY_WITHER_SHOOT") != null);
            check("sound ghast", VersionUtil.sound("ENTITY_GHAST_SHOOT") != null);
            check("sound blaze", VersionUtil.sound("ENTITY_BLAZE_SHOOT") != null);
            check("sound glass", VersionUtil.sound("BLOCK_GLASS_BREAK") != null);

            // Metin / renk
            check("hex color", Text.color("&#FF0000x").startsWith("§x§f§f"));

            // Kılıç şablonları ve menü
            check("20 effects", plugin.getSwordManager().getEffects().size() == 20);
            check("20 templates", plugin.getSwordManager().getTemplates().size() == 20);
            final SwordGUI.Holder holder = new SwordGUI.Holder();
            final Inventory inv = Bukkit.createInventory(holder, 27, Text.color("&cTest"));
            check("gui holder", inv.getHolder() == holder);

            // Dünya / mob API'leri
            final World world = Bukkit.getWorlds().get(0);
            final Location spawn = world.getSpawnLocation();
            final Pillager pillager = world.spawn(spawn, Pillager.class);
            VersionUtil.setMaxHealth(pillager, 200);
            check("pillager max health", Math.abs(pillager.getHealth() - 200.0) < 0.01);
            pillager.remove();
            final Slime slime = world.spawn(spawn, Slime.class);
            slime.setSize(4);
            check("slime size", slime.getSize() == 4);
            slime.remove();

            // PvP kararı (WorldGuard varsa onun üzerinden)
            final boolean wg = Bukkit.getPluginManager().isPluginEnabled("WorldGuard");
            if (wg) {
                WorldGuardHook.isPvPAllowed(spawn, false);
                WorldGuardHook.isPvPAllowed(spawn, true);
            }
            plugin.getSwordManager().isPvPEnabledAt(spawn);
            check("pvp query (worldguard=" + wg + ")", true);

            // Klan + dost akışı (veritabanı dahil)
            final UUID a = UUID.randomUUID();
            final UUID b = UUID.randomUUID();
            final UUID c = UUID.randomUUID();
            final Clan ca = plugin.getClanManager().createClan("zzTestA", a);
            final Clan cb = plugin.getClanManager().createClan("zzTestB", b);
            final Clan cc = plugin.getClanManager().createClan("zzTestC", c);
            check("create clans", ca != null && cb != null && cc != null);
            if (ca != null && cb != null && cc != null) {
                check("same clan", plugin.getClanManager().isSameClan(a, a));
                plugin.getAllyManager().addMember(ca, cb, a, b);
                plugin.getAllyManager().addMember(ca, cc, a, c);
                check("ally owner-member", plugin.getAllyManager().isAllied("zzTestA", "zzTestB"));
                check("ally member-member", plugin.getAllyManager().isAllied("zztestb", "ZZTESTC"));
                plugin.getAllyManager().disband(ca, false);
                check("ally disband", !plugin.getAllyManager().isAllied("zzTestA", "zzTestB"));
                plugin.getAllyManager().handleClanDeleted(ca);
                plugin.getAllyManager().handleClanDeleted(cb);
                plugin.getAllyManager().handleClanDeleted(cc);
                plugin.getClanManager().deleteClan(ca);
                plugin.getClanManager().deleteClan(cb);
                plugin.getClanManager().deleteClan(cc);
                check("delete clans", plugin.getClanManager().getClan("zzTestA") == null);
            }

            // Mesaj formatı (%1% sonrasında fazladan % kalmamalı)
            check("message format", !plugin.getMessageManager().format("klan.created", "X").endsWith("%"));
        } catch (final Throwable t) {
            failures.add("EXCEPTION: " + t);
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "SELFTEST exception", t);
        }

        final String server = Bukkit.getName() + " " + Bukkit.getBukkitVersion()
                + " | worldguard=" + Bukkit.getPluginManager().isPluginEnabled("WorldGuard")
                + " | java=" + System.getProperty("java.version");
        if (failures.isEmpty()) {
            plugin.getLogger().info("SELFTEST PASSED (" + checks + " kontrol) - " + server);
        } else {
            plugin.getLogger().severe("SELFTEST FAILED - " + server + " - " + failures);
        }
    }
}
