package com.jestron.clanplugin.clan;

import com.jestron.clanplugin.ClanPlugin;
import com.jestron.clanplugin.hook.EconomyHook;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Dost klan grupları.
 *
 * Bir grup = 1 sahip klan + ona katılmış üye klanlar. Aynı gruptaki TÜM klanlar
 * birbirinin dostudur (sahip-üye ve üye-üye). Bir klan aynı anda tek gruptadır.
 * Tüm anahtarlar küçük harf tutulur; ekranda klanın gerçek adı gösterilir.
 */
public final class AllyManager {

    public static final class AllyInvite {
        private final String ownerKey;
        private final UUID inviter;
        private final long createdAt;

        AllyInvite(final String ownerKey, final UUID inviter, final long createdAt) {
            this.ownerKey = ownerKey;
            this.inviter = inviter;
            this.createdAt = createdAt;
        }

        public String ownerKey() { return ownerKey; }
        public UUID inviter()    { return inviter; }
        public long createdAt()  { return createdAt; }
    }

    private final ClanPlugin plugin;
    private final Map<String, Set<String>> ownerToMembers = new ConcurrentHashMap<>();
    private final Map<String, String> memberToOwner = new ConcurrentHashMap<>();
    private final Map<String, Integer> limits = new ConcurrentHashMap<>();
    private final Map<String, Integer> increases = new ConcurrentHashMap<>();
    private final Map<String, List<AllyInvite>> invites = new HashMap<>();   // hedef -> davetler (ana thread)
    private final Map<String, Long> inviteCooldowns = new HashMap<>();        // ana thread

    public AllyManager(final ClanPlugin plugin) {
        this.plugin = plugin;
    }

    private static String key(final String name) {
        return name == null ? null : name.toLowerCase(Locale.ROOT);
    }

    private ClanManager clans() {
        return plugin.getClanManager();
    }

    // ------------------------------------------------------------------
    // Yükleme
    // ------------------------------------------------------------------

    public void load() {
        final List<String[]> orphanRows = new ArrayList<>();
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            try (Statement stmt = conn.createStatement()) {
                // Eski sürümün yarım kalmış davet satırları
                stmt.executeUpdate("DELETE FROM clan_allies WHERE status <> 'accepted'");
            }
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT owner_clan, ally_clan, ally_limit, limit_increases FROM clan_allies")) {
                while (rs.next()) {
                    final String ownerName = rs.getString("owner_clan");
                    final String memberName = rs.getString("ally_clan");
                    final String owner = key(ownerName);
                    final String member = key(memberName);
                    if (clans().getClan(owner) == null || clans().getClan(member) == null
                            || owner.equals(member) || memberToOwner.containsKey(member)
                            || memberToOwner.containsKey(owner)) {
                        orphanRows.add(new String[]{ownerName, memberName});
                        continue;
                    }
                    ownerToMembers.computeIfAbsent(owner, k -> newSet()).add(member);
                    memberToOwner.put(member, owner);
                    // Eski sürüm limitleri bu tabloda tutuyordu: taşı
                    final int limit = rs.getInt("ally_limit");
                    final int inc = rs.getInt("limit_increases");
                    if (inc > 0 && limit > 0) {
                        limits.merge(owner, limit, (x, y) -> Math.max(x, y));
                        increases.merge(owner, inc, (x, y) -> Math.max(x, y));
                    }
                }
            }
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT clan, ally_limit, limit_increases FROM clan_ally_limits")) {
                while (rs.next()) {
                    final String clan = key(rs.getString("clan"));
                    limits.put(clan, rs.getInt("ally_limit"));
                    increases.put(clan, rs.getInt("limit_increases"));
                }
            }
            for (final String[] row : orphanRows) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM clan_allies WHERE owner_clan = ? AND ally_clan = ?")) {
                    ps.setString(1, row[0]);
                    ps.setString(2, row[1]);
                    ps.executeUpdate();
                }
            }
        } catch (final SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Dost klan verileri yüklenemedi!", e);
        }
        // Taşınan limitleri yeni tabloya yaz
        for (final String clan : new ArrayList<>(limits.keySet())) {
            saveLimit(clan);
        }
    }

    private static Set<String> newSet() {
        return Collections.newSetFromMap(new ConcurrentHashMap<>());
    }

    // ------------------------------------------------------------------
    // Sorgular (async sohbet thread'inden de güvenle çağrılabilir)
    // ------------------------------------------------------------------

    /** Klanın içinde bulunduğu grubun sahibinin anahtarı; grupta değilse null. */
    private String groupOf(final String clanKey) {
        if (clanKey == null) {
            return null;
        }
        final String owner = memberToOwner.get(clanKey);
        if (owner != null) {
            return owner;
        }
        final Set<String> members = ownerToMembers.get(clanKey);
        return members != null && !members.isEmpty() ? clanKey : null;
    }

    public boolean isAllied(final String clanA, final String clanB) {
        final String a = key(clanA);
        final String b = key(clanB);
        if (a == null || b == null || a.equals(b)) {
            return false;
        }
        final String ga = groupOf(a);
        return ga != null && ga.equals(groupOf(b));
    }

    /** Bu klan başka bir grubun üyesi mi? (Sahibin gerçek adı, değilse null) */
    public String getOwnerOf(final String clanName) {
        final String owner = memberToOwner.get(key(clanName));
        return owner == null ? null : display(owner);
    }

    /** Bu klan, en az bir üyesi olan bir grubun sahibi mi? */
    public boolean isGroupOwner(final String clanName) {
        final Set<String> members = ownerToMembers.get(key(clanName));
        return members != null && !members.isEmpty();
    }

    public boolean isInGroup(final String clanName) {
        return groupOf(key(clanName)) != null;
    }

    /** Aynı gruptaki diğer tüm klanlar (kendisi hariç). */
    public Set<String> getAllies(final String clanName) {
        final String self = key(clanName);
        final String group = groupOf(self);
        if (group == null) {
            return Collections.emptySet();
        }
        final Set<String> out = new LinkedHashSet<>();
        if (!group.equals(self)) {
            out.add(display(group));
        }
        final Set<String> members = ownerToMembers.get(group);
        if (members != null) {
            for (final String m : members) {
                if (!m.equals(self)) {
                    out.add(display(m));
                }
            }
        }
        return out;
    }

    public int getMemberCount(final String ownerName) {
        final Set<String> members = ownerToMembers.get(key(ownerName));
        return members == null ? 0 : members.size();
    }

    public int getLimit(final String clanName) {
        final Integer v = limits.get(key(clanName));
        return v != null ? v : plugin.getConfig().getInt("ally.default-limit", 3);
    }

    private String display(final String clanKey) {
        final Clan clan = clans().getClan(clanKey);
        return clan != null ? clan.getName() : clanKey;
    }

    // ------------------------------------------------------------------
    // Davetler (ana thread)
    // ------------------------------------------------------------------

    public long getInviteCooldownRemaining(final String clanName) {
        final Long last = inviteCooldowns.get(key(clanName));
        if (last == null) {
            return 0L;
        }
        final long cdMs = plugin.getConfig().getInt("ally.invite-cooldown", 60) * 1000L;
        final long remaining = cdMs - (System.currentTimeMillis() - last);
        return remaining <= 0 ? 0L : (remaining + 999L) / 1000L;
    }

    public void sendInvite(final Clan owner, final Clan target, final UUID inviter) {
        final String targetKey = target.getKey();
        final List<AllyInvite> list = invites.computeIfAbsent(targetKey, k -> new ArrayList<>());
        list.removeIf(inv -> inv.ownerKey().equals(owner.getKey()));
        list.add(new AllyInvite(owner.getKey(), inviter, System.currentTimeMillis()));
        inviteCooldowns.put(owner.getKey(), System.currentTimeMillis());
    }

    /** Süresi dolmamış davetler (en yeni sonda). */
    public List<AllyInvite> getInvites(final String targetName) {
        final List<AllyInvite> list = invites.get(key(targetName));
        if (list == null) {
            return Collections.emptyList();
        }
        final long timeoutMs = plugin.getConfig().getInt("ally.invite-timeout", 300) * 1000L;
        final long now = System.currentTimeMillis();
        list.removeIf(inv -> now - inv.createdAt() > timeoutMs || clans().getClan(inv.ownerKey()) == null);
        if (list.isEmpty()) {
            invites.remove(key(targetName));
            return Collections.emptyList();
        }
        return new ArrayList<>(list);
    }

    /** İsim verilirse o klanın davetini, verilmezse en yenisini çıkarır. */
    public AllyInvite takeInvite(final String targetName, final String fromClan) {
        final List<AllyInvite> valid = getInvites(targetName);
        if (valid.isEmpty()) {
            return null;
        }
        AllyInvite chosen = null;
        if (fromClan == null) {
            chosen = valid.get(valid.size() - 1);
        } else {
            final String from = key(fromClan);
            for (final AllyInvite inv : valid) {
                if (inv.ownerKey().equals(from)) {
                    chosen = inv;
                }
            }
        }
        if (chosen != null) {
            final List<AllyInvite> list = invites.get(key(targetName));
            if (list != null) {
                list.remove(chosen);
                if (list.isEmpty()) {
                    invites.remove(key(targetName));
                }
            }
        }
        return chosen;
    }

    public int clearInvites(final String targetName) {
        final List<AllyInvite> removed = invites.remove(key(targetName));
        return removed == null ? 0 : removed.size();
    }

    public String displayName(final String clanKey) {
        return display(clanKey);
    }

    // ------------------------------------------------------------------
    // Grup değişiklikleri
    // ------------------------------------------------------------------

    /** Üyeyi gruba ekler ve kaydeder. Kontrolleri komut katmanı yapar. */
    public void addMember(final Clan owner, final Clan member, final UUID invitedBy, final UUID acceptedBy) {
        ownerToMembers.computeIfAbsent(owner.getKey(), k -> newSet()).add(member.getKey());
        memberToOwner.put(member.getKey(), owner.getKey());
        final String ownerName = owner.getName();
        final String memberName = member.getName();
        final int limit = getLimit(ownerName);
        final int inc = increases.getOrDefault(owner.getKey(), 0);
        plugin.getDatabaseManager().async(conn -> {
            deleteRow(conn, ownerName, memberName);
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO clan_allies (owner_clan, ally_clan, status, invited_by, accepted_by, ally_limit, limit_increases)"
                            + " VALUES (?, ?, 'accepted', ?, ?, ?, ?)")) {
                ps.setString(1, ownerName);
                ps.setString(2, memberName);
                ps.setString(3, invitedBy == null ? null : invitedBy.toString());
                ps.setString(4, acceptedBy == null ? null : acceptedBy.toString());
                ps.setInt(5, limit);
                ps.setInt(6, inc);
                ps.executeUpdate();
            }
        });
    }

    /** Üyeyi gruptan çıkarır. */
    public void removeMember(final String ownerName, final String memberName) {
        final String owner = key(ownerName);
        final String member = key(memberName);
        final Set<String> members = ownerToMembers.get(owner);
        if (members != null) {
            members.remove(member);
            if (members.isEmpty()) {
                ownerToMembers.remove(owner);
            }
        }
        memberToOwner.remove(member, owner);
        plugin.getDatabaseManager().async(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM clan_allies WHERE LOWER(owner_clan) = ? AND LOWER(ally_clan) = ?")) {
                ps.setString(1, owner);
                ps.setString(2, member);
                ps.executeUpdate();
            }
        });
    }

    /**
     * Sahibin grubunu dağıtır. refund=true ise satın alınmış limit artışları
     * sahibin hesabına iade edilir ve limit varsayılana döner.
     * @return dağıtılan üye klanların gerçek adları
     */
    public List<String> disband(final Clan owner, final boolean refund) {
        final Set<String> members = ownerToMembers.remove(owner.getKey());
        final List<String> removed = new ArrayList<>();
        if (members != null) {
            for (final String m : members) {
                memberToOwner.remove(m, owner.getKey());
                removed.add(display(m));
            }
        }
        final String ownerKey = owner.getKey();
        plugin.getDatabaseManager().async(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM clan_allies WHERE LOWER(owner_clan) = ?")) {
                ps.setString(1, ownerKey);
                ps.executeUpdate();
            }
        });
        if (refund) {
            refundIncreases(owner);
        }
        return removed;
    }

    private void refundIncreases(final Clan owner) {
        final Integer inc = increases.remove(owner.getKey());
        limits.remove(owner.getKey());
        deleteLimit(owner.getKey());
        if (inc == null || inc <= 0) {
            return;
        }
        final double amount = inc * plugin.getConfig().getDouble("ally.limit-increase-cost", 30000.0);
        final OfflinePlayer target = Bukkit.getOfflinePlayer(owner.getOwner());
        if (EconomyHook.deposit(target, amount) && target.isOnline() && target.getPlayer() != null) {
            target.getPlayer().sendMessage("§aDostluk limit artırma ücretleri iade edildi: §f"
                    + EconomyHook.format(amount));
        }
    }

    /** Klan silinince: sahibi olduğu grubu dağıtır, üyesi olduğu gruptan çıkar, davetleri temizler. */
    public void handleClanDeleted(final Clan clan) {
        if (isGroupOwner(clan.getName())) {
            disband(clan, true);
        } else {
            final String owner = memberToOwner.get(clan.getKey());
            if (owner != null) {
                removeMember(owner, clan.getKey());
            }
            refundIncreases(clan);
        }
        invites.remove(clan.getKey());
        for (final List<AllyInvite> list : invites.values()) {
            list.removeIf(inv -> inv.ownerKey().equals(clan.getKey()));
        }
        inviteCooldowns.remove(clan.getKey());
    }

    // ------------------------------------------------------------------
    // Limit
    // ------------------------------------------------------------------

    public void increaseLimit(final Clan clan) {
        final String k = clan.getKey();
        limits.put(k, getLimit(clan.getName()) + 1);
        increases.merge(k, 1, Integer::sum);
        saveLimit(k);
    }

    private void saveLimit(final String clanKey) {
        final Integer limit = limits.get(clanKey);
        if (limit == null) {
            return;
        }
        final int inc = increases.getOrDefault(clanKey, 0);
        final String name = display(clanKey);
        plugin.getDatabaseManager().async(conn -> {
            try (PreparedStatement del = conn.prepareStatement("DELETE FROM clan_ally_limits WHERE LOWER(clan) = ?")) {
                del.setString(1, clanKey);
                del.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO clan_ally_limits (clan, ally_limit, limit_increases) VALUES (?, ?, ?)")) {
                ps.setString(1, name);
                ps.setInt(2, limit);
                ps.setInt(3, inc);
                ps.executeUpdate();
            }
        });
    }

    private void deleteLimit(final String clanKey) {
        plugin.getDatabaseManager().async(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM clan_ally_limits WHERE LOWER(clan) = ?")) {
                ps.setString(1, clanKey);
                ps.executeUpdate();
            }
        });
    }

    private static void deleteRow(final Connection conn, final String owner, final String member) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM clan_allies WHERE LOWER(owner_clan) = ? AND LOWER(ally_clan) = ?")) {
            ps.setString(1, owner.toLowerCase(Locale.ROOT));
            ps.setString(2, member.toLowerCase(Locale.ROOT));
            ps.executeUpdate();
        }
    }
}
