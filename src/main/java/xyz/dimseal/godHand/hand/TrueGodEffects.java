package xyz.dimseal.godHand.hand;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/** Sounds are cues; target chat is reserved for meaningful action moments. */
public final class TrueGodEffects {

    private TrueGodEffects() {}

    public static void presence(Player target) {
        sound(target, "minecraft:entity.warden.heartbeat", 0.78f, 0.52f);
    }

    public static void slamStart(Player target) {
        sound(target, "minecraft:block.sculk_shrieker.shriek", 0.92f, 0.58f);
    }

    public static void slamWindup(Player target) {
        sound(target, "minecraft:entity.warden.sonic_charge", 1.10f, 0.50f);
        sound(target, "minecraft:entity.wither.shoot", 0.62f, 0.38f);
    }

    public static void slamImpact(Player target, boolean hit) {
        if (target == null || !hit) return;
        GodHandMessages.send(target, "slam_hit", "§4§lTHE HAND OF GOD HAS STRUCK YOU.");
        sound(target, "minecraft:entity.warden.sonic_boom", 1.45f, 0.55f);
    }

    public static void grabStart(Player target) {
        sound(target, "minecraft:entity.warden.heartbeat", 0.90f, 0.58f);
    }
    public static void grabClosing(Player target) {
        sound(target, "minecraft:block.sculk_shrieker.shriek", 0.68f, 0.72f);
    }
    public static void grabbed(Player target) {
        sound(target, "minecraft:entity.warden.roar", 0.98f, 0.46f);
    }

    public static void transportStart(Player target) { sound(target, "minecraft:entity.warden.heartbeat", 0.74f, 0.48f); }
    public static void transportTaken(Player target) { sound(target, "minecraft:entity.wither.shoot", 0.55f, 0.42f); }
    public static void transportArrive(Player target) { sound(target, "minecraft:entity.wither.shoot", 0.46f, 0.74f); }

    /** Surface-beam Judgment has no warning chat. */
    public static void judgmentStart(Player target) {
        sound(target, "minecraft:entity.warden.heartbeat", 0.84f, 0.44f);
    }
    public static void judgmentCharge(Player target) {
        sound(target, "minecraft:entity.warden.sonic_charge", 0.96f, 0.58f);
    }
    public static void judgmentBeamHit(Player target) {
        if (target == null) return;
        GodHandMessages.send(target, "judgment_hit", "§4§lTHE HAND OF GOD HAS PASSED JUDGMENT UPON YOU.");
        sound(target, "minecraft:entity.warden.sonic_boom", 1.18f, 0.78f);
    }
    // Legacy call retained for compatibility with older tests/classes.
    public static void judgmentImpact(Player target, boolean hit) { if (hit) judgmentBeamHit(target); }

    public static void forceSlapStart(Player target) {
        sound(target, "minecraft:entity.warden.heartbeat", 0.86f, 0.48f);
    }
    public static void forceSlapCharge(Player target) {
        sound(target, "minecraft:entity.warden.sonic_charge", 1.12f, 0.44f);
        sound(target, "minecraft:entity.wither.shoot", 0.62f, 0.36f);
    }
    public static void forceSlapImpact(Player target, boolean hit) {
        if (!hit || target == null) return;
        GodHandMessages.send(target, "force_slap_hit", "§4§lTHE HAND OF GOD HAS STRUCK YOU.");
        sound(target, "minecraft:entity.warden.sonic_boom", 1.42f, 0.64f);
    }

    public static void slapStart(Player target) {}
    public static void slapCharge(Player target) {}
    public static void slapImpact(Player target, boolean hit) {
        if (target == null || !hit) return;
        GodHandMessages.send(target, "slap_hit", "§4THE HAND CASTS YOU ASIDE.");
        sound(target, "minecraft:entity.warden.sonic_boom", 1.30f, 0.78f);
    }

    public static void cycloneStart(Player target) {
        sound(target, "minecraft:entity.wither.shoot", 0.62f, 0.24f);
    }
    public static void cycloneCharge(Player target) {
        sound(target, "minecraft:entity.warden.sonic_charge", 0.95f, 0.35f);
        sound(target, "minecraft:entity.wither.shoot", 0.72f, 0.22f);
    }
    public static void cycloneImpact(Player target) {
        if (target == null) return;
        GodHandMessages.send(target, "cyclone_hit", "§4§lTHE CYCLONE OF THE HAND TEARS THROUGH YOU.");
        sound(target, "minecraft:entity.warden.sonic_boom", 1.45f, 0.48f);
    }

    public static void breachStart(Player target) {
        sound(target, "minecraft:block.sculk_shrieker.shriek", 0.72f, 0.46f);
        sound(target, "minecraft:entity.wither.ambient", 0.38f, 0.31f);
    }

    public static void tossStart(Player target) {
        sound(target, "minecraft:entity.warden.heartbeat", 0.82f, 0.50f);
    }
    public static void tossThrow(Player target) {
        sound(target, "minecraft:entity.wither.shoot", 1.05f, 0.38f);
    }

    public static void blessStart(Player target) {
        sound(target, "minecraft:block.amethyst_block.chime", 0.70f, 1.18f);
        sound(target, "minecraft:entity.allay.ambient_with_item", 0.42f, 1.05f);
    }
    public static void blessCaptured(Player target) {
        sound(target, "minecraft:block.beacon.activate", 0.82f, 1.18f);
        sound(target, "minecraft:entity.allay.item_given", 0.68f, 1.22f);
    }
    public static void blessPulse(Player target, int stage) {
        float pitch = Math.min(1.85f, 1.02f + stage * 0.16f);
        sound(target, "minecraft:block.amethyst_block.chime", 0.78f, pitch);
        sound(target, "minecraft:entity.experience_orb.pickup", 0.34f, Math.min(1.95f, pitch + 0.12f));
    }
    public static void blessComplete(Player target) {
        if (target == null) return;
        GodHandMessages.send(target, "bless_complete", "§a§lTHE HAND OF GOD BLESSES YOU. GO WITH STRENGTH AND GRACE FOR FIVE MINUTES.");
        sound(target, "minecraft:block.beacon.power_select", 0.95f, 1.28f);
        sound(target, "minecraft:block.amethyst_block.resonate", 0.82f, 1.42f);
        sound(target, "minecraft:entity.allay.ambient_with_item", 0.58f, 1.18f);
    }

    public static void sanctuaryStart(Player target) {
        sound(target, "minecraft:block.beacon.activate", 0.70f, 1.25f);
        sound(target, "minecraft:block.amethyst_block.chime", 0.58f, 1.38f);
    }
    public static void sanctuaryArrived(Player target) {
        if (target == null) return;
        GodHandMessages.send(target, "sanctuary_arrive", "§e§lTHE HAND OF GOD SHELTERS YOU BENEATH ITS PALM.");
        sound(target, "minecraft:block.beacon.ambient", 0.46f, 1.35f);
    }
    public static void sanctuaryPulse(Player target) {
        sound(target, "minecraft:entity.experience_orb.pickup", 0.18f, 1.72f);
    }
    public static void sanctuaryComplete(Player target) {
        if (target == null) return;
        GodHandMessages.send(target, "sanctuary_complete", "§eTHE HAND OF GOD LEAVES YOU RESTORED AND GUARDED.");
        sound(target, "minecraft:block.beacon.power_select", 0.82f, 1.42f);
        sound(target, "minecraft:block.amethyst_block.resonate", 0.65f, 1.55f);
    }

    public static void spankStart(Player target) {
        sound(target, "minecraft:entity.warden.heartbeat", 0.72f, 0.45f);
    }
    public static void spankManifest(Player target) {
        sound(target, "minecraft:entity.wither.shoot", 0.55f, 0.52f);
    }
    public static void spankHit(Player target, int hit, boolean finalHit) {
        if (target == null) return;
        sound(target, "minecraft:entity.player.attack.knockback", 1.15f, finalHit ? 0.72f : 0.94f + hit * 0.035f);
        sound(target, "minecraft:entity.player.attack.strong", 0.72f, finalHit ? 0.62f : 0.88f);
        if (finalHit) {
            GodHandMessages.send(target, "spank_final", "§4§lTHE FINAL SPANK CASTS YOU FROM GOD'S GRASP.");
            sound(target, "minecraft:entity.warden.sonic_boom", 1.18f, 0.74f);
        }
    }

    public static void rageStart(Player target) {
        sound(target, "minecraft:entity.warden.heartbeat", 0.92f, 0.42f);
        sound(target, "minecraft:entity.wither.ambient", 0.62f, 0.52f);
    }
    public static void rageRoar(Player target, int beat) {
        float pitch = Math.max(0.42f, 0.66f - beat * 0.045f);
        sound(target, "minecraft:entity.warden.roar", 1.20f, pitch);
    }
    public static void rageDash(Player target, int beat) {
        sound(target, "minecraft:entity.player.attack.knockback", 1.05f, 0.82f + beat * 0.06f);
        sound(target, "minecraft:entity.wither.shoot", 0.78f, 0.52f + beat * 0.04f);
        sound(target, "minecraft:entity.warden.roar", 0.68f, 0.54f);
    }
    public static void rageImpact(Player target, int beat) {
        sound(target, "minecraft:entity.player.attack.strong", 1.10f, 0.72f + beat * 0.05f);
        sound(target, "minecraft:entity.warden.roar", 0.92f, 0.46f + beat * 0.02f);
    }
    public static void rageGrabbed(Player target) {
        sound(target, "minecraft:entity.warden.roar", 1.18f, 0.40f);
        sound(target, "minecraft:entity.wither.shoot", 0.86f, 0.34f);
    }
    public static void rageThrowWindup(Player target) {
        sound(target, "minecraft:entity.warden.sonic_charge", 0.88f, 0.48f);
        sound(target, "minecraft:entity.warden.roar", 0.82f, 0.38f);
    }
    public static void rageThrow(Player target) {
        if (target == null) return;
        GodHandMessages.send(target, "rage_throw", "§4§lTHE HAND OF GOD HURLS YOU FROM ITS GRASP.");
        sound(target, "minecraft:entity.warden.sonic_boom", 1.28f, 0.62f);
        sound(target, "minecraft:entity.player.attack.knockback", 1.20f, 0.66f);
    }

    public static void clapStart(Player target) {
        sound(target, "minecraft:entity.warden.heartbeat", 0.72f, 0.48f);
    }
    public static void clapCharge(Player target) {
        sound(target, "minecraft:entity.warden.sonic_charge", 0.88f, 0.62f);
    }
    public static void clapImpact(Player target) {
        if (target == null) return;
        GodHandMessages.send(target, "clap_hit", "§f§lTHE HANDS OF GOD CLOSE LIKE THUNDER AROUND YOU.");
        sound(target, "minecraft:entity.lightning_bolt.thunder", 2.8f, 0.72f);
        sound(target, "minecraft:entity.generic.explode", 1.85f, 0.82f);
        sound(target, "minecraft:entity.warden.sonic_boom", 1.35f, 0.86f);
    }

    public static void poundStart(Player target) {
        sound(target, "minecraft:entity.warden.heartbeat", 0.88f, 0.42f);
        sound(target, "minecraft:entity.wither.ambient", 0.42f, 0.62f);
    }
    public static void poundWindup(Player target, int hit) {
        float pitch = Math.min(1.15f, 0.58f + hit * 0.035f);
        sound(target, "minecraft:entity.warden.sonic_charge", 0.72f, pitch);
        sound(target, "minecraft:entity.wither.shoot", 0.52f, Math.max(0.42f, pitch - 0.12f));
    }
    public static void poundImpact(Player target, int hit) {
        sound(target, "minecraft:entity.generic.explode", 1.35f, 0.78f + Math.min(0.18f, hit * 0.015f));
        sound(target, "minecraft:entity.player.attack.strong", 1.10f, 0.62f);
        sound(target, "minecraft:entity.warden.sonic_boom", 0.72f, 0.72f);
    }
    public static void poundComplete(Player target, int hits) {
        if (target == null) return;
        GodHandMessages.send(target, "pound_complete", "§4THE HANDS OF GOD CEASE THEIR POUNDING.");
        sound(target, "minecraft:entity.warden.roar", 0.58f, 0.52f);
    }

    public static void waveStart(Player target) {
        sound(target, "minecraft:block.amethyst_block.chime", 0.34f, 1.42f);
    }
    public static void waveBeat(Player target) {
        sound(target, "minecraft:entity.allay.ambient_without_item", 0.12f, 1.55f);
    }
    public static void thumbStart(Player target, boolean up) {
        sound(target, "minecraft:block.amethyst_block.chime", 0.32f, up ? 1.55f : 0.72f);
    }
    public static void thumbDisplayed(Player target, boolean up) {
        sound(target, up ? "minecraft:entity.experience_orb.pickup" : "minecraft:block.note_block.bass",
                0.42f, up ? 1.48f : 0.72f);
    }

    public static void giveBirdMessage(Player target) {
        if (target == null) return;
        GodHandMessages.send(target, "givebird_attack", "§4§lF U");
        sound(target, "minecraft:entity.lightning_bolt.thunder", 1.35f, 0.62f);
    }
    public static void giveBirdLightning(Player target) {
        sound(target, "minecraft:entity.lightning_bolt.impact", 0.82f, 0.72f);
    }

    public static void juggleThrow(Player target, int cycle) {
        sound(target, "minecraft:entity.player.attack.knockback", 0.78f, 1.02f + Math.min(0.35f, cycle * 0.02f));
        sound(target, "minecraft:entity.breeze.jump", 0.56f, 0.88f);
    }
    public static void juggleCatch(Player target, int cycle) {
        sound(target, "minecraft:entity.player.attack.strong", 0.62f, 1.20f);
        sound(target, "minecraft:block.amethyst_block.chime", 0.34f, 0.86f + Math.min(0.45f, cycle * 0.02f));
    }

    public static void guardStart(Player target) {
        if (target == null) return;
        GodHandMessages.send(target, "guard_start", "§aTHE HAND OF GOD STANDS GUARD BESIDE YOU.");
        sound(target, "minecraft:block.beacon.activate", 0.52f, 1.24f);
    }
    public static void guardHit(Player owner, int attackIndex) {
        if (owner == null) return;
        String sound = attackIndex == 0 ? "minecraft:entity.player.attack.knockback"
                : attackIndex == 1 ? "minecraft:entity.player.attack.strong"
                : "minecraft:entity.generic.explode";
        sound(owner, sound, 0.62f, attackIndex == 2 ? 1.32f : 1.06f);
    }

    public static void punchStart(Player target) { sound(target, "minecraft:entity.warden.heartbeat", 0.92f, 0.42f); }
    public static void punchCharge(Player target) {
        sound(target, "minecraft:entity.wither.shoot", 0.82f, 0.31f);
        sound(target, "minecraft:entity.warden.sonic_charge", 0.96f, 0.48f);
    }
    public static void punchImpact(Player target, boolean hit) {
        if (target == null || !hit) return;
        GodHandMessages.send(target, "punch_hit", "§4§lTHE HAND OF GOD HAS STRUCK YOU.");
        sound(target, "minecraft:entity.warden.sonic_boom", 1.55f, 0.42f);
    }

    public static void smashStart(Player target, Location at) {
        if (target != null) sound(target, "minecraft:entity.warden.heartbeat", 1.0f, 0.38f);
        else worldSound(at, "minecraft:entity.wither.ambient", 1.5f, 0.30f);
    }
    public static void smashCharge(Player target, Location at) {
        if (target != null) {
            sound(target, "minecraft:entity.warden.sonic_charge", 1.25f, 0.35f);
            sound(target, "minecraft:entity.wither.shoot", 0.90f, 0.28f);
        } else worldSound(at, "minecraft:entity.warden.sonic_charge", 2.4f, 0.35f);
    }
    public static void smashImpact(Player target, Location at) {
        if (target != null) sound(target, "minecraft:entity.warden.sonic_boom", 1.55f, 0.34f);
        worldSound(at, "minecraft:entity.wither.spawn", 3.4f, 0.42f);
    }

    /** The sole non-hit chat cue requested for stalking. */
    public static void stalkStart(Player target) {
        GodHandMessages.send(target, "stalk_start", "§4THE HAND OF GOD IS WATCHING YOU.");
        sound(target, "minecraft:entity.warden.heartbeat", 0.68f, 0.46f);
    }
    public static void stalkWatch(Player target) {}
    public static void stalkOrbit(Player target) {}
    public static void stalkFollow(Player target) {}
    public static void chaseStart(Player target) { sound(target, "minecraft:entity.warden.heartbeat", 0.76f, 0.60f); }
    public static void stalkChase(Player target) { sound(target, "minecraft:entity.wither.shoot", 0.48f, 0.34f); }
    public static void release(Player target) { sound(target, "minecraft:entity.wither.shoot", 0.50f, 0.64f); }

    private static void sound(Player target, String key, float volume, float pitch) {
        if (target == null || !target.isOnline()) return;
        target.playSound(target.getLocation(), key, volume, pitch);
    }
    private static void worldSound(Location at, String key, float volume, float pitch) {
        if (at == null) return;
        World world = at.getWorld();
        if (world == null) return;
        world.playSound(at, key, volume, pitch);
    }
}
