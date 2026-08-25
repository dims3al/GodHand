package xyz.dimseal.godHand.config;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandSendEvent;

/** Hides GodHand commands from players who are not currently authorized. */
public final class CommandVisibilityListener implements Listener {

    private final GodHandConfig config;

    public CommandVisibilityListener(GodHandConfig config) {
        this.config = config;
    }

    @EventHandler
    public void onCommands(PlayerCommandSendEvent event) {
        if (config.isAuthorized(event.getPlayer())) return;
        event.getCommands().removeIf(command -> {
            String lower = command.toLowerCase(java.util.Locale.ROOT);
            return lower.equals("godhand") || lower.equals("gh")
                    || lower.endsWith(":godhand") || lower.endsWith(":gh");
        });
    }
}
