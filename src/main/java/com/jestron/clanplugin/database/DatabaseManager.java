package com.jestron.clanplugin.database;

import com.jestron.clanplugin.ClanPlugin;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * SQLite / MySQL bağlantısı. Tüm yazma işlemleri TEK bir arka plan thread'inde sırayla
 * yapılır: ana thread hiçbir zaman veritabanı beklemez ve SQLite kilitlenmesi oluşmaz.
 */
public final class DatabaseManager {

    private final ClanPlugin plugin;
    private HikariDataSource dataSource;
    private ExecutorService executor;
    private boolean mysql;

    public DatabaseManager(final ClanPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean connect() {
        final FileConfiguration cfg = plugin.getConfig();
        mysql = "MYSQL".equals(cfg.getString("database.type", "SQLITE").trim().toUpperCase(Locale.ROOT));
        try {
            final HikariConfig hikari = mysql ? mysqlConfig(cfg) : sqliteConfig();
            hikari.setPoolName("ClanSwordPlugin-DB");
            dataSource = new HikariDataSource(hikari);
            createTables();
            executor = Executors.newSingleThreadExecutor(runnable -> {
                final Thread thread = new Thread(runnable, "ClanSwordPlugin-DB-Writer");
                thread.setDaemon(true);
                return thread;
            });
            plugin.getLogger().info((mysql ? "MySQL" : "SQLite") + " veritabanı bağlantısı kuruldu.");
            return true;
        } catch (final Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "Veritabanı bağlantısı kurulamadı!", t);
            disconnect();
            return false;
        }
    }

    private HikariConfig mysqlConfig(final FileConfiguration cfg) {
        final HikariConfig config = new HikariConfig();
        final String driver = firstAvailable("com.mysql.cj.jdbc.Driver", "com.mysql.jdbc.Driver");
        if (driver != null) {
            config.setDriverClassName(driver);
        }
        config.setJdbcUrl("jdbc:mysql://"
                + cfg.getString("database.mysql.host", "localhost") + ':'
                + cfg.getInt("database.mysql.port", 3306) + '/'
                + cfg.getString("database.mysql.database", "clanplugin")
                + "?useSSL=" + cfg.getBoolean("database.mysql.use-ssl", false)
                + "&useUnicode=true&characterEncoding=utf8&autoReconnect=true");
        config.setUsername(cfg.getString("database.mysql.username", "root"));
        config.setPassword(cfg.getString("database.mysql.password", ""));
        config.setMaximumPoolSize(Math.max(2, cfg.getInt("database.mysql.pool-size", 10)));
        config.setConnectionTimeout(10_000L);
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        return config;
    }

    private HikariConfig sqliteConfig() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Eklenti klasörü oluşturulamadı: " + plugin.getDataFolder());
        }
        final File file = new File(plugin.getDataFolder(), "database.db");
        final HikariConfig config = new HikariConfig();
        if (firstAvailable("org.sqlite.JDBC") != null) {
            config.setDriverClassName("org.sqlite.JDBC");
        }
        config.setJdbcUrl("jdbc:sqlite:" + file.getAbsolutePath());
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(30_000L);
        config.addDataSourceProperty("journal_mode", "WAL");
        config.addDataSourceProperty("busy_timeout", "5000");
        return config;
    }

    private static String firstAvailable(final String... classNames) {
        for (final String name : classNames) {
            try {
                Class.forName(name);
                return name;
            } catch (final Throwable ignored) {
                // sıradakini dene
            }
        }
        return null;
    }

    private void createTables() throws SQLException {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS clans ("
                    + "name VARCHAR(32) PRIMARY KEY,"
                    + "owner VARCHAR(36) NOT NULL,"
                    + "members TEXT,"
                    + "mods TEXT,"
                    + "points INT DEFAULT 0,"
                    + "kills INT DEFAULT 0,"
                    + "deaths INT DEFAULT 0,"
                    + "member_limit INT DEFAULT 10,"
                    + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            // Eski sürümlerden gelen tablolar için (kolon zaten varsa sessizce geçer)
            silent(stmt, "ALTER TABLE clans ADD COLUMN kills INT DEFAULT 0");
            silent(stmt, "ALTER TABLE clans ADD COLUMN deaths INT DEFAULT 0");

            stmt.execute("CREATE TABLE IF NOT EXISTS clan_allies ("
                    + "owner_clan VARCHAR(32) NOT NULL,"
                    + "ally_clan VARCHAR(32) NOT NULL,"
                    + "status VARCHAR(16) DEFAULT 'accepted',"
                    + "invited_by VARCHAR(36),"
                    + "accepted_by VARCHAR(36),"
                    + "ally_limit INT DEFAULT 3,"
                    + "limit_increases INT DEFAULT 0,"
                    + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
                    + "PRIMARY KEY (owner_clan, ally_clan))");

            stmt.execute("CREATE TABLE IF NOT EXISTS clan_ally_limits ("
                    + "clan VARCHAR(32) PRIMARY KEY,"
                    + "ally_limit INT NOT NULL,"
                    + "limit_increases INT NOT NULL)");
        }
    }

    private static void silent(final Statement stmt, final String sql) {
        try {
            stmt.execute(sql);
        } catch (final SQLException ignored) {
            // beklenen durum
        }
    }

    public Connection getConnection() throws SQLException {
        final HikariDataSource ds = dataSource;
        if (ds == null || ds.isClosed()) {
            throw new SQLException("Veritabanı bağlantısı kapalı!");
        }
        return ds.getConnection();
    }

    /** İşi veritabanı thread'ine sıraya koyar. Kapanış sırasında aynı thread'de çalıştırır. */
    public void async(final SqlTask task) {
        final ExecutorService ex = executor;
        if (ex == null || ex.isShutdown()) {
            run(task);
            return;
        }
        try {
            ex.execute(() -> run(task));
        } catch (final Throwable rejected) {
            run(task);
        }
    }

    private void run(final SqlTask task) {
        try (Connection conn = getConnection()) {
            task.run(conn);
        } catch (final Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "Veritabanı işlemi başarısız!", t);
        }
    }

    /** Kuyruktaki tüm işlerin bitmesini bekler (onDisable). */
    public void flush() {
        final ExecutorService ex = executor;
        if (ex == null) {
            return;
        }
        ex.shutdown();
        try {
            if (!ex.awaitTermination(20, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("Veritabanı kuyruğu 20 saniyede bitmedi, kalanlar iptal ediliyor.");
                ex.shutdownNow();
            }
        } catch (final InterruptedException ie) {
            ex.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public void disconnect() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    public boolean isMySQL() {
        return mysql;
    }

    /** MySQL ve SQLite'ta ayrı söz dizimiyle "varsa güncelle yoksa ekle". */
    public String upsertClanSql() {
        if (mysql) {
            return "INSERT INTO clans (name,owner,members,mods,points,kills,deaths,member_limit)"
                    + " VALUES (?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE"
                    + " owner=VALUES(owner), members=VALUES(members), mods=VALUES(mods),"
                    + " points=VALUES(points), kills=VALUES(kills), deaths=VALUES(deaths),"
                    + " member_limit=VALUES(member_limit)";
        }
        return "INSERT INTO clans (name,owner,members,mods,points,kills,deaths,member_limit)"
                + " VALUES (?,?,?,?,?,?,?,?) ON CONFLICT(name) DO UPDATE SET"
                + " owner=excluded.owner, members=excluded.members, mods=excluded.mods,"
                + " points=excluded.points, kills=excluded.kills, deaths=excluded.deaths,"
                + " member_limit=excluded.member_limit";
    }

    @FunctionalInterface
    public interface SqlTask {
        void run(Connection connection) throws SQLException;
    }
}
