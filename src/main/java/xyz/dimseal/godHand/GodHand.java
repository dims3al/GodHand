package xyz.dimseal.godHand;

import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;
import xyz.dimseal.godHand.command.GodHandCommand;
import xyz.dimseal.godHand.config.CommandVisibilityListener;
import xyz.dimseal.godHand.config.GodHandConfig;
import xyz.dimseal.godHand.engine.GodHandEngine;
import xyz.dimseal.godHand.hand.GuardCombatListener;
import xyz.dimseal.godHand.hand.HandDeathListener;
import xyz.dimseal.godHand.hand.HandManager;
import xyz.dimseal.godHand.hand.MainHandSettings;
import xyz.dimseal.godHand.hand.TransportSafetyListener;

public final class GodHand extends JavaPlugin {

    private HandManager handManager;
    private GodHandEngine engine;
    private MainHandSettings mainHandSettings;
    private GodHandConfig godHandConfig;

    @Override
    public void onEnable() {
        cleanupStaleGodHandEntities();

        handManager = new HandManager();
        mainHandSettings = new MainHandSettings();
        godHandConfig = new GodHandConfig(this, mainHandSettings);
        godHandConfig.load();

        engine = new GodHandEngine(this, handManager);
        engine.start();

        getServer().getPluginManager().registerEvents(new TransportSafetyListener(handManager), this);
        getServer().getPluginManager().registerEvents(new GuardCombatListener(handManager), this);
        getServer().getPluginManager().registerEvents(new HandDeathListener(), this);
        getServer().getPluginManager().registerEvents(new CommandVisibilityListener(godHandConfig), this);

        GodHandCommand commandHandler = new GodHandCommand(this, handManager, mainHandSettings, godHandConfig);
        PluginCommand command = getCommand("godhand");
        if (command == null) {
            throw new IllegalStateException("Command 'godhand' is missing from plugin.yml");
        }
        command.setExecutor(commandHandler);
        command.setTabCompleter(commandHandler);

        getLogger().info("GodHand v" + getPluginMeta().getVersion() + " enabled.");
    }

    public void reloadGodHandConfiguration() {
        godHandConfig.reload();
        if (handManager.getHand() != null) {
            mainHandSettings.applyTo(handManager.getHand());
            handManager.refreshVisuals();
        }
    }

    private void cleanupStaleGodHandEntities() {
        int removed = 0;
        for (org.bukkit.World world : getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (!entity.getScoreboardTags().contains("godhand_grip_carrier")
                        && !entity.getScoreboardTags().contains("godhand_item_display")) continue;
                entity.remove();
                removed++;
            }
        }
        if (removed > 0) {
            getLogger().info("Removed " + removed + " stale GodHand runtime entit" + (removed == 1 ? "y." : "ies."));
        }
    }

    @Override
    public void onDisable() {
        if (engine != null) engine.stop();
    }

    public HandManager getHandManager() {
        return handManager;
    }

    public MainHandSettings getMainHandSettings() {
        return mainHandSettings;
    }

    public GodHandConfig getGodHandConfig() {
        return godHandConfig;
    }
}
