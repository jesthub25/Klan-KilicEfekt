package com.jestron.clanplugin;

import com.jestron.clanplugin.clan.AllyManager;
import com.jestron.clanplugin.clan.ClanChatManager;
import com.jestron.clanplugin.clan.ClanCommand;
import com.jestron.clanplugin.clan.ClanListener;
import com.jestron.clanplugin.clan.ClanManager;
import com.jestron.clanplugin.clan.InviteManager;
import com.jestron.clanplugin.config.ConfigManager;
import com.jestron.clanplugin.config.MessageManager;
import com.jestron.clanplugin.database.DatabaseManager;
import com.jestron.clanplugin.hook.PapiBridge;
import com.jestron.clanplugin.sword.SwordCommand;
import com.jestron.clanplugin.sword.SwordEffect;
import com.jestron.clanplugin.sword.SwordGUI;
import com.jestron.clanplugin.sword.SwordListener;
import com.jestron.clanplugin.sword.SwordManager;
import com.jestron.clanplugin.sword.effects.BlazeEffect;
import com.jestron.clanplugin.sword.effects.CadiEffect;
import com.jestron.clanplugin.sword.effects.CreeperEffect;
import com.jestron.clanplugin.sword.effects.EfektsilenEffect;
import com.jestron.clanplugin.sword.effects.EjderhaEffect;
import com.jestron.clanplugin.sword.effects.EndermanEffect;
import com.jestron.clanplugin.sword.effects.FreezeEffect;
import com.jestron.clanplugin.sword.effects.GhastEffect;
import com.jestron.clanplugin.sword.effects.KaplumbaEffect;
import com.jestron.clanplugin.sword.effects.KurbagaEffect;
import com.jestron.clanplugin.sword.effects.PhantomEffect;
import com.jestron.clanplugin.sword.effects.PillagerEffect;
import com.jestron.clanplugin.sword.effects.SelfBuffEffect;
import com.jestron.clanplugin.sword.effects.ShulkerEffect;
import com.jestron.clanplugin.sword.effects.SlimeEffect;
import com.jestron.clanplugin.sword.effects.SpiderEffect;
import com.jestron.clanplugin.sword.effects.WardenEffect;
import com.jestron.clanplugin.sword.effects.WitherEffect;
import com.jestron.clanplugin.util.SelfTest;
import com.jestron.clanplugin.util.Tags;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class ClanPlugin extends JavaPlugin {

    private static ClanPlugin instance;

    private ConfigManager configManager;
    private MessageManager messageManager;
    private DatabaseManager databaseManager;
    private ClanManager clanManager;
    private AllyManager allyManager;
    private InviteManager inviteManager;
    private ClanChatManager chatManager;
    private SwordManager swordManager;
    private SwordGUI swordGUI;

    @Override
    public void onEnable() {
        instance = this;
        Tags.init(this);
        try {
            configManager = new ConfigManager(this);
            messageManager = new MessageManager(this);

            databaseManager = new DatabaseManager(this);
            if (!databaseManager.connect()) {
                getLogger().severe("Veritabanına bağlanılamadı, eklenti devre dışı bırakılıyor.");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }

            clanManager = new ClanManager(this);
            clanManager.load();
            allyManager = new AllyManager(this);
            allyManager.load();
            inviteManager = new InviteManager(this);
            chatManager = new ClanChatManager(this);

            swordManager = new SwordManager(this);
            registerEffects();
            swordManager.reloadSwords();
            swordGUI = new SwordGUI(this);

            final PluginManager pm = getServer().getPluginManager();
            pm.registerEvents(chatManager, this);
            pm.registerEvents(new ClanListener(this), this);
            pm.registerEvents(new SwordListener(this), this);
            pm.registerEvents(swordGUI, this);
            for (final SwordEffect effect : swordManager.getEffects()) {
                if (effect instanceof Listener) {
                    pm.registerEvents((Listener) effect, this);
                }
            }

            final ClanCommand clanCommand = new ClanCommand(this);
            final SwordCommand swordCommand = new SwordCommand(this);
            bind("klan", clanCommand, clanCommand);
            bind("kilicefekt", swordCommand, swordCommand);
            bind("kilicefektgui", swordCommand, swordCommand);

            hookPlaceholderApi(pm);
            startTasks();
            SelfTest.scheduleIfRequested(this);

            getLogger().info("WorldGuard: " + (pm.isPluginEnabled("WorldGuard") ? "bağlandı" : "yok (efektler her yerde çalışır)")
                    + " | Vault: " + (pm.getPlugin("Vault") != null ? "var" : "yok"));
            getLogger().info("ClanSwordPlugin v" + getDescription().getVersion() + " aktif. Sunucu: "
                    + Bukkit.getName() + ' ' + Bukkit.getBukkitVersion());
        } catch (final Throwable t) {
            getLogger().log(Level.SEVERE, "Eklenti başlatılırken hata oluştu!", t);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void registerEffects() {
        swordManager.registerEffect(new SpiderEffect(this));
        swordManager.registerEffect(new EndermanEffect(this));
        swordManager.registerEffect(new WitherEffect(this));
        swordManager.registerEffect(new KurbagaEffect(this));
        swordManager.registerEffect(new ShulkerEffect(this));
        swordManager.registerEffect(new EjderhaEffect(this));
        swordManager.registerEffect(SelfBuffEffect.golemResistance(this));
        swordManager.registerEffect(SelfBuffEffect.golemStrength(this));
        swordManager.registerEffect(new PillagerEffect(this));
        swordManager.registerEffect(new CadiEffect(this));
        swordManager.registerEffect(new CreeperEffect(this));
        swordManager.registerEffect(new BlazeEffect(this));
        swordManager.registerEffect(new GhastEffect(this));
        swordManager.registerEffect(new WardenEffect(this));
        swordManager.registerEffect(SelfBuffEffect.regeneration(this));
        swordManager.registerEffect(new EfektsilenEffect(this));
        swordManager.registerEffect(new PhantomEffect(this));
        swordManager.registerEffect(new SlimeEffect(this));
        swordManager.registerEffect(new FreezeEffect(this));
        swordManager.registerEffect(new KaplumbaEffect(this));
    }

    private void bind(final String name, final CommandExecutor executor, final TabCompleter completer) {
        final PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().warning("plugin.yml'de '" + name + "' komutu bulunamadı!");
            return;
        }
        command.setExecutor(executor);
        command.setTabCompleter(completer);
    }

    private void hookPlaceholderApi(final PluginManager pm) {
        if (pm.getPlugin("PlaceholderAPI") == null) {
            return;
        }
        try {
            if (PapiBridge.register(this)) {
                getLogger().info("PlaceholderAPI bağlandı (%klan_...%).");
            }
        } catch (final Throwable t) {
            getLogger().log(Level.WARNING, "PlaceholderAPI bağlantısı kurulamadı", t);
        }
    }

    private void startTasks() {
        final long saveTicks = Math.max(60, getConfig().getInt("performance.save-interval", 300)) * 20L;
        Bukkit.getScheduler().runTaskTimer(this, () -> clanManager.saveAllAsync(), saveTicks, saveTicks);
        Bukkit.getScheduler().runTaskTimer(this, () -> swordManager.purgeExpired(), 6000L, 6000L);
    }

    @Override
    public void onDisable() {
        try {
            if (swordManager != null) {
                swordManager.shutdown(); // ağları sil, çağrılan mobları kaldır, gizlenenleri göster
            }
            if (databaseManager != null) {
                databaseManager.flush();
                if (clanManager != null) {
                    clanManager.saveAllSync();
                }
                databaseManager.disconnect();
            }
        } catch (final Throwable t) {
            getLogger().log(Level.SEVERE, "Eklenti kapatılırken hata oluştu!", t);
        }
        instance = null;
    }

    public static ClanPlugin getInstance()          { return instance; }
    public ConfigManager getConfigManager()         { return configManager; }
    public MessageManager getMessageManager()       { return messageManager; }
    public DatabaseManager getDatabaseManager()     { return databaseManager; }
    public ClanManager getClanManager()             { return clanManager; }
    public AllyManager getAllyManager()             { return allyManager; }
    public InviteManager getInviteManager()         { return inviteManager; }
    public ClanChatManager getChatManager()         { return chatManager; }
    public SwordManager getSwordManager()           { return swordManager; }
    public SwordGUI getSwordGUI()                   { return swordGUI; }
}
