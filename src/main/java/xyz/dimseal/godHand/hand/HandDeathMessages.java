package xyz.dimseal.godHand.hand;

import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight death attribution for GodHand attacks.
 *
 * Direct damage marks are removed immediately if the hit did not kill. Throw
 * marks intentionally survive for a few seconds so a later fall/impact can
 * still receive the correct Hand-specific death message.
 */
public final class HandDeathMessages {

    public enum Style {
        SLAM,
        JUDGMENT,
        FORCE_SLAP,
        PUNCH,
        SLAP,
        CYCLONE,
        BREACH,
        SPANK,
        RAGE,
        CLAP,
        SMASH,
        THROW,
        TOSS,
        JUGGLE,
        GUARD
    }

    private record Mark(Style style, long expiresAt) {}

    private static final Map<UUID, Mark> MARKS = new ConcurrentHashMap<>();

    private HandDeathMessages() {}

    public static void damage(Player player, double amount, Style style) {
        if (player == null || amount <= 0.0) return;
        markDirect(player, style);
        player.damage(amount);
        if (!player.isDead()) clear(player);
    }

    public static void damage(Player player, double amount, DamageSource source, Style style) {
        if (player == null || amount <= 0.0) return;
        markDirect(player, style);
        player.damage(amount, source);
        if (!player.isDead()) clear(player);
    }

    /** Marks an indirect launch/fall death for a longer window. */
    public static void markThrow(Player player, Style style) {
        if (player == null || style == null) return;
        MARKS.put(player.getUniqueId(), new Mark(style, System.currentTimeMillis() + 15_000L));
    }

    /** Used when a heavy attack may set health directly after normal damage. */
    public static void markDirect(Player player, Style style) {
        if (player == null || style == null) return;
        MARKS.put(player.getUniqueId(), new Mark(style, System.currentTimeMillis() + 2_000L));
    }

    public static void clear(Player player) {
        if (player != null) MARKS.remove(player.getUniqueId());
    }

    static Style consume(Player player) {
        if (player == null) return null;
        Mark mark = MARKS.remove(player.getUniqueId());
        if (mark == null || mark.expiresAt() < System.currentTimeMillis()) return null;
        return mark.style();
    }

    static String message(Player player, Style style) {
        String fallback = switch (style) {
            case SLAM -> "{player} was crushed beneath the Hand of God.";
            case JUDGMENT -> "{player} was consumed by the judgment of the Hand of God.";
            case FORCE_SLAP -> "{player} was struck down by the Hand of God.";
            case PUNCH -> "{player} was broken by the fist of the Hand of God.";
            case SLAP -> "{player} was cast aside by the Hand of God.";
            case CYCLONE -> "{player} was torn apart by the cyclone of the Hand of God.";
            case BREACH -> "{player} was hunted down in the dark by the Hand of God.";
            case SPANK -> "{player} could not endure the Hand of God's spank sequence.";
            case RAGE -> "{player} was torn apart by the rage of the Hand of God.";
            case CLAP -> "{player} was crushed between the Hands of God.";
            case SMASH -> "{player} was obliterated by the Hand of God.";
            case THROW -> "{player} was hurled to their death by the Hand of God.";
            case TOSS -> "{player} fell after being tossed by the Hand of God.";
            case JUGGLE -> "{player} fell from the impossible juggle of the Hands of God.";
            case GUARD -> "{player} was slain by the guardian Hand of God.";
        };
        return GodHandMessages.death(player, style.name().toLowerCase(java.util.Locale.ROOT), fallback);
    }
}
