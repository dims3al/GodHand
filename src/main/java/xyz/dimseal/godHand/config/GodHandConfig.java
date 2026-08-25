package xyz.dimseal.godHand.config;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import xyz.dimseal.godHand.hand.GodHandMessages;
import xyz.dimseal.godHand.hand.MainHandSettings;
import xyz.dimseal.godHand.hand.render.HandDensity;
import xyz.dimseal.godHand.hand.render.HandPalette;
import xyz.dimseal.godHand.hand.render.HandRenderMode;
import xyz.dimseal.godHand.hand.render.WristStyle;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Owns GodHand's configuration, operator allowlist, default Hand settings, and configurable messages.
 */
public final class GodHandConfig {

    public record OperatorEntry(String name, UUID uuid) {}

    private final JavaPlugin plugin;
    private final MainHandSettings settings;
    private final File configFile;
    private final File operatorWhitelistFile;
    private YamlConfiguration yaml;
    private YamlConfiguration operatorWhitelistYaml;

    public GodHandConfig(JavaPlugin plugin, MainHandSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
        this.configFile = new File(plugin.getDataFolder(), "config.yml");
        this.operatorWhitelistFile = new File(plugin.getDataFolder(), "operator_whitelist.yml");
    }

    public void load() {
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }
        if (!operatorWhitelistFile.exists()) {
            plugin.saveResource("operator_whitelist.yml", false);
        }

        this.yaml = loadYaml(configFile, "config.yml");
        this.operatorWhitelistYaml = loadYaml(operatorWhitelistFile, "operator_whitelist.yml");
        migrateLegacyWhitelistIfNeeded();
        loadHandSettings();
        GodHandMessages.configure(yaml);
    }

    private YamlConfiguration loadYaml(File file, String displayName) {
        YamlConfiguration loaded = new YamlConfiguration();
        loaded.options().parseComments(true);
        try {
            loaded.load(file);
        } catch (IOException | InvalidConfigurationException ex) {
            throw new IllegalStateException("Could not load GodHand " + displayName, ex);
        }
        return loaded;
    }

    /** Migrates the pre-split 1.0.0 whitelist layout without discarding existing entries. */
    private void migrateLegacyWhitelistIfNeeded() {
        boolean changedConfig = false;
        boolean changedWhitelist = false;

        if (!yaml.contains("enforce_operator_whitelist") && yaml.contains("operator_whitelist.enabled")) {
            yaml.set("enforce_operator_whitelist", yaml.getBoolean("operator_whitelist.enabled", false));
            changedConfig = true;
        }

        List<Map<?, ?>> legacyRows = yaml.getMapList("operator_whitelist.players");
        if (!legacyRows.isEmpty() && operatorWhitelistYaml.getMapList("players").isEmpty()) {
            operatorWhitelistYaml.set("players", legacyRows);
            changedWhitelist = true;
        }

        if (yaml.contains("operator_whitelist")) {
            yaml.set("operator_whitelist", null);
            changedConfig = true;
        }

        if (!yaml.contains("enforce_operator_whitelist")) {
            yaml.set("enforce_operator_whitelist", false);
            changedConfig = true;
        }

        if (changedWhitelist) saveOperatorWhitelist();
        if (changedConfig) save();
    }

    public void reload() {
        load();
    }

    public void save() {
        try {
            yaml.save(configFile);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not save GodHand config.yml", ex);
        }
    }

    private void saveOperatorWhitelist() {
        try {
            operatorWhitelistYaml.save(operatorWhitelistFile);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not save GodHand operator_whitelist.yml", ex);
        }
    }

    public boolean isOperatorWhitelistEnabled() {
        return yaml.getBoolean("enforce_operator_whitelist", false);
    }

    public void setOperatorWhitelistEnabled(boolean enabled) {
        yaml.set("enforce_operator_whitelist", enabled);
        save();
    }

    public boolean isAuthorized(Player player) {
        if (player == null || !player.isOp()) return false;
        if (!isOperatorWhitelistEnabled()) return true;
        UUID uuid = player.getUniqueId();
        for (OperatorEntry entry : getOperatorWhitelist()) {
            if (entry.uuid().equals(uuid)) return true;
        }
        return false;
    }

    public List<OperatorEntry> getOperatorWhitelist() {
        List<OperatorEntry> out = new ArrayList<>();
        List<Map<?, ?>> maps = operatorWhitelistYaml.getMapList("players");
        for (Map<?, ?> map : maps) {
            Object rawUuid = map.get("uuid");
            if (rawUuid == null) continue;
            try {
                UUID uuid = UUID.fromString(String.valueOf(rawUuid));
                Object rawName = map.get("name");
                String name = rawName == null ? "Unknown" : String.valueOf(rawName);
                out.add(new OperatorEntry(name, uuid));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Ignoring invalid UUID in operator_whitelist.yml: " + rawUuid);
            }
        }
        return List.copyOf(out);
    }

    public OperatorEntry resolveKnownPlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return new OperatorEntry(online.getName(), online.getUniqueId());

        for (OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
            if (offline.getName() != null && offline.getName().equalsIgnoreCase(name)) {
                return new OperatorEntry(offline.getName(), offline.getUniqueId());
            }
        }
        return null;
    }

    public OperatorEntry addOperator(String playerName) {
        OperatorEntry entry = resolveKnownPlayer(playerName);
        if (entry == null) {
            throw new IllegalArgumentException("Player '" + playerName + "' must be online or have joined this server before.");
        }

        List<Map<String, Object>> rows = mutableWhitelistRows();
        rows.removeIf(row -> entry.uuid().toString().equalsIgnoreCase(String.valueOf(row.get("uuid"))));
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", entry.name());
        row.put("uuid", entry.uuid().toString());
        rows.add(row);
        operatorWhitelistYaml.set("players", rows);
        saveOperatorWhitelist();
        return entry;
    }

    public boolean removeOperator(String nameOrUuid) {
        String query = nameOrUuid.toLowerCase(Locale.ROOT);
        List<Map<String, Object>> rows = mutableWhitelistRows();
        boolean removed = rows.removeIf(row -> {
            String name = String.valueOf(row.getOrDefault("name", "")).toLowerCase(Locale.ROOT);
            String uuid = String.valueOf(row.getOrDefault("uuid", "")).toLowerCase(Locale.ROOT);
            return name.equals(query) || uuid.equals(query);
        });
        if (removed) {
            operatorWhitelistYaml.set("players", rows);
            saveOperatorWhitelist();
        }
        return removed;
    }

    private List<Map<String, Object>> mutableWhitelistRows() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<?, ?> raw : operatorWhitelistYaml.getMapList("players")) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                row.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            rows.add(row);
        }
        return rows;
    }

    public void saveCurrentSettings() {
        yaml.set("hand.settings.preset", settings.getPreset().configName());
        yaml.set("hand.settings.scale", settings.getScale());
        yaml.set("hand.settings.density", settings.getDensity().commandName());
        yaml.set("hand.settings.model", settings.getWristStyle().commandName());
        yaml.set("hand.settings.renderer", settings.getRenderMode().commandName());
        yaml.set("hand.settings.shading", settings.isShading());
        yaml.set("hand.settings.force_particles", settings.isForceParticles());

        String preset = HandPalette.presetName(settings.getColor());
        if (preset != null) {
            yaml.set("hand.settings.color.preset", preset);
        } else {
            yaml.set("hand.settings.color.preset", "custom");
        }
        yaml.set("hand.settings.color.custom_rgb", List.of(
                settings.getColor().getRed(), settings.getColor().getGreen(), settings.getColor().getBlue()));
        save();
    }

    private void loadHandSettings() {
        String presetName = yaml.getString("hand.settings.preset", "high");
        MainHandSettings.Preset preset = MainHandSettings.Preset.parse(presetName);
        if (preset != MainHandSettings.Preset.CUSTOM) {
            settings.applyPreset(preset);
            return;
        }

        double scale = yaml.getDouble("hand.settings.scale", MainHandSettings.DEFAULT_SCALE);
        HandDensity density = parseDensity(yaml.getString("hand.settings.density", "ultra"));
        WristStyle model = WristStyle.parse(yaml.getString("hand.settings.model", "anatomical"));
        HandRenderMode renderer = HandRenderMode.parse(yaml.getString("hand.settings.renderer", "itemdisplays"));
        boolean shading = yaml.getBoolean("hand.settings.shading", true);
        boolean forceParticles = yaml.getBoolean("hand.settings.force_particles", true);
        Color color = readConfiguredColor();
        settings.loadCustom(scale, density, color, shading, forceParticles, model, renderer);
    }

    private HandDensity parseDensity(String input) {
        try {
            return HandDensity.parse(input);
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Invalid hand.settings.density; using ultra.");
            return HandDensity.ULTRA;
        }
    }

    private Color readConfiguredColor() {
        String preset = yaml.getString("hand.settings.color.preset", "white");
        if (!"custom".equalsIgnoreCase(preset)) {
            try {
                return HandPalette.parsePreset(preset);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Invalid hand.settings.color.preset; using white.");
                return HandPalette.WHITE;
            }
        }
        List<Integer> rgb = yaml.getIntegerList("hand.settings.color.custom_rgb");
        if (rgb.size() != 3) {
            plugin.getLogger().warning("hand.settings.color.custom_rgb must contain exactly three values; using white.");
            return HandPalette.WHITE;
        }
        try {
            return HandPalette.parseRgb(rgb.get(0), rgb.get(1), rgb.get(2));
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Invalid custom RGB color; using white.");
            return HandPalette.WHITE;
        }
    }

    public YamlConfiguration yaml() {
        return yaml;
    }
}
