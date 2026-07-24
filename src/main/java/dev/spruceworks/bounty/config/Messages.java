package dev.spruceworks.bounty.config;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/**
 * Resolves player-facing text from messages.yml as MiniMessage components.
 * The {@code <prefix>} tag is available in every message.
 */
public final class Messages {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final ConfigManager configManager;

    public Messages(ConfigManager configManager) {
        this.configManager = configManager;
    }

    public Component get(String key, TagResolver... resolvers) {
        String raw = this.configManager.messages().getString(key);
        if (raw == null) {
            return Component.text("Missing message: " + key, NamedTextColor.RED);
        }
        return MINI_MESSAGE.deserialize(raw, TagResolver.resolver(prefixTag(), TagResolver.resolver(resolvers)));
    }

    public void send(Audience audience, String key, TagResolver... resolvers) {
        audience.sendMessage(get(key, resolvers));
    }

    private TagResolver prefixTag() {
        String raw = this.configManager.messages().getString("prefix", "");
        return TagResolver.resolver("prefix", Tag.selfClosingInserting(MINI_MESSAGE.deserialize(raw)));
    }
}
