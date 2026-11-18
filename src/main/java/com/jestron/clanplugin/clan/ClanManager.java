package com.jestron.clanplugin.clan;

import com.jestron.clanplugin.ClanPlugin;
import com.jestron.clanplugin.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class ClanManager {

    private static final long TOP_CACHE_MS = 5_000L;

    private final ClanPlugin plugin;
    private final Map<String, Clan> clans = new ConcurrentHashMap<>();      // küçük harf isim -> klan
    private final Map<UUID, String> playerClan = new ConcurrentHashMap<>(); // oyuncu -> küçük harf isim

    private final Map<String, List<Clan>> topCache = new ConcurrentHashMap<>();
    private volatile long topBuiltAt;

    public ClanManager(final ClanPlugin plugin) {
        this.plugin = plugin;
    }

    private DatabaseManager db() {
        return plugin.getDatabaseManager();
    }

    /** Sunucu açılışında senkron yükler (komutlar ilk andan itibaren doğru çalışsın). */
    public void load() {
        final int defLimit = plugin.getConfig().getInt("clan.default-member-limit", 10);
        try (Connection conn = db().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM clans")) {
            while (rs.next()) {
                final String name = rs.getString("name");
                final UUID owner;
                try {
                    owner = UUID.fromString(rs.getString("owner"));
                } catch (final Exception bad) {
                    plugin.getLogger().warning("Bozuk klan kaydı atlandı: " + name);
                    continue;
                }
                final int limit = rs.getInt("member_limit");
                final Clan clan = new Clan(name, owner, limit > 0 ? limit : defLimit);
                clan.deserializeMembers(rs.getString("members"));
                clan.deserializeMods(rs.getString("mods"));
                clan.setPoints(rs.getInt("points"));
                clan.setKills(rs.getInt("kills"));
                clan.setDeaths(rs.getInt("deaths"));
                register(clan);
            }
            plugin.getLogger().info(clans.size() + " klan yüklendi.");
        } catch (final SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Klanlar yüklenemedi!", e);
        }
    }

    private void register(final Clan clan) {
        clans.put(clan.getKey(), clan);
        for (final UUID member : clan.getMembers()) {
            playerClan.put(member, clan.getKey());
        }
        invalidateTop();
    }

    // ------------------------------------------------------------------
    // Sorgular
    // ------------------------------------------------------------------

    public Clan getClan(final String name) {
        return name == null ? null : clans.get(name.toLowerCase(Locale.ROOT));
    }

    public Clan getPlayerClan(final UUID uuid) {
        if (uuid == null) {
            return null;
        }
        final String key = playerClan.get(uuid);
        return key == null ? null : clans.get(key);
    }

    public Collection<Clan> getAllClans() {
        return Collections.unmodifiableCollection(clans.values());
    }

    public boolean isSameClan(final UUID a, final UUID b) {
        if (a == null || b == null) {
            return false;
        }
        final String ca = playerClan.get(a);
        return ca != null && ca.equals(playerClan.get(b));
    }

    // ------------------------------------------------------------------
    // Değişiklikler (bellek anında, veritabanı arka planda)
    // ------------------------------------------------------------------

    public Clan createClan(final String name, final UUID owner) {
        final String key = name.toLowerCase(Locale.ROOT);
        if (clans.containsKey(key) || playerClan.containsKey(owner)) {
            return null;
        }
        final Clan clan = new Clan(name, owner, plugin.getConfig().getInt("clan.default-member-limit", 10));
        register(clan);
        save(clan);
        return clan;
    }

    public void deleteClan(final Clan clan) {
        if (clan == null || clans.remove(clan.getKey()) == null) {
            return;
        }
        for (final UUID member : clan.getMembers()) {
            playerClan.remove(member, clan.getKey());
        }
        invalidateTop();
        final String name = clan.getName();
        db().async(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM clans WHERE name = ?")) {
                ps.setString(1, name);
                ps.executeUpdate();
            }
        });
    }

    public void addMember(final Clan clan, final UUID player) {
        clan.addMember(player);
        playerClan.put(player, clan.getKey());
        save(clan);
    }

    public void removeMember(final Clan clan, final UUID player) {
        clan.removeMember(player);
        playerClan.remove(player, clan.getKey());
        save(clan);
    }

    public void addKill(final Clan clan) {
        if (clan == null) {
            return;
        }
        clan.addKill();
        invalidateTop();
        saveStats(clan);
    }

    public void addDeath(final Clan clan) {
        if (clan == null) {
            return;
        }
        clan.addDeath();
        invalidateTop();
        saveStats(clan);
    }

    // ------------------------------------------------------------------
    // Kayıt
    // ------------------------------------------------------------------

    /** Klanın o anki halini kopyalayıp arka planda yazar. */
    public void save(final Clan clan) {
        final Row row = new Row(clan);
        db().async(conn -> write(conn, row));
    }

    private void saveStats(final Clan clan) {
        final String name = clan.getName();
        final String key = clan.getKey();
        final int kills = clan.getKills();
        final int deaths = clan.getDeaths();
        db().async(conn -> {
            if (!clans.containsKey(key)) {
                return;
            }
            try (PreparedStatement ps = conn.prepareStatement("UPDATE clans SET kills = ?, deaths = ? WHERE name = ?")) {
                ps.setInt(1, kills);
                ps.setInt(2, deaths);
                ps.setString(3, name);
                ps.executeUpdate();
            }
        });
    }

    public void saveAllAsync() {
        final List<Row> rows = snapshot();
        db().async(conn -> {
            for (final Row row : rows) {
                write(conn, row);
            }
        });
    }

    /** onDisable: kuyruk boşaltıldıktan sonra son durumu doğrudan yazar. */
    public void saveAllSync() {
        try (Connection conn = db().getConnection()) {
            for (final Row row : snapshot()) {
                write(conn, row);
            }
        } catch (final SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Klanlar kaydedilemedi!", e);
        }
    }

    private List<Row> snapshot() {
        final List<Row> rows = new ArrayList<>(clans.size());
        for (final Clan clan : clans.values()) {
            rows.add(new Row(clan));
        }
        return rows;
    }

    private void write(final Connection conn, final Row row) throws SQLException {
        if (!clans.containsKey(row.key)) {
            return; // bu arada silindi, hayalet kayıt oluşturma
        }
        try (PreparedStatement ps = conn.prepareStatement(db().upsertClanSql())) {
            ps.setString(1, row.name);
            ps.setString(2, row.owner);
            ps.setString(3, row.members);
            ps.setString(4, row.mods);
            ps.setInt(5, row.points);
            ps.setInt(6, row.kills);
            ps.setInt(7, row.deaths);
            ps.setInt(8, row.limit);
            ps.executeUpdate();
        }
    }

    private static final class Row {
        final String key;
        final String name;
        final String owner;
        final String members;
        final String mods;
        final int points;
        final int kills;
        final int deaths;
        final int limit;

        Row(final Clan clan) {
            key = clan.getKey();
            name = clan.getName();
            owner = clan.getOwner().toString();
            members = clan.serializeMembers();
            mods = clan.serializeMods();
            points = clan.getPoints();
            kills = clan.getKills();
            deaths = clan.getDeaths();
            limit = clan.getMemberLimit();
        }
    }

    // ------------------------------------------------------------------
    // Sıralama (5 sn önbellekli - placeholder'lar her tick'te sıralama yapmasın)
    // ------------------------------------------------------------------

    public void invalidateTop() {
        topBuiltAt = 0L;
    }

    /** type: "kills", "deaths" veya "kdr". */
    public List<Clan> getTop(final String type, final int limit) {
        final long now = System.currentTimeMillis();
        if (now - topBuiltAt > TOP_CACHE_MS) {
            topCache.clear();
            topBuiltAt = now;
        }
        final List<Clan> sorted = topCache.computeIfAbsent(type, this::sort);
        return sorted.size() <= limit ? sorted : sorted.subList(0, limit);
    }

    private List<Clan> sort(final String type) {
        final List<Clan> list = new ArrayList<>(clans.values());
        final Comparator<Clan> comparator;
        if ("kills".equals(type)) {
            comparator = Comparator.comparingInt(Clan::getKills);
        } else if ("deaths".equals(type)) {
            comparator = Comparator.comparingInt(Clan::getDeaths);
        } else {
            comparator = Comparator.comparingDouble(Clan::getKDR);
        }
        list.sort(comparator.reversed().thenComparing(Clan::getName, String.CASE_INSENSITIVE_ORDER));
        return Collections.unmodifiableList(list);
    }
}
