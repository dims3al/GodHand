package xyz.dimseal.godHand.hand;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Centralized configurable target-chat and custom death-message text. */
public final class GodHandMessages {

    private static boolean enabled = true;
    private static boolean targetEnabled = true;
    private static boolean deathEnabled = true;
    private static String prefix = "§8[§f§lHAND OF GOD§8] §r";
    private static final Map<String, String> TARGET = new HashMap<>();
    private static final Map<String, String> DEATH = new HashMap<>();

    private GodHandMessages() {}

    public static void configure(YamlConfiguration yaml) {
        enabled = yaml.getBoolean("messages.enabled", true);
        targetEnabled = yaml.getBoolean("messages.target.enabled", true);
        deathEnabled = yaml.getBoolean("messages.death.enabled", true);
        prefix = color(yaml.getString("messages.prefix", "&8[&f&lHAND OF GOD&8] &r"));

        TARGET.clear();
        if (yaml.isConfigurationSection("messages.target.text")) {
            for (String key : yaml.getConfigurationSection("messages.target.text").getKeys(false)) {
                TARGET.put(normalize(key), color(yaml.getString("messages.target.text." + key, "")));
            }
        }

        DEATH.clear();
        if (yaml.isConfigurationSection("messages.death.text")) {
            for (String key : yaml.getConfigurationSection("messages.death.text").getKeys(false)) {
                DEATH.put(normalize(key), color(yaml.getString("messages.death.text." + key, "")));
            }
        }
    }

    public static void send(Player target, String key, String fallback) {
        if (!enabled || !targetEnabled || target == null || !target.isOnline()) return;
        String text = TARGET.getOrDefault(normalize(key), fallback);
        if (text == null || text.isBlank()) return;
        target.sendMessage(prefix + text);
    }

    public static String death(Player player, String key, String fallback) {
        if (!enabled || !deathEnabled) return null;
        String text = DEATH.getOrDefault(normalize(key), fallback);
        if (text == null || text.isBlank()) return null;
        String name = player == null ? "A player" : player.getName();
        return text.replace("{player}", name);
    }

    private static String normalize(String key) {
        return key == null ? "" : key.toLowerCase(Locale.ROOT).replace('-', '_');
    }

    private static String color(String text) {
        if (text == null) return "";
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
