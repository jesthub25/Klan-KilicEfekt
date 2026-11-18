package com.jestron.clanplugin.clan;

import com.jestron.clanplugin.ClanPlugin;
import com.jestron.clanplugin.config.MessageManager;
import com.jestron.clanplugin.hook.EconomyHook;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class ClanCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = Arrays.asList(
            "oluştur", "sil", "sıralama", "yetkiver", "yetkial", "davet", "kabulet", "reddet",
            "çıkar", "oyuncular", "ayrıl", "limitartır", "sohbet", "dost", "yardım");
    private static final List<String> ALLY_SUBS = Arrays.asList(
            "davet", "kabulet", "reddet", "ayrıl", "at", "liste", "limitartır", "sohbet");

    private final ClanPlugin plugin;

    public ClanCommand(final ClanPlugin plugin) {
        this.plugin = plugin;
    }

    private ClanManager clans()    { return plugin.getClanManager(); }
    private AllyManager allies()   { return plugin.getAllyManager(); }
    private InviteManager invites() { return plugin.getInviteManager(); }
    private MessageManager msg()   { return plugin.getMessageManager(); }

    /** Türkçe karakterli/karaktersiz yazımları aynı komuta eşler. */
    static String norm(final String input) {
        final String lower = input.toLowerCase(Locale.ROOT);
        final StringBuilder sb = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            final char c = lower.charAt(i);
            switch (c) {
                case 'ı': sb.append('i'); break; // ı
                case '̇': break;                  // İ'nin küçük harfindeki nokta
                case 'ş': sb.append('s'); break; // ş
                case 'ç': sb.append('c'); break; // ç
                case 'ğ': sb.append('g'); break; // ğ
                case 'ü': sb.append('u'); break; // ü
                case 'ö': sb.append('o'); break; // ö
                default: sb.append(c);
            }
        }
        return sb.toString();
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        if (args.length > 0 && "reload".equals(norm(args[0]))) {
            handleReload(sender);
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(msg().get("general.player-only"));
            return true;
        }
        final Player player = (Player) sender;
        if (args.length == 0) {
            sendHelp(player);
            return true;
        }
        switch (norm(args[0])) {
            case "olustur": handleCreate(player, args); break;
            case "sil": handleDelete(player); break;
            case "siralama":
            case "sirala":
            case "top": handleRanking(player); break;
            case "yetkiver": handleModAdd(player, args); break;
            case "yetkial": handleModRemove(player, args); break;
            case "davet": handleInvite(player, args); break;
            case "kabulet": handleAccept(player, args); break;
            case "reddet": handleReject(player); break;
            case "cikar":
            case "at": handleKick(player, args); break;
            case "oyuncular":
            case "uyeler":
            case "bilgi": handleMembers(player); break;
            case "ayril": handleLeave(player); break;
            case "limitarttir": handleLimitIncrease(player); break;
            case "sohbet": plugin.getChatManager().toggleClanChat(player); break;
            case "dost": handleAlly(player, args); break;
            default: sendHelp(player);
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Yardımcılar
    // ------------------------------------------------------------------

    private static String nameOf(final UUID uuid) {
        final Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            return online.getName();
        }
        final String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name != null ? name : "Bilinmiyor";
    }

    private static UUID findMember(final Clan clan, final String name) {
        for (final UUID id : clan.getMembers()) {
            if (nameOf(id).equalsIgnoreCase(name)) {
                return id;
            }
        }
        return null;
    }

    private static void notifyClan(final Clan clan, final String message, final UUID except) {
        if (clan == null) {
            return;
        }
        for (final UUID id : clan.getMembers()) {
            if (id.equals(except)) {
                continue;
            }
            final Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.sendMessage(message);
            }
        }
    }

    private Clan requireClan(final Player player) {
        final Clan clan = clans().getPlayerClan(player.getUniqueId());
        if (clan == null) {
            player.sendMessage(msg().get("klan.not-in-clan"));
        }
        return clan;
    }

    private boolean requireOwner(final Player player, final Clan clan) {
        if (!clan.isOwner(player.getUniqueId())) {
            player.sendMessage(msg().get("klan.not-owner"));
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Klan komutları
    // ------------------------------------------------------------------

    private void handleCreate(final Player player, final String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cKullanım: /klan oluştur <isim>");
            return;
        }
        if (clans().getPlayerClan(player.getUniqueId()) != null) {
            player.sendMessage(msg().get("klan.already-in-clan"));
            return;
        }
        final String name = args[1];
        final int max = plugin.getConfig().getInt("clan.max-name-length", 16);
        if (name.length() < 2 || name.length() > max) {
            player.sendMessage(msg().get("error-prefix", "") + "§cKlan adı 2-" + max + " karakter olmalı!");
            return;
        }
        if (!name.matches("[a-zA-Z0-9_]+")) {
            player.sendMessage(msg().get("error-prefix", "")
                    + "§cİsim sadece harf, rakam ve alt çizgi içerebilir!");
            return;
        }
        if (clans().createClan(name, player.getUniqueId()) == null) {
            player.sendMessage(msg().get("error-prefix", "") + "§cBu isimde klan zaten var!");
            return;
        }
        player.sendMessage(msg().format("klan.created", name));
    }

    private void handleDelete(final Player player) {
        final Clan clan = requireClan(player);
        if (clan == null || !requireOwner(player, clan)) {
            return;
        }
        notifyClan(clan, "§c" + clan.getName() + " klanı sahibi tarafından silindi.", player.getUniqueId());
        allies().handleClanDeleted(clan);
        clans().deleteClan(clan);
        player.sendMessage(msg().get("klan.deleted"));
    }

    private void handleRanking(final Player player) {
        RankingGUI.open(plugin, player);
    }

    private void handleModAdd(final Player player, final String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cKullanım: /klan yetkiver <oyuncu>");
            return;
        }
        final Clan clan = requireClan(player);
        if (clan == null || !requireOwner(player, clan)) {
            return;
        }
        final UUID target = findMember(clan, args[1]);
        if (target == null) {
            player.sendMessage(msg().get("klan.target-not-in-clan"));
            return;
        }
        if (clan.isMod(target)) {
            player.sendMessage("§cBu oyuncu zaten yetkili!");
            return;
        }
        clan.addMod(target);
        clans().save(clan);
        player.sendMessage("§a" + nameOf(target) + " yetkili yapıldı!");
        final Player online = Bukkit.getPlayer(target);
        if (online != null) {
            online.sendMessage("§a" + clan.getName() + " klanında yetkili oldun!");
        }
    }

    private void handleModRemove(final Player player, final String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cKullanım: /klan yetkial <oyuncu>");
            return;
        }
        final Clan clan = requireClan(player);
        if (clan == null || !requireOwner(player, clan)) {
            return;
        }
        final UUID target = findMember(clan, args[1]);
        if (target == null || !clan.isMod(target)) {
            player.sendMessage("§cBu oyuncu klanında yetkili değil!");
            return;
        }
        if (clan.isOwner(target)) {
            player.sendMessage("§cSahibin yetkisini alamazsın!");
            return;
        }
        clan.removeMod(target);
        clans().save(clan);
        player.sendMessage("§c" + nameOf(target) + " yetkisi alındı!");
        final Player online = Bukkit.getPlayer(target);
        if (online != null) {
            online.sendMessage("§c" + clan.getName() + " klanındaki yetkin alındı!");
        }
    }

    private void handleInvite(final Player player, final String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cKullanım: /klan davet <oyuncu>");
            return;
        }
        final Clan clan = requireClan(player);
        if (clan == null) {
            return;
        }
        if (!clan.isMod(player.getUniqueId())) {
            player.sendMessage(msg().get("klan.no-permission"));
            return;
        }
        final long cd = invites().getCooldownRemaining(player.getUniqueId());
        if (cd > 0) {
            player.sendMessage(msg().format("klan.invite-cooldown", cd));
            return;
        }
        final Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            player.sendMessage(msg().get("klan.player-not-found"));
            return;
        }
        if (target.equals(player)) {
            player.sendMessage("§cKendini davet edemezsin!");
            return;
        }
        if (clans().getPlayerClan(target.getUniqueId()) != null) {
            player.sendMessage(msg().get("klan.target-in-clan"));
            return;
        }
        if (clan.isFull()) {
            player.sendMessage("§cKlanın dolu! (" + clan.getSize() + "/" + clan.getMemberLimit() + ")");
            return;
        }
        invites().send(clan, player.getUniqueId(), target.getUniqueId());
        player.sendMessage(msg().format("klan.invite-sent", target.getName()));
        target.sendMessage(msg().format("klan.invite-received", player.getName(), clan.getName()));
        target.sendMessage("§eKabul: §f/klan kabulet " + clan.getName() + " §7| §eRed: §f/klan reddet");
    }

    private void handleAccept(final Player player, final String[] args) {
        if (clans().getPlayerClan(player.getUniqueId()) != null) {
            player.sendMessage(msg().get("klan.already-in-clan"));
            return;
        }
        final InviteManager.Invite invite = invites().find(player.getUniqueId(), args.length >= 2 ? args[1] : null);
        if (invite == null) {
            player.sendMessage(msg().get("klan.invite-expired"));
            return;
        }
        final Clan clan = clans().getClan(invite.clanKey());
        if (clan == null) {
            player.sendMessage("§cKlan artık mevcut değil!");
            invites().clear(player.getUniqueId());
            return;
        }
        if (clan.isFull()) {
            player.sendMessage("§cKlan dolu!");
            return;
        }
        clans().addMember(clan, player.getUniqueId());
        invites().clear(player.getUniqueId());
        player.sendMessage(msg().format("klan.joined", clan.getName()));
        notifyClan(clan, "§e" + player.getName() + " klana katıldı!", player.getUniqueId());
    }

    private void handleReject(final Player player) {
        if (invites().getInvites(player.getUniqueId()).isEmpty()) {
            player.sendMessage(msg().get("klan.invite-expired"));
            return;
        }
        invites().clear(player.getUniqueId());
        player.sendMessage("§cTüm davetler reddedildi.");
    }

    private void handleKick(final Player player, final String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cKullanım: /klan çıkar <oyuncu>");
            return;
        }
        final Clan clan = requireClan(player);
        if (clan == null) {
            return;
        }
        final boolean isOwner = clan.isOwner(player.getUniqueId());
        if (!clan.isMod(player.getUniqueId())) {
            player.sendMessage(msg().get("klan.no-permission"));
            return;
        }
        final UUID target = findMember(clan, args[1]);
        if (target == null) {
            player.sendMessage(msg().get("klan.target-not-in-clan"));
            return;
        }
        if (target.equals(player.getUniqueId())) {
            player.sendMessage("§cKendini atamazsın!");
            return;
        }
        if (clan.isOwner(target)) {
            player.sendMessage("§cSahibi atamazsın!");
            return;
        }
        if (clan.isMod(target) && !isOwner) {
            player.sendMessage("§cYetkilileri sadece sahip atabilir!");
            return;
        }
        final String targetName = nameOf(target);
        clans().removeMember(clan, target);
        player.sendMessage(msg().format("klan.player-kicked", targetName));
        final Player online = Bukkit.getPlayer(target);
        if (online != null) {
            online.sendMessage(msg().format("klan.you-kicked", clan.getName()));
        }
    }

    private void handleMembers(final Player player) {
        final Clan clan = requireClan(player);
        if (clan == null) {
            return;
        }
        player.sendMessage(msg().format("klan.members-header", clan.getName()));
        player.sendMessage("§4[Sahip] §f" + nameOf(clan.getOwner()));
        for (final UUID mod : clan.getMods()) {
            if (!clan.isOwner(mod)) {
                player.sendMessage("§c[Yetkili] §f" + nameOf(mod));
            }
        }
        for (final UUID member : clan.getMembers()) {
            if (!clan.isMod(member)) {
                player.sendMessage("§7[Üye] §f" + nameOf(member));
            }
        }
        player.sendMessage("§eÜye: §f" + clan.getSize() + "/" + clan.getMemberLimit()
                + " §7| §eKDR: §f" + clan.getKDR()
                + " §7| §eÖldürme: §f" + clan.getKills()
                + " §7| §eÖlüm: §f" + clan.getDeaths());
        final Set<String> allyNames = allies().getAllies(clan.getName());
        if (!allyNames.isEmpty()) {
            player.sendMessage("§dDost Klanlar: §f" + String.join(", ", allyNames));
        }
    }

    private void handleLeave(final Player player) {
        final Clan clan = requireClan(player);
        if (clan == null) {
            return;
        }
        if (clan.isOwner(player.getUniqueId())) {
            player.sendMessage(msg().get("klan.cannot-leave-owner"));
            return;
        }
        clans().removeMember(clan, player.getUniqueId());
        player.sendMessage(msg().get("klan.left"));
        notifyClan(clan, "§e" + player.getName() + " klandan ayrıldı.", player.getUniqueId());
    }

    private void handleLimitIncrease(final Player player) {
        final Clan clan = requireClan(player);
        if (clan == null || !requireOwner(player, clan)) {
            return;
        }
        if (!EconomyHook.isAvailable()) {
            player.sendMessage("§cEkonomi sistemi aktif değil! (Vault + ekonomi eklentisi gerekli)");
            return;
        }
        final double cost = plugin.getConfig().getDouble("clan.limit-increase-cost", 10000.0);
        if (!EconomyHook.has(player, cost)) {
            player.sendMessage(msg().format("klan.not-enough-money", EconomyHook.format(cost)));
            return;
        }
        if (!EconomyHook.withdraw(player, cost)) {
            player.sendMessage("§cÖdeme alınamadı, tekrar dene.");
            return;
        }
        clan.setMemberLimit(clan.getMemberLimit() + plugin.getConfig().getInt("clan.limit-increase-amount", 5));
        clans().save(clan);
        player.sendMessage(msg().format("klan.limit-increased", clan.getMemberLimit()));
    }

    private void handleReload(final CommandSender sender) {
        if (!sender.hasPermission("clan.reload")) {
            sender.sendMessage(msg().get("general.no-permission"));
            return;
        }
        plugin.getConfigManager().reloadAll();
        msg().reload();
        plugin.getSwordManager().reloadSwords();
        sender.sendMessage(msg().get("general.reload-success"));
        sender.sendMessage("§7config.yml, efekts.yml, messages.yml ve kılıç şablonları yenilendi.");
    }

    // ------------------------------------------------------------------
    // Dost klan komutları
    // ------------------------------------------------------------------

    private void handleAlly(final Player player, final String[] args) {
        if (args.length < 2) {
            sendAllyHelp(player);
            return;
        }
        final Clan clan = requireClan(player);
        if (clan == null) {
            return;
        }
        switch (norm(args[1])) {
            case "davet":
                if (args.length < 3) {
                    player.sendMessage("§cKullanım: /klan dost davet <klan>");
                    return;
                }
                allyInvite(player, clan, args[2]);
                break;
            case "kabulet":
                allyAccept(player, clan, args.length >= 3 ? args[2] : null);
                break;
            case "reddet":
                allyReject(player, clan);
                break;
            case "ayril":
                allyLeave(player, clan);
                break;
            case "at":
                if (args.length < 3) {
                    player.sendMessage("§cKullanım: /klan dost at <klan>");
                    return;
                }
                allyKick(player, clan, args[2]);
                break;
            case "liste":
                allyList(player, clan);
                break;
            case "limitarttir":
                allyLimit(player, clan);
                break;
            case "sohbet":
                plugin.getChatManager().toggleAllyChat(player);
                break;
            default:
                sendAllyHelp(player);
        }
    }

    private void notifyOwner(final Clan clan, final String message) {
        if (clan == null) {
            return;
        }
        final Player owner = Bukkit.getPlayer(clan.getOwner());
        if (owner != null) {
            owner.sendMessage(message);
        }
    }

    private void allyInvite(final Player player, final Clan clan, final String targetName) {
        if (!requireOwner(player, clan)) {
            return;
        }
        final String myOwner = allies().getOwnerOf(clan.getName());
        if (myOwner != null) {
            player.sendMessage("§c" + myOwner + " klanının dost grubundasın; davet sadece grup sahibi tarafından atılabilir.");
            return;
        }
        final Clan target = clans().getClan(targetName);
        if (target == null) {
            player.sendMessage("§cBu isimde bir klan bulunamadı!");
            return;
        }
        if (target == clan) {
            player.sendMessage("§cKendi klanını davet edemezsin!");
            return;
        }
        if (allies().isAllied(clan.getName(), target.getName())) {
            player.sendMessage("§cZaten bu klanla dostsun!");
            return;
        }
        if (allies().isInGroup(target.getName())) {
            player.sendMessage("§c" + target.getName() + " zaten başka bir dost grubunda!");
            return;
        }
        final int count = allies().getMemberCount(clan.getName());
        final int limit = allies().getLimit(clan.getName());
        if (count >= limit) {
            player.sendMessage("§cDostluk limitin dolu! (" + count + "/" + limit + ") §7/klan dost limitartır");
            return;
        }
        final long cd = allies().getInviteCooldownRemaining(clan.getName());
        if (cd > 0) {
            player.sendMessage("§cDost daveti için beklemelisin! §f" + cd + " saniye");
            return;
        }
        allies().sendInvite(clan, target, player.getUniqueId());
        player.sendMessage("§a" + target.getName() + " klanına dost daveti gönderildi!");
        notifyOwner(target, "§e" + clan.getName() + " §aklanı size dostluk daveti gönderdi!");
        notifyOwner(target, "§eKabul: §f/klan dost kabulet " + clan.getName() + " §7| §eRed: §f/klan dost reddet");
    }

    private void allyAccept(final Player player, final Clan clan, final String from) {
        if (!requireOwner(player, clan)) {
            return;
        }
        if (allies().isInGroup(clan.getName())) {
            player.sendMessage("§cZaten bir dost grubundasın! Önce §f/klan dost ayrıl §cyazmalısın.");
            return;
        }
        final AllyManager.AllyInvite invite = allies().takeInvite(clan.getName(), from);
        if (invite == null) {
            player.sendMessage("§cAktif dostluk davetin yok" + (from != null ? " (" + from + ")" : "") + "!");
            return;
        }
        final Clan owner = clans().getClan(invite.ownerKey());
        if (owner == null) {
            player.sendMessage("§cDavet eden klan artık yok.");
            return;
        }
        if (allies().getOwnerOf(owner.getName()) != null) {
            player.sendMessage("§c" + owner.getName() + " artık başka bir grubun üyesi, davet geçersiz.");
            return;
        }
        if (allies().getMemberCount(owner.getName()) >= allies().getLimit(owner.getName())) {
            player.sendMessage("§c" + owner.getName() + " klanının dostluk limiti dolmuş.");
            return;
        }
        allies().addMember(owner, clan, invite.inviter(), player.getUniqueId());
        allies().clearInvites(clan.getName());
        player.sendMessage("§a" + owner.getName() + " ile dostluk kuruldu!");
        notifyClan(clan, "§dKlanın artık " + owner.getName() + " dost grubunda!", player.getUniqueId());
        notifyClan(owner, "§d" + clan.getName() + " klanı dostluğunuzu kabul etti!", null);
    }

    private void allyReject(final Player player, final Clan clan) {
        if (!requireOwner(player, clan)) {
            return;
        }
        final List<AllyManager.AllyInvite> pending = allies().getInvites(clan.getName());
        if (pending.isEmpty()) {
            player.sendMessage("§cAktif davetin yok!");
            return;
        }
        allies().clearInvites(clan.getName());
        player.sendMessage("§cDostluk davetleri reddedildi.");
        for (final AllyManager.AllyInvite inv : pending) {
            notifyOwner(clans().getClan(inv.ownerKey()), "§c" + clan.getName() + " klanı dostluk davetinizi reddetti.");
        }
    }

    private void allyLeave(final Player player, final Clan clan) {
        if (!requireOwner(player, clan)) {
            return;
        }
        final String owner = allies().getOwnerOf(clan.getName());
        if (owner != null) {
            allies().removeMember(owner, clan.getName());
            player.sendMessage("§c" + owner + " klanıyla olan dostluğun sona erdi!");
            notifyOwner(clans().getClan(owner), "§c" + clan.getName() + " klanı dost grubunuzdan ayrıldı!");
            return;
        }
        if (!allies().isGroupOwner(clan.getName())) {
            player.sendMessage("§cAktif dostluğun yok!");
            return;
        }
        final List<String> removed = allies().disband(clan, true);
        player.sendMessage("§cDost grubun dağıtıldı. Eski dostların artık düşman konumunda!");
        for (final String name : removed) {
            notifyOwner(clans().getClan(name), "§c" + clan.getName() + " dost grubunu dağıttı!");
        }
    }

    private void allyKick(final Player player, final Clan clan, final String targetName) {
        if (!requireOwner(player, clan)) {
            return;
        }
        if (!allies().isGroupOwner(clan.getName())) {
            player.sendMessage("§cSadece dost grubunun sahibi klan atabilir!");
            return;
        }
        final Clan target = clans().getClan(targetName);
        final String targetOwner = target == null ? null : allies().getOwnerOf(target.getName());
        if (target == null || targetOwner == null || !targetOwner.equalsIgnoreCase(clan.getName())) {
            player.sendMessage("§cBu klan dost grubunda değil!");
            return;
        }
        allies().removeMember(clan.getName(), target.getName());
        player.sendMessage("§c" + target.getName() + " klanı dostluktan atıldı!");
        notifyOwner(target, "§c" + clan.getName() + " klanı sizi dost grubundan attı!");
    }

    private void allyList(final Player player, final Clan clan) {
        player.sendMessage("§6=== §eDost Klanlar §6===");
        final Set<String> list = allies().getAllies(clan.getName());
        final String owner = allies().getOwnerOf(clan.getName());
        if (owner != null) {
            player.sendMessage("§eGrup sahibi: §f" + owner);
        } else {
            player.sendMessage("§7Limit: §f" + allies().getMemberCount(clan.getName()) + "/"
                    + allies().getLimit(clan.getName()));
        }
        if (list.isEmpty()) {
            player.sendMessage("§7Aktif dostluğun yok.");
            return;
        }
        for (final String ally : list) {
            player.sendMessage("  §7- §f" + ally);
        }
    }

    private void allyLimit(final Player player, final Clan clan) {
        if (!requireOwner(player, clan)) {
            return;
        }
        if (allies().getOwnerOf(clan.getName()) != null) {
            player.sendMessage("§cDostluk limitini sadece grup sahibi klan artırabilir!");
            return;
        }
        if (!EconomyHook.isAvailable()) {
            player.sendMessage("§cEkonomi sistemi aktif değil!");
            return;
        }
        final double cost = plugin.getConfig().getDouble("ally.limit-increase-cost", 30000.0);
        if (!EconomyHook.has(player, cost)) {
            player.sendMessage("§cYeterli paran yok! Gereken: §f" + EconomyHook.format(cost));
            return;
        }
        if (!EconomyHook.withdraw(player, cost)) {
            player.sendMessage("§cÖdeme alınamadı, tekrar dene.");
            return;
        }
        allies().increaseLimit(clan);
        player.sendMessage("§aDostluk limiti artırıldı! Yeni limit: §f" + allies().getLimit(clan.getName()));
    }

    // ------------------------------------------------------------------
    // Yardım
    // ------------------------------------------------------------------

    private void sendHelp(final Player player) {
        final String cost = EconomyHook.format(plugin.getConfig().getDouble("clan.limit-increase-cost", 10000.0));
        player.sendMessage("§6=== §eKlan Komutları §6===");
        player.sendMessage("§e/klan oluştur <isim> §7- Klan oluştur");
        player.sendMessage("§e/klan sil §7- Klanı sil");
        player.sendMessage("§e/klan sıralama §7- Sıralama menüsü");
        player.sendMessage("§e/klan yetkiver|yetkial <oyuncu> §7- Yetki ver/al");
        player.sendMessage("§e/klan davet <oyuncu> §7- Davet gönder");
        player.sendMessage("§e/klan kabulet [klan] | reddet §7- Davet işlemleri");
        player.sendMessage("§e/klan çıkar <oyuncu> §7- Üye at");
        player.sendMessage("§e/klan oyuncular §7- Üye listesi");
        player.sendMessage("§e/klan ayrıl §7- Klandan ayrıl");
        player.sendMessage("§e/klan limitartır §7- Üye limiti artır (" + cost + ")");
        player.sendMessage("§e/klan sohbet §7- Klan sohbetini aç/kapat");
        player.sendMessage("§e/klan dost §7- Dost klan sistemi");
        if (player.hasPermission("clan.reload")) {
            player.sendMessage("§e/klan reload §7- Ayarları yenile");
        }
    }

    private void sendAllyHelp(final Player player) {
        final String cost = EconomyHook.format(plugin.getConfig().getDouble("ally.limit-increase-cost", 30000.0));
        player.sendMessage("§6=== §eDost Klan Komutları §6===");
        player.sendMessage("§e/klan dost davet <klan> §7- Dostluk daveti gönder (sahip)");
        player.sendMessage("§e/klan dost kabulet [klan] §7- Daveti kabul et (sahip)");
        player.sendMessage("§e/klan dost reddet §7- Davetleri reddet (sahip)");
        player.sendMessage("§e/klan dost ayrıl §7- Gruptan ayrıl / grubu dağıt (sahip)");
        player.sendMessage("§e/klan dost at <klan> §7- Gruptan klan at (grup sahibi)");
        player.sendMessage("§e/klan dost liste §7- Dost klanları listele");
        player.sendMessage("§e/klan dost limitartır §7- Dost limiti +1 (" + cost + ")");
        player.sendMessage("§e/klan dost sohbet §7- Dost sohbetini aç/kapat");
    }

    // ------------------------------------------------------------------
    // Tab tamamlama
    // ------------------------------------------------------------------

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command, final String alias,
                                      final String[] args) {
        if (!(sender instanceof Player)) {
            return args.length == 1 ? Collections.singletonList("reload") : Collections.<String>emptyList();
        }
        final Player player = (Player) sender;
        final List<String> out = new ArrayList<>();
        final Clan clan = clans().getPlayerClan(player.getUniqueId());

        if (args.length == 1) {
            out.addAll(SUBS);
            if (player.hasPermission("clan.reload")) {
                out.add("reload");
            }
        } else if (args.length == 2) {
            switch (norm(args[0])) {
                case "yetkiver":
                    if (clan != null && clan.isOwner(player.getUniqueId())) {
                        for (final UUID id : clan.getMembers()) {
                            if (!clan.isMod(id)) {
                                out.add(nameOf(id));
                            }
                        }
                    }
                    break;
                case "yetkial":
                    if (clan != null && clan.isOwner(player.getUniqueId())) {
                        for (final UUID id : clan.getMods()) {
                            if (!clan.isOwner(id)) {
                                out.add(nameOf(id));
                            }
                        }
                    }
                    break;
                case "davet":
                    for (final Player p : Bukkit.getOnlinePlayers()) {
                        if (!p.equals(player) && clans().getPlayerClan(p.getUniqueId()) == null) {
                            out.add(p.getName());
                        }
                    }
                    break;
                case "kabulet":
                    for (final InviteManager.Invite inv : invites().getInvites(player.getUniqueId())) {
                        final Clan c = clans().getClan(inv.clanKey());
                        if (c != null) {
                            out.add(c.getName());
                        }
                    }
                    break;
                case "cikar":
                case "at":
                    if (clan != null) {
                        final boolean owner = clan.isOwner(player.getUniqueId());
                        for (final UUID id : clan.getMembers()) {
                            if (!id.equals(player.getUniqueId()) && !clan.isOwner(id) && (owner || !clan.isMod(id))) {
                                out.add(nameOf(id));
                            }
                        }
                    }
                    break;
                case "dost":
                    out.addAll(ALLY_SUBS);
                    break;
                default:
                    break;
            }
        } else if (args.length == 3 && "dost".equals(norm(args[0])) && clan != null) {
            switch (norm(args[1])) {
                case "davet":
                    for (final Clan c : clans().getAllClans()) {
                        if (c != clan && !allies().isAllied(clan.getName(), c.getName())) {
                            out.add(c.getName());
                        }
                    }
                    break;
                case "at":
                    if (allies().isGroupOwner(clan.getName())) {
                        out.addAll(allies().getAllies(clan.getName()));
                    }
                    break;
                case "kabulet":
                    for (final AllyManager.AllyInvite inv : allies().getInvites(clan.getName())) {
                        out.add(allies().displayName(inv.ownerKey()));
                    }
                    break;
                default:
                    break;
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
