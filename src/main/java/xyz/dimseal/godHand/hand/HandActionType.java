package xyz.dimseal.godHand.hand;

import java.util.Locale;

public enum HandActionType {
    IDLE,
    SLAM,
    GRAB,
    JUDGMENT,
    FORCE_SLAP,
    PUNCH,
    SLAP,
    CYCLONE,
    BREACH,
    TOSS,
    BLESS,
    SANCTUARY,
    SPANK,
    RAGE,
    CLAP,
    POUND,
    WAVE,
    THUMBS_UP,
    THUMBS_DOWN,
    BIRD,
    GIVE_BIRD,
    JUGGLE,
    GUARD,
    SMASH,
    TRANSPORT,
    MOVE_TO,
    STALK,
    CHASE,
    HOLDING,
    RELEASING,
    THROWING;

    public String commandName() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
