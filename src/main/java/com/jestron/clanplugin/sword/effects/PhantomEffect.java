package com.jestron.clanplugin.sword.effects;

import com.jestron.clanplugin.ClanPlugin;
import com.jestron.clanplugin.sword.SwordEffect;
import com.jestron.clanplugin.util.VersionUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Phantom: süre boyunca düşmanlar seni (zırh dahil) göremez. Klan üyeleri ve dostlar görmeye devam eder.
 *
 * Eski sürümdeki SPECTATOR yöntemi oyun modunu bozuyor ve duvar içinde kalmaya yol açıyordu;
 * bunun yerine oyuncu düşmanlardan gizlenir + Görünmezlik verilir. Çıkışta/kapanışta geri gösterilir.
 */
public final class PhantomEffect extends SwordEffect {

    private final Map<UUID, BukkitTask> active = new HashMap<>();

    public PhantomEffect(final ClanPlugin plugin) {
        super(plugin, "Phantom", 4, 90, 3, 1);
    }

    @Override
    public boolean usesRadius() {
        return false;
    }

    @Override
    protected String tag() {
        return "&8[&1Phantom&8] &7";
    }

    @Override
    public boolean apply(final Player user) {
        if (active.containsKey(user.getUniqueId())) {
            send(user, tag() + "&cZaten görünmezsin!");
            return false;
        }
        final int seconds = getDuration();
        potion(user, VersionUtil.invisibility(), seconds * 20, 0, false);
        for (final Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.equals(user) && !swords().isSafe(user.getUniqueId(), viewer)) {
                viewer.hidePlayer(plugin, user);
            }
        }
        final UUID id = user.getUniqueId();
        active.put(id, Bukkit.getScheduler().runTaskLater(plugin, () -> reveal(id, true), seconds * 20L));
        VersionUtil.spawnParticle(user.getLocation().add(0, 1, 0), 30, 0.4, 0.05, "SMOKE_NORMAL", "SMOKE");
        VersionUtil.playSound(user.getLocation(), 1.0f, 1.0f, "ENTITY_PHANTOM_FLAP");
        send(user, tag() + "&dGörünmezsin! &7(" + seconds + " saniye)");
        return true;
    }

    private void reveal(final UUID id, final boolean notify) {
        final BukkitTask task = active.remove(id);
        if (task != null) {
            task.cancel();
        }
        final Player user = Bukkit.getPlayer(id);
        if (user == null) {
            return;
        }
        for (final Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.equals(user)) {
                viewer.showPlayer(plugin, user);
            }
        }
        if (notify) {
            send(user, tag() + "&7Artık görünürsün.");
        }
    }

    @Override
    public void onJoin(final Player player) {
        // Yeni giren düşman, aktif phantom'ları göremesin
        for (final UUID id : active.keySet()) {
            final Player hidden = Bukkit.getPlayer(id);
            if (hidden != null && !swords().isSafe(id, player)) {
                player.hidePlayer(plugin, hidden);
            }
        }
    }

    @Override
    public void onQuit(final Player player) {
        if (active.containsKey(player.getUniqueId())) {
            reveal(player.getUniqueId(), false);
        }
    }

    @Override
    public void onDisable() {
        for (final UUID id : new ArrayList<>(active.keySet())) {
            reveal(id, false);
        }
    }
}
