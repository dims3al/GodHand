package xyz.dimseal.godHand.command;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import xyz.dimseal.godHand.GodHand;
import xyz.dimseal.godHand.config.GodHandConfig;
import xyz.dimseal.godHand.hand.HandManager;
import xyz.dimseal.godHand.hand.MainHandSettings;
import xyz.dimseal.godHand.hand.ParticleHand;
import xyz.dimseal.godHand.hand.TrueGodAttackPresets;
import xyz.dimseal.godHand.hand.animation.EasingCurve;
import xyz.dimseal.godHand.hand.render.HandDensity;
import xyz.dimseal.godHand.hand.render.HandPalette;
import xyz.dimseal.godHand.hand.render.HandRenderMode;
import xyz.dimseal.godHand.hand.render.WristStyle;
import xyz.dimseal.godHand.hand.skeleton.HandDigit;
import xyz.dimseal.godHand.hand.skeleton.HandPose;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class GodHandCommand implements CommandExecutor, TabCompleter {

    private static final String PREFIX = "§8[§fGodHand§8] §r";
    private final GodHand plugin;
    private final HandManager handManager;
    private final MainHandSettings mainSettings;
    private final GodHandConfig config;

    public GodHandCommand(GodHand plugin, HandManager handManager, MainHandSettings mainSettings, GodHandConfig config) {
        this.plugin = plugin;
        this.handManager = handManager;
        this.mainSettings = mainSettings;
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String root = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);

        // Console can always recover configuration/whitelist state. In-game
        // users must be operators, and when the whitelist is enabled their UUID
        // must also be present in operator_whitelist.yml.
        try {
            if (root.equals("reload")) return handleReload(sender, label, args);
            if (root.equals("whitelist")) return handleWhitelist(sender, label, args);
        } catch (IllegalArgumentException ex) {
            sender.sendMessage(PREFIX + "§c" + ex.getMessage());
            return true;
        }

        Player operator = requireAuthorizedOperator(sender);
        if (operator == null) return true;

        if (args.length == 0 || root.equals("help")) {
            if (args.length > 2) {
                sender.sendMessage(PREFIX + "§cUsage: /" + label + " help [page]");
                return true;
            }
            int page = 1;
            if (args.length == 2) {
                try {
                    page = Integer.parseInt(args[1]);
                } catch (NumberFormatException ex) {
                    sender.sendMessage(PREFIX + "§cHelp page must be a number from 1 to " + HELP_PAGE_COUNT + ".");
                    return true;
                }
            }
            if (page < 1 || page > HELP_PAGE_COUNT) {
                sender.sendMessage(PREFIX + "§cHelp page must be from 1 to " + HELP_PAGE_COUNT + ".");
                return true;
            }
            sendHelp(sender, label, page);
            return true;
        }

        try {
            return switch (root) {
                case "summon", "spawn" -> mainSummon(operator, label, args);
                case "despawn" -> mainDespawn(operator, label, args);
                case "settings", "setting" -> mainSettings(operator, label, args);
                case "status", "info" -> mainStatus(operator);
                case "attack" -> handleAttack(operator, label, args);
                case "action" -> handleAction(operator, label, args);
                case "friendly" -> handleFriendly(operator, label, args);
                case "stop" -> mainStop(operator, label, args);
                case "dev", "developer" -> handleDeveloper(operator, label, args);
                default -> {
                    sender.sendMessage(PREFIX + "§cUnknown command: §f" + args[0]);
                    sendHelp(sender, label, 1);
                    yield true;
                }
            };
        } catch (IllegalArgumentException ex) {
            sender.sendMessage(PREFIX + "§c" + ex.getMessage());
            return true;
        }
    }

    private boolean handleAttack(Player operator, String label, String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: /" + label + " attack <breach|clap|cyclone|forceslap|slap|givebird|groundslam|judgment|pound|toss|punch|rage|smash|spank> ...");
        }
        String[] routed = stripFirst(args);
        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "breach" -> presetBreach(operator, label + " attack", routed);
            case "clap" -> presetClap(operator, label + " attack", routed);
            case "cyclone" -> presetCyclone(operator, label + " attack", routed);
            case "forceslap", "force-slap" -> presetForceSlap(operator, label + " attack", routed);
            case "slap" -> presetSlap(operator, label + " attack", routed);
            case "givebird", "give-bird" -> presetGiveBird(operator, label + " attack", routed);
            case "groundslam", "slam" -> presetGroundSlam(operator, label + " attack", routed);
            case "judgment", "judgement" -> presetJudgment(operator, label + " attack", routed);
            case "pound" -> presetPound(operator, label + " attack", routed);
            case "toss" -> presetToss(operator, label + " attack", routed);
            case "punch" -> presetPunch(operator, label + " attack", routed);
            case "rage" -> presetRage(operator, label + " attack", routed);
            case "smash" -> presetSmash(operator, label + " attack", routed);
            case "spank" -> presetSpank(operator, label + " attack", routed);
            default -> throw new IllegalArgumentException("Unknown attack '" + args[1] + "'.");
        };
    }

    private boolean handleAction(Player operator, String label, String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: /" + label + " action <bird|chase|grab|idle|juggle|lookat|moveto|pose|release|stalk|throw|thumbsdown|thumbsup|transport|wave> ...");
        }
        String[] routed = stripFirst(args);
        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "bird", "middlefinger", "middle-finger" -> presetBird(operator, label + " action", routed);
            case "chase" -> presetChase(operator, label + " action", routed);
            case "grab" -> presetGrab(operator, label + " action", routed);
            case "idle" -> mainStop(operator, label + " action", routed);
            case "juggle" -> presetJuggle(operator, label + " action", routed);
            case "lookat", "look" -> mainLookAt(operator, label + " action", routed);
            case "moveto", "move" -> presetMoveTo(operator, label + " action", routed);
            case "pose" -> mainActionPose(operator, label + " action", routed);
            case "release" -> mainRelease(operator, label + " action", routed);
            case "stalk" -> presetStalk(operator, label + " action", routed);
            case "throw" -> mainThrow(operator, label + " action", routed);
            case "thumbsup", "thumbs-up", "thumbup" -> presetThumb(operator, label + " action", routed, true);
            case "thumbsdown", "thumbs-down", "thumbdown" -> presetThumb(operator, label + " action", routed, false);
            case "transport" -> presetTransport(operator, label + " action", routed);
            case "wave" -> presetWave(operator, label + " action", routed);
            default -> throw new IllegalArgumentException("Unknown action '" + args[1] + "'.");
        };
    }

    private boolean handleFriendly(Player operator, String label, String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: /" + label + " friendly <bless|guard|sanctuary> [player]");
        }
        String[] routed = stripFirst(args);
        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "bless" -> presetBless(operator, label + " friendly", routed);
            case "guard" -> presetGuard(operator, label + " friendly", routed);
            case "sanctuary" -> presetSanctuary(operator, label + " friendly", routed);
            default -> throw new IllegalArgumentException("Unknown friendly action '" + args[1] + "'.");
        };
    }

    private boolean handleReload(CommandSender sender, String label, String[] args) {
        if (!isAdminSender(sender)) return hideUnauthorized(sender);
        if (args.length != 1) {
            sender.sendMessage(PREFIX + "§cUsage: /" + label + " reload");
            return true;
        }
        plugin.reloadGodHandConfiguration();
        refreshCommandTrees();
        sender.sendMessage(PREFIX + "§aConfiguration and operator whitelist reloaded from disk.");
        return true;
    }

    private boolean handleWhitelist(CommandSender sender, String label, String[] args) {
        if (sender instanceof Player player && !config.isAuthorized(player)) return hideUnauthorized(sender);
        if (!(sender instanceof Player) && !(sender instanceof org.bukkit.command.ConsoleCommandSender) && !sender.isOp()) return hideUnauthorized(sender);

        if (args.length < 2) {
            sender.sendMessage(PREFIX + "§7Operator whitelist: " + (config.isOperatorWhitelistEnabled() ? "§aenabled" : "§cdisabled"));
            sender.sendMessage(PREFIX + "§7Use §f/" + label + " whitelist <on|off|add|remove|list>§7.");
            return true;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "on", "enable" -> {
                config.setOperatorWhitelistEnabled(true);
                refreshCommandTrees();
                sender.sendMessage(PREFIX + "§aOperator whitelist enabled. Only listed operators can use or see GodHand commands.");
            }
            case "off", "disable" -> {
                config.setOperatorWhitelistEnabled(false);
                refreshCommandTrees();
                sender.sendMessage(PREFIX + "§eOperator whitelist disabled. All operators can use GodHand commands.");
            }
            case "add" -> {
                if (args.length != 3) throw new IllegalArgumentException("Usage: /" + label + " whitelist add <player>");
                GodHandConfig.OperatorEntry entry = config.addOperator(args[2]);
                refreshCommandTrees();
                sender.sendMessage(PREFIX + "§aAdded §f" + entry.name() + " §7(" + entry.uuid() + ") §ato the operator whitelist.");
            }
            case "remove", "delete" -> {
                if (args.length != 3) throw new IllegalArgumentException("Usage: /" + label + " whitelist remove <player|uuid>");
                boolean removed = config.removeOperator(args[2]);
                if (removed) refreshCommandTrees();
                sender.sendMessage(PREFIX + (removed ? "§aRemoved whitelist entry." : "§7No matching whitelist entry was found."));
            }
            case "list" -> {
                sender.sendMessage("§8§m--------------------------------");
                sender.sendMessage("§fGodHand operator whitelist §7(" + (config.isOperatorWhitelistEnabled() ? "enabled" : "disabled") + ")");
                List<GodHandConfig.OperatorEntry> entries = config.getOperatorWhitelist();
                if (entries.isEmpty()) sender.sendMessage("§7No players configured.");
                else for (GodHandConfig.OperatorEntry entry : entries) sender.sendMessage("§7- §f" + entry.name() + " §8" + entry.uuid());
                sender.sendMessage("§8§m--------------------------------");
            }
            default -> throw new IllegalArgumentException("Usage: /" + label + " whitelist <on|off|add|remove|list> ...");
        }
        return true;
    }

    private boolean isAdminSender(CommandSender sender) {
        if (sender instanceof Player player) return config.isAuthorized(player);
        return sender instanceof org.bukkit.command.ConsoleCommandSender || sender.isOp();
    }

    private void refreshCommandTrees() {
        for (Player player : plugin.getServer().getOnlinePlayers()) player.updateCommands();
    }

    private boolean hideUnauthorized(CommandSender sender) {
        sender.sendMessage("§cUnknown command. Type \"/help\" for help.");
        return true;
    }


    // ---------------------------------------------------------------------
    // main Hand interface / defaults
    // ---------------------------------------------------------------------

    private boolean mainSummon(Player operator, String label, String[] args) {
        if (args.length > 2) {
            throw new IllegalArgumentException("Usage: /" + label + " summon [player]");
        }
        Player focus = args.length == 2 ? requireOnlinePlayer(args[1]) : operator;
        ParticleHand hand = TrueGodAttackPresets.summon(handManager, focus, mainSettings);
        operator.sendMessage(PREFIX + "§fHand summoned §7(scale §f" + format(hand.getScale())
                + "§7, color §f" + HandPalette.describe(hand.getBaseColor())
                + "§7, density §f" + hand.getDensity().commandName() + "§7).");
        return true;
    }

    private boolean mainDespawn(Player operator, String label, String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: /" + label + " despawn");
        }
        if (handManager.despawnHand()) {
            operator.sendMessage(PREFIX + "§fHand despawn begun.");
        } else {
            operator.sendMessage(PREFIX + "§7No main Hand is present.");
        }
        return true;
    }

    private boolean mainStatus(Player operator) {
        operator.sendMessage("§8§m--------------------------------");
        operator.sendMessage("§fGodHand §7— Main Instance");
        operator.sendMessage("§7Operator whitelist: " + (config.isOperatorWhitelistEnabled() ? "§aenabled" : "§cdisabled"));
        operator.sendMessage("§7Settings preset: §f" + mainSettings.getPreset().configName());
        ParticleHand hand = handManager.getHand();
        if (hand == null) {
            operator.sendMessage("§7Hand: §8not summoned");
        } else {
            operator.sendMessage("§7Hand: §aactive §8| §7world: §f" + hand.getWorld().getName());
            operator.sendMessage("§7Action: §f" + hand.getActionDescription());
            operator.sendMessage("§7Pose: §f" + hand.getPoseName());
            operator.sendMessage("§7Renderer: §f" + hand.getRenderMode().commandName()
                    + (hand.getRenderMode() == HandRenderMode.ITEM_DISPLAYS
                    ? " §8| §7solid displays: §f" + handManager.getPrimaryItemDisplayCount()
                    : ""));
        }
        operator.sendMessage("§8§m--------------------------------");
        return true;
    }


    private boolean mainSettings(Player operator, String label, String[] args) {
        if (args.length == 1 || args[1].equalsIgnoreCase("info")) {
            sendMainSettingsInfo(operator);
            return true;
        }

        String sub = args[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "preset" -> {
                requireArgs(args, 3, "/" + label + " settings preset <low|medium|high>");
                MainHandSettings.Preset preset = MainHandSettings.Preset.parse(args[2]);
                if (preset == MainHandSettings.Preset.CUSTOM) {
                    throw new IllegalArgumentException("Use low, medium, or high. Custom appears automatically after changing individual settings.");
                }
                mainSettings.applyPreset(preset);
                saveAndApplySettings();
                operator.sendMessage(PREFIX + "§aApplied §f" + preset.configName() + " §apreset.");
            }
            case "reset" -> {
                mainSettings.reset();
                saveAndApplySettings();
                operator.sendMessage(PREFIX + "§aSettings reset to the high preset.");
            }
            case "scale", "size" -> {
                requireArgs(args, 3, "/" + label + " settings scale <palm-width-blocks>");
                mainSettings.setScale(parseDouble(args[2], "scale"));
                saveAndApplySettings();
                operator.sendMessage(PREFIX + "§7Scale: §f" + format(mainSettings.getScale()) + " §8(preset: custom)");
            }
            case "density" -> {
                requireArgs(args, 3, "/" + label + " settings density <low|normal|high|ultra>");
                mainSettings.setDensity(HandDensity.parse(args[2]));
                saveAndApplySettings();
                operator.sendMessage(PREFIX + "§7Density: §f" + mainSettings.getDensity().commandName() + " §8(preset: custom)");
            }
            case "shading", "shade" -> {
                boolean enabled = parseToggle(args, 2, mainSettings.isShading(), "/" + label + " settings shading [on|off]");
                mainSettings.setShading(enabled);
                saveAndApplySettings();
                operator.sendMessage(PREFIX + "§7Shading: §f" + onOff(enabled) + " §8(preset: custom)");
            }
            case "forceparticles", "force" -> {
                boolean enabled = parseToggle(args, 2, mainSettings.isForceParticles(), "/" + label + " settings forceparticles [on|off]");
                mainSettings.setForceParticles(enabled);
                saveAndApplySettings();
                operator.sendMessage(PREFIX + "§7Forced particles: §f" + onOff(enabled) + " §8(preset: custom)");
            }
            case "renderer", "render" -> {
                requireArgs(args, 3, "/" + label + " settings renderer <particles|itemdisplays>");
                mainSettings.setRenderMode(HandRenderMode.parse(args[2]));
                saveAndApplySettings();
                operator.sendMessage(PREFIX + "§7Renderer: §f" + mainSettings.getRenderMode().commandName() + " §8(preset: custom)");
            }
            case "model" -> {
                requireArgs(args, 3, "/" + label + " settings model <anatomical|legacy>");
                mainSettings.setWristStyle(WristStyle.parse(args[2]));
                saveAndApplySettings();
                operator.sendMessage(PREFIX + "§7Model: §f" + mainSettings.getWristStyle().commandName() + " §8(preset: custom)");
            }
            case "color", "colour" -> {
                requireArgs(args, 3, "/" + label + " settings color <white|spectral|crimson|violet|void|gold|emerald|cyan|sand|custom> [r g b]");
                Color color;
                if (args[2].equalsIgnoreCase("custom")) {
                    if (args.length != 6) throw new IllegalArgumentException("Usage: /" + label + " settings color custom <red 0-255> <green 0-255> <blue 0-255>");
                    color = HandPalette.parseRgb(parseInt(args[3], "red"), parseInt(args[4], "green"), parseInt(args[5], "blue"));
                } else {
                    if (args.length != 3) throw new IllegalArgumentException("Named colors do not take RGB values. Use: /" + label + " settings color custom <r> <g> <b>");
                    color = HandPalette.parsePreset(args[2]);
                }
                mainSettings.setColor(color);
                saveAndApplySettings();
                String materialNote = HandPalette.presetName(color) == null ? " §8(solid model maps this RGB to the nearest concrete color)" : "";
                operator.sendMessage(PREFIX + "§7Color: §f" + HandPalette.describe(color) + materialNote + " §8| preset: custom");
            }
            default -> throw new IllegalArgumentException(
                    "Usage: /" + label + " settings <preset|color|scale|density|model|renderer|shading|forceparticles|reset|info> ..."
            );
        }
        return true;
    }


    private void sendMainSettingsInfo(CommandSender sender) {
        sender.sendMessage("§8§m--------------------------------");
        sender.sendMessage("§fGodHand settings §8| §7preset: §f" + mainSettings.getPreset().configName());
        sender.sendMessage("§7Scale: §f" + format(mainSettings.getScale())
                + " §8| §7density: §f" + mainSettings.getDensity().commandName()
                + " §8| §7model: §f" + mainSettings.getWristStyle().commandName());
        sender.sendMessage("§7Color: §f" + HandPalette.describe(mainSettings.getColor())
                + " §8| §7renderer: §f" + mainSettings.getRenderMode().commandName());
        sender.sendMessage("§7Shading: §f" + onOff(mainSettings.isShading())
                + " §8| §7forced particles: §f" + onOff(mainSettings.isForceParticles())
                + " §8| §7motion smoothing: §freduced §8(built in)");
        sender.sendMessage("§8§m--------------------------------");
    }

    private void saveAndApplySettings() {
        config.saveCurrentSettings();
        applyMainSettingsToCurrentHand();
    }


    private void applyMainSettingsToCurrentHand() {
        ParticleHand hand = handManager.getHand();
        if (hand != null) {
            mainSettings.applyTo(hand);
            // Settings are also a convenient visual repair boundary. If a client
            // or unloaded chunk lost the solid model, rebuild it immediately.
            handManager.refreshVisuals();
        }
    }

    private boolean mainActionPose(Player operator, String label, String[] args) {
        if (args.length < 2 || args.length > 4) {
            throw new IllegalArgumentException("Usage: /" + label + " pose <open|relaxed|fist|point|bird|thumbs_up|thumbs_down|claw> [seconds] [easing]");
        }
        ParticleHand hand = requireHand();
        HandPose pose = HandPose.parse(args[1]);
        if (args.length >= 3) {
            int ticks = parseDurationTicks(args[2]);
            EasingCurve easing = args.length >= 4 ? EasingCurve.parse(args[3]) : EasingCurve.SMOOTH;
            hand.animatePose(pose, ticks, easing);
            operator.sendMessage(PREFIX + "§fPose §7→ §f" + pose.commandName() + " §7over §f" + format(ticks / 20.0) + "s§7 (" + easing.commandName() + ").");
        } else {
            hand.applyPose(pose);
            operator.sendMessage(PREFIX + "§fPose §7→ §f" + pose.commandName() + "§7.");
        }
        return true;
    }

    private boolean mainRelease(Player operator, String label, String[] args) {
        if (args.length > 2) {
            throw new IllegalArgumentException("Usage: /" + label + " release [seconds]");
        }
        ParticleHand hand = requireHand();
        int ticks = args.length == 2 ? parseDurationTicks(args[1]) : 12;
        if (hand.releaseGrab(ticks)) {
            operator.sendMessage(PREFIX + "§fReleased.");
        } else {
            operator.sendMessage(PREFIX + "§7The Hand is not holding a target.");
        }
        return true;
    }

    private boolean mainThrow(Player operator, String label, String[] args) {
        if (args.length > 4) {
            throw new IllegalArgumentException("Usage: /" + label + " throw [forward-speed] [upward-speed] [open-seconds]");
        }
        ParticleHand hand = requireHand();
        double forward = args.length >= 2 ? parseDouble(args[1], "throw forward speed") : 1.35;
        double upward = args.length >= 3 ? parseDouble(args[2], "throw upward speed") : 0.45;
        int openTicks = args.length >= 4 ? parseDurationTicks(args[3]) : 8;
        if (hand.throwHeldPlayer(forward, upward, openTicks)) {
            operator.sendMessage(PREFIX + "§fThrown.");
        } else {
            operator.sendMessage(PREFIX + "§cThe Hand must be holding a player first.");
        }
        return true;
    }

    private boolean handleDeveloper(Player operator, String label, String[] args) {
        if (args.length < 2 || args[1].equalsIgnoreCase("help")) {
            sendDeveloperHelp(operator, label);
            return true;
        }
        String branch = args[1].toLowerCase(Locale.ROOT);
        if (branch.equals("hand")) return handleHand(operator, label + " dev", stripFirst(args));
        if (branch.equals("debug")) return handleDeveloperDebug(operator, label, args);
        throw new IllegalArgumentException("Usage: /" + label + " dev <hand|debug> ...");
    }


    private boolean handleDeveloperDebug(Player operator, String label, String[] args) {
        if (args.length < 3) {
            throw new IllegalArgumentException("Usage: /" + label + " dev debug <axes|skeleton|grip|combat|displays> ...");
        }
        String type = args[2].toLowerCase(Locale.ROOT);
        String toggle = args.length >= 4 ? args[3] : null;
        return switch (type) {
            case "axes" -> handleHand(operator, label + " dev", compact("hand", "axes", toggle));
            case "skeleton", "bones" -> handleHand(operator, label + " dev", compact("hand", "skeleton", toggle));
            case "grip", "gripdebug" -> handleHand(operator, label + " dev", compact("hand", "gripdebug", toggle));
            case "combat", "hitbox" -> handleHand(operator, label + " dev", compact("hand", "combat", "debug", toggle));
            case "displays", "itemdisplays", "display" -> handleDisplayDebug(operator, label, args);
            default -> throw new IllegalArgumentException("Usage: /" + label + " dev debug <axes|skeleton|grip|combat|displays> ...");
        };
    }

    private boolean handleDisplayDebug(Player operator, String label, String[] args) {
        if (args.length != 4) {
            throw new IllegalArgumentException("Usage: /" + label + " dev debug displays <status|purge>");
        }
        return switch (args[3].toLowerCase(Locale.ROOT)) {
            case "status", "info" -> {
                int worldTagged = handManager.countTaggedItemDisplaysServerWide();
                operator.sendMessage(PREFIX + "§7ItemDisplay safety status:");
                operator.sendMessage(PREFIX + "§7Primary tracked: §f" + handManager.getPrimaryItemDisplayCount()
                        + " §8| §7secondary tracked: §f" + handManager.getSecondaryItemDisplayCount());
                operator.sendMessage(PREFIX + "§7Known registry: §f" + handManager.getKnownItemDisplayCount()
                        + " §8| §7server-wide tagged: §f" + worldTagged);
                operator.sendMessage(PREFIX + "§7Automatic runaway cutoff: §f" + handManager.getDisplayRunawayLimit() + " tagged/known displays§7.");
                yield true;
            }
            case "purge", "clear", "cleanup" -> {
                int removed = handManager.emergencyPurgeItemDisplays(true);
                operator.sendMessage(PREFIX + "§cEmergency ItemDisplay purge complete. §f" + removed
                        + " §ctagged display" + (removed == 1 ? "" : "s") + " removed; live Hand visuals were rebuilt from a clean renderer state.");
                yield true;
            }
            default -> throw new IllegalArgumentException("Usage: /" + label + " dev debug displays <status|purge>");
        };
    }

    private static String[] stripFirst(String[] args) {
        return Arrays.copyOfRange(args, 1, args.length);
    }


    private static String[] compact(String... values) {
        List<String> out = new ArrayList<>();
        for (String value : values) {
            if (value != null) out.add(value);
        }
        return out.toArray(String[]::new);
    }


    private boolean handleHand(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            sendHandHelp(sender, label);
            return true;
        }

        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "create" -> handCreate(sender, label, args);
            case "remove" -> handRemove(sender);
            case "here" -> handHere(sender);
            case "tp" -> handTeleport(sender, label, args);
            case "move" -> handMove(sender, label, args);
            case "goto", "travel" -> handGoto(sender, label, args);
            case "chase" -> handChase(sender, label, args);
            case "follow" -> handFollow(sender, label, args);
            case "orbit" -> handOrbit(sender, label, args);
            case "target" -> handTarget(sender, label, args);
            case "targetdown" -> handTargetDown(sender, label, args);
            case "motion" -> handMotion(sender, label, args);
            case "lookat", "look" -> handLookAt(sender, label, args);
            case "downlook", "attacklook" -> handDownLook(sender, label, args);
            case "lookstop" -> handLookStop(sender);
            case "rotate" -> handRotate(sender, label, args);
            case "yaw" -> handSingleRotation(sender, label, args, Axis.YAW);
            case "pitch" -> handSingleRotation(sender, label, args, Axis.PITCH);
            case "roll" -> handSingleRotation(sender, label, args, Axis.ROLL);
            case "scale", "size" -> handScale(sender, label, args);
            case "spin" -> handSpin(sender, label, args);
            case "stop" -> handStop(sender);
            case "axes" -> handAxes(sender, label, args);
            case "skeleton" -> handSkeleton(sender, label, args);
            case "gripdebug", "grip" -> handGripDebug(sender, label, args);
            case "forceparticles", "forceparticle", "force" -> handForceParticles(sender, label, args);
            case "combat" -> handCombat(sender, label, args);
            case "density" -> handDensity(sender, label, args);
            case "color", "colour" -> handColor(sender, label, args);
            case "shading", "shade" -> handShading(sender, label, args);
            case "renderer", "render" -> handRenderer(sender, label, args);
            case "model" -> handModel(sender, label, args);
            case "visual" -> handVisual(sender, label, args);
            case "finger", "curl" -> handFinger(sender, label, args);
            case "joint" -> handJoint(sender, label, args);
            case "pose" -> handPose(sender, label, args);
            case "animation", "anim" -> handAnimation(sender, label, args);
            case "animstop", "cancel" -> handAnimationStop(sender);
            case "info" -> handInfo(sender);
            default -> {
                sender.sendMessage(PREFIX + "§cUnknown hand subcommand: §f" + args[1]);
                sendHandHelp(sender, label);
                yield true;
            }
        };
    }

    // ---------------------------------------------------------------------
    // Main preconfigured attacks / autonomous presence
    // ---------------------------------------------------------------------

    private boolean presetGroundSlam(Player operator, String label, String[] args) {
        if (args.length > 2) {
            throw new IllegalArgumentException("Usage: /" + label + " groundslam [player]");
        }
        Player target = resolvePresetTarget(operator, args.length >= 2 ? args[1] : null);
        ParticleHand hand = TrueGodAttackPresets.prepare(handManager, target, mainSettings);

        double height = 10.5;
        int riseTicks = TrueGodAttackPresets.groundSlamApproachTicks(hand, target, height);
        int dropTicks = TrueGodAttackPresets.groundSlamDropTicks(height);
        hand.setSlamDamage(12.0);
        hand.setSlamHorizontalKnockback(1.75);
        hand.setSlamVerticalKnockback(0.85);
        EasingCurve approachEasing = TrueGodAttackPresets.groundSlamApproachEasing(hand, target, height);
        hand.slam(target, height, riseTicks, dropTicks, approachEasing);

        operator.sendMessage(PREFIX + "§4Hand of God groundslam §7→ §f" + target.getName()
                + " §8(" + formatTicks(riseTicks) + " " + approachEasing.commandName() + " approach, "
                + formatTicks(dropTicks) + " descent)");
        return true;
    }

    private boolean presetGrab(Player operator, String label, String[] args) {
        if (args.length > 2) {
            throw new IllegalArgumentException("Usage: /" + label + " grab [player]");
        }
        Player target = resolvePresetTarget(operator, args.length >= 2 ? args[1] : null);
        ParticleHand hand = TrueGodAttackPresets.prepare(handManager, target, mainSettings);

        double height = 7.5;
        int approachTicks = TrueGodAttackPresets.grabApproachTicks(hand, target, height);
        int closeTicks = TrueGodAttackPresets.grabCloseTicks(hand, target);
        hand.grab(target, height, approachTicks, closeTicks);

        operator.sendMessage(PREFIX + "§4Hand of God grab §7→ §f" + target.getName()
                + " §8(physical pursuit enabled; no distance snap)");
        return true;
    }

    private boolean presetJudgment(Player operator, String label, String[] args) {
        if (args.length > 2) throw new IllegalArgumentException("Usage: /" + label + " judgment [player]");
        Player target = resolvePresetTarget(operator, args.length >= 2 ? args[1] : null);
        ParticleHand hand = TrueGodAttackPresets.prepare(handManager, target, mainSettings, false);
        double orbitRadius = 16.0;
        int approachTicks = TrueGodAttackPresets.surfaceJudgmentApproachTicks(hand, target, orbitRadius);
        hand.judgment(target, orbitRadius, approachTicks, 5);
        operator.sendMessage(PREFIX + "§4Surface Judgment §7→ §f" + target.getName() + "§7; beam persists until death or surface loss.");
        return true;
    }

    private boolean presetForceSlap(Player operator, String label, String[] args) {
        if (args.length > 2) throw new IllegalArgumentException("Usage: /" + label + " forceslap [player]");
        Player target = resolvePresetTarget(operator, args.length >= 2 ? args[1] : null);
        ParticleHand hand = TrueGodAttackPresets.prepare(handManager, target, mainSettings, false);
        double stageDistance = 11.0;
        int approachTicks = TrueGodAttackPresets.forceSlapApproachTicks(hand, target, stageDistance);
        int strikeTicks = TrueGodAttackPresets.forceSlapStrikeTicks(stageDistance);
        hand.setSlamDamage(10.5);
        hand.setSlamHorizontalKnockback(1.80);
        hand.setSlamVerticalKnockback(0.55);
        hand.forceSlap(target, stageDistance, approachTicks, strikeTicks);
        operator.sendMessage(PREFIX + "§4ForceSlap §7→ §f" + target.getName() + "§7.");
        return true;
    }

    private boolean presetPunch(Player operator, String label, String[] args) {
        if (args.length > 2) {
            throw new IllegalArgumentException("Usage: /" + label + " punch [player]");
        }
        Player target = resolvePresetTarget(operator, args.length >= 2 ? args[1] : null);
        ParticleHand hand = TrueGodAttackPresets.prepare(handManager, target, mainSettings);

        double stageDistance = 9.0;
        int approachTicks = TrueGodAttackPresets.punchApproachTicks(hand, target, stageDistance);
        int strikeTicks = TrueGodAttackPresets.punchStrikeTicks(stageDistance);
        hand.punch(target, stageDistance, approachTicks, strikeTicks,
                96.0, 12.0, 4.60, 1.15);

        operator.sendMessage(PREFIX + "§4Hand of God punch §7→ §f" + target.getName()
                + " §8(heavy armor-piercing impact, extreme knockback)");
        return true;
    }

    private boolean presetSlap(Player operator, String label, String[] args) {
        if (args.length > 2) {
            throw new IllegalArgumentException("Usage: /" + label + " slap [player]");
        }
        Player target = resolvePresetTarget(operator, args.length >= 2 ? args[1] : null);
        ParticleHand hand = TrueGodAttackPresets.prepare(handManager, target, mainSettings, false);

        double stageDistance = 7.0;
        int approachTicks = TrueGodAttackPresets.slapApproachTicks(hand, target, stageDistance);
        int strikeTicks = TrueGodAttackPresets.slapStrikeTicks(stageDistance);
        hand.slap(target, stageDistance, approachTicks, strikeTicks, 3.0, 5.5, 0.65);

        // Intentionally no command-start chat line. A successful
        // physical contact is the only moment SLAP is allowed to message.
        return true;
    }

    private boolean presetRage(Player operator, String label, String[] args) {
        if (args.length > 2) throw new IllegalArgumentException("Usage: /" + label + " rage [player]");
        Player target = resolvePresetTarget(operator, args.length >= 2 ? args[1] : null);
        ParticleHand hand = TrueGodAttackPresets.prepare(handManager, target, mainSettings, false);
        hand.rage(target);
        operator.sendMessage(PREFIX + "§4Rage sequence §7→ §f" + target.getName()
                + " §8(3+5+7 dash damage max, then aerial swing throw)");
        return true;
    }

    private boolean presetClap(Player operator, String label, String[] args) {
        if (args.length > 2) throw new IllegalArgumentException("Usage: /" + label + " clap [player]");
        Player target = resolvePresetTarget(operator, args.length >= 2 ? args[1] : null);
        ParticleHand hand = TrueGodAttackPresets.prepare(handManager, target, mainSettings, false);
        hand.clap(target);
        operator.sendMessage(PREFIX + "§fThunder clap §7→ §f" + target.getName() + "§7.");
        return true;
    }

    private boolean presetPound(Player operator, String label, String[] args) {
        if (args.length > 2) throw new IllegalArgumentException("Usage: /" + label + " pound [player]");
        Player target = resolvePresetTarget(operator, args.length >= 2 ? args[1] : null);
        ParticleHand hand = TrueGodAttackPresets.prepare(handManager, target, mainSettings, false);
        hand.pound(target);
        operator.sendMessage(PREFIX + "§4Pound §7→ §f" + target.getName() + " §8(alternating 3-damage fist slams to ≤3 hearts)");
        return true;
    }

    private boolean presetWave(Player operator, String label, String[] args) {
        if (args.length > 2) throw new IllegalArgumentException("Usage: /" + label + " wave [player]");
        Player target = resolvePresetTarget(operator, args.length >= 2 ? args[1] : null);
        ParticleHand hand = TrueGodAttackPresets.prepare(handManager, target, mainSettings, false);
        hand.wave(target);
        operator.sendMessage(PREFIX + "§fWave §7→ §f" + target.getName() + "§7.");
        return true;
    }

    private boolean presetThumb(Player operator, String label, String[] args, boolean up) {
        if (args.length > 2) throw new IllegalArgumentException("Usage: /" + label + (up ? " thumbsup" : " thumbsdown") + " [player]");
        Player target = resolvePresetTarget(operator, args.length >= 2 ? args[1] : null);
        ParticleHand hand = TrueGodAttackPresets.prepare(handManager, target, mainSettings, false);
        if (up) hand.thumbsUp(target); else hand.thumbsDown(target);
        operator.sendMessage(PREFIX + (up ? "§aThumbs up" : "§cThumbs down") + " §7→ §f" + target.getName() + "§7.");
        return true;
    }

    private boolean presetBird(Player operator, String label, String[] args) {
        if (args.length > 2) throw new IllegalArgumentException("Usage: /" + label + " bird [player]");
        Player target = resolvePresetTarget(operator, args.length >= 2 ? args[1] : null);
        ParticleHand hand = TrueGodAttackPresets.prepare(handManager, target, mainSettings, false);
        hand.bird(target);
        operator.sendMessage(PREFIX + "§fBird gesture §7→ §f" + target.getName() + "§7.");
        return true;
    }

    private boolean presetGiveBird(Player operator, String label, String[] args) {
        if (args.length > 2) throw new IllegalArgumentException("Usage: /" + label + " givebird [player]");
        Player target = resolvePresetTarget(operator, args.length >= 2 ? args[1] : null);
        ParticleHand hand = TrueGodAttackPresets.prepare(handManager, target, mainSettings, false);
        hand.giveBird(target);
        operator.sendMessage(PREFIX + "§4GiveBird §7→ §f" + target.getName() + "§7.");
        return true;
    }

    private boolean presetJuggle(Player operator, String label, String[] args) {
        if (args.length > 2) throw new IllegalArgumentException("Usage: /" + label + " juggle [player]");
        Player target = resolvePresetTarget(operator, args.length >= 2 ? args[1] : null);
        ParticleHand hand = TrueGodAttackPresets.prepare(handManager, target, mainSettings, false);
        double height = 8.5;
        int approachTicks = TrueGodAttackPresets.grabApproachTicks(hand, target, height);
        int closeTicks = TrueGodAttackPresets.grabCloseTicks(hand, target);
        hand.juggle(target, height, approachTicks, closeTicks);
        operator.sendMessage(PREFIX + "§fWide two-Hand juggle §7→ §f" + target.getName() + "§7. §8(/gh despawn to end)");
        return true;
    }

    private boolean presetGuard(Player operator, String label, String[] args) {
        if (args.length > 2) throw new IllegalArgumentException("Usage: /" + label + " guard [player|off]");
        if (args.length == 2 && (args[1].equalsIgnoreCase("off") || args[1].equalsIgnoreCase("stop"))) {
            ParticleHand existing = handManager.getHand();
            if (existing != null && existing.isGuarding()) {
                existing.cancelAction();
                existing.startIdle();
                operator.sendMessage(PREFIX + "§aGuard dismissed to idle.");
            } else {
                operator.sendMessage(PREFIX + "§7The Hand is not guarding anyone.");
            }
            return true;
        }
        Player guarded = resolvePresetTarget(operator, args.length >= 2 ? args[1] : null);
        ParticleHand hand = TrueGodAttackPresets.prepare(handManager, guarded, mainSettings, false);
        hand.guard(guarded);
        operator.sendMessage(PREFIX + "§aEmerald guardian assigned to §f" + guarded.getName() + "§a.");
        return true;
    }

    private boolean presetCyclone(Player operator, String label, String[] args) {
        if (args.length > 2) {
            throw new IllegalArgumentException("Usage: /" + label + " cyclone [player]");
        }
        Player target = resolvePresetTarget(operator, args.length >= 2 ? args[1] : null);
        ParticleHand hand = TrueGodAttackPresets.prepare(handManager, target, mainSettings);

        double stageDistance = 10.0;
        int approachTicks = TrueGodAttackPresets.cycloneApproachTicks(hand, target, stageDistance);
        int strikeTicks = TrueGodAttackPresets.cycloneStrikeTicks(stageDistance);
        hand.cyclone(target, stageDistance, approachTicks, strikeTicks);
        operator.sendMessage(PREFIX + "§4Cyclone §7→ §f" + target.getName() + "§7.");
        return true;
    }

    private boolean presetBreach(Player operator, String label, String[] args) {
        if (args.length > 2) {
            throw new IllegalArgumentException("Usage: /" + label + " breach [player]");
        }
        Player target = resolvePresetTarget(operator, args.length >= 2 ? args[1] : null);
        ParticleHand hand = TrueGodAttackPresets.prepare(handManager, target, mainSettings, false);
        hand.breach(target, 20 * 7);
        operator.sendMessage(PREFIX + "§4Breach manifested inside §f" + target.getName() + "§4's space.");
        return true;
    }

    private boolean presetToss(Player operator, String label, String[] args) {
        if (args.length > 2) throw new IllegalArgumentException("Usage: /" + label + " toss [player]");
        Player target = resolvePresetTarget(operator, args.length >= 2 ? args[1] : null);
        ParticleHand hand = TrueGodAttackPresets.prepare(handManager, target, mainSettings, false);
        double grabHeight = 7.5;
        int approachTicks = TrueGodAttackPresets.grabApproachTicks(hand, target, grabHeight);
        int closeTicks = TrueGodAttackPresets.grabCloseTicks(hand, target);
        hand.toss(target, grabHeight, approachTicks, closeTicks);
        operator.sendMessage(PREFIX + "§4Toss §7→ §f" + target.getName() + "§7.");
        return true;
    }

    private boolean presetBless(Player operator, String label, String[] args) {
        if (args.length > 2) {
            throw new IllegalArgumentException("Usage: /" + label + " bless [player]");
        }
        Player target = resolvePresetTarget(operator, args.length >= 2 ? args[1] : null);
        ParticleHand hand = TrueGodAttackPresets.prepare(handManager, target, mainSettings, false);
        double height = 7.0;
        int approachTicks = TrueGodAttackPresets.grabApproachTicks(hand, target, height);
        int closeTicks = TrueGodAttackPresets.grabCloseTicks(hand, target);
        hand.bless(target, height, approachTicks, closeTicks);
        operator.sendMessage(PREFIX + "§fBlessing §7→ §f" + target.getName()
                + " §8(5-second hold, five-minute boons, visible departure)");
        return true;
    }

    private boolean presetSanctuary(Player operator, String label, String[] args) {
        if (args.length > 2) {
            throw new IllegalArgumentException("Usage: /" + label + " sanctuary [player]");
        }
        Player target = resolvePresetTarget(operator, args.length >= 2 ? args[1] : null);
        ParticleHand hand = TrueGodAttackPresets.prepare(handManager, target, mainSettings, false);
        hand.sanctuary(target, 20 * 6);
        operator.sendMessage(PREFIX + "§eSanctuary §7→ §f" + target.getName() + "§7.");
        return true;
    }

    private boolean presetSpank(Player operator, String label, String[] args) {
        if (args.length > 2) {
            throw new IllegalArgumentException("Usage: /" + label + " spank [player]");
        }
        Player target = resolvePresetTarget(operator, args.length >= 2 ? args[1] : null);
        ParticleHand hand = TrueGodAttackPresets.prepare(handManager, target, mainSettings, false);
        double height = 7.5;
        int approachTicks = TrueGodAttackPresets.grabApproachTicks(hand, target, height);
        int closeTicks = TrueGodAttackPresets.grabCloseTicks(hand, target);
        hand.spank(target, height, approachTicks, closeTicks);
        operator.sendMessage(PREFIX + "§4Spank §7→ §f" + target.getName() + "§7.");
        return true;
    }

    private boolean presetChase(Player operator, String label, String[] args) {
        if (args.length > 2) {
            throw new IllegalArgumentException("Usage: /" + label + " chase [player|off]");
        }
        if (args.length == 2 && (args[1].equalsIgnoreCase("off") || args[1].equalsIgnoreCase("stop"))) {
            ParticleHand existing = handManager.getHand();
            if (existing != null && existing.isHoverChasing()) {
                existing.cancelAction();
                operator.sendMessage(PREFIX + "§4Hover chase ended.");
            } else {
                operator.sendMessage(PREFIX + "§7The Hand is not hover-chasing anyone.");
            }
            return true;
        }
        Player target = resolvePresetTarget(operator, args.length >= 2 ? args[1] : null);
        ParticleHand hand = TrueGodAttackPresets.prepare(handManager, target, mainSettings, false);
        hand.hoverChase(target);
        operator.sendMessage(PREFIX + "§4Hover chase §7→ §f" + target.getName());
        return true;
    }

    private boolean presetTransport(Player operator, String label, String[] args) {
        if (args.length != 3 && args.length != 5) {
            throw new IllegalArgumentException("Usage: /" + label + " transport <player> <destination-player|x y z>");
        }

        Player carried = requireOnlinePlayer(args[1]);
        ParticleHand hand = TrueGodAttackPresets.prepare(handManager, carried, mainSettings);
        double height = 7.5;
        int approachTicks = TrueGodAttackPresets.grabApproachTicks(hand, carried, height);
        int closeTicks = TrueGodAttackPresets.grabCloseTicks(hand, carried);

        if (args.length == 3) {
            Player destination = requireOnlinePlayer(args[2]);
            requireSameWorld(hand, destination);
            hand.transport(carried, destination, height, approachTicks, closeTicks);
            operator.sendMessage(PREFIX + "§fTransport §7→ §f" + carried.getName() + " §7to moving target §f" + destination.getName() + "§7.");
            return true;
        }

        if (!operator.getWorld().equals(carried.getWorld())) {
            throw new IllegalArgumentException("Relative/coordinate transport requires you to be in the carried player's world.");
        }
        Location base = operator.getLocation();
        Location destination = new Location(
                carried.getWorld(),
                parseCoordinate(args[2], base.getX(), "x"),
                parseCoordinate(args[3], base.getY(), "y"),
                parseCoordinate(args[4], base.getZ(), "z")
        );
        hand.transport(carried, destination, height, approachTicks, closeTicks);
        operator.sendMessage(PREFIX + "§fTransport §7→ §f" + carried.getName() + " §7to §f"
                + format(destination.getX()) + " " + format(destination.getY()) + " " + format(destination.getZ()) + "§7.");
        return true;
    }

    private boolean presetMoveTo(Player operator, String label, String[] args) {
        if (args.length != 2 && args.length != 4) {
            throw new IllegalArgumentException("Usage: /" + label + " moveto <destination-player|x y z>");
        }

        ParticleHand hand = handManager.getHand();
        if (hand == null) {
            hand = TrueGodAttackPresets.summon(handManager, operator, mainSettings);
        } else {
            handManager.refreshVisuals();
        }

        // Preserve retained-grab semantics when a passenger is present.
        if (hand.isHoldingPlayer()) {
            if (args.length == 2) {
                Player destination = requireOnlinePlayer(args[1]);
                requireSameWorld(hand, destination);
                hand.moveHeldTo(destination);
                operator.sendMessage(PREFIX + "§fHeld target moving to §f" + destination.getName() + "§7; grip remains closed.");
                return true;
            }

            Location base = operator.getLocation();
            if (!operator.getWorld().equals(hand.getWorld())) {
                throw new IllegalArgumentException("Relative MOVE_TO coordinates require you to be in the Hand's world.");
            }
            Location destination = new Location(
                    hand.getWorld(),
                    parseCoordinate(args[1], base.getX(), "x"),
                    parseCoordinate(args[2], base.getY(), "y"),
                    parseCoordinate(args[3], base.getZ(), "z")
            );
            hand.moveHeldTo(destination);
            operator.sendMessage(PREFIX + "§fHeld target moving to §f"
                    + format(destination.getX()) + " " + format(destination.getY()) + " " + format(destination.getZ())
                    + "§7; grip remains closed.");
            return true;
        }

        // MOVE_TO is also a normal main-Hand relocation command.
        Location destination;
        if (args.length == 2) {
            Player destinationPlayer = requireOnlinePlayer(args[1]);
            requireSameWorld(hand, destinationPlayer);
            destination = destinationPlayer.getEyeLocation().clone().add(0.0, 2.2, 0.0);
            hand.lookAt(destinationPlayer, 12.0);
        } else {
            if (!operator.getWorld().equals(hand.getWorld())) {
                throw new IllegalArgumentException("Relative MOVE_TO coordinates require you to be in the Hand's world.");
            }
            Location base = operator.getLocation();
            destination = new Location(
                    hand.getWorld(),
                    parseCoordinate(args[1], base.getX(), "x"),
                    parseCoordinate(args[2], base.getY(), "y"),
                    parseCoordinate(args[3], base.getZ(), "z")
            );
            hand.lookAt(destination, 12.0);
        }

        double distance = hand.getLocation().distance(destination);
        int ticks = Math.max(8, Math.min(100, (int) Math.round((0.45 + distance / 18.0) * 20.0)));
        hand.travelToAndIdle(destination, ticks, EasingCurve.EASE_IN_OUT);
        operator.sendMessage(PREFIX + "§fHand moving to §f"
                + format(destination.getX()) + " " + format(destination.getY()) + " " + format(destination.getZ()) + "§7.");
        return true;
    }

    private boolean presetSmash(Player operator, String label, String[] args) {
        if (args.length != 2 && args.length != 4) {
            throw new IllegalArgumentException("Usage: /" + label + " smash <player|x y z>");
        }

        double height = 13.0;
        float explosionPower = 7.5f;
        if (args.length == 2) {
            Player target = requireOnlinePlayer(args[1]);
            ParticleHand hand = TrueGodAttackPresets.prepare(handManager, target, mainSettings);
            int approachTicks = TrueGodAttackPresets.smashApproachTicks(hand, target.getLocation(), height);
            hand.smash(target, height, approachTicks, explosionPower);
            operator.sendMessage(PREFIX + "§4Destructive smash §7→ §f" + target.getName() + "§7.");
            return true;
        }

        Location base = operator.getLocation();
        Location point = new Location(
                operator.getWorld(),
                parseCoordinate(args[1], base.getX(), "x"),
                parseCoordinate(args[2], base.getY(), "y"),
                parseCoordinate(args[3], base.getZ(), "z")
        );
        ParticleHand hand = TrueGodAttackPresets.prepareAt(handManager, point, mainSettings);
        int approachTicks = TrueGodAttackPresets.smashApproachTicks(hand, point, height);
        hand.smash(point, height, approachTicks, explosionPower);
        operator.sendMessage(PREFIX + "§4Destructive smash §7→ §f"
                + format(point.getX()) + " " + format(point.getY()) + " " + format(point.getZ()) + "§7.");
        return true;
    }

    private boolean presetStalk(Player operator, String label, String[] args) {
        if (args.length > 2) {
            throw new IllegalArgumentException("Usage: /" + label + " stalk [player|off]");
        }

        if (args.length == 2 && (args[1].equalsIgnoreCase("off") || args[1].equalsIgnoreCase("stop"))) {
            ParticleHand existing = handManager.getHand();
            if (existing != null && existing.isStalking()) {
                existing.cancelAction();
                operator.sendMessage(PREFIX + "§4Stalking ended.");
            } else {
                operator.sendMessage(PREFIX + "§7The Hand is not stalking anyone.");
            }
            return true;
        }

        Player target = resolvePresetTarget(operator, args.length >= 2 ? args[1] : null);
        // STALK has its own WATCHING cue, so suppress the generic SEEN cue on
        // first summon to avoid two chat lines landing on the same tick.
        ParticleHand hand = TrueGodAttackPresets.prepare(handManager, target, mainSettings, false);
        hand.stalk(target);
        operator.sendMessage(PREFIX + "§4Stalking §7→ §f" + target.getName());
        return true;
    }

    private boolean mainLookAt(Player operator, String label, String[] args) {
        if (args.length != 1 && args.length != 2 && args.length != 4) {
            throw new IllegalArgumentException("Usage: /" + label + " lookat [player|off|x y z]");
        }
        if (args.length == 2 && (args[1].equalsIgnoreCase("off") || args[1].equalsIgnoreCase("stop"))) {
            return mainLookStop(operator, label, new String[]{"lookat", "stop"});
        }

        ParticleHand hand = handManager.getHand();
        if (args.length == 4) {
            if (hand == null) hand = TrueGodAttackPresets.summon(handManager, operator, mainSettings);
            else handManager.refreshVisuals();
            if (!operator.getWorld().equals(hand.getWorld())) {
                throw new IllegalArgumentException("Coordinate lookat requires you to be in the Hand's world.");
            }
            Location base = operator.getLocation();
            Location point = new Location(hand.getWorld(),
                    parseCoordinate(args[1], base.getX(), "x"),
                    parseCoordinate(args[2], base.getY(), "y"),
                    parseCoordinate(args[3], base.getZ(), "z"));
            hand.lookAt(point, 12.0);
            hand.startIdle();
            operator.sendMessage(PREFIX + "§fHand is now watching that point.");
            return true;
        }

        Player target = resolvePresetTarget(operator, args.length == 2 ? args[1] : null);
        if (hand == null) {
            hand = TrueGodAttackPresets.prepare(handManager, target, mainSettings, false);
        } else {
            requireSameWorld(hand, target);
            handManager.refreshVisuals();
        }
        hand.lookAt(target, 12.0);
        hand.startIdle();
        operator.sendMessage(PREFIX + "§fHand is now watching §f" + target.getName() + "§7.");
        return true;
    }

    private boolean mainLookStop(Player operator, String label, String[] args) {
        if (args.length > 2) throw new IllegalArgumentException("Usage: /" + label + " lookat stop");
        ParticleHand hand = handManager.getHand();
        if (hand == null) {
            operator.sendMessage(PREFIX + "§7No Hand is present.");
            return true;
        }
        boolean stopped = hand.stopLooking();
        hand.startIdle();
        operator.sendMessage(PREFIX + (stopped ? "§fLook tracking stopped; the Hand remains alive and idle." : "§7The Hand was not looking at anything."));
        return true;
    }

    private boolean mainStop(Player operator, String label, String[] args) {
        String commandName = args.length > 0 && args[0].equalsIgnoreCase("idle") ? "idle" : "stop";
        if (args.length != 1) throw new IllegalArgumentException("Usage: /" + label + " " + commandName);
        ParticleHand hand = handManager.getHand();
        if (hand == null) {
            operator.sendMessage(PREFIX + "§7No Hand is present.");
            return true;
        }
        hand.cancelAction();
        hand.stopMotion();
        hand.stopLooking();
        hand.stopRotation();
        hand.startIdle();
        operator.sendMessage(PREFIX + "§fHand returned to idle.");
        return true;
    }

    // ---------------------------------------------------------------------
    // animated articulated-hand commands
    // ---------------------------------------------------------------------

    private boolean handCreate(CommandSender sender, String label, String[] args) {
        Player player = requirePlayer(sender, "/" + label + " hand create");
        double palmWidth = args.length >= 3 ? parseDouble(args[2], "palm width") : 6.0;

        // Spawn far enough away that the full palm and wrist are immediately visible.
        ParticleHand hand = handManager.createHand(pointInFrontOf(player, 10.0), palmWidth);

        // The local palm front is +Z. Facing it back toward the creator makes
        // the hand readable immediately regardless of which compass direction
        // the player happened to be looking when it was spawned.
        hand.setRotation(player.getLocation().getYaw() + 180.0, 0.0, 0.0);

        sender.sendMessage(PREFIX + "§aCreated developer Hand. Palm width: §f" + format(palmWidth) + "§a blocks.");
        sender.sendMessage(PREFIX + "§7Orange points are joints, cyan lines are bones, and RGB lines are the hand root axes.");
        sender.sendMessage(PREFIX + "§7Try §f/" + label + " hand target <player>§7, §f/" + label + " hand orbit <player>§7, or a pose animation.");
        return true;
    }

    private boolean handRemove(CommandSender sender) {
        if (!handManager.removeHand()) {
            sender.sendMessage(PREFIX + "§cThere is no hand to remove.");
        } else {
            sender.sendMessage(PREFIX + "§aRemoved the developer Hand.");
        }
        return true;
    }

    private boolean handHere(CommandSender sender) {
        ParticleHand hand = requireHand();
        Player player = requirePlayer(sender, "hand here");
        hand.setLocation(pointInFrontOf(player, 10.0));
        sender.sendMessage(PREFIX + "§aMoved the hand in front of you.");
        return true;
    }

    private boolean handTeleport(CommandSender sender, String label, String[] args) {
        ParticleHand hand = requireHand();
        requireArgs(args, 5, "/" + label + " hand tp <x> <y> <z>");
        Location current = hand.getLocation();
        hand.setLocation(new Location(current.getWorld(), parseDouble(args[2], "x"), parseDouble(args[3], "y"), parseDouble(args[4], "z")));
        sender.sendMessage(PREFIX + "§aHand origin updated.");
        return true;
    }

    private boolean handMove(CommandSender sender, String label, String[] args) {
        ParticleHand hand = requireHand();
        requireArgs(args, 5, "/" + label + " hand move <dx> <dy> <dz>");
        hand.translate(parseDouble(args[2], "dx"), parseDouble(args[3], "dy"), parseDouble(args[4], "dz"));
        sender.sendMessage(PREFIX + "§aHand translated.");
        return true;
    }

    private boolean handGoto(CommandSender sender, String label, String[] args) {
        ParticleHand hand = requireHand();
        requireArgs(args, 6, "/" + label + " hand goto <x> <y> <z> <seconds> [easing]");
        Location current = hand.getLocation();
        Location target = new Location(
                current.getWorld(),
                parseDouble(args[2], "x"),
                parseDouble(args[3], "y"),
                parseDouble(args[4], "z")
        );
        int ticks = parseMotionDurationTicks(args[5]);
        EasingCurve easing = args.length >= 7 ? EasingCurve.parse(args[6]) : EasingCurve.SMOOTH;
        hand.travelTo(target, ticks, easing);
        sender.sendMessage(PREFIX + "§aTraveling hand to §f" + format(target.getX()) + ", " + format(target.getY()) + ", " + format(target.getZ())
                + "§a over §f" + format(ticks / 20.0) + "s§a (" + easing.commandName() + ").");
        return true;
    }

    private boolean handChase(CommandSender sender, String label, String[] args) {
        ParticleHand hand = requireHand();
        requireArgs(args, 3, "/" + label + " hand chase <player> [stop-distance] [max-speed] [acceleration]");
        Player target = requireOnlinePlayer(args[2]);
        requireSameWorld(hand, target);
        double stopDistance = args.length >= 4 ? parseDouble(args[3], "stop distance") : 4.0;
        double maxSpeed = args.length >= 5 ? parseDouble(args[4], "max speed") : 0.80;
        double acceleration = args.length >= 6 ? parseDouble(args[5], "acceleration") : 0.06;
        hand.chase(target, stopDistance, maxSpeed, acceleration);
        sender.sendMessage(PREFIX + "§aChasing §f" + target.getName() + "§a; stop distance §f" + format(stopDistance)
                + "§a, max speed §f" + format(maxSpeed) + " b/t§a, acceleration §f" + format(acceleration) + " b/t²§a.");
        return true;
    }

    private boolean handFollow(CommandSender sender, String label, String[] args) {
        ParticleHand hand = requireHand();
        requireArgs(args, 3, "/" + label + " hand follow <player> [distance] [height] [max-speed] [acceleration]");
        Player target = requireOnlinePlayer(args[2]);
        requireSameWorld(hand, target);
        double distance = args.length >= 4 ? parseDouble(args[3], "follow distance") : 10.0;
        double height = args.length >= 5 ? parseDouble(args[4], "follow height") : 2.0;
        double maxSpeed = args.length >= 6 ? parseDouble(args[5], "max speed") : 0.80;
        double acceleration = args.length >= 7 ? parseDouble(args[6], "acceleration") : 0.06;
        hand.follow(target, distance, height, maxSpeed, acceleration);
        sender.sendMessage(PREFIX + "§aFollowing behind §f" + target.getName() + "§a at §f" + format(distance)
                + "§a blocks and height offset §f" + format(height) + "§a.");
        return true;
    }

    private boolean handOrbit(CommandSender sender, String label, String[] args) {
        ParticleHand hand = requireHand();
        requireArgs(args, 3, "/" + label + " hand orbit <player> [radius] [deg/tick] [height] [max-speed] [acceleration]");
        Player target = requireOnlinePlayer(args[2]);
        requireSameWorld(hand, target);
        double radius = args.length >= 4 ? parseDouble(args[3], "orbit radius") : 12.0;
        double degreesPerTick = args.length >= 5 ? parseDouble(args[4], "orbit angular speed") : 2.0;
        double height = args.length >= 6 ? parseDouble(args[5], "orbit height") : 2.0;
        double maxSpeed = args.length >= 7 ? parseDouble(args[6], "max speed") : 1.0;
        double acceleration = args.length >= 8 ? parseDouble(args[7], "acceleration") : 0.08;
        hand.orbit(target, radius, degreesPerTick, height, maxSpeed, acceleration);
        sender.sendMessage(PREFIX + "§aOrbiting §f" + target.getName() + "§a at radius §f" + format(radius)
                + "§a and §f" + format(degreesPerTick) + "°/tick§a.");
        return true;
    }

    /** Convenience pursuit: chase and keep the palm front aimed at the same player. */
    private boolean handTarget(CommandSender sender, String label, String[] args) {
        ParticleHand hand = requireHand();
        requireArgs(args, 3, "/" + label + " hand target <player> [stop-distance] [max-speed] [acceleration] [turn-speed]");
        Player target = requireOnlinePlayer(args[2]);
        requireSameWorld(hand, target);
        double stopDistance = args.length >= 4 ? parseDouble(args[3], "stop distance") : 5.0;
        double maxSpeed = args.length >= 5 ? parseDouble(args[4], "max speed") : 0.85;
        double acceleration = args.length >= 6 ? parseDouble(args[5], "acceleration") : 0.06;
        double turnSpeed = args.length >= 7 ? parseDouble(args[6], "turn speed") : 6.0;
        hand.chase(target, stopDistance, maxSpeed, acceleration);
        hand.lookAt(target, turnSpeed);
        sender.sendMessage(PREFIX + "§aTargeting §f" + target.getName() + "§a: pursuit + live look tracking enabled.");
        return true;
    }

    /** pursuit variant: chase while preserving the palm-down attack stance. */
    private boolean handTargetDown(CommandSender sender, String label, String[] args) {
        ParticleHand hand = requireHand();
        requireArgs(args, 3, "/" + label + " hand targetdown <player> [stop-distance] [max-speed] [acceleration] [turn-speed]");
        Player target = requireOnlinePlayer(args[2]);
        requireSameWorld(hand, target);
        double stopDistance = args.length >= 4 ? parseDouble(args[3], "stop distance") : 5.0;
        double maxSpeed = args.length >= 5 ? parseDouble(args[4], "max speed") : 0.85;
        double acceleration = args.length >= 6 ? parseDouble(args[5], "acceleration") : 0.06;
        double turnSpeed = args.length >= 7 ? parseDouble(args[6], "turn speed") : 8.0;
        hand.chase(target, stopDistance, maxSpeed, acceleration);
        hand.downLookAt(target, turnSpeed);
        sender.sendMessage(PREFIX + "§atarget-down enabled for §f" + target.getName()
                + "§a: pursuit + palm-down/finger-bearing tracking.");
        return true;
    }

    private boolean handMotion(CommandSender sender, String label, String[] args) {
        requireArgs(args, 3, "/" + label + " hand motion <stop|info>");
        if (args[2].equalsIgnoreCase("stop") || args[2].equalsIgnoreCase("cancel")) {
            ParticleHand hand = requireHand();
            if (hand.stopMotion()) {
                sender.sendMessage(PREFIX + "§aStopped translation and zeroed its velocity.");
            } else {
                sender.sendMessage(PREFIX + "§7No translation controller is active.");
            }
            return true;
        }
        if (args[2].equalsIgnoreCase("info")) {
            return handInfo(sender);
        }
        throw new IllegalArgumentException("Usage: /" + label + " hand motion <stop|info>");
    }

    private boolean handLookAt(CommandSender sender, String label, String[] args) {
        ParticleHand hand = requireHand();
        requireArgs(args, 3, "/" + label + " hand lookat <player|off|x y z> [turn-deg/tick]");

        if (args[2].equalsIgnoreCase("off") || args[2].equalsIgnoreCase("stop") || args[2].equalsIgnoreCase("cancel")) {
            return handLookStop(sender);
        }

        // Coordinate form: lookat <x> <y> <z> [turn-speed]
        if (args.length >= 5 && isDouble(args[2]) && isDouble(args[3]) && isDouble(args[4])) {
            Location current = hand.getLocation();
            Location point = new Location(current.getWorld(),
                    parseDouble(args[2], "x"), parseDouble(args[3], "y"), parseDouble(args[4], "z"));
            double turnSpeed = args.length >= 6 ? parseDouble(args[5], "turn speed") : 6.0;
            hand.lookAt(point, turnSpeed);
            sender.sendMessage(PREFIX + "§aTracking look point §f" + format(point.getX()) + ", " + format(point.getY()) + ", " + format(point.getZ())
                    + "§a at up to §f" + format(turnSpeed) + "°/tick§a.");
            return true;
        }

        Player target = requireOnlinePlayer(args[2]);
        requireSameWorld(hand, target);
        double turnSpeed = args.length >= 4 ? parseDouble(args[3], "turn speed") : 6.0;
        hand.lookAt(target, turnSpeed);
        sender.sendMessage(PREFIX + "§aPalm-front look tracking enabled for §f" + target.getName() + "§a at up to §f" + format(turnSpeed) + "°/tick§a.");
        return true;
    }

    private boolean handDownLook(CommandSender sender, String label, String[] args) {
        ParticleHand hand = requireHand();
        requireArgs(args, 3, "/" + label + " hand downlook <player|off|x y z> [turn-deg/tick]");

        if (args[2].equalsIgnoreCase("off") || args[2].equalsIgnoreCase("stop") || args[2].equalsIgnoreCase("cancel")) {
            return handLookStop(sender);
        }

        if (args.length >= 5 && isDouble(args[2]) && isDouble(args[3]) && isDouble(args[4])) {
            Location current = hand.getLocation();
            Location point = new Location(current.getWorld(),
                    parseDouble(args[2], "x"), parseDouble(args[3], "y"), parseDouble(args[4], "z"));
            double turnSpeed = args.length >= 6 ? parseDouble(args[5], "turn speed") : 8.0;
            hand.downLookAt(point, turnSpeed);
            sender.sendMessage(PREFIX + "§aPalm-down attack tracking enabled toward §f"
                    + format(point.getX()) + ", " + format(point.getY()) + ", " + format(point.getZ())
                    + "§a. Palm +Z stays down; finger +Y tracks horizontally.");
            return true;
        }

        Player target = requireOnlinePlayer(args[2]);
        requireSameWorld(hand, target);
        double turnSpeed = args.length >= 4 ? parseDouble(args[3], "turn speed") : 8.0;
        hand.downLookAt(target, turnSpeed);
        sender.sendMessage(PREFIX + "§aPalm-down attack tracking enabled for §f" + target.getName()
                + "§a at up to §f" + format(turnSpeed) + "°/tick§a.");
        return true;
    }


    private boolean handLookStop(CommandSender sender) {
        ParticleHand hand = requireHand();
        if (hand.stopLooking()) {
            sender.sendMessage(PREFIX + "§aStopped look tracking; current orientation is held.");
        } else {
            sender.sendMessage(PREFIX + "§7No look target is active.");
        }
        return true;
    }

    private boolean handRotate(CommandSender sender, String label, String[] args) {
        ParticleHand hand = requireHand();
        requireArgs(args, 5, "/" + label + " hand rotate <yaw> <pitch> <roll>");
        hand.setRotation(parseDouble(args[2], "yaw"), parseDouble(args[3], "pitch"), parseDouble(args[4], "roll"));
        sender.sendMessage(PREFIX + "§aHand rotation updated.");
        return true;
    }

    private boolean handSingleRotation(CommandSender sender, String label, String[] args, Axis axis) {
        ParticleHand hand = requireHand();
        requireArgs(args, 3, "/" + label + " hand " + axis.name().toLowerCase(Locale.ROOT) + " <degrees>");
        double value = parseDouble(args[2], axis.name().toLowerCase(Locale.ROOT));
        switch (axis) {
            case YAW -> hand.setYaw(value);
            case PITCH -> hand.setPitch(value);
            case ROLL -> hand.setRoll(value);
        }
        sender.sendMessage(PREFIX + "§aHand " + axis.name().toLowerCase(Locale.ROOT) + " set to §f" + format(value) + "°§a.");
        return true;
    }

    private boolean handScale(CommandSender sender, String label, String[] args) {
        ParticleHand hand = requireHand();
        requireArgs(args, 3, "/" + label + " hand scale <palm-width-blocks>");
        hand.setScale(parseDouble(args[2], "palm width"));
        sender.sendMessage(PREFIX + "§aHand palm width set to §f" + format(hand.getScale()) + "§a blocks.");
        return true;
    }

    private boolean handSpin(CommandSender sender, String label, String[] args) {
        ParticleHand hand = requireHand();
        requireArgs(args, 5, "/" + label + " hand spin <yaw/tick> <pitch/tick> <roll/tick>");
        hand.setAngularVelocity(parseDouble(args[2], "yaw velocity"), parseDouble(args[3], "pitch velocity"), parseDouble(args[4], "roll velocity"));
        sender.sendMessage(PREFIX + "§aHand angular velocity updated.");
        return true;
    }

    private boolean handStop(CommandSender sender) {
        requireHand().stopRotation();
        sender.sendMessage(PREFIX + "§aStopped manual root spin. translation/look tracking are unchanged.");
        return true;
    }

    private boolean handAxes(CommandSender sender, String label, String[] args) {
        ParticleHand hand = requireHand();
        hand.setAxesVisible(parseToggle(args, 2, hand.isAxesVisible(), "/" + label + " hand axes [on|off]"));
        sender.sendMessage(PREFIX + "§aHand root axes/landmarks: §f" + onOff(hand.isAxesVisible()));
        return true;
    }

    private boolean handSkeleton(CommandSender sender, String label, String[] args) {
        ParticleHand hand = requireHand();
        hand.setSkeletonVisible(parseToggle(args, 2, hand.isSkeletonVisible(), "/" + label + " hand skeleton [on|off]"));
        sender.sendMessage(PREFIX + "§aArticulation skeleton: §f" + onOff(hand.isSkeletonVisible()));
        return true;
    }

    private boolean handGripDebug(CommandSender sender, String label, String[] args) {
        ParticleHand hand = requireHand();
        boolean enabled = parseToggle(args, 2, hand.isGripDebugVisible(), "/" + label + " hand gripdebug [on|off]");
        hand.setGripDebugVisible(enabled);
        sender.sendMessage(PREFIX + "§agrip debug: §f" + onOff(enabled) + "§a. §dMagenta§a = torso cage center; §aGreen§a = held player feet anchor.");
        return true;
    }

    private boolean handForceParticles(CommandSender sender, String label, String[] args) {
        ParticleHand hand = requireHand();
        boolean enabled = parseToggle(args, 2, hand.isForceParticles(), "/" + label + " hand forceparticles [on|off]");
        hand.setForceParticles(enabled);
        sender.sendMessage(PREFIX + "§aForced long-distance particles: §f" + onOff(enabled) + "§a.");
        if (enabled) {
            sender.sendMessage(PREFIX + "§7Paper's force flag is used and GodHand extends receivers to 512 blocks with aggressive distance LOD.");
        } else {
            sender.sendMessage(PREFIX + "§7Normal GodHand receiver range remains 160 blocks.");
        }
        return true;
    }

    private boolean handCombat(CommandSender sender, String label, String[] args) {
        ParticleHand hand = requireHand();
        requireArgs(args, 3, "/" + label + " hand combat <damage|knockback|debug|info> ...");
        String sub = args[2].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "damage" -> {
                requireArgs(args, 4, "/" + label + " hand combat damage <health-points>");
                double damage = parseDouble(args[3], "slam damage");
                hand.setSlamDamage(damage);
                sender.sendMessage(PREFIX + "§aSLAM damage: §f" + format(damage) + "§a health points.");
                return true;
            }
            case "knockback", "kb" -> {
                requireArgs(args, 4, "/" + label + " hand combat knockback <horizontal> [vertical]");
                double horizontal = parseDouble(args[3], "horizontal knockback");
                double vertical = args.length >= 5
                        ? parseDouble(args[4], "vertical knockback")
                        : hand.getSlamVerticalKnockback();
                hand.setSlamHorizontalKnockback(horizontal);
                hand.setSlamVerticalKnockback(vertical);
                sender.sendMessage(PREFIX + "§aSLAM knockback: §f" + format(horizontal)
                        + "§a horizontal, §f" + format(vertical) + "§a vertical blocks/tick.");
                return true;
            }
            case "debug" -> {
                boolean enabled = parseToggle(args, 3, hand.isCombatDebugVisible(),
                        "/" + label + " hand combat debug [on|off]");
                hand.setCombatDebugVisible(enabled);
                sender.sendMessage(PREFIX + "§aPalm hit-volume debug: §f" + onOff(enabled)
                        + "§a. §cRed§a box = oriented SLAM collision volume.");
                return true;
            }
            case "info" -> {
                sender.sendMessage(PREFIX + "§7SLAM damage: §f" + format(hand.getSlamDamage())
                        + " §7| knockback H/V: §f" + format(hand.getSlamHorizontalKnockback())
                        + "/" + format(hand.getSlamVerticalKnockback())
                        + " §7| last hits: §f" + hand.getLastImpactHitCount()
                        + " §7| debug: §f" + onOff(hand.isCombatDebugVisible()));
                return true;
            }
            default -> throw new IllegalArgumentException(
                    "Usage: /" + label + " hand combat <damage|knockback|debug|info> ..."
            );
        }
    }

    private boolean handDensity(CommandSender sender, String label, String[] args) {
        ParticleHand hand = requireHand();
        requireArgs(args, 3, "/" + label + " hand density <low|normal|high|ultra>");
        HandDensity density = HandDensity.parse(args[2]);
        hand.setDensity(density);
        sender.sendMessage(PREFIX + "§aHand surface density: §f" + density.commandName() + "§a.");
        return true;
    }

    private boolean handColor(CommandSender sender, String label, String[] args) {
        ParticleHand hand = requireHand();
        requireArgs(args, 3, "/" + label + " hand color <white|spectral|crimson|violet|void|gold|emerald|cyan|sand|custom> [r g b]");

        Color color;
        if (args[2].equalsIgnoreCase("custom")) {
            if (args.length != 6) {
                throw new IllegalArgumentException("Usage: /" + label + " hand color custom <red 0-255> <green 0-255> <blue 0-255>");
            }
            color = HandPalette.parseRgb(
                    parseInt(args[3], "red"),
                    parseInt(args[4], "green"),
                    parseInt(args[5], "blue")
            );
        } else {
            if (args.length != 3) {
                throw new IllegalArgumentException("Named colors do not take RGB values. Use: /" + label + " hand color custom <r> <g> <b>");
            }
            color = HandPalette.parsePreset(args[2]);
        }

        hand.setBaseColor(color);
        sender.sendMessage(PREFIX + "§aBase color: §f" + HandPalette.describe(color)
                + "§a. Custom RGB uses the nearest concrete material in solid mode.");
        return true;
    }

    private boolean handShading(CommandSender sender, String label, String[] args) {
        ParticleHand hand = requireHand();
        boolean enabled = parseToggle(args, 2, hand.isShadingEnabled(), "/" + label + " hand shading [on|off]");
        hand.setShadingEnabled(enabled);
        sender.sendMessage(PREFIX + "§aPseudo-light/shadow shading: §f" + onOff(enabled));
        return true;
    }


    private boolean handModel(CommandSender sender, String label, String[] args) {
        ParticleHand hand = requireHand();
        requireArgs(args, 3, "/" + label + " hand model <legacy|anatomical>");
        WristStyle model = WristStyle.parse(args[2]);
        hand.setWristStyle(model);
        sender.sendMessage(PREFIX + "§aHand model: §f" + model.commandName() + "§a.");
        return true;
    }

    private boolean handRenderer(CommandSender sender, String label, String[] args) {
        ParticleHand hand = requireHand();
        requireArgs(args, 3, "/" + label + " hand renderer <particles|itemdisplays>");
        HandRenderMode mode = HandRenderMode.parse(args[2]);
        hand.setRenderMode(mode);
        sender.sendMessage(PREFIX + "§aRenderer: §f" + mode.commandName()
                + (mode == HandRenderMode.ITEM_DISPLAYS ? " §7(default solid block ItemDisplays; block palettes + shading supported)" : ""));
        return true;
    }

    private boolean handVisual(CommandSender sender, String label, String[] args) {
        ParticleHand hand = requireHand();
        requireArgs(args, 3, "/" + label + " hand visual <reset|info>");
        if (args[2].equalsIgnoreCase("reset")) {
            hand.resetVisuals();
            sender.sendMessage(PREFIX + "§aReset visuals: white color, ultra density, shading ON, reduced motion smoothing, forced particles ON, anatomical model, ItemDisplays.");
            return true;
        }
        if (args[2].equalsIgnoreCase("info")) {
            return handInfo(sender);
        }
        throw new IllegalArgumentException("Usage: /" + label + " hand visual <reset|info>");
    }

    private boolean handFinger(CommandSender sender, String label, String[] args) {
        ParticleHand hand = requireHand();
        requireArgs(args, 4, "/" + label + " hand finger <thumb|index|middle|ring|pinky|all> <0-100> [seconds] [easing]");
        double percent = parseDouble(args[3], "curl percent");

        if (args.length >= 5) {
            int ticks = parseDurationTicks(args[4]);
            EasingCurve easing = args.length >= 6 ? EasingCurve.parse(args[5]) : EasingCurve.SMOOTH;
            if (args[2].equalsIgnoreCase("all")) {
                hand.animateAllFingerCurl(percent, ticks, easing);
                sender.sendMessage(PREFIX + "§aAnimating all finger curls to §f" + format(percent) + "%§a over §f" + format(ticks / 20.0) + "s§a (" + easing.commandName() + ").");
                return true;
            }
            HandDigit digit = HandDigit.parse(args[2]);
            hand.animateFingerCurl(digit, percent, ticks, easing);
            sender.sendMessage(PREFIX + "§aAnimating " + capitalize(digit.commandName()) + " curl to §f" + format(percent) + "%§a over §f" + format(ticks / 20.0) + "s§a (" + easing.commandName() + ").");
            return true;
        }

        if (args[2].equalsIgnoreCase("all")) {
            hand.setAllFingerCurl(percent);
            sender.sendMessage(PREFIX + "§aAll finger curls snapped to §f" + format(percent) + "%§a.");
            return true;
        }

        HandDigit digit = HandDigit.parse(args[2]);
        hand.setFingerCurl(digit, percent);
        sender.sendMessage(PREFIX + "§a" + capitalize(digit.commandName()) + " curl snapped to §f" + format(percent) + "%§a.");
        return true;
    }

    private boolean handJoint(CommandSender sender, String label, String[] args) {
        ParticleHand hand = requireHand();
        requireArgs(args, 5, "/" + label + " hand joint <finger> <1|2|3> <degrees> [seconds] [easing]");
        HandDigit digit = HandDigit.parse(args[2]);
        int joint = parseInt(args[3], "joint");
        double degrees = parseDouble(args[4], "joint angle");

        if (args.length >= 6) {
            int ticks = parseDurationTicks(args[5]);
            EasingCurve easing = args.length >= 7 ? EasingCurve.parse(args[6]) : EasingCurve.SMOOTH;
            hand.animateJointAngle(digit, joint, degrees, ticks, easing);
            sender.sendMessage(PREFIX + "§aAnimating " + capitalize(digit.commandName()) + " joint §f" + joint + "§a to §f" + format(degrees) + "°§a over §f" + format(ticks / 20.0) + "s§a (" + easing.commandName() + ").");
            return true;
        }

        hand.setJointAngle(digit, joint, degrees);
        sender.sendMessage(PREFIX + "§a" + capitalize(digit.commandName()) + " joint §f" + joint + "§a snapped to §f" + format(degrees) + "°§a.");
        return true;
    }

    private boolean handPose(CommandSender sender, String label, String[] args) {
        ParticleHand hand = requireHand();
        requireArgs(args, 3, "/" + label + " hand pose <open|relaxed|fist|point|bird|thumbs_up|thumbs_down|claw> [seconds] [easing]");
        HandPose pose = HandPose.parse(args[2]);

        if (args.length >= 4) {
            int ticks = parseDurationTicks(args[3]);
            EasingCurve easing = args.length >= 5 ? EasingCurve.parse(args[4]) : EasingCurve.SMOOTH;
            hand.animatePose(pose, ticks, easing);
            sender.sendMessage(PREFIX + "§aAnimating to pose §f" + pose.commandName() + "§a over §f" + format(ticks / 20.0) + "s§a (" + easing.commandName() + ").");
            return true;
        }

        hand.applyPose(pose);
        sender.sendMessage(PREFIX + "§aSnapped to pose: §f" + pose.commandName() + "§a.");
        return true;
    }

    private boolean handAnimation(CommandSender sender, String label, String[] args) {
        requireArgs(args, 3, "/" + label + " hand animation <stop|info>");
        if (args[2].equalsIgnoreCase("stop") || args[2].equalsIgnoreCase("cancel")) {
            return handAnimationStop(sender);
        }
        if (args[2].equalsIgnoreCase("info")) {
            return handInfo(sender);
        }
        throw new IllegalArgumentException("Usage: /" + label + " hand animation <stop|info>");
    }

    private boolean handAnimationStop(CommandSender sender) {
        ParticleHand hand = requireHand();
        if (hand.cancelAnimation()) {
            sender.sendMessage(PREFIX + "§aCancelled the active hand animation and held the current pose.");
        } else {
            sender.sendMessage(PREFIX + "§7No hand animation is currently active.");
        }
        return true;
    }

    private boolean handInfo(CommandSender sender) {
        ParticleHand hand = requireHand();
        Location location = hand.getLocation();
        sender.sendMessage("§8§m--------------------------------");
        sender.sendMessage("§fGodHand — Hand of God");
        sender.sendMessage("§7Scale meaning: §fpalm width in blocks");
        sendTransformInfo(sender, location, hand.getScale(), hand.getYaw(), hand.getPitch(), hand.getRoll(), hand.getYawVelocity(), hand.getPitchVelocity(), hand.getRollVelocity(), hand.isAxesVisible());
        sender.sendMessage("§7Pose: §f" + hand.getPoseName());
        sender.sendMessage("§7Skeleton debug: §f" + onOff(hand.isSkeletonVisible()));
        sender.sendMessage("§7Motion: §f" + hand.getMotionDescription());
        sender.sendMessage("§7Motion velocity: §f" + format(hand.getMotionVelocityX()) + ", " + format(hand.getMotionVelocityY()) + ", " + format(hand.getMotionVelocityZ())
                + " §7(speed " + format(hand.getMotionSpeed()) + " b/t)");
        if (hand.getTranslationMode() == xyz.dimseal.godHand.hand.motion.TranslationMode.TRAVEL) {
            sender.sendMessage("§7Travel progress: §f" + format(hand.getTravelProgress() * 100.0) + "% §7(" + hand.getTravelEasing().commandName() + ")");
            sender.sendMessage("§7Travel time: §f" + format(hand.getTravelElapsedTicks() / 20.0) + " / " + format(hand.getTravelDurationTicks() / 20.0) + "s");
        }
        sender.sendMessage("§7Look tracking: §f" + hand.getLookDescription());
        if (hand.isLooking()) {
            sender.sendMessage("§7Look mode: §f" + hand.getLookModeName());
            sender.sendMessage("§7Look turn limit: §f" + format(hand.getLookTurnSpeed()) + "°/tick");
        }
        sender.sendMessage("§7Current action: §f" + hand.getActionDescription());
        sender.sendMessage("§7Grip debug: §f" + onOff(hand.isGripDebugVisible()));
        sender.sendMessage("§7Surface density: §f" + hand.getDensity().commandName());
        sender.sendMessage("§7Base color: §f" + HandPalette.describe(hand.getBaseColor()));
        sender.sendMessage("§7Shading: §f" + onOff(hand.isShadingEnabled()));
        sender.sendMessage("§7Renderer: §f" + hand.getRenderMode().commandName());
        sender.sendMessage("§7Forced long-distance particles: §f" + onOff(hand.isForceParticles()) + (hand.isForceParticles() ? " §7(512-block receiver range)" : " §7(160-block receiver range)"));
        sender.sendMessage("§7SLAM combat: §f" + format(hand.getSlamDamage()) + " damage, "
                + format(hand.getSlamHorizontalKnockback()) + "/" + format(hand.getSlamVerticalKnockback()) + " H/V knockback");
        sender.sendMessage("§7SLAM last hit count: §f" + hand.getLastImpactHitCount() + " §7| hitbox debug: §f" + onOff(hand.isCombatDebugVisible()));
        var grip = hand.getGripLocalPoint();
        sender.sendMessage("§7Grip cage local XYZ: §f" + format(grip.x()) + ", " + format(grip.y()) + ", " + format(grip.z()));
        if (hand.isHoldingPlayer() && hand.getHeldPlayer() != null) {
            sender.sendMessage("§7Held player: §f" + hand.getHeldPlayer().getName());
        }
        if (hand.isAnimating()) {
            sender.sendMessage("§7Animation: §f" + hand.getAnimationDescription());
            sender.sendMessage("§7Animation progress: §f" + format(hand.getAnimationProgress() * 100.0) + "% §7(" + hand.getAnimationEasing().commandName() + ")");
            sender.sendMessage("§7Animation time: §f" + format(hand.getAnimationElapsedTicks() / 20.0) + " / " + format(hand.getAnimationDurationTicks() / 20.0) + "s");
        } else {
            sender.sendMessage("§7Animation: §fidle");
        }
        for (HandDigit digit : HandDigit.values()) {
            var state = hand.getFingerState(digit);
            sender.sendMessage("§7" + capitalize(digit.commandName()) + " joints: §f" + format(state.getJoint(1)) + ", " + format(state.getJoint(2)) + ", " + format(state.getJoint(3)));
        }
        sender.sendMessage("§8§m--------------------------------");
        return true;
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private Player requireAuthorizedOperator(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX + "§cThis command requires an in-game operator.");
            return null;
        }
        if (!config.isAuthorized(player)) {
            hideUnauthorized(player);
            return null;
        }
        return player;
    }


    /**
     * Routed actions accept an optional explicit player. With no argument,
     * target the nearest other player in the operator's world; if testing alone,
     * fall back to the operator so commands remain useful on an empty test server.
     */
    private Player resolvePresetTarget(Player operator, String explicitName) {
        if (explicitName != null && !explicitName.isBlank()) {
            return requireOnlinePlayer(explicitName);
        }

        Player nearest = null;
        double bestDistanceSquared = Double.POSITIVE_INFINITY;
        Location origin = operator.getLocation();
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            if (!online.isOnline() || online.getUniqueId().equals(operator.getUniqueId())) {
                continue;
            }
            Location at = online.getLocation();
            if (origin.getWorld() == null || at.getWorld() == null || !origin.getWorld().equals(at.getWorld())) {
                continue;
            }
            double dx = at.getX() - origin.getX();
            double dy = at.getY() - origin.getY();
            double dz = at.getZ() - origin.getZ();
            double distanceSquared = dx * dx + dy * dy + dz * dz;
            if (distanceSquared < bestDistanceSquared) {
                bestDistanceSquared = distanceSquared;
                nearest = online;
            }
        }
        return nearest != null ? nearest : operator;
    }

    private static String formatTicks(int ticks) {
        return String.format(Locale.US, "%.2fs", ticks / 20.0);
    }


    private ParticleHand requireHand() {
        ParticleHand hand = handManager.getHand();
        if (hand == null) {
            throw new IllegalArgumentException("No hand exists. Run /godhand summon for the main Hand or /godhand dev hand create for a developer instance.");
        }
        return hand;
    }

    private static Player requirePlayer(CommandSender sender, String action) {
        if (sender instanceof Player player) {
            return player;
        }
        throw new IllegalArgumentException("A player must run " + action + ".");
    }

    private static Location pointInFrontOf(Player player, double distance) {
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize().multiply(distance);
        return eye.add(direction);
    }

    private Player requireOnlinePlayer(String name) {
        Player player = plugin.getServer().getPlayerExact(name);
        if (player == null || !player.isOnline()) {
            throw new IllegalArgumentException("Player '" + name + "' is not online.");
        }
        return player;
    }

    private static void requireSameWorld(ParticleHand hand, Player player) {
        Location playerLocation = player.getLocation();
        if (playerLocation.getWorld() == null || hand.getWorld() == null || !hand.getWorld().equals(playerLocation.getWorld())) {
            throw new IllegalArgumentException("Cross-dimensional Hand movement is disabled. The target must be in the Hand's current world.");
        }
    }

    private static boolean isDouble(String input) {
        try {
            return Double.isFinite(Double.parseDouble(input));
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static int parseInt(String input, String name) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid " + name + ": '" + input + "'.");
        }
    }

    private static String capitalize(String input) {
        return input.isEmpty() ? input : Character.toUpperCase(input.charAt(0)) + input.substring(1);
    }

    /** True for vanilla-style absolute/relative coordinate tokens used by main action commands. */
    private static boolean isCoordinateToken(String input) {
        if (input == null || input.isBlank()) return false;
        if (input.charAt(0) == '~') {
            if (input.length() == 1) return true;
            return isDouble(input.substring(1));
        }
        return isDouble(input);
    }

    /** Vanilla-style absolute or relative coordinate parser: ~, ~5, ~-2.5, or an absolute number. */
    private static double parseCoordinate(String input, double base, String name) {
        if (input == null || input.isEmpty()) {
            throw new IllegalArgumentException("Invalid " + name + " coordinate.");
        }
        if (input.charAt(0) == '~') {
            if (input.length() == 1) return base;
            return base + parseDouble(input.substring(1), name + " relative offset");
        }
        return parseDouble(input, name);
    }

    private static double parseDouble(String input, String name) {
        try {
            double value = Double.parseDouble(input);
            if (!Double.isFinite(value)) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid " + name + ": '" + input + "'.");
        }
    }

    private static int parseDurationTicks(String input) {
        double seconds = parseDouble(input, "animation duration");
        if (seconds <= 0.0 || seconds > 120.0) {
            throw new IllegalArgumentException("Animation duration must be greater than 0 and at most 120 seconds.");
        }
        return Math.max(1, (int) Math.round(seconds * 20.0));
    }

    private static int parseMotionDurationTicks(String input) {
        double seconds = parseDouble(input, "travel duration");
        if (seconds <= 0.0 || seconds > 300.0) {
            throw new IllegalArgumentException("Travel duration must be greater than 0 and at most 300 seconds.");
        }
        return Math.max(1, (int) Math.round(seconds * 20.0));
    }

    private static boolean parseToggle(String[] args, int index, boolean current, String usage) {
        if (args.length <= index) {
            return !current;
        }
        if (args[index].equalsIgnoreCase("on")) {
            return true;
        }
        if (args[index].equalsIgnoreCase("off")) {
            return false;
        }
        throw new IllegalArgumentException("Usage: " + usage);
    }

    private static void requireArgs(String[] args, int count, String usage) {
        if (args.length < count) {
            throw new IllegalArgumentException("Usage: " + usage);
        }
    }

    private static String format(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private static String onOff(boolean value) {
        return value ? "ON" : "OFF";
    }

    private static void sendTransformInfo(
            CommandSender sender,
            Location location,
            double scale,
            double yaw,
            double pitch,
            double roll,
            double yawVelocity,
            double pitchVelocity,
            double rollVelocity,
            boolean axes
    ) {
        sender.sendMessage("§7World: §f" + (location.getWorld() == null ? "null" : location.getWorld().getName()));
        sender.sendMessage("§7Origin: §f" + format(location.getX()) + ", " + format(location.getY()) + ", " + format(location.getZ()));
        sender.sendMessage("§7Scale: §f" + format(scale));
        sender.sendMessage("§7Rotation Y/P/R: §f" + format(yaw) + ", " + format(pitch) + ", " + format(roll));
        sender.sendMessage("§7Spin/tick Y/P/R: §f" + format(yawVelocity) + ", " + format(pitchVelocity) + ", " + format(rollVelocity));
        sender.sendMessage("§7Debug axes: §f" + onOff(axes));
    }

    private static final int HELP_PAGE_COUNT = 4;

    private static void sendHelp(CommandSender sender, String label, int requestedPage) {
        int page = Math.max(1, Math.min(HELP_PAGE_COUNT, requestedPage));
        sender.sendMessage("§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        sender.sendMessage("§5§lGODHAND §8• §fAdmin Utility §8• §7Help §f" + page + "§8/§f" + HELP_PAGE_COUNT);
        sender.sendMessage("§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        switch (page) {
            case 1 -> {
                helpLine(sender, label, "summon [player]", "Summon the configured Hand.", "§d");
                helpLine(sender, label, "despawn", "Remove the active Hand and runtime visuals.", "§d");
                helpLine(sender, label, "status", "Show the current Hand/action/settings state.", "§d");
                helpLine(sender, label, "action <action> ...", "Movement, control, gestures, and utility actions.", "§b");
                helpLine(sender, label, "attack <attack> ...", "Hostile Hand attacks.", "§c");
                helpLine(sender, label, "friendly <action> ...", "Beneficial/protective actions.", "§a");
                helpLine(sender, label, "stop", "Emergency stop: cancel motion/actions and return to idle.", "§7");
                sender.sendMessage("§8Tip: §7use §f/" + label + " help 2 §7for the complete action list.");
            }
            case 2 -> {
                sender.sendMessage("§b§lACTIONS §8— §7control, movement, and presentation");
                sender.sendMessage("§f bird §8• §fchase §8• §fgrab §8• §fidle §8• §fjuggle");
                sender.sendMessage("§f lookat §8• §fmoveto §8• §fpose §8• §frelease §8• §fstalk");
                sender.sendMessage("§f throw §8• §fthumbsdown §8• §fthumbsup §8• §ftransport §8• §fwave");
                sender.sendMessage("");
                helpLine(sender, label, "action lookat <player|x y z|stop>", "Aim the Hand or stop look tracking.", "§b");
                helpLine(sender, label, "action pose <pose> [seconds] [easing]", "Apply or animate a Hand pose.", "§b");
                helpLine(sender, label, "action idle", "Cancel activity and return the Hand to idle.", "§b");
                helpLine(sender, label, "action transport ...", "Carry a grabbed player to a destination.", "§b");
            }
            case 3 -> {
                sender.sendMessage("§c§lATTACKS");
                sender.sendMessage("§f breach §8• §fclap §8• §fcyclone §8• §fforceslap §8• §fslap");
                sender.sendMessage("§f givebird §8• §fgroundslam §8• §fjudgment §8• §fpound §8• §ftoss");
                sender.sendMessage("§f punch §8• §frage §8• §fsmash §8• §fspank");
                sender.sendMessage("");
                sender.sendMessage("§a§lFRIENDLY");
                sender.sendMessage("§f bless §8• §fguard §8• §fsanctuary");
                sender.sendMessage("");
                sender.sendMessage("§8Targets default to you where the underlying action supports it; tab completion shows valid player arguments.");
            }
            case 4 -> {
                sender.sendMessage("§e§lSETTINGS & ADMIN");
                helpLine(sender, label, "settings preset <low|medium|high>", "Apply a visual preset.", "§e");
                helpLine(sender, label, "settings <color|scale|density|model|renderer|...>", "Customize Hand defaults; preset becomes custom.", "§e");
                helpLine(sender, label, "whitelist <on|off|add|remove|list>", "Manage operator access enforcement.", "§6");
                helpLine(sender, label, "reload", "Reload config.yml and operator_whitelist.yml.", "§6");
                helpLine(sender, label, "dev help", "Open low-level developer controls.", "§8");
                helpLine(sender, label, "dev debug displays <status|purge>", "Inspect or emergency-purge Hand ItemDisplays.", "§8");
                sender.sendMessage("§8Cross-dimensional Hand travel is currently blocked by design.");
            }
            default -> { }
        }

        sendHelpNavigation(sender, label, page);
    }

    private static void helpLine(CommandSender sender, String label, String syntax, String description, String color) {
        sender.sendMessage(color + "/" + label + " " + syntax + " §8— §7" + description);
    }

    private static void sendHelpNavigation(CommandSender sender, String label, int page) {
        Component bar = Component.text("━━━━━━━━━━━━━━━━ ", NamedTextColor.DARK_GRAY);
        if (page > 1) {
            bar = bar.append(Component.text("◀ PREV", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
                    .clickEvent(ClickEvent.runCommand("/" + label + " help " + (page - 1)))
                    .hoverEvent(HoverEvent.showText(Component.text("Open help page " + (page - 1), NamedTextColor.GRAY))));
        } else {
            bar = bar.append(Component.text("◀ PREV", NamedTextColor.DARK_GRAY));
        }
        bar = bar.append(Component.text("  [" + page + "/" + HELP_PAGE_COUNT + "]  ", NamedTextColor.GRAY));
        if (page < HELP_PAGE_COUNT) {
            bar = bar.append(Component.text("NEXT ▶", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
                    .clickEvent(ClickEvent.runCommand("/" + label + " help " + (page + 1)))
                    .hoverEvent(HoverEvent.showText(Component.text("Open help page " + (page + 1), NamedTextColor.GRAY))));
        } else {
            bar = bar.append(Component.text("NEXT ▶", NamedTextColor.DARK_GRAY));
        }
        bar = bar.append(Component.text(" ━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY));
        sender.sendMessage(bar);
    }


    private static void sendDeveloperHelp(CommandSender sender, String label) {
        sender.sendMessage("§8§m--------------------------------");
        sender.sendMessage("§fGodHand §7— Developer Tools");
        sender.sendMessage("§f/" + label + " dev hand <...> §7- low-level transform, pose, motion, render, and combat controls");
        sender.sendMessage("§f/" + label + " dev debug <axes|skeleton|grip|combat> [on|off]");
        sender.sendMessage("§f/" + label + " dev debug displays <status|purge> §7- ItemDisplay watchdog/emergency cleanup");
        sender.sendMessage("§7Preconfigured behavior belongs under /" + label + " attack, /" + label + " action, and /" + label + " friendly.");
        sender.sendMessage("§7Motion smoothing is fixed to reduced and is not configurable.");
        sender.sendMessage("§8§m--------------------------------");
    }



    private static void sendHandHelp(CommandSender sender, String label) {
        sender.sendMessage("§8§m--------------------------------");
        sender.sendMessage("§fGodHand Developer Hand Controls");
        sender.sendMessage("§f/" + label + " hand create|remove|here|tp|move|goto ...");
        sender.sendMessage("§f/" + label + " hand chase|follow|orbit|target|targetdown|motion ...");
        sender.sendMessage("§f/" + label + " hand lookat|downlook|lookstop ...");
        sender.sendMessage("§f/" + label + " hand rotate|yaw|pitch|roll|scale|spin|stop ...");
        sender.sendMessage("§f/" + label + " hand axes|skeleton|gripdebug|combat ...");
        sender.sendMessage("§f/" + label + " hand density|color|shading|forceparticles|renderer|model|visual ...");
        sender.sendMessage("§f/" + label + " hand pose|finger|joint|animation|animstop|info ...");
        sender.sendMessage("§7Blur/deghosting controls are removed; reduced smoothing is built in.");
        sender.sendMessage("§8§m--------------------------------");
    }


    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (sender instanceof Player player && !config.isAuthorized(player)) return Collections.emptyList();
        if (!(sender instanceof Player) && !sender.isOp()) return Collections.emptyList();

        if (args.length == 1) {
            return filter(args[0], List.of(
                    "summon", "despawn", "settings", "status", "attack", "action", "friendly",
                    "stop", "whitelist", "reload", "dev", "help"));
        }

        String root = args[0].toLowerCase(Locale.ROOT);
        if (root.equals("help")) {
            if (args.length == 2) return filter(args[1], List.of("1", "2", "3", "4"));
            return Collections.emptyList();
        }
        if (root.equals("attack")) {
            if (args.length == 2) return filter(args[1], List.of("breach", "clap", "cyclone", "forceslap", "slap", "givebird", "groundslam", "judgment", "pound", "toss", "punch", "rage", "smash", "spank"));
            if (args.length == 3) return playerNames(args[2], false);
            return Collections.emptyList();
        }
        if (root.equals("action")) return completeAction(args);
        if (root.equals("friendly")) {
            if (args.length == 2) return filter(args[1], List.of("bless", "guard", "sanctuary"));
            if (args.length == 3) {
                List<String> names = new ArrayList<>(playerNames(args[2], false));
                if (args[1].equalsIgnoreCase("guard")) names.addAll(filter(args[2], List.of("off")));
                return names;
            }
            return Collections.emptyList();
        }
        if (root.equals("settings")) {
            if (args.length == 2) return filter(args[1], List.of("preset", "color", "scale", "density", "model", "renderer", "shading", "forceparticles", "reset", "info"));
            if (args.length == 3) {
                return switch (args[1].toLowerCase(Locale.ROOT)) {
                    case "preset" -> filter(args[2], List.of("low", "medium", "high"));
                    case "color", "colour" -> filter(args[2], List.of("white", "spectral", "crimson", "violet", "void", "gold", "emerald", "cyan", "sand", "custom"));
                    case "scale", "size" -> filter(args[2], List.of("2", "3", "4", "6"));
                    case "density" -> filter(args[2], List.of("low", "normal", "high", "ultra"));
                    case "model" -> filter(args[2], List.of("legacy", "anatomical"));
                    case "renderer", "render" -> filter(args[2], List.of("itemdisplays", "particles"));
                    case "shading", "forceparticles" -> filter(args[2], List.of("on", "off"));
                    default -> Collections.emptyList();
                };
            }
            if (args.length >= 4 && args[1].equalsIgnoreCase("color") && args[2].equalsIgnoreCase("custom")) {
                return filter(args[args.length - 1], List.of("0", "32", "64", "128", "192", "255"));
            }
            return Collections.emptyList();
        }
        if (root.equals("whitelist")) {
            if (args.length == 2) return filter(args[1], List.of("on", "off", "add", "remove", "list"));
            if (args.length == 3 && args[1].equalsIgnoreCase("add")) return playerNames(args[2], false);
            if (args.length == 3 && args[1].equalsIgnoreCase("remove")) {
                List<String> entries = new ArrayList<>();
                for (GodHandConfig.OperatorEntry entry : config.getOperatorWhitelist()) entries.add(entry.name());
                return filter(args[2], entries);
            }
            return Collections.emptyList();
        }
        if (root.equals("summon") && args.length == 2) return playerNames(args[1], false);
        if (root.equals("dev")) return completeDeveloper(args);
        return Collections.emptyList();
    }

    private List<String> completeAction(String[] args) {
        if (args.length == 2) {
            return filter(args[1], List.of("bird", "chase", "grab", "idle", "juggle", "lookat", "moveto", "pose", "release", "stalk", "throw", "thumbsdown", "thumbsup", "transport", "wave"));
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        List<String> worldCoordinates = List.of("~", "~1", "~-1", "0", "10", "-10");

        if (args.length == 3) {
            return switch (action) {
                case "chase", "stalk" -> merge(playerNames(args[2], false), filter(args[2], List.of("off")));
                case "lookat" -> merge(merge(playerNames(args[2], false), filter(args[2], List.of("stop"))), filter(args[2], worldCoordinates));
                case "moveto" -> merge(playerNames(args[2], false), filter(args[2], worldCoordinates));
                case "grab", "juggle", "wave", "thumbsup", "thumbsdown", "bird", "transport" -> playerNames(args[2], false);
                case "pose" -> filter(args[2], List.of("open", "relaxed", "fist", "point", "bird", "thumbs_up", "thumbs_down", "claw"));
                case "release" -> filter(args[2], List.of("0.25", "0.5", "1"));
                case "throw" -> filter(args[2], List.of("1.0", "1.35", "1.75", "2.0"));
                default -> Collections.emptyList();
            };
        }

        if (action.equals("pose")) {
            if (args.length == 4) return filter(args[3], List.of("0.25", "0.5", "1", "2"));
            if (args.length == 5) return filter(args[4], easingNames());
        }
        if (action.equals("throw")) {
            if (args.length == 4) return filter(args[3], List.of("0.25", "0.45", "0.75", "1.0"));
            if (args.length == 5) return filter(args[4], List.of("0.25", "0.4", "0.6", "1"));
        }
        if (action.equals("lookat")) {
            if (args.length == 4 && isCoordinateToken(args[2])) return filter(args[3], worldCoordinates);
            if (args.length == 5 && isCoordinateToken(args[2])) return filter(args[4], worldCoordinates);
        }
        if (action.equals("moveto")) {
            if (args.length == 4 && isCoordinateToken(args[2])) return filter(args[3], worldCoordinates);
            if (args.length == 5 && isCoordinateToken(args[2])) return filter(args[4], worldCoordinates);
        }
        if (action.equals("transport")) {
            if (args.length == 4) return merge(playerNames(args[3], false), filter(args[3], worldCoordinates));
            if (args.length == 5 && isCoordinateToken(args[3])) return filter(args[4], worldCoordinates);
            if (args.length == 6 && isCoordinateToken(args[3])) return filter(args[5], worldCoordinates);
        }
        return Collections.emptyList();
    }

    private List<String> completeDeveloper(String[] args) {
        if (args.length == 2) return filter(args[1], List.of("hand", "debug", "help"));

        if (args[1].equalsIgnoreCase("debug")) {
            if (args.length == 3) return filter(args[2], List.of("axes", "skeleton", "grip", "combat", "displays"));
            if (args.length == 4) {
                if (args[2].equalsIgnoreCase("displays")) return filter(args[3], List.of("status", "purge"));
                return filter(args[3], List.of("on", "off"));
            }
            return Collections.emptyList();
        }

        if (!args[1].equalsIgnoreCase("hand")) return Collections.emptyList();
        if (args.length == 3) {
            return filter(args[2], List.of(
                    "create", "remove", "here", "tp", "move", "goto",
                    "chase", "follow", "orbit", "target", "targetdown", "motion",
                    "lookat", "downlook", "lookstop",
                    "rotate", "yaw", "pitch", "roll", "scale", "spin", "stop",
                    "axes", "skeleton", "gripdebug", "forceparticles", "combat",
                    "density", "color", "shading", "renderer", "model", "visual",
                    "pose", "finger", "joint", "animation", "animstop", "info"));
        }

        String sub = args[2].toLowerCase(Locale.ROOT);
        List<String> coordinates = List.of("0", "10", "-10");
        List<String> angles = List.of("0", "45", "90", "180", "-90");
        List<String> speeds = List.of("0.25", "0.5", "0.8", "1", "2");

        if (args.length == 4) {
            return switch (sub) {
                case "create", "scale" -> filter(args[3], List.of("1.5", "2", "3", "4", "6"));
                case "tp", "move", "goto" -> filter(args[3], coordinates);
                case "chase", "follow", "orbit", "target", "targetdown" -> playerNames(args[3], false);
                case "motion" -> filter(args[3], List.of("stop", "info"));
                case "lookat", "downlook" -> merge(merge(playerNames(args[3], false), filter(args[3], List.of("off", "stop"))), filter(args[3], coordinates));
                case "rotate", "spin", "yaw", "pitch", "roll" -> filter(args[3], angles);
                case "axes", "skeleton", "gripdebug", "forceparticles", "shading" -> filter(args[3], List.of("on", "off"));
                case "combat" -> filter(args[3], List.of("damage", "knockback", "debug", "info"));
                case "density" -> filter(args[3], List.of("low", "normal", "high", "ultra"));
                case "color" -> filter(args[3], List.of("white", "spectral", "crimson", "violet", "void", "gold", "emerald", "cyan", "sand", "custom"));
                case "renderer" -> filter(args[3], List.of("itemdisplays", "particles"));
                case "model" -> filter(args[3], List.of("legacy", "anatomical"));
                case "visual" -> filter(args[3], List.of("reset", "info"));
                case "pose" -> filter(args[3], List.of("open", "relaxed", "fist", "point", "bird", "thumbs_up", "thumbs_down", "claw"));
                case "finger" -> filter(args[3], List.of("thumb", "index", "middle", "ring", "pinky", "all"));
                case "joint" -> filter(args[3], List.of("thumb", "index", "middle", "ring", "pinky"));
                case "animation" -> filter(args[3], List.of("stop", "info"));
                default -> Collections.emptyList();
            };
        }

        if (args.length == 5) {
            return switch (sub) {
                case "tp", "move", "goto" -> filter(args[4], coordinates);
                case "chase", "target", "targetdown" -> filter(args[4], List.of("3", "4", "5", "8", "12"));
                case "follow" -> filter(args[4], List.of("6", "8", "10", "14"));
                case "orbit" -> filter(args[4], List.of("6", "10", "12", "16"));
                case "lookat", "downlook" -> filter(args[4], isDouble(args[3]) ? coordinates : List.of("4", "6", "8", "12"));
                case "rotate", "spin" -> filter(args[4], angles);
                case "combat" -> {
                    if (args[3].equalsIgnoreCase("debug")) yield filter(args[4], List.of("on", "off"));
                    if (args[3].equalsIgnoreCase("damage")) yield filter(args[4], List.of("3", "6", "10", "20"));
                    if (args[3].equalsIgnoreCase("knockback")) yield filter(args[4], List.of("0.5", "1", "2", "4"));
                    yield Collections.emptyList();
                }
                case "color" -> args[3].equalsIgnoreCase("custom")
                        ? filter(args[4], List.of("0", "32", "64", "128", "192", "255"))
                        : Collections.emptyList();
                case "pose" -> filter(args[4], List.of("0.25", "0.5", "1", "2"));
                case "finger" -> filter(args[4], List.of("0", "25", "50", "75", "100"));
                case "joint" -> filter(args[4], List.of("1", "2", "3"));
                default -> Collections.emptyList();
            };
        }

        if (args.length == 6) {
            return switch (sub) {
                case "tp", "move", "goto" -> filter(args[5], coordinates);
                case "chase", "target", "targetdown" -> filter(args[5], speeds);
                case "follow" -> filter(args[5], List.of("0", "1", "2", "4", "6"));
                case "orbit" -> filter(args[5], List.of("1", "2", "3", "5"));
                case "lookat", "downlook" -> isDouble(args[3]) ? filter(args[5], coordinates) : Collections.emptyList();
                case "rotate", "spin" -> filter(args[5], angles);
                case "combat" -> args[3].equalsIgnoreCase("knockback")
                        ? filter(args[5], List.of("0.25", "0.5", "0.8", "1.2"))
                        : Collections.emptyList();
                case "color" -> args[3].equalsIgnoreCase("custom")
                        ? filter(args[5], List.of("0", "32", "64", "128", "192", "255"))
                        : Collections.emptyList();
                case "pose" -> filter(args[5], easingNames());
                case "finger" -> filter(args[5], List.of("0.25", "0.5", "1", "2"));
                case "joint" -> filter(args[5], List.of("0", "30", "60", "90", "120"));
                default -> Collections.emptyList();
            };
        }

        if (args.length == 7) {
            return switch (sub) {
                case "goto" -> filter(args[6], List.of("0.5", "1", "2", "3"));
                case "chase" -> filter(args[6], List.of("0.03", "0.06", "0.1", "0.2"));
                case "follow" -> filter(args[6], speeds);
                case "orbit" -> filter(args[6], List.of("0", "1", "2", "4", "6"));
                case "target", "targetdown" -> filter(args[6], List.of("0.03", "0.06", "0.1", "0.2"));
                case "lookat", "downlook" -> isDouble(args[3]) ? filter(args[6], List.of("4", "6", "8", "12")) : Collections.emptyList();
                case "color" -> args[3].equalsIgnoreCase("custom")
                        ? filter(args[6], List.of("0", "32", "64", "128", "192", "255"))
                        : Collections.emptyList();
                case "finger" -> filter(args[6], easingNames());
                case "joint" -> filter(args[6], List.of("0.25", "0.5", "1", "2"));
                default -> Collections.emptyList();
            };
        }

        if (args.length == 8) {
            return switch (sub) {
                case "goto" -> filter(args[7], easingNames());
                case "follow" -> filter(args[7], List.of("0.03", "0.06", "0.1", "0.2"));
                case "orbit" -> filter(args[7], speeds);
                case "target", "targetdown" -> filter(args[7], List.of("4", "6", "8", "12"));
                case "joint" -> filter(args[7], easingNames());
                default -> Collections.emptyList();
            };
        }

        if (args.length == 9 && sub.equals("orbit")) {
            return filter(args[8], List.of("0.03", "0.06", "0.1", "0.2"));
        }
        return Collections.emptyList();
    }

    private static List<String> easingNames() {
        return List.of("linear", "smooth", "easein", "easeout", "easeinout");
    }

    private static List<String> merge(List<String> first, List<String> second) {
        List<String> out = new ArrayList<>(first);
        for (String item : second) if (!out.contains(item)) out.add(item);
        return out;
    }

    private List<String> playerNames(String prefix, boolean allowOff) {
        List<String> options = new ArrayList<>();
        for (Player online : plugin.getServer().getOnlinePlayers()) options.add(online.getName());
        if (allowOff) options.add("off");
        return filter(prefix, options);
    }


    private static List<String> filter(String input, List<String> options) {
        String lower = input.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(option);
            }
        }
        return result;
    }

    private enum Axis {
        YAW,
        PITCH,
        ROLL
    }
}
