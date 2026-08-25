package xyz.dimseal.godHand.hand;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Camera-free physical grip carrier.
 *
 * deliberately avoids teleporting the carrier every server tick during
 * normal movement. Repeated entity teleports are interpolated differently from
 * ItemDisplays on the client, which made a mounted player visibly trail a fast
 * Hand. The carrier is now velocity-tethered to the desired grip point and only
 * hard-corrected if it falls materially out of sync.
 */
public final class HandGripCarrier {

    private static final double DEFAULT_PASSENGER_Y_OFFSET = 0.72;
    private static final double HARD_CORRECTION_DISTANCE = 5.25;
    private static final double SOFT_SNAP_DISTANCE = 2.25;
    private static final double MAX_TETHER_SPEED = 10.0;

    private ArmorStand carrier;
    private double passengerYOffset = DEFAULT_PASSENGER_Y_OFFSET;
    private boolean calibrated;
    private Location lastFeetAnchor;

    public void attach(Player player, Location feetAnchor) {
        cleanup();
        if (player == null || !player.isOnline() || feetAnchor == null || feetAnchor.getWorld() == null) {
            return;
        }

        player.leaveVehicle();

        Location vehicleAnchor = carrierLocationFor(feetAnchor);
        carrier = feetAnchor.getWorld().spawn(vehicleAnchor, ArmorStand.class);
        carrier.setInvisible(true);
        carrier.setMarker(true);
        carrier.setGravity(false);
        carrier.setInvulnerable(true);
        carrier.setSilent(true);
        carrier.setSmall(true);
        carrier.setBasePlate(false);
        carrier.setArms(false);
        carrier.setCollidable(false);
        carrier.setPersistent(false);
        carrier.addScoreboardTag("godhand_grip_carrier");
        carrier.addPassenger(player);

        player.setFallDistance(0.0f);
        calibrateSeatOffset(player);
        hardMove(feetAnchor);
        lastFeetAnchor = feetAnchor.clone();
    }

    public void update(Player player, Location feetAnchor) {
        if (player == null || !player.isOnline() || feetAnchor == null || feetAnchor.getWorld() == null) {
            cleanup();
            return;
        }

        if (carrier == null || !carrier.isValid() || carrier.getWorld() != feetAnchor.getWorld()) {
            attach(player, feetAnchor);
            return;
        }

        calibrateSeatOffset(player);

        Location desiredVehicle = carrierLocationFor(feetAnchor);
        Location currentVehicle = carrier.getLocation();
        Vector error = desiredVehicle.toVector().subtract(currentVehicle.toVector());
        double distance = error.length();

        // When the requested grip anchor has stopped changing, pin the carrier
        // exactly into the solved cup instead of leaving the final centimeters
        // to ArmorStand velocity/drag. This is intentionally a stationary-only
        // correction, so it cannot reintroduce the moving teleport jitter.
        boolean stationaryAnchor = lastFeetAnchor != null
                && lastFeetAnchor.getWorld() != null
                && lastFeetAnchor.getWorld().equals(feetAnchor.getWorld())
                && feetAnchor.distanceSquared(lastFeetAnchor) < 0.0004;

        // A velocity tether gives the client a continuous vehicle path instead
        // of a stream of unrelated teleports. The ItemDisplay grip uses a
        // matching one-tick render profile, so rider and fingers move together.
        if (stationaryAnchor && distance > 0.045 && Double.isFinite(distance)) {
            carrier.teleport(desiredVehicle);
            carrier.setVelocity(new Vector());
        } else if (stationaryAnchor) {
            carrier.setVelocity(new Vector());
        } else if (distance > HARD_CORRECTION_DISTANCE || !Double.isFinite(distance)) {
            carrier.teleport(desiredVehicle);
            carrier.setVelocity(new Vector());
        } else {
            // Important: do NOT add the Hand's frame step on top of this
            // error. At this point the error already *is* the displacement
            // from the carrier's current position to this tick's grip anchor.
            // Adding handStep again causes overshoot on one tick and a reverse
            // correction on the next, which is visible as carrier/hand jitter.
            // Moving by exactly this displacement during the next client tick
            // matches the ItemDisplay's one-tick interpolation target.
            // ArmorStand velocity experiences normal entity integration/drag.
            // A very small lead compensates for that without reintroducing the
            // overshoot that came from adding a whole
            // extra Hand frame-step.
            Vector desiredStep = error.multiply(1.035);
            if (desiredStep.lengthSquared() > MAX_TETHER_SPEED * MAX_TETHER_SPEED) {
                desiredStep.normalize().multiply(MAX_TETHER_SPEED);
            }
            carrier.setVelocity(desiredStep);

            // Small corrections are intentionally not teleports. A slightly
            // larger drift gets a rare snap before it becomes visually obvious.
            if (distance > SOFT_SNAP_DISTANCE && (lastFeetAnchor == null || feetAnchor.distance(lastFeetAnchor) < 0.10)) {
                carrier.teleport(desiredVehicle);
                carrier.setVelocity(new Vector());
            }
        }

        if (player.getVehicle() != carrier) {
            player.leaveVehicle();
            carrier.addPassenger(player);
            calibrateSeatOffset(player);
            hardMove(feetAnchor);
        }

        player.setFallDistance(0.0f);
        lastFeetAnchor = feetAnchor.clone();
    }

    public void release(Player player, Location feetAnchor) {
        ArmorStand existing = carrier;
        carrier = null;

        if (existing != null && existing.isValid()) {
            existing.setVelocity(new Vector());
            if (player != null) existing.removePassenger(player);
            existing.remove();
        }

        calibrated = false;
        passengerYOffset = DEFAULT_PASSENGER_Y_OFFSET;
        lastFeetAnchor = null;

        if (player != null && player.isOnline() && !player.isDead() && feetAnchor != null && feetAnchor.getWorld() != null) {
            Location release = feetAnchor.clone();
            Location current = player.getLocation();
            release.setYaw(current.getYaw());
            release.setPitch(current.getPitch());
            player.teleport(release);
            player.setFallDistance(0.0f);
        }
    }

    public void cleanup() {
        ArmorStand existing = carrier;
        carrier = null;
        if (existing != null && existing.isValid()) {
            existing.setVelocity(new Vector());
            existing.eject();
            existing.remove();
        }
        calibrated = false;
        passengerYOffset = DEFAULT_PASSENGER_Y_OFFSET;
        lastFeetAnchor = null;
    }

    /**
     * Current carrier-derived feet anchor for rendering the visible hand around
     * the same physical object that owns the passenger. A small fraction of the
     * current velocity is included so one-tick ItemDisplay interpolation follows
     * the moving vehicle rather than visibly trailing it.
     */
    public Location getVisualFeetAnchor() {
        if (carrier == null || !carrier.isValid()) return null;
        Location feet = carrier.getLocation().clone().add(0.0, passengerYOffset, 0.0);
        Vector velocity = carrier.getVelocity();
        if (velocity != null && velocity.lengthSquared() > 0.04) {
            // Only lead an actually moving carrier. Tiny settle/correction
            // velocities used to shift the rendered claw around an otherwise
            // stationary rider, making the grab look orientation-dependent.
            feet.add(velocity.clone().multiply(0.30));
        }
        return feet;
    }

    public boolean isActive() {
        return carrier != null && carrier.isValid();
    }

    private void calibrateSeatOffset(Player player) {
        if (carrier == null || !carrier.isValid() || player == null || !player.isOnline()) return;
        if (player.getVehicle() != carrier) return;

        double measured = player.getLocation().getY() - carrier.getLocation().getY();
        if (!Double.isFinite(measured) || measured < -0.25 || measured > 3.0) return;

        // Once mounted, Bukkit reports the passenger's server-space feet
        // location relative to the carrier. Use that exact offset instead of
        // slowly easing toward it; easing the seat calibration itself creates
        // a visible vertical drift for several ticks after a grab.
        passengerYOffset = measured;
        calibrated = true;
    }

    private void hardMove(Location feetAnchor) {
        if (carrier == null || !carrier.isValid()) return;
        carrier.teleport(carrierLocationFor(feetAnchor));
        carrier.setVelocity(new Vector());
    }

    private Location carrierLocationFor(Location feetAnchor) {
        Location vehicle = feetAnchor.clone().subtract(0.0, passengerYOffset, 0.0);
        vehicle.setYaw(0.0f);
        vehicle.setPitch(0.0f);
        return vehicle;
    }
}
