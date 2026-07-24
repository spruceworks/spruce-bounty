package dev.spruceworks.bounty.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Owns config.yml and messages.yml: copies the bundled defaults on first run,
 * loads both files, and supports runtime reloads without a server restart.
 */
public final class ConfigManager {

    private final JavaPlugin plugin;
    private FileConfiguration config = new YamlConfiguration();
    private FileConfiguration messages = new YamlConfiguration();

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        if (!reload()) {
            plugin.getSLF4JLogger().error("Falling back to the bundled default configuration.");
            this.config = bundledDefaults("config.yml");
            this.messages = bundledDefaults("messages.yml");
        }
    }

    /**
     * Re-reads both files from disk. Only replaces the live configuration if
     * every file parses, so a broken edit never half-applies.
     *
     * @return true if the reload succeeded
     */
    public boolean reload() {
        try {
            FileConfiguration config = loadYaml("config.yml");
            FileConfiguration messages = loadYaml("messages.yml");
            this.config = config;
            this.messages = messages;
            return true;
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getSLF4JLogger().error("Failed to load configuration", e);
            return false;
        }
    }

    public FileConfiguration config() {
        return this.config;
    }

    public FileConfiguration messages() {
        return this.messages;
    }

    private FileConfiguration loadYaml(String fileName) throws IOException, InvalidConfigurationException {
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            plugin.saveResource(fileName, false);
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(file);
        // Keys added in newer plugin versions resolve from the bundled file.
        yaml.setDefaults(bundledDefaults(fileName));
        return yaml;
    }

    private FileConfiguration bundledDefaults(String fileName) {
        try (InputStream bundled = plugin.getResource(fileName)) {
            if (bundled == null) {
                return new YamlConfiguration();
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(bundled, StandardCharsets.UTF_8));
        } catch (IOException e) {
            plugin.getSLF4JLogger().error("Failed to read bundled {}", fileName, e);
            return new YamlConfiguration();
        }
    }
}
