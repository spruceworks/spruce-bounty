package dev.spruceworks.bounty.settings;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.slf4j.Logger;

/**
 * Optional integration with SpruceSettings: when that plugin is installed,
 * players get a "Bounty broadcasts" toggle in {@code /settings} controlling
 * whether they see bounty claim announcements.
 *
 * <h2>Why reflection instead of a compileOnly dependency</h2>
 *
 * SpruceSettings is not published to any Maven repository yet. Adding a
 * JitPack coordinate would make this plugin's CI depend on JitPack
 * successfully building a Java 25 project on demand — an external service in
 * our build path, for a two-method integration. Reflection keeps the coupling
 * at zero: no coordinate, no shaded classes, no version skew, and CI cannot
 * break because someone else's build server had a bad day.
 *
 * <p>SpruceSettings' API exposes {@code registerSimple(...)} taking only JDK
 * types precisely so callers can do this. If SpruceSettings is ever published
 * properly, this class can be swapped for the typed API with no behaviour
 * change.
 *
 * <h2>Failure behaviour</h2>
 *
 * Every failure path degrades to "no toggle, broadcasts visible to everyone",
 * which is exactly how SpruceBounty behaves without SpruceSettings installed.
 * The integration can never make things worse than not having it.
 */
public final class SettingsHook {

    /** Namespaced under our own plugin name, as the API requires. */
    public static final String TOGGLE_KEY = "sprucebounty:broadcasts";

    private static final String API_CLASS = "dev.spruceworks.settings.api.SpruceSettingsAPI";

    private final Logger logger;
    private Object api;
    private Method isEnabledMethod;

    public SettingsHook(Logger logger) {
        this.logger = logger;
    }

    /**
     * Registers our toggle if SpruceSettings is present. Safe to call
     * unconditionally — the plugin-name check happens first and touches
     * nothing belonging to SpruceSettings.
     */
    public void install() {
        if (Bukkit.getPluginManager().getPlugin("SpruceSettings") == null) {
            return; // not installed: stay silent, this is the normal case
        }
        try {
            Class<?> apiClass = Class.forName(API_CLASS);
            RegisteredServiceProvider<?> rsp = Bukkit.getServicesManager().getRegistration(apiClass);
            if (rsp == null) {
                this.logger.warn("SpruceSettings is installed but its API service is not registered; "
                        + "bounty broadcast toggle unavailable this session.");
                return;
            }
            Object provider = rsp.getProvider();

            Method registerSimple = apiClass.getMethod("registerSimple",
                    String.class, String.class, List.class, String.class, boolean.class, String.class);
            registerSimple.invoke(provider,
                    TOGGLE_KEY,
                    "<gold>Bounty broadcasts</gold>",
                    List.of(
                            "<gray>Show bounty claim announcements</gray>",
                            "<gray>in chat.</gray>"),
                    "GOLD_INGOT",
                    true,       // visible by default — opting out is the deliberate act
                    null);      // no permission required

            this.isEnabledMethod = apiClass.getMethod("isEnabled", UUID.class, String.class);
            this.api = provider;
            this.logger.info("Hooked into SpruceSettings — players can toggle bounty broadcasts in /settings.");
        } catch (ReflectiveOperationException | RuntimeException e) {
            // Version mismatch, renamed method, security manager — all the same
            // to us: fall back to always-visible broadcasts.
            this.logger.warn("Could not hook into SpruceSettings ({}); bounty broadcasts stay visible to everyone.",
                    e.toString());
            this.api = null;
            this.isEnabledMethod = null;
        }
    }

    /**
     * Whether this player wants to see bounty broadcasts.
     *
     * <p>Returns true whenever SpruceSettings is absent or the lookup fails, so
     * the default is always "player sees the broadcast".
     */
    public boolean wantsBroadcasts(UUID player) {
        if (this.api == null || this.isEnabledMethod == null) {
            return true;
        }
        try {
            return (boolean) this.isEnabledMethod.invoke(this.api, player, TOGGLE_KEY);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return true;
        }
    }
}
