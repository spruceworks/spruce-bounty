package dev.spruceworks.bounty;

import dev.spruceworks.bounty.command.BountyAdminCommand;
import dev.spruceworks.bounty.command.BountyCommand;
import dev.spruceworks.bounty.config.ConfigManager;
import dev.spruceworks.bounty.config.Messages;
import dev.spruceworks.bounty.gui.GuiListener;
import dev.spruceworks.bounty.listener.BountyClaimListener;
import dev.spruceworks.bounty.placeholder.SprucePlaceholderExpansion;
import dev.spruceworks.bounty.settings.SettingsHook;
import dev.spruceworks.bounty.service.AntiAbuseService;
import dev.spruceworks.bounty.service.BountyService;
import dev.spruceworks.bounty.storage.BountyStorage;
import dev.spruceworks.bounty.storage.SqliteBountyStorage;
import dev.spruceworks.bounty.util.SchedulerAdapter;
import net.milkbowl.vault.economy.Economy;
import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class SpruceBountyPlugin extends JavaPlugin {

    /** bStats service id — https://bstats.org/plugin/bukkit/SpruceBounty/32880 */
    private static final int BSTATS_SERVICE_ID = 32880;
    private static final long COOLDOWN_SWEEP_PERIOD_TICKS = 20L * 60 * 30; // 30 minutes

    private ConfigManager configManager;
    private Messages messages;
    private SchedulerAdapter scheduler;
    private BountyStorage storage;
    private BountyService bountyService;
    private Economy economy;
    private Metrics metrics;
    private SettingsHook settingsHook;

    @Override
    public void onEnable() {
        this.configManager = new ConfigManager(this);
        this.configManager.load();
        this.messages = new Messages(this.configManager);
        this.scheduler = new SchedulerAdapter(this);

        // Checked by name, not by class: merely referencing the Economy class
        // literal (even just for a null check) forces the JVM to load it, which
        // throws ClassNotFoundException when Vault isn't installed since Economy
        // is compileOnly. This plugin-name check touches no Vault class, so it's
        // always safe, and it gates every line below that does touch one.
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            getSLF4JLogger().error("Vault was not found. SpruceBounty needs Vault plus an economy plugin "
                    + "(e.g. EssentialsX) installed to place and pay out bounties. Disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        Economy hooked = hookEconomy();
        if (hooked != null) {
            finishEnable(hooked);
            return;
        }
        // Vault itself is confirmed present and loaded before us (plugin.yml
        // softdepend), but the economy plugin behind it might not have registered
        // its service yet — every plugin's onEnable() runs before the first tick,
        // so one retry at tick 1 is enough; no need to poll indefinitely.
        this.scheduler.runLaterSync(() -> {
            Economy retried = hookEconomy();
            if (retried == null) {
                getSLF4JLogger().error("No economy plugin is registered with Vault (e.g. EssentialsX). "
                        + "SpruceBounty needs one installed to place and pay out bounties. Disabling.");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
            finishEnable(retried);
        }, 1L);
    }

    private Economy hookEconomy() {
        RegisteredServiceProvider<Economy> provider = getServer().getServicesManager().getRegistration(Economy.class);
        return provider == null ? null : provider.getProvider();
    }

    private void finishEnable(Economy economy) {
        this.economy = economy;

        this.storage = new SqliteBountyStorage(getDataFolder(), getSLF4JLogger());
        this.storage.open();

        AntiAbuseService antiAbuse = new AntiAbuseService(this.configManager);
        this.bountyService = new BountyService(this.configManager, this.storage, this.scheduler, this.economy,
                antiAbuse, getSLF4JLogger());
        this.bountyService.loadFromStorage();

        BountyCommand.register(this);
        BountyAdminCommand.register(this);

        // Optional SpruceSettings integration — no-op when that plugin is absent.
        this.settingsHook = new SettingsHook(getSLF4JLogger());
        this.settingsHook.install();

        getServer().getPluginManager().registerEvents(new BountyClaimListener(this), this);
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);

        this.scheduler.runTimerAsync(this.bountyService::sweepExpiredCooldowns,
                COOLDOWN_SWEEP_PERIOD_TICKS, COOLDOWN_SWEEP_PERIOD_TICKS);

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new SprucePlaceholderExpansion(this).register();
            getSLF4JLogger().info("Hooked into PlaceholderAPI.");
        }

        if (BSTATS_SERVICE_ID > 0 && this.configManager.config().getBoolean("metrics", true)) {
            this.metrics = new Metrics(this, BSTATS_SERVICE_ID);
        }

        if (this.configManager.config().getBoolean("debug", false)) {
            getSLF4JLogger().info("Debug mode is enabled.");
        }
        getSLF4JLogger().info("SpruceBounty enabled — hooked economy provider: {}", economy.getName());
    }

    @Override
    public void onDisable() {
        if (this.storage != null) {
            this.storage.close();
        }
        if (this.metrics != null) {
            this.metrics.shutdown();
        }
    }

    public ConfigManager configManager() {
        return this.configManager;
    }

    public Messages messages() {
        return this.messages;
    }

    public SchedulerAdapter scheduler() {
        return this.scheduler;
    }

    public BountyService bountyService() {
        return this.bountyService;
    }

    public SettingsHook settingsHook() {
        return this.settingsHook;
    }

    public Economy economy() {
        return this.economy;
    }
}
